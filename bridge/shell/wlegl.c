/* Window contents as Android gralloc buffers (docs/HALIUM.md): on a Halium device the compositor (Lomiri/Mir, Lipstick) has no
 * linux-dmabuf; it imports the device's gralloc buffers through libhybris's android_wlegl protocol (bridge/shell/protocols/
 * wayland-android.xml): the buffer's native_handle goes over as its ints (create_handle) and fds (add_fd, one by one), then
 * create_buffer makes the wl_buffer. Buffers are kept per slot like dmabuf ones (dmabuf.c) and shown the same way. */
#include <string.h>
#include "shell.h"
#include "wayland-android-client-protocol.h"

struct android_wlegl *wlegl;

void wlegl_bind(struct wl_registry *r, uint32_t name, uint32_t ver)
{
    wlegl = wl_registry_bind(r, name, &android_wlegl_interface, ver < 2 ? ver : 2);
}

static void gbuf_release(void *data, struct wl_buffer *b)
{
    struct window *w = data;
    for (int i = 0; i < DBUF_MAX; i++)
        if (w->dbufs[i] == b) send_msg(w->client, MSG_DMABUF_RELEASE, (int32_t[]){ w->id, i }, 2);
}
static const struct wl_buffer_listener gbuf_listener = { gbuf_release };

/* m: the GRALLOC_FRAME payload (shell.h), len its bytes; fds: its num_fds handle fds (the caller closes them). */
void window_gralloc_frame(struct window *w, const int32_t *m, uint32_t len, const int *fds, int nfds)
{
    int buf = m[1], num_ints = m[8];
    if (buf < 0 || buf >= DBUF_MAX || num_ints < 0 || len < (uint32_t)(13 + num_ints) * 4) return;
    if (nfds > 0 || num_ints > 0) {
        if (!wlegl) { fprintf(stderr, "[shell] no android_wlegl: cannot show a gralloc frame\n"); return; }
        struct wl_array ints;
        wl_array_init(&ints);
        memcpy(wl_array_add(&ints, (size_t)num_ints * 4), m + 13, (size_t)num_ints * 4);
        struct android_wlegl_handle *h = android_wlegl_create_handle(wlegl, nfds, &ints);
        wl_array_release(&ints);
        for (int i = 0; i < nfds; i++) android_wlegl_handle_add_fd(h, fds[i]);
        if (w->dbufs[buf]) wl_buffer_destroy(w->dbufs[buf]);
        if (m[2] > 0 && m[3] > 0 && (m[2] != w->w || m[3] != w->h)) { w->w = m[2]; w->h = m[3]; if (w->configured) window_layout(w); }
        /* usage: the low 32 bits of gralloc's 64-bit usage (the protocol carries an int; the CPU/GPU/composer bits are there) */
        w->dbufs[buf] = android_wlegl_create_buffer(wlegl, m[2], m[3], m[4], m[5], m[6], h);
        android_wlegl_handle_destroy(h);
        wl_buffer_add_listener(w->dbufs[buf], &gbuf_listener, w);
        fprintf(stderr, "[shell] gralloc buffer %d: %dx%d stride %d format %d, %d fds, %d ints\n", buf, m[2], m[3], m[4], m[5], nfds, num_ints);
    }
    if (!w->dbufs[buf]) return;
    if (!w->configured) {
        w->pending_dbuf = buf;
        memcpy(w->pending_ddamage, m + 9, sizeof w->pending_ddamage);
        return;
    }
    window_dmabuf_show(w, buf, m + 9);
}
