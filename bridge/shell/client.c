/* App connections: the unix socket protocol (see shell.h) and sending messages to apps. */
#include "shell.h"
#include <sys/uio.h>

/* ---- sending to apps ---- */

/* Messages to an app never block the shell. The sockets are blocking, and a write to an app that has stopped reading (a hung one, or one that crashed and is still writing
 * its core: PGR's 541 MB took 92 s) froze the shell's single thread in the kernel: no Wayland dispatch, no answer to xdg_wm_base pings, KWin's "not responding" helper then
 * killed the shell with SIGABRT (core of 2026-11-22 20:44:48; the stuck write was a 24 byte hover event). Now every send is MSG_DONTWAIT. Input and vsync messages that find
 * the socket full are dropped (stale by the time the app reads); the others (configure, release, ...) wait in a per-client queue that is flushed as soon as the socket
 * drains (POLLOUT in the main loop, client_flush) and keeps the order; a queue past 256 KB drops the new message. A message carrying an fd cannot be queued: it is dropped. */
#define CLIENT_QUEUE_MAX (256 * 1024)

void client_flush(struct client *c)
{
    while (c && c->fd >= 0 && c->pend_len > 0) {
        ssize_t n = send(c->fd, c->pend, c->pend_len, MSG_NOSIGNAL | MSG_DONTWAIT);
        if (n < 0 && errno == EINTR) continue;
        if (n <= 0) return;  /* EAGAIN: later; any other error: the read side sees the hang-up */
        memmove(c->pend, c->pend + n, c->pend_len - (size_t)n);
        c->pend_len -= (size_t)n;
    }
    if (c && c->pend_len == 0 && c->stalled) { fprintf(stderr, "[shell] %s reads its socket again\n", c->pkg[0] ? c->pkg : "?"); c->stalled = 0; }
}

static void client_queue(struct client *c, const uint8_t *data, size_t size)
{
    if (c->pend_len + size > CLIENT_QUEUE_MAX) return;  /* the app is far behind: this one is lost */
    if (c->pend_len + size > c->pend_cap) {
        size_t cap = c->pend_cap ? c->pend_cap * 2 : 4096;
        while (cap < c->pend_len + size) cap *= 2;
        uint8_t *p = realloc(c->pend, cap);
        if (!p) return;
        c->pend = p;
        c->pend_cap = cap;
    }
    memcpy(c->pend + c->pend_len, data, size);
    c->pend_len += size;
}

static int client_send(struct client *c, const void *data, size_t size, int pass_fd, int droppable)
{
    if (!c || c->fd < 0) return -1;
    if (c->pend_len > 0) client_flush(c);
    if (c->pend_len > 0) {  /* the earlier messages are still waiting: keep the order */
        if (!droppable && pass_fd < 0) client_queue(c, data, size);
        return -1;
    }
    struct iovec iov = { (void *)data, size };
    char control[CMSG_SPACE(sizeof(int))] = { 0 };
    struct msghdr mh = { .msg_iov = &iov, .msg_iovlen = 1 };
    if (pass_fd >= 0) {
        mh.msg_control = control;
        mh.msg_controllen = sizeof control;
        struct cmsghdr *cm = CMSG_FIRSTHDR(&mh);
        cm->cmsg_level = SOL_SOCKET;
        cm->cmsg_type = SCM_RIGHTS;
        cm->cmsg_len = CMSG_LEN(sizeof(int));
        memcpy(CMSG_DATA(cm), &pass_fd, sizeof pass_fd);
    }
    ssize_t n;
    do n = sendmsg(c->fd, &mh, MSG_NOSIGNAL | MSG_DONTWAIT); while (n < 0 && errno == EINTR);
    if (n == (ssize_t)size) return 0;
    if (n > 0) { client_queue(c, (const uint8_t *)data + n, size - (size_t)n); return 0; }  /* the rest follows in order */
    if (errno != EAGAIN && errno != EWOULDBLOCK) { fprintf(stderr, "[shell] write to %s failed: %s\n", c->pkg, strerror(errno)); return -1; }
    if (!c->stalled) fprintf(stderr, "[shell] %s does not read its socket (hung or dying?): input is dropped, the rest waits in a queue\n", c->pkg[0] ? c->pkg : "?");
    c->stalled = 1;
    if (!droppable && pass_fd < 0) client_queue(c, data, size);
    return -1;
}

int send_msg(struct client *c, uint32_t type, const int32_t *v, int n)
{
    if (!c || c->fd < 0) return -1;
    uint32_t buf[2 + 16] = { type, (uint32_t)(n * 4) };
    memcpy(buf + 2, v, (size_t)n * 4);
    return client_send(c, buf, 8 + (size_t)n * 4, -1, type == MSG_INPUT || type == MSG_VSYNC);
}

