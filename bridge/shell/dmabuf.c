/* Zero-copy window contents (PLAN archive P7.1): dmabufs from apklayer's HWC go to the compositor through zwp_linux_dmabuf_v1. The shell
 * imports each buffer once (first DMABUF_FRAME naming it carries the fds), then shows it by index; the compositor's
 * wl_buffer.release is passed back as DMABUF_RELEASE so the producer knows when it may draw into the buffer again. */
#include <string.h>
#include <time.h>
#include "shell.h"

struct zwp_linux_dmabuf_v1 *dmabuf_mgr;

static void dbuf_release(void *data, struct wl_buffer *b)
{
    struct window *w = data;
    for (int i = 0; i < DBUF_MAX; i++)
        if (w->dbufs[i] == b) {
            if (w->plain_buf == i) w->plain_buf = -1;  /* no longer on screen as it is */
            send_msg(w->client, MSG_DMABUF_RELEASE, (int32_t[]){ w->id, i }, 2);
        }
}
static const struct wl_buffer_listener dbuf_listener = { dbuf_release };

void window_dmabuf_destroy(struct window *w)
{
    sharpen_win_destroy(w);
    dshm_destroy(w);
    for (int i = 0; i < DBUF_MAX; i++) {
        if (w->dbufs[i]) wl_buffer_destroy(w->dbufs[i]);
        w->dbufs[i] = NULL;
    }
}

/* m: the DMABUF_FRAME payload (see shell.h), fds: its nplanes dmabufs (the caller closes them). */
void window_dmabuf_frame(struct window *w, const int32_t *m, const int *fds, int nfds)
{
    int buf = m[1];
    static unsigned frames;
    if (nfds > 0 || getenv("APKLAYER_RUNTIME_DEBUG")) {  /* a buffer import (a window's first frames) always: tests wait for it; every frame with the debug flag */
        struct timespec ts;
        clock_gettime(CLOCK_MONOTONIC, &ts);
        fprintf(stderr, "[shell] t=%ld.%03ld dmabuf frame %u (buffer %d, %dx%d, %d fds, modifier 0x%08x%08x)\n", (long)(ts.tv_sec % 1000), ts.tv_nsec / 1000000, ++frames,
                buf, m[3], m[4], nfds, (uint32_t)m[7], (uint32_t)m[6]);
    }
    if (buf < 0 || buf >= DBUF_MAX) return;
    sharpen_win_new_frame(w, buf);  /* the render filter gives back the buffers it has drawn */
    if (nfds > 0) {
        if (!dmabuf_mgr) {  /* dmabuf_shm.c: the frames are copied */
            if (dshm_import(w, m, fds[0]) < 0) return;
            if (m[3] > 0 && m[4] > 0 && (m[3] != w->w || m[4] != w->h)) { w->w = m[3]; w->h = m[4]; if (w->configured) window_layout(w); }
            goto pending;
        }
        struct zwp_linux_buffer_params_v1 *p = zwp_linux_dmabuf_v1_create_params(dmabuf_mgr);
        uint64_t modifier = ((uint64_t)(uint32_t)m[7] << 32) | (uint32_t)m[6];
        /* Mutter lists only the implicit modifier for the 8888 formats: an explicit LINEAR it did not advertise makes the import fail (a black window) */
        if (modifier == 0) {
            int ok = 0;
            for (int i = 0; i < dmabuf_linear_n; i++) ok |= dmabuf_linear[i] == (uint32_t)m[2];
            if (!ok) modifier = 0x00ffffffffffffffull;  /* DRM_FORMAT_MOD_INVALID */
        }
        for (int i = 0; i < nfds; i++)
            zwp_linux_buffer_params_v1_add(p, fds[i], i, (uint32_t)m[12 + i], (uint32_t)m[8 + i], (uint32_t)(modifier >> 32), (uint32_t)modifier);
        if (w->dbufs[buf]) wl_buffer_destroy(w->dbufs[buf]);
        if (m[3] > 0 && m[4] > 0 && (m[3] != w->w || m[4] != w->h)) { w->w = m[3]; w->h = m[4]; if (w->configured) window_layout(w); }  /* the display changed size */
        w->dbufs[buf] = zwp_linux_buffer_params_v1_create_immed(p, m[3], m[4], (uint32_t)m[2], 0);
        zwp_linux_buffer_params_v1_destroy(p);
        wl_buffer_add_listener(w->dbufs[buf], &dbuf_listener, w);
        sharpen_win_import(w, buf, m, fds, nfds);  /* the render filter's own import of the same buffer (it may fail: the buffer is shown as it is) */
    }
    if (!w->dbufs[buf] && !(w->dshm && !dmabuf_mgr)) return;
pending:
    if (!w->configured) {  // the first configure has not been acknowledged yet: show it from there
        if (w->pending_dbuf >= 0 && w->pending_dbuf != buf)
            send_msg(w->client, MSG_DMABUF_RELEASE, (int32_t[]){ w->id, w->pending_dbuf }, 2);
        w->pending_dbuf = buf;
        memcpy(w->pending_ddamage, m + 16, sizeof w->pending_ddamage);
        return;
    }
    window_dmabuf_show(w, buf, m + 16);
}

void window_dmabuf_show(struct window *w, int buf, const int32_t *damage)
{
    if (!dmabuf_mgr) { dshm_show(w, buf, damage); return; }
    if (sharpen_win_show(w, buf)) return;
    window_dmabuf_attach(w, buf, damage);
}

/* the app's own buffer, as it is */
void window_dmabuf_attach(struct window *w, int buf, const int32_t *damage)
{
    if (buf < 0 || buf >= DBUF_MAX || !w->dbufs[buf]) return;  /* a stale index (a waiting frame of a buffer the app replaced): attaching NULL would unmap the window */
    w->framed = 1;
    w->plain_buf = buf;
    wl_surface_attach(w->surface, w->dbufs[buf], 0, 0);
    surface_damage(w->surface, damage[0], damage[1], damage[2], damage[3]);
    wl_surface_commit(w->surface);
}
