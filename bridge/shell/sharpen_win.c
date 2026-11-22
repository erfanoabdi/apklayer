/* The window side of the upscale pass (sharpen.h): a window whose app asked for the filter (`apklayer settings` render-filter sharp: DMABUF_FRAME's
 * flags word) and renders at a size other than the window's pixels is shown through sharpen.c's output ring instead of the app's own buffer. The app's
 * buffers are still imported as wl_buffers (dmabuf.c): anything failing here shows them, as without the filter.
 *
 * The pass is prepared in a helper thread (EGL, shaders: ~100 ms): an app sends a burst of frames when it starts and the display sink, which holds
 * six acquired buffers at most, wedges for good if the shell does not hand buffers back during it ("max acquired buffer count reached"). So the first
 * frames take the normal path and the app's dmabufs are kept (dups) until the helper is done; sharpen_win_poll (the main loop) then imports them and
 * shows the current frame through the pass. */
#include <pthread.h>
#include <string.h>
#include <unistd.h>
#include "shell.h"

#ifdef HAVE_SHARPEN
#include "sharpen.h"

#define OUT_SLOTS 5
#define DRM_FORMAT_XRGB8888 0x34325258u
enum { S_NONE, S_PREPARING, S_READY, S_OFF };

struct kept {  /* one app buffer's dmabuf, until the pass can import it */
    int valid, n, w, h;
    int fd[4];
    uint32_t fourcc, stride[4], offset[4];
    uint64_t modifier;
};

struct swin {
    struct sharpen *s;
    struct wl_buffer *wl[OUT_SLOTS];
    struct kept kept[DBUF_MAX];
    int w[DBUF_MAX], h[DBUF_MAX];  /* the app's buffers' sizes, once imported */
    int state;
    pthread_t thread;
    int thread_done, thread_rc;
    struct kept sample;             /* the first buffer, for the helper (its own dups) */
    int said_ok, said_fail, said_skip, ok_n, fail_n;
    long long pending_ms;           /* when the waiting frame started to wait (the watchdog in sharpen_win_poll) */
    int pending;                    /* an app buffer waiting for a free output buffer (-1: none; held back until it is drawn) */
    int keep;                       /* the buffer being shown again is the compositor's already: not given back here */
    struct window *win;
};

static void kept_close(struct kept *k)
{
    for (int i = 0; i < k->n && i < 4; i++) if (k->fd[i] >= 0) close(k->fd[i]);
    memset(k, 0, sizeof *k);
}

static void give_back(struct window *w, int buf, const char *why)
{
    if (getenv("APKLAYER_RUNTIME_DEBUG")) fprintf(stderr, "[shell] render filter: release buffer %d (%s)\n", buf, why);
    send_msg(w->client, MSG_DMABUF_RELEASE, (int32_t[]){ w->id, buf }, 2);
}

static void slot_released(void *data, struct wl_buffer *b)
{
    struct swin *sw = data;
    for (int i = 0; i < OUT_SLOTS; i++)
        if (sw->wl[i] == b) sharpen_slot_release(sw->s, i);
    if (sw->pending >= 0) {  /* the newest frame waited for a free output buffer: draw it now (as the app's own if the pass cannot) */
        int buf = sw->pending;
        sw->pending = -1;
        static const int32_t whole[4] = { 0, 0, INT32_MAX, INT32_MAX };
        if (getenv("APKLAYER_RUNTIME_DEBUG")) fprintf(stderr, "[shell] %s: render filter: the waiting frame (buffer %d) is drawn\n", sw->win->title, buf);
        window_dmabuf_show(sw->win, buf, whole);
    }
}
static const struct wl_buffer_listener slot_listener = { slot_released };

static void *prepare_thread(void *arg)
{
    struct swin *sw = arg;
    struct kept *k = &sw->sample;
    sw->thread_rc = sharpen_prepare(sw->s, k->fourcc, k->w, k->h, k->n, k->fd, k->stride, k->offset, k->modifier);
    __atomic_store_n(&sw->thread_done, 1, __ATOMIC_RELEASE);
    return NULL;
}

static int import_kept(struct swin *sw, int buf)
{
    struct kept *k = &sw->kept[buf];
    if (!k->valid) return -1;
    int rc = sharpen_import(sw->s, buf, k->fourcc, k->w, k->h, k->n, k->fd, k->stride, k->offset, k->modifier);
    if (rc == 0) { sw->w[buf] = k->w; sw->h[buf] = k->h; }
    kept_close(k);
    return rc;
}

