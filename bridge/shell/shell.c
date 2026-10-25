/*
 * apklayer-shell: the per-session Wayland side of apklayer (PLAN D6/D7).
 *
 * App processes (any JVM arch, pure Java) connect to $XDG_RUNTIME_DIR/apklayer/shell.sock.
 * Each app window is one xdg_toplevel. Its pixels live in a file the app creates (two
 * width*height*4 ARGB buffers); the shell maps the same file into a wl_shm_pool, so frames are
 * handed to the compositor without copying. A buffer goes back to the app on wl_buffer.release.
 *
 * Wire format: little-endian {u32 type, u32 payload_len, payload}.
 *   app -> shell   1 HELLO          {i32 pid} utf8 package
 *                  2 WINDOW_CREATE  {i32 id, i32 w, i32 h} utf8 "shm-path\0title"
 *                  3 FRAME          {i32 id, i32 buf, i32 x, i32 y, i32 w, i32 h}  (damage rect)
 *                  4 WINDOW_DESTROY {i32 id}
 *   shell -> app 101 INPUT          {i32 id, i32 type, i32 a, i32 b}
 *                    type 0/1/2 = MotionEvent DOWN/UP/MOVE at (a, b); 100 = key (evdev a, pressed b)
 *                103 CLOSE          {i32 id}
 *                104 RELEASE        {i32 id, i32 buf}
 */
#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <signal.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/un.h>
#include <unistd.h>
#include <wayland-client.h>
#include "xdg-shell-client-protocol.h"

enum { MSG_HELLO = 1, MSG_WINDOW_CREATE, MSG_FRAME, MSG_WINDOW_DESTROY,
       MSG_INPUT = 101, MSG_CLOSE = 103, MSG_RELEASE = 104 };
enum { EV_DOWN = 0, EV_UP = 1, EV_MOVE = 2, EV_KEY = 100 };
#define MAX_CLIENTS 32
#define MAX_WINDOWS 64
#define NBUF 2

struct client {
    int fd, pid;
    char pkg[128];
    uint8_t rx[65536];
    size_t rx_len;
};

struct window {
    struct client *client;
    int id, w, h, configured;
    struct wl_surface *surface;
    struct xdg_surface *xdg_surface;
    struct xdg_toplevel *toplevel;
    void *map;
    size_t map_len;
    struct wl_buffer *buffers[NBUF];
    int pending_buf;            /* frame received before the first configure */
    int32_t pending_damage[4];
};

static struct wl_display *display;
static struct wl_compositor *compositor;
static struct wl_shm *shm;
static struct xdg_wm_base *wm_base;
static struct wl_seat *seat;
static struct client clients[MAX_CLIENTS];
static struct window windows[MAX_WINDOWS];
static struct window *pointer_focus, *keyboard_focus;
static int ptr_x, ptr_y, ptr_down;

/* ---- sending to apps ---- */

static void send_msg(struct client *c, uint32_t type, const int32_t *v, int n)
{
    if (!c || c->fd < 0) return;
    uint32_t buf[2 + 8] = { type, (uint32_t)(n * 4) };
    memcpy(buf + 2, v, (size_t)n * 4);
    if (write(c->fd, buf, 8 + (size_t)n * 4) < 0 && errno != EAGAIN) {
        fprintf(stderr, "[shell] write to %s failed: %s\n", c->pkg, strerror(errno));
    }
}

static void send_input(struct window *w, int32_t type, int32_t a, int32_t b)
{
    if (!w) return;
    int32_t v[4] = { w->id, type, a, b };
    send_msg(w->client, MSG_INPUT, v, 4);
}

/* ---- windows ---- */

static struct window *window_for_surface(struct wl_surface *s)
{
    for (int i = 0; i < MAX_WINDOWS; i++)
        if (windows[i].client && windows[i].surface == s) return &windows[i];
    return NULL;
}

static struct window *window_find(struct client *c, int id)
{
    for (int i = 0; i < MAX_WINDOWS; i++)
        if (windows[i].client == c && windows[i].id == id) return &windows[i];
    return NULL;
}

static void buffer_release(void *data, struct wl_buffer *b)
{
    struct window *w = data;
    for (int i = 0; i < NBUF; i++) {
        if (w->buffers[i] == b) {
            int32_t v[2] = { w->id, i };
            send_msg(w->client, MSG_RELEASE, v, 2);
        }
    }
}
static const struct wl_buffer_listener buffer_listener = { buffer_release };

