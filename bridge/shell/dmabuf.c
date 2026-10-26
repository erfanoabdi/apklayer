/* Zero-copy window contents (PLAN P7.1): dmabufs from apklayer's HWC go to the compositor through zwp_linux_dmabuf_v1. The shell
 * imports each buffer once (first DMABUF_FRAME naming it carries the fds), then shows it by index; the compositor's
 * wl_buffer.release is passed back as DMABUF_RELEASE so the producer knows when it may draw into the buffer again. */
#include <time.h>
#include "shell.h"

struct zwp_linux_dmabuf_v1 *dmabuf_mgr;

static void dbuf_release(void *data, struct wl_buffer *b)
{
    struct window *w = data;
    for (int i = 0; i < DBUF_MAX; i++)
        if (w->dbufs[i] == b) send_msg(w->client, MSG_DMABUF_RELEASE, (int32_t[]){ w->id, i }, 2);
}
static const struct wl_buffer_listener dbuf_listener = { dbuf_release };

void window_dmabuf_destroy(struct window *w)
{
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
    if (getenv("APKLAYER_RUNTIME_DEBUG")) {
        struct timespec ts;
        clock_gettime(CLOCK_MONOTONIC, &ts);
        fprintf(stderr, "[shell] t=%ld.%03ld dmabuf frame %u (buffer %d, %dx%d, %d fds)\n", (long)(ts.tv_sec % 1000), ts.tv_nsec / 1000000, ++frames, buf, m[3], m[4], nfds);
    }
    if (buf < 0 || buf >= DBUF_MAX) return;
    if (nfds > 0) {
        if (!dmabuf_mgr) { fprintf(stderr, "[shell] no zwp_linux_dmabuf_v1: cannot show a dmabuf frame\n"); return; }
        struct zwp_linux_buffer_params_v1 *p = zwp_linux_dmabuf_v1_create_params(dmabuf_mgr);
        uint64_t modifier = ((uint64_t)(uint32_t)m[7] << 32) | (uint32_t)m[6];
        for (int i = 0; i < nfds; i++)
            zwp_linux_buffer_params_v1_add(p, fds[i], i, (uint32_t)m[12 + i], (uint32_t)m[8 + i], (uint32_t)(modifier >> 32), (uint32_t)modifier);
        if (w->dbufs[buf]) wl_buffer_destroy(w->dbufs[buf]);
        w->dbufs[buf] = zwp_linux_buffer_params_v1_create_immed(p, m[3], m[4], (uint32_t)m[2], 0);
        zwp_linux_buffer_params_v1_destroy(p);
        wl_buffer_add_listener(w->dbufs[buf], &dbuf_listener, w);
    }
    if (!w->dbufs[buf]) return;
    wl_surface_attach(w->surface, w->dbufs[buf], 0, 0);
    wl_surface_damage_buffer(w->surface, m[16], m[17], m[18], m[19]);
    wl_surface_commit(w->surface);
}
