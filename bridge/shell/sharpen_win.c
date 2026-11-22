/* The window side of the upscale pass (sharpen.h): a window whose app asked for the filter (`apklayer settings` render-filter sharp: DMABUF_FRAME's
 * flags word) and renders at a size other than the window's pixels is shown through sharpen.c's output ring instead of the app's own buffer. The app's
 * buffers are still imported as wl_buffers (dmabuf.c): anything failing here shows them, as without the filter. */
#include <string.h>
#include "shell.h"

#ifdef HAVE_SHARPEN
#include "sharpen.h"

#define OUT_SLOTS 3
#define DRM_FORMAT_XRGB8888 0x34325258u

struct swin {
    struct sharpen *s;
    struct wl_buffer *wl[OUT_SLOTS];
    int w[DBUF_MAX], h[DBUF_MAX];  /* the app's buffers' sizes */
    int off;                        /* the pass failed once: not again for this window */
    struct window *win;
};

static void slot_released(void *data, struct wl_buffer *b)
{
    struct swin *sw = data;
    for (int i = 0; i < OUT_SLOTS; i++)
        if (sw->wl[i] == b) sharpen_slot_release(sw->s, i);
}
static const struct wl_buffer_listener slot_listener = { slot_released };

void sharpen_win_import(struct window *w, int buf, const int32_t *m, const int *fds, int nfds)
{
    if (!w->filter || !dmabuf_mgr || nfds < 1 || buf < 0 || buf >= DBUF_MAX) return;
    struct swin *sw = w->sharp;
    if (!sw) {
        sw = w->sharp = calloc(1, sizeof *sw);
        if (!sw) return;
        sw->win = w;
        sw->s = sharpen_open();
        if (!sw->s) { sw->off = 1; return; }
        sharpen_accept(sw->s, dmabuf_xrgb_mods, dmabuf_xrgb_n);
    }
    if (sw->off) return;
    uint32_t strides[4] = { 0 }, offsets[4] = { 0 };
    for (int i = 0; i < nfds && i < 4; i++) { strides[i] = (uint32_t)m[8 + i]; offsets[i] = (uint32_t)m[12 + i]; }
    uint64_t modifier = ((uint64_t)(uint32_t)m[7] << 32) | (uint32_t)m[6];
    if (sharpen_import(sw->s, buf, (uint32_t)m[2], m[3], m[4], nfds, fds, strides, offsets, modifier) < 0) {
        fprintf(stderr, "[shell] %s: render filter off: the GPU could not import the app's buffers\n", w->title);
        sw->off = 1;
        return;
    }
    sw->w[buf] = m[3]; sw->h[buf] = m[4];
}

/* 1: the buffer was shown through the pass (the app's own is released at once: the compositor never holds it) */
int sharpen_win_show(struct window *w, int buf)
{
    struct swin *sw = w->sharp;
    if (!w->filter || !sw || sw->off || w->turn || buf < 0 || buf >= DBUF_MAX || sw->w[buf] <= 0) return 0;
    int lw = w->cfg_w > 0 ? w->cfg_w : w->w, lh = w->cfg_h > 0 ? w->cfg_h : w->h, scale = window_scale(w);
    int ow = (lw * scale + 50) / 100, oh = (lh * scale + 50) / 100;
    if (ow <= 0 || oh <= 0 || (sw->w[buf] == ow && sw->h[buf] == oh)) return 0;  /* the window's own pixels: nothing to resample */
    int slot = -1, fresh = 0;
    if (sharpen_render(sw->s, buf, ow, oh, 0.6f, &slot, &fresh) < 0) return 0;
    if (fresh || !sw->wl[slot]) {
        struct sharpen_out o;
        if (sharpen_slot_info(sw->s, slot, &o) < 0) { sharpen_slot_release(sw->s, slot); return 0; }
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
    send_msg(w->client, MSG_DMABUF_RELEASE, (int32_t[]){ w->id, buf }, 2);  /* the pass is done reading it (glFinish) */
    return 1;
}

void sharpen_win_forget(struct window *w, int buf)
{
    struct swin *sw = w->sharp;
    if (sw && sw->s && buf >= 0 && buf < DBUF_MAX) { sharpen_forget(sw->s, buf); sw->w[buf] = sw->h[buf] = 0; }
}

void sharpen_win_destroy(struct window *w)
{
    struct swin *sw = w->sharp;
    if (!sw) return;
    for (int i = 0; i < OUT_SLOTS; i++) if (sw->wl[i]) wl_buffer_destroy(sw->wl[i]);
    if (sw->s) sharpen_close(sw->s);
    free(sw);
    w->sharp = NULL;
}

#else  /* built without EGL/GBM/GLES */
void sharpen_win_import(struct window *w, int buf, const int32_t *m, const int *fds, int nfds) { (void)w; (void)buf; (void)m; (void)fds; (void)nfds; }
int sharpen_win_show(struct window *w, int buf) { (void)w; (void)buf; return 0; }
void sharpen_win_forget(struct window *w, int buf) { (void)w; (void)buf; }
void sharpen_win_destroy(struct window *w) { (void)w; }
#endif