static void show(struct window *w, int buf, const int32_t *d)
{
    wl_surface_attach(w->surface, w->buffers[buf], 0, 0);
    wl_surface_damage_buffer(w->surface, d[0], d[1], d[2], d[3]);
    wl_surface_commit(w->surface);
}

static void xs_configure(void *data, struct xdg_surface *xs, uint32_t serial)
{
    struct window *w = data;
    xdg_surface_ack_configure(xs, serial);
    if (!w->configured) {
        w->configured = 1;
        if (w->pending_buf >= 0) show(w, w->pending_buf, w->pending_damage);
        else wl_surface_commit(w->surface);
    }
}
static const struct xdg_surface_listener xs_listener = { xs_configure };

static void top_configure(void *d, struct xdg_toplevel *t, int32_t w, int32_t h, struct wl_array *s)
{ (void)d; (void)t; (void)w; (void)h; (void)s; }
static void top_close(void *data, struct xdg_toplevel *t)
{
    (void)t;
    struct window *w = data;
    int32_t v[1] = { w->id };
    send_msg(w->client, MSG_CLOSE, v, 1);
}
static const struct xdg_toplevel_listener top_listener = { top_configure, top_close };

static void window_create(struct client *c, int id, int width, int height, const char *path,
                          const char *title)
{
    struct window *w = NULL;
    for (int i = 0; i < MAX_WINDOWS && !w; i++)
        if (!windows[i].client) w = &windows[i];
    if (!w || width <= 0 || height <= 0) return;
    int fd = open(path, O_RDWR);
    if (fd < 0) { fprintf(stderr, "[shell] open %s: %s\n", path, strerror(errno)); return; }
    size_t len = (size_t)width * height * 4 * NBUF;
    struct stat st;
    if (fstat(fd, &st) < 0 || (size_t)st.st_size < len) { close(fd); return; }

    memset(w, 0, sizeof *w);
    w->client = c; w->id = id; w->w = width; w->h = height; w->pending_buf = -1;
    struct wl_shm_pool *pool = wl_shm_create_pool(shm, fd, (int32_t)len);
    for (int i = 0; i < NBUF; i++) {
        w->buffers[i] = wl_shm_pool_create_buffer(pool, i * width * height * 4, width, height,
                                                  width * 4, WL_SHM_FORMAT_ARGB8888);
        wl_buffer_add_listener(w->buffers[i], &buffer_listener, w);
    }
    wl_shm_pool_destroy(pool);
    close(fd);

    w->surface = wl_compositor_create_surface(compositor);
    w->xdg_surface = xdg_wm_base_get_xdg_surface(wm_base, w->surface);
    xdg_surface_add_listener(w->xdg_surface, &xs_listener, w);
    w->toplevel = xdg_surface_get_toplevel(w->xdg_surface);
    xdg_toplevel_add_listener(w->toplevel, &top_listener, w);
    xdg_toplevel_set_title(w->toplevel, title[0] ? title : c->pkg);
    xdg_toplevel_set_app_id(w->toplevel, c->pkg);
    xdg_toplevel_set_min_size(w->toplevel, width, height);
    xdg_toplevel_set_max_size(w->toplevel, width, height);
    wl_surface_commit(w->surface);
    fprintf(stderr, "[shell] %s: window %d %dx%d \"%s\"\n", c->pkg, id, width, height, title);
}

static void window_destroy(struct window *w)
{
    if (pointer_focus == w) pointer_focus = NULL;
    if (keyboard_focus == w) keyboard_focus = NULL;
    for (int i = 0; i < NBUF; i++) wl_buffer_destroy(w->buffers[i]);
    xdg_toplevel_destroy(w->toplevel);
    xdg_surface_destroy(w->xdg_surface);
    wl_surface_destroy(w->surface);
    memset(w, 0, sizeof *w);
}

static void window_frame(struct window *w, int buf, const int32_t *damage)
{
    if (buf < 0 || buf >= NBUF) return;
    if (!w->configured) {
        w->pending_buf = buf;
        memcpy(w->pending_damage, damage, sizeof w->pending_damage);
        return;
    }
    show(w, buf, damage);
}

/* ---- input ---- */