void send_msg_fd(struct client *c, uint32_t type, const int32_t *v, int n, int fd)
{
    if (fd < 0) { send_msg(c, type, v, n); return; }
    if (!c || c->fd < 0) return;
    uint32_t buf[2 + 16] = { type, (uint32_t)(n * 4) };
    memcpy(buf + 2, v, (size_t)n * 4);
    client_send(c, buf, 8 + (size_t)n * 4, fd, 1);
}

/* Variable-length message (text payloads): header and data in one write. */
void send_bytes(struct client *c, uint32_t type, const char *data, size_t len)
{
    if (!c || c->fd < 0 || len > 60000) return;
    uint8_t *buf = malloc(8 + len);
    if (!buf) return;
    uint32_t hdr[2] = { type, (uint32_t)len };
    memcpy(buf, hdr, 8);
    memcpy(buf + 8, data, len);
    client_send(c, buf, 8 + len, -1, 0);
    free(buf);
}

void send_input(struct window *w, int32_t type, int32_t a, int32_t b)
{
    if (!w) return;
    if (type <= EV_SECONDARY) { window_to_app(w, &a, &b); a = a * w->scale / 100; b = b * w->scale / 100; } /* logical -> px */
    int32_t v[4] = { W_ID(w), type, a, b };
    send_msg(W_TARGET(w), MSG_INPUT, v, 4);
}


/* ---- app connections ---- */

static void client_drop(struct client *c)
{
    client_vsync_forget(c);
    clipboard_client_drop(c);
    window_drop_proxy(c);
    for (int i = 0; i < MAX_WINDOWS; i++)
        if (windows[i].client == c) window_destroy(&windows[i]);
    for (int i = 0; i < c->nfds; i++) close(c->fds[i]);
    free(c->pend);
    fprintf(stderr, "[shell] %s (pid %d) disconnected\n", c->pkg[0] ? c->pkg : "?", c->pid);
    close(c->fd);
    memset(c, 0, sizeof *c);
    c->fd = -1;
}

