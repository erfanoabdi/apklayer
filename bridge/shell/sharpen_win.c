/* The window side of the upscale pass (sharpen.h): a window whose app asked for the filter (`apklayer settings` render-filter sharp: DMABUF_FRAME's
 * flags word) and renders at a size other than the window's pixels is shown through sharpen.c's output ring instead of the app's own buffer. The app's
 * buffers are still imported as wl_buffers (dmabuf.c): anything failing here shows them, as without the filter.
 *
 * The pass is prepared in a helper thread (EGL, shaders: ~100 ms): an app sends a burst of frames when it starts and the display sink, which holds
 * six acquired buffers at most, wedges for good if the shell does not hand buffers back during it ("max acquired buffer count reached"). So the first
 * frames take the normal path and the app's dmabufs are kept (dups) until the helper is done; sharpen_win_poll (the main loop) then imports them and
 * shows the current frame through the pass.
 *
 * A window on the neural pass renders on its own thread (apklayer patch): the pass takes milliseconds and the shell's main thread serves every window and
 * the input, so sharpen_win_show only posts the frame (the newest waiting one replaces an older waiting one, which goes back to the app) and
 * sharpen_win_poll commits the finished output. The app's buffer goes back to the app only when its frame is done. */
#include <pthread.h>
#include <string.h>
#include <unistd.h>
#include "shell.h"

#ifdef HAVE_SHARPEN
#include "sharpen.h"

#define OUT_SLOTS 8
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
    /* the neural window's render thread: one job at a time (wstate: 0 idle, 1 posted, 2 running, 3 done) */
    pthread_t wthread;
    pthread_mutex_t wm;
    pthread_cond_t wc;
    int wstarted, wquit, wstate;
    int jbuf, jnocap, jow, joh, jrc, jslot, jfresh, jdead, jkeep, jcaptured, jgave;  /* jnocap: draw the snapshot already taken (no app buffer); jcaptured: the GPU has copied the app's buffer, jgave: it went back to the app */
    int snap_wait, snap_retry, swow, swoh;  /* a captured frame waits for a free output buffer (all are with the compositor); retry: a slot was freed while a job ran */
    float jsh;
    int queued;                     /* the newest frame that arrived while a job ran (-1: none) */
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

static void job_post(struct swin *sw, int buf, int nocap, int ow, int oh, float sh);