static void ptr_enter(void *d, struct wl_pointer *p, uint32_t s, struct wl_surface *sf,
                      wl_fixed_t x, wl_fixed_t y)
{
    (void)d; (void)p; (void)s;
    pointer_focus = window_for_surface(sf);
    ptr_x = wl_fixed_to_int(x); ptr_y = wl_fixed_to_int(y);
}
static void ptr_leave(void *d, struct wl_pointer *p, uint32_t s, struct wl_surface *sf)
{
    (void)d; (void)p; (void)s; (void)sf;
    if (ptr_down) { ptr_down = 0; send_input(pointer_focus, EV_UP, ptr_x, ptr_y); }
    pointer_focus = NULL;
}
static void ptr_motion(void *d, struct wl_pointer *p, uint32_t t, wl_fixed_t x, wl_fixed_t y)
{
    (void)d; (void)p; (void)t;
    ptr_x = wl_fixed_to_int(x); ptr_y = wl_fixed_to_int(y);
    if (ptr_down) send_input(pointer_focus, EV_MOVE, ptr_x, ptr_y);
}
static void ptr_button(void *d, struct wl_pointer *p, uint32_t s, uint32_t t, uint32_t b, uint32_t st)
{
    (void)d; (void)p; (void)s; (void)t;
    if (b != 0x110 /* BTN_LEFT */) return;
    ptr_down = st == WL_POINTER_BUTTON_STATE_PRESSED;
    send_input(pointer_focus, ptr_down ? EV_DOWN : EV_UP, ptr_x, ptr_y);
}
static void ptr_axis(void *d, struct wl_pointer *p, uint32_t t, uint32_t a, wl_fixed_t v)
{ (void)d; (void)p; (void)t; (void)a; (void)v; }
static const struct wl_pointer_listener ptr_listener = {
    ptr_enter, ptr_leave, ptr_motion, ptr_button, ptr_axis };

static void kb_keymap(void *d, struct wl_keyboard *k, uint32_t f, int32_t fd, uint32_t sz)
{ (void)d; (void)k; (void)f; (void)sz; close(fd); }
static void kb_enter(void *d, struct wl_keyboard *k, uint32_t s, struct wl_surface *sf,
                     struct wl_array *keys)
{ (void)d; (void)k; (void)s; (void)keys; keyboard_focus = window_for_surface(sf); }
static void kb_leave(void *d, struct wl_keyboard *k, uint32_t s, struct wl_surface *sf)
{ (void)d; (void)k; (void)s; (void)sf; keyboard_focus = NULL; }
static void kb_key(void *d, struct wl_keyboard *k, uint32_t s, uint32_t t, uint32_t key, uint32_t st)
{
    (void)d; (void)k; (void)s; (void)t;
    send_input(keyboard_focus, EV_KEY, (int32_t)key, st == WL_KEYBOARD_KEY_STATE_PRESSED);
}
static void kb_mods(void *d, struct wl_keyboard *k, uint32_t s, uint32_t a, uint32_t b,
                    uint32_t c, uint32_t g)
{ (void)d; (void)k; (void)s; (void)a; (void)b; (void)c; (void)g; }
static const struct wl_keyboard_listener kb_listener = {
    kb_keymap, kb_enter, kb_leave, kb_key, kb_mods };

static void seat_caps(void *d, struct wl_seat *st, uint32_t caps)
{
    (void)d;
    static int have_ptr, have_kb;
    if ((caps & WL_SEAT_CAPABILITY_POINTER) && !have_ptr) {
        have_ptr = 1;
        wl_pointer_add_listener(wl_seat_get_pointer(st), &ptr_listener, NULL);
    }
    if ((caps & WL_SEAT_CAPABILITY_KEYBOARD) && !have_kb) {
        have_kb = 1;
        wl_keyboard_add_listener(wl_seat_get_keyboard(st), &kb_listener, NULL);
    }
}
static const struct wl_seat_listener seat_listener = { seat_caps };

/* ---- globals ---- */

static void wm_ping(void *d, struct xdg_wm_base *wm, uint32_t s)
{ (void)d; xdg_wm_base_pong(wm, s); }
static const struct xdg_wm_base_listener wm_listener = { wm_ping };

static void registry(void *d, struct wl_registry *r, uint32_t name, const char *iface, uint32_t ver)
{
    (void)d; (void)ver;
    if (!strcmp(iface, wl_compositor_interface.name))
        compositor = wl_registry_bind(r, name, &wl_compositor_interface, 4);
    else if (!strcmp(iface, wl_shm_interface.name))
        shm = wl_registry_bind(r, name, &wl_shm_interface, 1);
    else if (!strcmp(iface, xdg_wm_base_interface.name)) {
        wm_base = wl_registry_bind(r, name, &xdg_wm_base_interface, 1);
        xdg_wm_base_add_listener(wm_base, &wm_listener, NULL);
    } else if (!strcmp(iface, wl_seat_interface.name) && !seat) {
        /* v1: only the events our listeners above implement are ever sent. */
        seat = wl_registry_bind(r, name, &wl_seat_interface, 1);
        wl_seat_add_listener(seat, &seat_listener, NULL);
    }
}
static void registry_rm(void *d, struct wl_registry *r, uint32_t n)
{ (void)d; (void)r; (void)n; }
static const struct wl_registry_listener registry_listener = { registry, registry_rm };