void sharpen_win_import(struct window *w, int buf, const int32_t *m, const int *fds, int nfds)
{
    if (!w->filter || !dmabuf_mgr || nfds < 1 || buf < 0 || buf >= DBUF_MAX) return;
    struct swin *sw = w->sharp;
    if (!sw) {
        sw = w->sharp = calloc(1, sizeof *sw);
        if (!sw) return;
        sw->win = w;
        sw->pending = -1;
        sw->s = sharpen_open();
        if (!sw->s) { sw->state = S_OFF; return; }
        sharpen_accept(sw->s, dmabuf_xrgb_mods, dmabuf_xrgb_n);
    }
    if (sw->state == S_OFF) return;
    if (sw->pending == buf) sw->pending = -1;  /* the app made a new buffer of this index (a resize): the waiting frame is gone */
    struct kept *k = &sw->kept[buf];
    kept_close(k);
    k->valid = 1; k->n = nfds > 4 ? 4 : nfds; k->w = m[3]; k->h = m[4]; k->fourcc = (uint32_t)m[2];
    k->modifier = ((uint64_t)(uint32_t)m[7] << 32) | (uint32_t)m[6];
    for (int i = 0; i < k->n; i++) { k->fd[i] = dup(fds[i]); k->stride[i] = (uint32_t)m[8 + i]; k->offset[i] = (uint32_t)m[12 + i]; }
    if (sw->state == S_NONE) {  /* the helper prepares the pass from this first buffer while the frames go the normal way */
        struct kept *s = &sw->sample;
        *s = *k;
        for (int i = 0; i < s->n; i++) s->fd[i] = dup(k->fd[i]);
        sw->thread_done = 0;
        if (pthread_create(&sw->thread, NULL, prepare_thread, sw) == 0) sw->state = S_PREPARING;
        else { kept_close(s); sw->state = S_OFF; }
    } else if (sw->state == S_READY && import_kept(sw, buf) < 0) {
        fprintf(stderr, "[shell] %s: render filter: the GPU could not import a buffer of the app (shown as it is)\n", w->title);
        sw->w[buf] = 0;
    }
}

/* the main loop: the helper may be done; then the kept buffers are imported and the frame on screen is shown again through the pass */
void sharpen_win_poll(void)
{
    for (int i = 0; i < MAX_WINDOWS; i++) {
        struct window *w = &windows[i];
        struct swin *sw = w->client ? w->sharp : NULL;
        /* watchdog: a frame waiting for a free output buffer for over 100 ms (the compositor releases late or not at all) is shown as the app's own, so the app never stalls */
        if (sw && sw->state == S_READY && sw->pending >= 0 && w->configured && now_ms() - sw->pending_ms > 100) {
            static const int32_t whole[4] = { 0, 0, INT32_MAX, INT32_MAX };
            int b = sw->pending;
            sw->pending = -1;
            if (getenv("APKLAYER_RUNTIME_DEBUG")) fprintf(stderr, "[shell] %s: render filter: no output buffer for 100 ms, buffer %d shown as it is\n", w->title, b);
            window_dmabuf_attach(w, b, whole);
            continue;
        }
        if (!sw || sw->state != S_PREPARING || !__atomic_load_n(&sw->thread_done, __ATOMIC_ACQUIRE)) continue;
        pthread_join(sw->thread, NULL);
        kept_close(&sw->sample);
        if (sw->thread_rc < 0) {
            fprintf(stderr, "[shell] %s: render filter off: no GPU takes the app's buffers\n", w->title);
            sw->state = S_OFF;
            if (sw->pending >= 0 && w->configured) { static const int32_t whole[4] = { 0, 0, INT32_MAX, INT32_MAX }; int b = sw->pending; sw->pending = -1; window_dmabuf_attach(w, b, whole); }
            continue;
        }
        sw->state = S_READY;
        int ok = 0;
        for (int b = 0; b < DBUF_MAX; b++) if (sw->kept[b].valid && import_kept(sw, b) == 0) ok++;
        if (getenv("APKLAYER_RUNTIME_DEBUG")) fprintf(stderr, "[shell] %s: render filter ready: %d buffers imported\n", w->title, ok);
        if (ok && sw->pending >= 0 && w->configured) {  /* the newest frame that waited for the pass */
            static const int32_t whole[4] = { 0, 0, INT32_MAX, INT32_MAX };
            int b = sw->pending;
            sw->pending = -1;
            window_dmabuf_show(w, b, whole);
        } else if (ok && w->plain_buf >= 0 && w->configured) {  /* the picture on screen is the app's own buffer: it goes through the pass now */
            static const int32_t whole[4] = { 0, 0, INT32_MAX, INT32_MAX };
            sw->keep = 1;
            window_dmabuf_show(w, w->plain_buf, whole);
            sw->keep = 0;
        }
        if (sw->pending >= 0 && w->configured) {  /* no buffer could be imported: the waiting frame is shown as it is */
            static const int32_t whole[4] = { 0, 0, INT32_MAX, INT32_MAX };
            int b = sw->pending;
            sw->pending = -1;
            window_dmabuf_attach(w, b, whole);
        }
    }
}