static void client_message(struct client *c, uint32_t type, const uint8_t *p, uint32_t len)
{
    const int32_t *v = (const int32_t *)p;
    switch (type) {
    case MSG_HELLO:
        if (len < 4) return;
        c->pid = v[0];
        snprintf(c->pkg, sizeof c->pkg, "%.*s", (int)(len - 4), (const char *)p + 4);
        fprintf(stderr, "[shell] %s (pid %d) connected\n", c->pkg, c->pid);
        display_refresh_changed();
        gamepad_client_hello(c);
        break;
    case MSG_WINDOW_CREATE: {
        if (len < 13 || len - 12 >= 1024) return;
        char text[1024];                        /* "path\0title", copied by length */
        memcpy(text, p + 12, len - 12);
        text[len - 12] = 0;
        size_t path_len = strlen(text);
        const char *title = path_len + 1 < len - 12 ? text + path_len + 1 : "";
        window_create(c, v[0], v[1], v[2], text, title);
        break;
    }
    case MSG_ATTACH_INPUT:
        if (len >= 5) {
            char title[64];
            snprintf(title, sizeof title, "%.*s", (int)(len - 4 < 63 ? len - 4 : 63), (const char *)p + 4);
            window_attach_input(c, v[0], title);
        }
        break;
    case MSG_WINDOW_SIZE: {
        struct window *w = len >= 12 ? window_find_input(c, v[0]) : NULL;
        if (w) window_request_size(w, v[1], v[2]);
        break;
    }
    case MSG_TEXT_INPUT: {
        struct window *w = len >= 8 ? window_find_input(c, v[0]) : NULL;
        if (w && v[1] == 1) { w->input_type = len >= 16 ? v[2] : 0x20001; w->ime_options = len >= 16 ? v[3] : 0; }  /* older hosts: multi-line text */
        if (w && v[1] == 3) {  /* the field's text around the cursor: {id, 3, cursor, anchor} utf8 */
            if (len >= 16) (text_mgr ? text_input_surrounding : maliit_surrounding)(w, v[2], v[3], (const char *)p + 16, len - 16);
        } else if (w) text_input_enable(w, v[1]);
        break;
    }
    case MSG_DMABUF_FRAME: {
        struct window *w = len >= 80 ? window_find(c, v[0]) : NULL;
        int nplanes = len >= 80 ? v[5] : 0;
        if (nplanes < 0 || nplanes > 4 || nplanes > c->nfds) nplanes = 0;
        if (w) { w->filter = len >= 84 && (v[20] & 1);  /* FSR 1; bits 1-2 render-text, bits 3-6 unused (once the mode and the phases) */ w->text = len >= 84 ? (v[20] >> 1) & 3 : 0; w->aa = len >= 84 ? (v[20] >> 7) & 3 : 0; w->calm = len >= 84 ? (v[20] >> 9) & 3 : 0; w->sharpl = len >= 84 ? (v[20] >> 11) & 7 : 0; window_dmabuf_frame(w, v, c->fds, nplanes); }
        for (int i = 0; i < nplanes; i++) close(c->fds[i]); /* the shell kept its own dup, if it needed one */
        memmove(c->fds, c->fds + nplanes, (size_t)(c->nfds - nplanes) * sizeof(int));
        c->nfds -= nplanes;
        break;
    }
    case MSG_GRALLOC_FRAME: {
        struct window *w = len >= 52 ? window_find(c, v[0]) : NULL;
        int nfds = len >= 52 ? v[7] : 0;
        if (nfds < 0 || nfds > c->nfds) nfds = 0;
        if (w) window_gralloc_frame(w, v, len, c->fds, nfds);
        for (int i = 0; i < nfds; i++) close(c->fds[i]);  /* android_wlegl_handle_add_fd sent its own copies */
        memmove(c->fds, c->fds + nfds, (size_t)(c->nfds - nfds) * sizeof(int));
        c->nfds -= nfds;
        break;
    }
    case MSG_VSYNC_REQUEST: {
        if (len >= 4) client_vsync_request(c, v[0]);
        break;
    }
    case MSG_DECOR_DARK: {
        if (len < 8) return;
        c->dark = v[1];
        struct window *w = window_find_input(c, v[0]);
        if (w) window_set_dark(w, c->dark == 2);
        break;
    }
    case MSG_WINDOW_MODE: {  /* before its window: the style it is created with; later: a live switch */
        if (len < 8 || v[1] < WM_AUTO || v[1] > WM_MOBILE) return;
        c->window_mode = v[1] + 1;
        int pref = len >= 12 && v[2] >= NB_AUTO && v[2] <= NB_OFF ? v[2] : NB_AUTO, pref_changed = pref != c->navbar_pref;
        c->navbar_pref = pref;
        struct window *w = window_find_input(c, v[0]);
        if (w) w->navbar_pref = pref;
        if (w && (w->mode != v[1] || pref_changed)) window_set_mode(w, v[1]);
        break;
    }
    case MSG_NAVBAR: {
        struct window *w = len >= 8 ? window_find_input(c, v[0]) : NULL;
        if (w) window_nav_visible(w, v[1]);
        break;
    }
    case MSG_FULLSCREEN: {
        struct window *w = len >= 8 ? window_find_input(c, v[0]) : NULL;
        if (w) window_set_fullscreen(w, v[1]);
        break;
    }
    case MSG_NAVBAR_LAYER: {
        struct window *w = len >= 4 ? window_find(c, v[0]) : NULL;
        if (w) window_nav_layer(w);
        break;
    }
    case MSG_PAD_RUMBLE:
        gamepad_rumble(v, len);
        break;
    case MSG_ACTIVATE:
        activate_message(p, len);
        break;
    case MSG_TEST_INPUT:
        test_input(c, p, len);
        break;
    case MSG_CLIPBOARD_SET:
        clipboard_set(c, (const char *)p, len);
        break;
    case MSG_FRAME: {
        struct window *w = len >= 24 ? window_find(c, v[0]) : NULL;
        if (w) window_frame(w, v[1], v + 2);
        break;
    }
    case MSG_WINDOW_DESTROY: {
        struct window *w = len >= 4 ? window_find(c, v[0]) : NULL;
        if (w) window_destroy(w);
        break;
    }
    }
}

void client_read(struct client *c)
{
    struct iovec iov = { c->rx + c->rx_len, sizeof c->rx - c->rx_len };
    char control[CMSG_SPACE(sizeof(int) * CLIENT_MAX_FDS)];
    struct msghdr mh = { .msg_iov = &iov, .msg_iovlen = 1, .msg_control = control, .msg_controllen = sizeof control };
    ssize_t n = recvmsg(c->fd, &mh, MSG_CMSG_CLOEXEC);
    if (n <= 0) { client_drop(c); return; }
    for (struct cmsghdr *cm = CMSG_FIRSTHDR(&mh); cm; cm = CMSG_NXTHDR(&mh, cm)) {
        if (cm->cmsg_level != SOL_SOCKET || cm->cmsg_type != SCM_RIGHTS) continue;
        int count = (int)((cm->cmsg_len - CMSG_LEN(0)) / sizeof(int));
        for (int i = 0; i < count; i++) {
            int fd;
            memcpy(&fd, CMSG_DATA(cm) + i * sizeof(int), sizeof fd);
            if (c->nfds < CLIENT_MAX_FDS) c->fds[c->nfds++] = fd; else close(fd);
        }
    }
    c->rx_len += (size_t)n;
    size_t off = 0;
    while (c->rx_len - off >= 8) {
        uint32_t type, len;
        memcpy(&type, c->rx + off, 4);
        memcpy(&len, c->rx + off + 4, 4);
        if (len > sizeof c->rx - 8) { client_drop(c); return; }
        if (c->rx_len - off - 8 < len) break;
        client_message(c, type, c->rx + off + 8, len);
        if (c->fd < 0) return;
        off += 8 + len;
    }
    memmove(c->rx, c->rx + off, c->rx_len - off);
    c->rx_len -= off;
}