static void slot_released(void *data, struct wl_buffer *b)
{
    struct swin *sw = data;
    static int n_rel;
    for (int i = 0; i < OUT_SLOTS; i++)
        if (sw->wl[i] == b) { sharpen_slot_release(sw->s, i); if (getenv("APKLAYER_RUNTIME_DEBUG")) fprintf(stderr, "[shell] %s: render filter: compositor released slot %d (%d)\n", sw->win->title, i, ++n_rel); }
    if (sw->snap_wait && sw->wstarted) {  /* a captured frame waits for an output buffer */
        if (sw->wstate == 0) { sw->snap_wait = 0; job_post(sw, -1, 1, sw->swow, sw->swoh, 0); }
        else sw->snap_retry = 1;
    }
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

static void *render_thread(void *arg)
{
    struct swin *sw = arg;
    pthread_mutex_lock(&sw->wm);
    for (;;) {
        while (!sw->wquit && sw->wstate != 1) pthread_cond_wait(&sw->wc, &sw->wm);
        if (sw->wquit) break;
        sw->wstate = 2;
        int buf = sw->jbuf, nocap = sw->jnocap, ow = sw->jow, oh = sw->joh;
        float sh = sw->jsh;
        pthread_mutex_unlock(&sw->wm);
        int slot = -1, fresh = 0, rc;
        if (nocap) rc = sharpen_render_snapshot(sw->s, 0, ow, oh, &slot, &fresh);
        else if (sharpen_neural_capture(sw->s, buf, ow, oh, 0) == 0) {  /* the app's buffer is copied: the main loop gives it back before the network runs */
            __atomic_store_n(&sw->jcaptured, 1, __ATOMIC_RELEASE);
            rc = sharpen_render_snapshot(sw->s, 0, ow, oh, &slot, &fresh);
        } else rc = sharpen_render(sw->s, buf, ow, oh, sh, &slot, &fresh);  /* the neural pass cannot take this frame: the usual pass, with the buffer held */
        pthread_mutex_lock(&sw->wm);
        sw->jrc = rc; sw->jslot = slot; sw->jfresh = fresh;
        sw->wstate = 3;
    }
    pthread_mutex_unlock(&sw->wm);
    return NULL;
}

static int worker_start(struct swin *sw)
{
    if (sw->wstarted) return 0;
    pthread_mutex_init(&sw->wm, NULL);
    pthread_cond_init(&sw->wc, NULL);
    if (pthread_create(&sw->wthread, NULL, render_thread, sw) != 0) return -1;
    sw->wstarted = 1;
    return 0;
}

static void worker_stop(struct swin *sw)
{
    if (!sw->wstarted) return;
    pthread_mutex_lock(&sw->wm);
    sw->wquit = 1;
    pthread_cond_signal(&sw->wc);
    pthread_mutex_unlock(&sw->wm);
    pthread_join(sw->wthread, NULL);
    sw->wstarted = 0;
}

static void job_post(struct swin *sw, int buf, int nocap, int ow, int oh, float sh)
{
    pthread_mutex_lock(&sw->wm);
    sw->jbuf = buf; sw->jnocap = nocap; sw->jow = ow; sw->joh = oh; sw->jsh = sh; sw->jdead = 0; sw->jkeep = nocap ? 1 : sw->keep; sw->jcaptured = 0; sw->jgave = 0; sw->snap_wait = 0; sw->wstate = 1;  /* keep: the buffer shown again is the compositor's already, never given back */
    pthread_cond_signal(&sw->wc);
    pthread_mutex_unlock(&sw->wm);
}

/* the output slot `slot` holds the filtered frame of app buffer `buf`: its wl_buffer is made (a new slot) and shown, then the app's buffer goes back */
static void commit_slot(struct window *w, struct swin *sw, int buf, int slot, int fresh, int ow, int oh, int keep)
{
    if (fresh || !sw->wl[slot]) {
        struct sharpen_out o;
        if (sharpen_slot_info(sw->s, slot, &o) < 0) { sharpen_slot_release(sw->s, slot); fprintf(stderr, "[shell] %s: render filter: no output dmabuf\n", w->title); static const int32_t whole[4] = { 0, 0, INT32_MAX, INT32_MAX }; window_dmabuf_attach(w, buf, whole); return; }
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
    static int n_commit;
    if (getenv("APKLAYER_RUNTIME_DEBUG")) fprintf(stderr, "[shell] %s: render filter: commit %d (buffer %d, slot %d, %s)\n", w->title, ++n_commit, buf, slot, keep ? "kept" : "given back");
    if (getenv("APKLAYER_SHARPEN_DUMP") && (n_commit <= 3 || n_commit % 25 == 0)) {  /* a test aid: what the compositor was given (PPM) */
        char path[300];
        unsigned char *px = malloc((size_t)ow * oh * 4);
        snprintf(path, sizeof path, "%s/commit-%04d.ppm", getenv("APKLAYER_SHARPEN_DUMP"), n_commit);
        FILE *f = px && sharpen_readback(sw->s, slot, px) == 0 ? fopen(path, "wb") : NULL;
        if (f) {
            fprintf(f, "P6\n%d %d\n255\n", ow, oh);
            for (int y = 0; y < oh; y++) for (int x = 0; x < ow; x++) fwrite(px + ((size_t)y * ow + x) * 4, 1, 3, f);
            fclose(f);
        }
        free(px);
    }
    if (!keep) give_back(w, buf, "drawn");  /* the pass is done: it no longer reads the app's buffer (the sink holds six acquired buffers at most) */
}

/* a finished job of the render thread (main loop) */
static void job_done(struct window *w, struct swin *sw)
{
    static const int32_t whole[4] = { 0, 0, INT32_MAX, INT32_MAX };
    pthread_mutex_lock(&sw->wm);
    int buf = sw->jbuf, ow = sw->jow, oh = sw->joh, rc = sw->jrc, slot = sw->jslot, fresh = sw->jfresh, dead = sw->jdead, keep = sw->jkeep, captured = sw->jcaptured, gave = sw->jgave;
    sw->wstate = 0;
    pthread_mutex_unlock(&sw->wm);
    if (dead) {  /* the app destroyed the buffer meanwhile: nothing to show or give back */
        if (rc == 0) sharpen_slot_release(sw->s, slot);
    } else if (rc == -2 && captured) {  /* the frame is a snapshot: it waits for a free output buffer, the app's buffer is not needed any more */
        if (!gave && !keep) give_back(w, buf, "captured");
        sw->snap_wait = 1; sw->swow = ow; sw->swoh = oh;
        if (sw->snap_retry) { sw->snap_retry = 0; sw->snap_wait = 0; job_post(sw, -1, 1, ow, oh, 0); return; }
    } else if (rc == -2) {  /* all output buffers are with the compositor: this frame is drawn at the next release */
        if (sw->pending != buf) sw->pending_ms = now_ms();
        sw->pending = buf;
    } else if (rc < 0) {
        sw->fail_n++;
        if (!sw->said_fail++) fprintf(stderr, "[shell] %s: render filter: the pass could not draw %dx%d -> %dx%d (shown as it is)\n", w->title, sw->w[buf], sw->h[buf], ow, oh);
        if (w->configured) window_dmabuf_attach(w, buf, whole);
    } else {
        if (!sw->said_ok++) fprintf(stderr, "[shell] %s: render filter: %dx%d -> %dx%d\n", w->title, sw->w[buf], sw->h[buf], ow, oh);
        sw->ok_n++;
        if (getenv("APKLAYER_RUNTIME_DEBUG") && (sw->ok_n + sw->fail_n) % 50 == 0) fprintf(stderr, "[shell] %s: render filter: %d frames filtered, %d shown as they are\n", w->title, sw->ok_n, sw->fail_n);
        commit_slot(w, sw, buf, slot, fresh, ow, oh, keep || (captured && gave));
        if (captured && !gave && !keep) { /* given back by commit_slot: keep == 0 here */ }
    }
    if (sw->queued >= 0) {  /* the newest frame that came in meanwhile */
        int b = sw->queued;
        sw->queued = -1;
        if (!sharpen_win_show(w, b) && w->configured) window_dmabuf_attach(w, b, whole);
    }
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
        sw->queued = -1;
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
        if (sw && sw->wstarted && !sw->jgave && !sw->jkeep && __atomic_load_n(&sw->jcaptured, __ATOMIC_ACQUIRE) && __atomic_load_n(&sw->wstate, __ATOMIC_ACQUIRE) >= 2 && !sw->jdead) { give_back(w, sw->jbuf, "captured"); sw->jgave = 1; }
        if (sw && sw->wstarted && __atomic_load_n(&sw->wstate, __ATOMIC_ACQUIRE) == 3) job_done(w, sw);
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
        if (windows[i].client && windows[i].sharp && ((struct swin *)windows[i].sharp)->wstate != 0) return 2;  /* a render thread job is in flight: look again soon */
    for (int i = 0; i < MAX_WINDOWS; i++)
        if (windows[i].client && windows[i].sharp && (((struct swin *)windows[i].sharp)->state == S_PREPARING || ((struct swin *)windows[i].sharp)->pending >= 0)) return 25;
    return -1;
}

/* 1: the buffer was shown through the pass (the app's own goes back at once: the compositor never holds it) */
int sharpen_win_show(struct window *w, int buf)
{
    struct swin *sw = w->sharp;
    if (sw && sw->pending >= 0 && sw->pending != buf) {  /* a newer frame arrived: the waiting one must not be drawn over it later, whichever way the new one is shown (review finding) */
        give_back(w, sw->pending, "replaced by a newer frame");
        sw->pending = -1;
    }
    if (w->filter && sw && sw->state == S_PREPARING && !w->turn && w->plain_buf >= 0 && buf >= 0 && buf < DBUF_MAX && sw->kept[buf].valid) {
        /* the helper is still starting the pass and the window already shows something: only the newest frame waits (the others go back at once), so the
         * compositor never holds a pile of the app's buffers (the sink wedges at seven acquired). It is drawn through the pass when that is ready. */
        int lw = w->cfg_w > 0 ? w->cfg_w : w->w, lh = w->cfg_h > 0 ? w->cfg_h : w->h, ow, oh;
        window_output_px(w, lw, lh, &ow, &oh);
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
    int lw = w->cfg_w > 0 ? w->cfg_w : w->w, lh = w->cfg_h > 0 ? w->cfg_h : w->h, ow, oh;
    window_output_px(w, lw, lh, &ow, &oh);
    if (ow <= 0 || oh <= 0 || (sw->w[buf] == ow && sw->h[buf] == oh)) return 0;  /* the window's own pixels: nothing to resample */
    int slot = -1, fresh = 0;
    if (sw->pending >= 0 && sw->pending != buf) {  /* a newer frame replaces the waiting one */
        give_back(w, sw->pending, "replaced before it was drawn");
        sw->pending = -1;
    }
    /* the sharpness: FSR 1's RCAS constant exp2(-stops) of the app's render-sharpness: off 0 (no sharpening), low 1 stop (0.5), medium 0.2 stops (0.87, the reference's default), high 0 stops (1, the sharpest the reference allows) */
    static const float lvl[5] = { 0.8706f, 0.0f, 0.5f, 0.8706f, 1.0f };
    float sh = lvl[w->sharpl >= 0 && w->sharpl <= 4 ? w->sharpl : 0];
    if (w->neural && worker_start(sw) == 0) {  /* the neural pass: off the main thread. The pass itself falls back to FSR 1 where the network is unavailable. */
        sharpen_set_neural(sw->s, 1);
        if (sw->wstate != 0) {  /* a job is running: only the newest frame waits */
            if (sw->queued >= 0 && sw->queued != buf) give_back(w, sw->queued, "replaced while the pass runs");
            sw->queued = buf;
        } else job_post(sw, buf, 0, ow, oh, sh);
        return 1;
    }
    sharpen_set_neural(sw->s, w->neural);
    int rc = sharpen_render(sw->s, buf, ow, oh, sh, &slot, &fresh);
    if (rc == -2) { if (sw->pending != buf) sw->pending_ms = now_ms(); sw->pending = buf; return 1; }  /* all output buffers are with the compositor: this frame is drawn at the next release */
    if (rc < 0) {
        sw->fail_n++;
        if (!sw->said_fail++) fprintf(stderr, "[shell] %s: render filter: the pass could not draw %dx%d -> %dx%d (shown as it is)\n", w->title, sw->w[buf], sw->h[buf], ow, oh);
        return 0;
    }
    if (!sw->said_ok++) fprintf(stderr, "[shell] %s: render filter: %dx%d -> %dx%d\n", w->title, sw->w[buf], sw->h[buf], ow, oh);
    sw->ok_n++;
    if (getenv("APKLAYER_RUNTIME_DEBUG") && (sw->ok_n + sw->fail_n) % 50 == 0) fprintf(stderr, "[shell] %s: render filter: %d frames filtered, %d shown as they are\n", w->title, sw->ok_n, sw->fail_n);
    commit_slot(w, sw, buf, slot, fresh, ow, oh, sw->keep);
    return 1;
}

void sharpen_win_new_frame(struct window *w, int buf) { (void)w; (void)buf; }

void sharpen_win_forget(struct window *w, int buf)
{
    struct swin *sw = w->sharp;
    if (!sw || buf < 0 || buf >= DBUF_MAX) return;
    if (sw->pending == buf) sw->pending = -1;
    if (sw->queued == buf) sw->queued = -1;
    if (sw->wstarted && sw->wstate != 0 && sw->jbuf == buf) sw->jdead = 1;  /* its job is in flight: the result is dropped */
    kept_close(&sw->kept[buf]);
    if (sw->state == S_READY) { sharpen_forget(sw->s, buf); sw->w[buf] = sw->h[buf] = 0; }
}

void sharpen_win_destroy(struct window *w)
{
    struct swin *sw = w->sharp;
    if (!sw) return;
    if (sw->state == S_PREPARING) { pthread_join(sw->thread, NULL); kept_close(&sw->sample); }
    worker_stop(sw);
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