/* ---- app connections ---- */

static void client_drop(struct client *c)
{
    for (int i = 0; i < MAX_WINDOWS; i++)
        if (windows[i].client == c) window_destroy(&windows[i]);
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
        break;
    case MSG_WINDOW_CREATE: {
        if (len < 13) return;
        char text[1024];
        snprintf(text, sizeof text, "%.*s", (int)(len - 12), (const char *)p + 12);
        const char *title = text + strlen(text) + 1;
        if (title > text + (len - 12)) title = "";
        window_create(c, v[0], v[1], v[2], text, title);
        break;
    }
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

static void client_read(struct client *c)
{
    ssize_t n = read(c->fd, c->rx + c->rx_len, sizeof c->rx - c->rx_len);
    if (n <= 0) { client_drop(c); return; }
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

static int listen_socket(const char *path)
{
    int fd = socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0);
    struct sockaddr_un addr = { .sun_family = AF_UNIX };
    snprintf(addr.sun_path, sizeof addr.sun_path, "%s", path);
    unlink(path);
    if (fd < 0 || bind(fd, (struct sockaddr *)&addr, sizeof addr) < 0 || listen(fd, 8) < 0) {
        fprintf(stderr, "[shell] cannot listen on %s: %s\n", path, strerror(errno));
        exit(1);
    }
    return fd;
}

int main(void)
{
    const char *rt = getenv("XDG_RUNTIME_DIR");
    if (!rt) { fprintf(stderr, "[shell] XDG_RUNTIME_DIR is not set\n"); return 1; }
    char dir[512], path[600];
    snprintf(dir, sizeof dir, "%s/apklayer", rt);
    mkdir(dir, 0700);
    snprintf(path, sizeof path, "%s/shell.sock", dir);

    signal(SIGPIPE, SIG_IGN);
    display = wl_display_connect(NULL);
    if (!display) { fprintf(stderr, "[shell] no Wayland compositor\n"); return 1; }
    struct wl_registry *reg = wl_display_get_registry(display);
    wl_registry_add_listener(reg, &registry_listener, NULL);
    wl_display_roundtrip(display);
    if (!compositor || !shm || !wm_base) { fprintf(stderr, "[shell] missing Wayland globals\n"); return 1; }

    for (int i = 0; i < MAX_CLIENTS; i++) clients[i].fd = -1;
    int lfd = listen_socket(path);
    fprintf(stderr, "[shell] listening on %s\n", path);

    for (;;) {
        struct pollfd pfd[2 + MAX_CLIENTS];
        struct client *who[2 + MAX_CLIENTS];
        int n = 0;
        while (wl_display_prepare_read(display) != 0) wl_display_dispatch_pending(display);
        wl_display_flush(display);
        pfd[n++] = (struct pollfd){ .fd = wl_display_get_fd(display), .events = POLLIN };
        pfd[n++] = (struct pollfd){ .fd = lfd, .events = POLLIN };
        for (int i = 0; i < MAX_CLIENTS; i++) {
            if (clients[i].fd < 0) continue;
            who[n] = &clients[i];
            pfd[n++] = (struct pollfd){ .fd = clients[i].fd, .events = POLLIN };
        }
        if (poll(pfd, (nfds_t)n, -1) < 0 && errno != EINTR) { wl_display_cancel_read(display); break; }

        if (pfd[0].revents & POLLIN) {
            if (wl_display_read_events(display) < 0) break;
        } else {
            wl_display_cancel_read(display);
        }
        wl_display_dispatch_pending(display);

        if (pfd[1].revents & POLLIN) {
            int cfd = accept4(lfd, NULL, NULL, SOCK_CLOEXEC);
            struct client *c = NULL;
            for (int i = 0; i < MAX_CLIENTS && !c; i++)
                if (clients[i].fd < 0) c = &clients[i];
            if (c && cfd >= 0) { memset(c, 0, sizeof *c); c->fd = cfd; }
            else if (cfd >= 0) close(cfd);
        }
        for (int i = 2; i < n; i++) {
            if (who[i]->fd >= 0 && (pfd[i].revents & (POLLIN | POLLHUP | POLLERR))) client_read(who[i]);
        }
    }
    fprintf(stderr, "[shell] Wayland connection lost\n");
    return 1;
}