/* the poll timeout the main loop needs while a helper runs (ms, -1: none) */
int sharpen_win_timeout(void)
{
    for (int i = 0; i < MAX_WINDOWS; i++)
        if (windows[i].client && windows[i].sharp && (((struct swin *)windows[i].sharp)->state == S_PREPARING || ((struct swin *)windows[i].sharp)->pending >= 0)) return 25;
    return -1;
}

/* 1: the buffer was shown through the pass (the app's own goes back at once: the compositor never holds it) */
int sharpen_win_show(struct window *w, int buf)
{
    struct swin *sw = w->sharp;
    if (w->filter && sw && sw->state == S_PREPARING && !w->turn && w->plain_buf >= 0 && buf >= 0 && buf < DBUF_MAX && sw->kept[buf].valid) {
        /* the helper is still starting the pass and the window already shows something: only the newest frame waits (the others go back at once), so the
         * compositor never holds a pile of the app's buffers (the sink wedges at seven acquired). It is drawn through the pass when that is ready. */
        int lw = w->cfg_w > 0 ? w->cfg_w : w->w, lh = w->cfg_h > 0 ? w->cfg_h : w->h, scale = window_scale(w);
        int ow = (lw * scale + 50) / 100, oh = (lh * scale + 50) / 100;
        if (ow > 0 && oh > 0 && !(sw->kept[buf].w == ow && sw->kept[buf].h == oh)) {
            if (sw->pending >= 0 && sw->pending != buf) give_back(w, sw->pending, "replaced while the pass starts");
            sw->pending = buf;
            return 1;
        }
    }
    if (!w->filter || !sw || sw->state != S_READY || w->turn || buf < 0 || buf >= DBUF_MAX || sw->w[buf] <= 0) {
        if (w->filter && sw && sw->state == S_READY && !sw->said_skip++)
            fprintf(stderr, "[shell] %s: render filter skipped: %s\n", w->title, w->turn ? "turned window" : "buffer not imported");
        return 0;
    }
    int lw = w->cfg_w > 0 ? w->cfg_w : w->w, lh = w->cfg_h > 0 ? w->cfg_h : w->h, scale = window_scale(w);
    int ow = (lw * scale + 50) / 100, oh = (lh * scale + 50) / 100;
    if (ow <= 0 || oh <= 0 || (sw->w[buf] == ow && sw->h[buf] == oh)) return 0;  /* the window's own pixels: nothing to resample */
    int slot = -1, fresh = 0;
    if (sw->pending >= 0 && sw->pending != buf) {  /* a newer frame replaces the waiting one */
        give_back(w, sw->pending, "replaced before it was drawn");
        sw->pending = -1;
    }
    sharpen_set_mode(sw->s, w->filter - 1);  /* 0 FSR 1, 1 NIS, 2 SGSR 1, 3 integer, 4 LS1-like, 5 Lanczos 3, 6 Anime4K */
    int rc = sharpen_render(sw->s, buf, ow, oh, w->filter == 2 ? 0.4f : w->filter == 3 ? 1.0f : 0.65f, &slot, &fresh);
    if (rc == -2) { if (sw->pending != buf) sw->pending_ms = now_ms(); sw->pending = buf; return 1; }  /* all output buffers are with the compositor: this frame is drawn at the next release */
    if (rc < 0) {
        sw->fail_n++;
        if (!sw->said_fail++) fprintf(stderr, "[shell] %s: render filter: the pass could not draw %dx%d -> %dx%d (shown as it is)\n", w->title, sw->w[buf], sw->h[buf], ow, oh);
        return 0;
    }
    if (!sw->said_ok++) fprintf(stderr, "[shell] %s: render filter: %dx%d -> %dx%d\n", w->title, sw->w[buf], sw->h[buf], ow, oh);
    sw->ok_n++;
    if (getenv("APKLAYER_RUNTIME_DEBUG") && (sw->ok_n + sw->fail_n) % 50 == 0) fprintf(stderr, "[shell] %s: render filter: %d frames filtered, %d shown as they are\n", w->title, sw->ok_n, sw->fail_n);
    if (fresh || !sw->wl[slot]) {
        struct sharpen_out o;
        if (sharpen_slot_info(sw->s, slot, &o) < 0) { sharpen_slot_release(sw->s, slot); fprintf(stderr, "[shell] %s: render filter: no output dmabuf\n", w->title); return 0; }
        if (sw->wl[slot]) wl_buffer_destroy(sw->wl[slot]);
        struct zwp_linux_buffer_params_v1 *p = zwp_linux_dmabuf_v1_create_params(dmabuf_mgr);
        uint64_t modifier = o.modifier;
        if (modifier == 0) {  /* as dmabuf.c: an explicit LINEAR the compositor did not list fails the import */
            int ok = 0;
            for (int i = 0; i < dmabuf_linear_n; i++) ok |= dmabuf_linear[i] == DRM_FORMAT_XRGB8888;
            if (!ok) modifier = 0x00ffffffffffffffull;
        }
        zwp_linux_buffer_params_v1_add(p, o.fd, 0, o.offset, o.stride, (uint32_t)(modifier >> 32), (uint32_t)modifier);
        sw->wl[slot] = zwp_linux_buffer_params_v1_create_immed(p, o.w, o.h, DRM_FORMAT_XRGB8888, 0);
        zwp_linux_buffer_params_v1_destroy(p);
        close(o.fd);
        wl_buffer_add_listener(sw->wl[slot], &slot_listener, sw);
    }
    w->framed = 1;
    wl_surface_attach(w->surface, sw->wl[slot], 0, 0);
    surface_damage(w->surface, 0, 0, ow, oh);
    wl_surface_commit(w->surface);
    if (sw->pending == buf) sw->pending = -1;
    if (!sw->keep) give_back(w, buf, "drawn");  /* glFinish is done: the pass no longer reads it (the sink holds six acquired buffers at most) */
    return 1;
}

void sharpen_win_new_frame(struct window *w, int buf) { (void)w; (void)buf; }

void sharpen_win_forget(struct window *w, int buf)
{
    struct swin *sw = w->sharp;
    if (!sw || buf < 0 || buf >= DBUF_MAX) return;
    if (sw->pending == buf) sw->pending = -1;
    kept_close(&sw->kept[buf]);
    if (sw->state == S_READY) { sharpen_forget(sw->s, buf); sw->w[buf] = sw->h[buf] = 0; }
}

void sharpen_win_destroy(struct window *w)
{
    struct swin *sw = w->sharp;
    if (!sw) return;
    if (sw->state == S_PREPARING) { pthread_join(sw->thread, NULL); kept_close(&sw->sample); }
    for (int i = 0; i < DBUF_MAX; i++) kept_close(&sw->kept[i]);
    for (int i = 0; i < OUT_SLOTS; i++) if (sw->wl[i]) wl_buffer_destroy(sw->wl[i]);
    if (sw->s) sharpen_close(sw->s);
    free(sw);
    w->sharp = NULL;
}

#else  /* built without EGL/GBM/GLES */
void sharpen_win_import(struct window *w, int buf, const int32_t *m, const int *fds, int nfds) { (void)w; (void)buf; (void)m; (void)fds; (void)nfds; }
void sharpen_win_poll(void) {}
int sharpen_win_timeout(void) { return -1; }
int sharpen_win_show(struct window *w, int buf) { (void)w; (void)buf; return 0; }
void sharpen_win_new_frame(struct window *w, int buf) { (void)w; (void)buf; }
void sharpen_win_forget(struct window *w, int buf) { (void)w; (void)buf; }
void sharpen_win_destroy(struct window *w) { (void)w; }
#endif
