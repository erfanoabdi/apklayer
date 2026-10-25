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
 *                105 KEY            {i32 id, i32 action(0 down,1 up), i32 android keycode,
 *                                    i32 unicode (UTF-32), i32 android meta}  (xkb-translated, P2)
 *                106 SCROLL         {i32 id, i32 x, i32 y, i32 vscroll*1000, i32 hscroll*1000}
 *                                    (x,y in buffer pixels; scroll in notches, +vscroll = up)
 *                107 CONFIGURE      {i32 id, i32 logical w, i32 logical h, i32 scale*100}
 *                                    the app renders w*scale x h*scale pixels and re-sends
 *                                    WINDOW_CREATE with the same id to rebuffer in place
 */
#define _GNU_SOURCE
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

#include <time.h>
#include <xkbcommon/xkbcommon.h>

#include <wayland-client.h>
#include <wayland-cursor.h>
#include "xdg-shell-client-protocol.h"
#include "cursor-shape-v1-client-protocol.h"
#include "decor.h"

enum { MSG_HELLO = 1, MSG_WINDOW_CREATE, MSG_FRAME, MSG_WINDOW_DESTROY,
       MSG_INPUT = 101, MSG_CLOSE = 103, MSG_RELEASE = 104,
       MSG_KEY = 105, MSG_SCROLL = 106, MSG_CONFIGURE = 107 };
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
    int id, w, h, configured;   /* w,h: buffer pixels */
    int scale;                  /* percent the buffers were rendered at (wl buffer scale) */
    int told_scale;             /* scale last announced to the app */
    int cfg_w, cfg_h;           /* latest logical content size requested by the compositor, 0 = none */
    int maximized, activated;   /* xdg toplevel states */
    struct decor decor;         /* title bar subsurface */
    int last_click_ms;          /* double click on the title bar = maximize */
    int bar_hover;              /* DECOR_* under the pointer, DECOR_NONE if none */
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
static struct wl_subcompositor *subcompositor;
static struct wp_cursor_shape_manager_v1 *cursor_mgr;
static struct wp_cursor_shape_device_v1 *cursor_dev; /* shape of the pointer, set per region */
static uint32_t ptr_enter_serial;
static struct wl_cursor_theme *cursor_theme;  /* fallback when the compositor has no cursor-shape */
static struct wl_surface *cursor_surface;
static struct wl_pointer *the_pointer;
static const char *cursor_name_cur;
static struct wl_shm *shm;
static struct xdg_wm_base *wm_base;
static struct wl_seat *seat;
static struct wl_output *output;
static int output_scale = 100;            /* percent: 100 = 1x, 200 = 2x (P2 HiDPI) */
static struct xkb_context *xkb_ctx;
static struct xkb_keymap *xkb_keymap_g;
static struct xkb_state *xkb_state;
static struct client clients[MAX_CLIENTS];
static struct window windows[MAX_WINDOWS];
static struct window *pointer_focus, *keyboard_focus;
static int ptr_x, ptr_y, ptr_down;   /* surface-local logical coordinates */
static int debug_input;               /* APKLAYER_DEBUG_INPUT=1: log focus changes and key codes */
static int repeat_rate = 25, repeat_delay = 400;   /* keys/s, ms (wl_keyboard.repeat_info) */
static int repeat_key = -1;                        /* evdev code of the held repeating key */
static long long repeat_next_ms;

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
    if (type <= EV_MOVE) { a = a * w->scale / 100; b = b * w->scale / 100; } /* logical -> px */
    int32_t v[4] = { w->id, type, a, b };
    send_msg(w->client, MSG_INPUT, v, 4);
}

/* ---- windows ---- */

static struct window *window_for_surface(struct wl_surface *s)
{
    for (int i = 0; i < MAX_WINDOWS; i++)
        if (windows[i].client && (windows[i].surface == s
                                  || (windows[i].decor.surface && windows[i].decor.surface == s)))
            return &windows[i];
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

/* Window geometry = content + title bar; the bar follows the content width and scale. */
static void window_layout(struct window *w)
{
    int lw = w->w * 100 / w->scale, lh = w->h * 100 / w->scale;
    decor_update(&w->decor, lw, w->scale, w->maximized,
                 w->bar_hover, w->activated);
    xdg_surface_set_window_geometry(w->xdg_surface, 0, -DECOR_H, lw, lh + DECOR_H);
}

static void xs_configure(void *data, struct xdg_surface *xs, uint32_t serial)
{
    struct window *w = data;
    xdg_surface_ack_configure(xs, serial);
    if (!w->configured) {
        w->configured = 1;
        /* Tell the app its size and the output scale (P2 HiDPI: density = 160*scale). */
        int32_t lw = w->cfg_w > 0 ? w->cfg_w : w->w * 100 / w->scale;
        int32_t lh = w->cfg_h > 0 ? w->cfg_h : w->h * 100 / w->scale;
        int32_t v[4] = { w->id, lw, lh, output_scale };
        w->told_scale = output_scale;
        send_msg(w->client, MSG_CONFIGURE, v, 4);
        if (w->pending_buf >= 0) show(w, w->pending_buf, w->pending_damage);
        else wl_surface_commit(w->surface);
    }
}
static const struct xdg_surface_listener xs_listener = { xs_configure };
static void top_configure(void *d, struct xdg_toplevel *t, int32_t cw, int32_t ch,
                          struct wl_array *st)
{
    (void)t;
    struct window *w = d;
    int maximized = 0, activated = 0;
    uint32_t *state;
    wl_array_for_each(state, st) {
        if (*state == XDG_TOPLEVEL_STATE_MAXIMIZED || *state == XDG_TOPLEVEL_STATE_FULLSCREEN)
            maximized = 1;
        if (*state == XDG_TOPLEVEL_STATE_ACTIVATED) activated = 1;
    }
    int changed = maximized != w->maximized || activated != w->activated;
    w->maximized = maximized;
    w->activated = activated;
    /* The configure size is the window geometry: content + title bar. 0x0 = "your preferred
     * size". Any other size is a resize request: tell the app the logical content size; it
     * relayouts and re-sends WINDOW_CREATE with the same id, and the shell swaps the buffers of
     * the existing toplevel (no re-map). */
    ch = ch > DECOR_H ? ch - DECOR_H : 0;
    w->cfg_w = cw > 0 ? cw : 0;
    w->cfg_h = ch > 0 ? ch : 0;
    if (w->configured && changed) window_layout(w);
    if (cw > 0 && ch > 0 && w->configured
        && (cw != w->w * 100 / w->scale || ch != w->h * 100 / w->scale)) {
        int32_t v[4] = { w->id, cw, ch, output_scale };
        w->told_scale = output_scale;
        send_msg(w->client, MSG_CONFIGURE, v, 4);
    }
}
static void top_close(void *data, struct xdg_toplevel *t)
{
    (void)t;
    struct window *w = data;
    int32_t v[1] = { w->id };
    send_msg(w->client, MSG_CLOSE, v, 1);
}
static const struct xdg_toplevel_listener top_listener = {
    .configure = top_configure, .close = top_close };

static void window_destroy(struct window *w);

/* (Re)creates the buffers of w from the shared file. */
static int window_buffers(struct window *w, int width, int height, const char *path)
{
    int fd = open(path, O_RDWR);
    if (fd < 0) { fprintf(stderr, "[shell] open %s: %s\n", path, strerror(errno)); return -1; }
    size_t len = (size_t)width * height * 4 * NBUF;
    struct stat st;
    if (fstat(fd, &st) < 0 || (size_t)st.st_size < len) { close(fd); return -1; }
    for (int i = 0; i < NBUF; i++) {
        if (w->buffers[i]) wl_buffer_destroy(w->buffers[i]);
        w->buffers[i] = NULL;
    }
    struct wl_shm_pool *pool = wl_shm_create_pool(shm, fd, (int32_t)len);
    for (int i = 0; i < NBUF; i++) {
        w->buffers[i] = wl_shm_pool_create_buffer(pool, i * width * height * 4, width, height,
                                                  width * 4, WL_SHM_FORMAT_ARGB8888);
        wl_buffer_add_listener(w->buffers[i], &buffer_listener, w);
    }
    wl_shm_pool_destroy(pool);
    close(fd);
    w->w = width;
    w->h = height;
    w->pending_buf = -1;
    w->scale = w->told_scale ? w->told_scale : 100;
    wl_surface_set_buffer_scale(w->surface, (w->scale + 50) / 100);
    if (w->xdg_surface) window_layout(w);
    return 0;
}

static void window_create(struct client *c, int id, int width, int height, const char *path,
                          const char *title)
{
    if (width <= 0 || height <= 0) return;
    struct window *w = window_find(c, id);
    if (w) {
        /* Resize / DPI change (P2): same id = rebuffer the existing toplevel in place. The next
         * FRAME attaches the new buffers; the old attached buffer stays valid until then. */
        if (window_buffers(w, width, height, path) == 0)
            fprintf(stderr, "[shell] %s: window %d rebuffered %dx%d @%d%%\n", c->pkg, id, width,
                    height, w->scale);
        return;
    }
    for (int i = 0; i < MAX_WINDOWS && !w; i++)
        if (!windows[i].client) w = &windows[i];
    if (!w) return;
    memset(w, 0, sizeof *w);
    w->client = c; w->id = id; w->scale = 100;
    w->surface = wl_compositor_create_surface(compositor);
    if (window_buffers(w, width, height, path) < 0) {
        wl_surface_destroy(w->surface);
        memset(w, 0, sizeof *w);
        return;
    }
    w->xdg_surface = xdg_wm_base_get_xdg_surface(wm_base, w->surface);
    xdg_surface_add_listener(w->xdg_surface, &xs_listener, w);
    decor_create(&w->decor, w->surface);
    w->bar_hover = DECOR_NONE;
    w->toplevel = xdg_surface_get_toplevel(w->xdg_surface);
    xdg_toplevel_add_listener(w->toplevel, &top_listener, w);
    xdg_toplevel_set_title(w->toplevel, title[0] ? title : c->pkg);
    xdg_toplevel_set_app_id(w->toplevel, c->pkg);
    xdg_toplevel_set_min_size(w->toplevel, 240, 240 + DECOR_H);
    window_layout(w); /* geometry needs the toplevel role */ /* desktop-resizable (P2) */
    wl_surface_commit(w->surface);
    fprintf(stderr, "[shell] %s: window %d %dx%d \"%s\"\n", c->pkg, id, width, height, title);
}

static void window_destroy(struct window *w)
{
    if (pointer_focus == w) pointer_focus = NULL;
    if (keyboard_focus == w) keyboard_focus = NULL;
    for (int i = 0; i < NBUF; i++) if (w->buffers[i]) wl_buffer_destroy(w->buffers[i]);
    decor_destroy(&w->decor);
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

static struct window *pointer_decor_win; /* window whose title bar the pointer is over, if any */

static void bar_set_hover(struct window *w, int hover)
{
    if (!w || w->bar_hover == hover) return;
    w->bar_hover = hover;
    window_layout(w);
    wl_surface_commit(w->surface); /* geometry/position apply with the parent commit */
}

static void update_cursor(void);

static void ptr_enter(void *d, struct wl_pointer *p, uint32_t s, struct wl_surface *sf,
                      wl_fixed_t x, wl_fixed_t y)
{
    (void)d;
    ptr_enter_serial = s;
    cursor_name_cur = NULL; /* every enter needs the cursor set again */
    the_pointer = p;
    if (!cursor_dev && cursor_mgr) cursor_dev = wp_cursor_shape_manager_v1_get_pointer(cursor_mgr, p);
    struct window *w = window_for_surface(sf);
    ptr_x = wl_fixed_to_int(x); ptr_y = wl_fixed_to_int(y);
    if (w && sf == w->decor.surface) { pointer_decor_win = w; pointer_focus = NULL; }
    else { pointer_decor_win = NULL; pointer_focus = w; }
    if (debug_input) fprintf(stderr, "[shell] pointer enter %s at %d,%d\n",
                             pointer_decor_win ? "title bar" : w ? "content" : "?", ptr_x, ptr_y);
    update_cursor();
}
static void ptr_leave(void *d, struct wl_pointer *p, uint32_t s, struct wl_surface *sf)
{
    (void)d; (void)p; (void)s; (void)sf;
    if (ptr_down) { ptr_down = 0; send_input(pointer_focus, EV_UP, ptr_x, ptr_y); }
    if (pointer_decor_win) bar_set_hover(pointer_decor_win, DECOR_NONE);
    pointer_focus = NULL;
    pointer_decor_win = NULL;
}
static void ptr_motion(void *d, struct wl_pointer *p, uint32_t t, wl_fixed_t x, wl_fixed_t y)
{
    (void)d; (void)p; (void)t;
    ptr_x = wl_fixed_to_int(x); ptr_y = wl_fixed_to_int(y);
    update_cursor();
    if (pointer_decor_win) {
        bar_set_hover(pointer_decor_win, decor_hit(&pointer_decor_win->decor, ptr_x, ptr_y));
        return;
    }
    if (ptr_down) send_input(pointer_focus, EV_MOVE, ptr_x, ptr_y);
}

static uint32_t kb_android_meta(void);

/* xdg_toplevel resize edge under (x, y) given in window geometry coordinates (bar included). */
static uint32_t resize_edge(const struct window *w, int x, int y)
{
    const int E = 8;
    int lw = w->w * 100 / w->scale, lh = w->h * 100 / w->scale + DECOR_H;
    uint32_t e = 0;
    if (x < E) e |= XDG_TOPLEVEL_RESIZE_EDGE_LEFT;
    if (x >= lw - E) e |= XDG_TOPLEVEL_RESIZE_EDGE_RIGHT;
    if (y < E) e |= XDG_TOPLEVEL_RESIZE_EDGE_TOP;
    if (y >= lh - E) e |= XDG_TOPLEVEL_RESIZE_EDGE_BOTTOM;
    return w->maximized ? 0 : e;
}

/* Cursor names (xcursor) for a resize edge; NULL = the default arrow. */
static const char *edge_cursor_name(uint32_t edge)
{
    switch (edge) {
    case XDG_TOPLEVEL_RESIZE_EDGE_TOP: return "top_side";
    case XDG_TOPLEVEL_RESIZE_EDGE_BOTTOM: return "bottom_side";
    case XDG_TOPLEVEL_RESIZE_EDGE_LEFT: return "left_side";
    case XDG_TOPLEVEL_RESIZE_EDGE_RIGHT: return "right_side";
    case XDG_TOPLEVEL_RESIZE_EDGE_TOP_LEFT: return "top_left_corner";
    case XDG_TOPLEVEL_RESIZE_EDGE_TOP_RIGHT: return "top_right_corner";
    case XDG_TOPLEVEL_RESIZE_EDGE_BOTTOM_LEFT: return "bottom_left_corner";
    case XDG_TOPLEVEL_RESIZE_EDGE_BOTTOM_RIGHT: return "bottom_right_corner";
    default: return "left_ptr";
    }
}

/* Themed cursor through wl_pointer.set_cursor (no cursor-shape protocol on this compositor). */
static void set_themed_cursor(const char *name)
{
    if (!the_pointer) return;
    if (!cursor_theme) {
        int size = 24 * ((output_scale + 50) / 100);
        cursor_theme = wl_cursor_theme_load(NULL, size, shm);
        cursor_surface = wl_compositor_create_surface(compositor);
        if (!cursor_theme) { fprintf(stderr, "[shell] no cursor theme\n"); return; }
    }
    struct wl_cursor *c = wl_cursor_theme_get_cursor(cursor_theme, name);
    if (!c && !strcmp(name, "left_ptr")) c = wl_cursor_theme_get_cursor(cursor_theme, "default");
    if (!c) return;
    struct wl_cursor_image *img = c->images[0];
    int scale = (output_scale + 50) / 100;
    wl_surface_set_buffer_scale(cursor_surface, scale);
    wl_surface_attach(cursor_surface, wl_cursor_image_get_buffer(img), 0, 0);
    wl_surface_damage_buffer(cursor_surface, 0, 0, (int)img->width, (int)img->height);
    wl_surface_commit(cursor_surface);
    wl_pointer_set_cursor(the_pointer, ptr_enter_serial, cursor_surface,
                          (int32_t)img->hotspot_x / scale, (int32_t)img->hotspot_y / scale);
}

/* Resize cursor over the window edges and corners, the default arrow elsewhere. */
static void update_cursor(void)
{
    struct window *w = pointer_decor_win ? pointer_decor_win : pointer_focus;
    uint32_t edge = 0;
    if (w) edge = resize_edge(w, ptr_x, ptr_y + (pointer_decor_win ? 0 : DECOR_H));
    const char *name = edge_cursor_name(edge);
    if (name == cursor_name_cur) return; /* string literals: identity compare is enough */
    cursor_name_cur = name;
    if (debug_input) fprintf(stderr, "[shell] cursor %s\n", name);
    if (cursor_dev) {
        int shape;
        switch (edge) {
        case XDG_TOPLEVEL_RESIZE_EDGE_TOP: shape = WP_CURSOR_SHAPE_DEVICE_V1_SHAPE_N_RESIZE; break;
        case XDG_TOPLEVEL_RESIZE_EDGE_BOTTOM: shape = WP_CURSOR_SHAPE_DEVICE_V1_SHAPE_S_RESIZE; break;
        case XDG_TOPLEVEL_RESIZE_EDGE_LEFT: shape = WP_CURSOR_SHAPE_DEVICE_V1_SHAPE_W_RESIZE; break;
        case XDG_TOPLEVEL_RESIZE_EDGE_RIGHT: shape = WP_CURSOR_SHAPE_DEVICE_V1_SHAPE_E_RESIZE; break;
        case XDG_TOPLEVEL_RESIZE_EDGE_TOP_LEFT: shape = WP_CURSOR_SHAPE_DEVICE_V1_SHAPE_NW_RESIZE; break;
        case XDG_TOPLEVEL_RESIZE_EDGE_TOP_RIGHT: shape = WP_CURSOR_SHAPE_DEVICE_V1_SHAPE_NE_RESIZE; break;
        case XDG_TOPLEVEL_RESIZE_EDGE_BOTTOM_LEFT: shape = WP_CURSOR_SHAPE_DEVICE_V1_SHAPE_SW_RESIZE; break;
        case XDG_TOPLEVEL_RESIZE_EDGE_BOTTOM_RIGHT: shape = WP_CURSOR_SHAPE_DEVICE_V1_SHAPE_SE_RESIZE; break;
        default: shape = WP_CURSOR_SHAPE_DEVICE_V1_SHAPE_DEFAULT; break;
        }
        wp_cursor_shape_device_v1_set_shape(cursor_dev, ptr_enter_serial, (uint32_t)shape);
    } else {
        set_themed_cursor(name);
    }
}

static void send_back(struct window *w)
{
    int32_t down[5] = { w->id, 0, 4 /* KEYCODE_BACK */, 0, 0 };
    int32_t up[5] = { w->id, 1, 4, 0, 0 };
    send_msg(w->client, MSG_KEY, down, 5);
    send_msg(w->client, MSG_KEY, up, 5);
}

static void toggle_maximize(struct window *w)
{
    if (w->maximized) xdg_toplevel_unset_maximized(w->toplevel);
    else xdg_toplevel_set_maximized(w->toplevel);
}

static void bar_button(struct window *w, uint32_t serial, uint32_t time)
{
    int hit = decor_hit(&w->decor, ptr_x, ptr_y);
    uint32_t edge = resize_edge(w, ptr_x, ptr_y);
    if (edge) { xdg_toplevel_resize(w->toplevel, seat, serial, edge); return; }
    switch (hit) {
    case DECOR_BACK: send_back(w); break;
    case DECOR_MIN: xdg_toplevel_set_minimized(w->toplevel); break;
    case DECOR_MAX: toggle_maximize(w); break;
    case DECOR_CLOSE: {
        int32_t v[1] = { w->id };
        send_msg(w->client, MSG_CLOSE, v, 1);
        break;
    }
    case DECOR_BAR:
        if ((int)time - w->last_click_ms < 400) { toggle_maximize(w); w->last_click_ms = 0; }
        else { w->last_click_ms = (int)time; xdg_toplevel_move(w->toplevel, seat, serial); }
        break;
    default: break;
    }
}

static void ptr_button(void *d, struct wl_pointer *p, uint32_t s, uint32_t t, uint32_t b, uint32_t st)
{
    (void)d; (void)p;
    int pressed = st == WL_POINTER_BUTTON_STATE_PRESSED;
    if (pressed && pointer_decor_win && b == 0x110) { bar_button(pointer_decor_win, s, t); return; }
    if (pressed && pointer_focus && (b == 0x110 || b == 0x111)) {
        if (kb_android_meta() & 0x02) {
            /* Alt+left-drag moves, Alt+right-drag resizes (also without the title bar). */
            if (b == 0x110) xdg_toplevel_move(pointer_focus->toplevel, seat, s);
            else xdg_toplevel_resize(pointer_focus->toplevel, seat, s,
                                     XDG_TOPLEVEL_RESIZE_EDGE_BOTTOM_RIGHT);
            return;
        }
        /* Content edges resize the window instead of reaching the app. */
        uint32_t edge = b == 0x110 ? resize_edge(pointer_focus, ptr_x, ptr_y + DECOR_H) : 0;
        if (edge) { xdg_toplevel_resize(pointer_focus->toplevel, seat, s, edge); return; }
    }
    if (b != 0x110 /* BTN_LEFT */ || pointer_decor_win) return;
    if (!pressed && !ptr_down) return; /* release of a press the shell consumed (resize edge) */
    ptr_down = pressed;
    send_input(pointer_focus, ptr_down ? EV_DOWN : EV_UP, ptr_x, ptr_y);
}
static double axis_v, axis_h; /* accumulated wheel deltas, flushed on pointer frame (P2) */
static void ptr_axis(void *d, struct wl_pointer *p, uint32_t t, uint32_t a, wl_fixed_t v)
{
    (void)d; (void)p; (void)t;
    double val = wl_fixed_to_double(v);
    if (debug_input) fprintf(stderr, "[shell] axis %u %.1f (focus %s)\n", a, val, pointer_focus ? "content" : pointer_decor_win ? "bar" : "none");
    if (a == WL_POINTER_AXIS_VERTICAL_SCROLL) axis_v += val;
    else if (a == WL_POINTER_AXIS_HORIZONTAL_SCROLL) axis_h += val;
}
static void ptr_frame(void *d, struct wl_pointer *p)
{
    (void)d; (void)p;
    if ((axis_v != 0 || axis_h != 0) && pointer_focus) {
        /* Wayland axis: ~10 per wheel notch, + = down/right. Android: AXIS_VSCROLL + = up,
         * AXIS_HSCROLL + = right, in notches (the view applies its scroll factor); sent *1000. */
        struct window *w = pointer_focus;
        if (debug_input) fprintf(stderr, "[shell] scroll v=%.1f h=%.1f at %d,%d\n", axis_v, axis_h, ptr_x, ptr_y);
        int32_t v[5] = { w->id, ptr_x * w->scale / 100, ptr_y * w->scale / 100,
                         (int32_t)(-axis_v * 100), (int32_t)(axis_h * 100) };
        send_msg(pointer_focus->client, MSG_SCROLL, v, 5);
    }
    axis_v = axis_h = 0;
}
static void ptr_axis_source(void *d, struct wl_pointer *p, uint32_t s)
{ (void)d; (void)p; (void)s; }
static void ptr_axis_stop(void *d, struct wl_pointer *p, uint32_t t, uint32_t a)
{ (void)d; (void)p; (void)t; (void)a; }
static void ptr_axis_discrete(void *d, struct wl_pointer *p, uint32_t a, int32_t n)
{ (void)d; (void)p; (void)a; (void)n; }
static const struct wl_pointer_listener ptr_listener = {
    .enter = ptr_enter, .leave = ptr_leave, .motion = ptr_motion, .button = ptr_button,
    .axis = ptr_axis, .frame = ptr_frame, .axis_source = ptr_axis_source,
    .axis_stop = ptr_axis_stop, .axis_discrete = ptr_axis_discrete };

/* ---- keyboard: xkb -> Android key events (P2) ---- */

/* keysym -> Android keycode; printable keys derive from the char (a-z=29.., 0-9=7..),
 * specials by table. KEYCODE_UNKNOWN (0) with a nonzero unicode still types. */
static int32_t keysym_to_android(xkb_keysym_t sym, int32_t *uni)
{
    uint32_t ch = xkb_keysym_to_utf32(sym);
    *uni = (int32_t)ch;
    if (ch >= 'a' && ch <= 'z') return 29 + (int32_t)(ch - 'a');
    if (ch >= 'A' && ch <= 'Z') return 29 + (int32_t)(ch - 'A');
    if (ch >= '0' && ch <= '9') return 7 + (int32_t)(ch - '0');
    switch (sym) {
    case XKB_KEY_Return: case XKB_KEY_KP_Enter: return 66;
    case XKB_KEY_BackSpace: return 67;
    case XKB_KEY_Tab: return 61;
    case XKB_KEY_space: return 62;
    case XKB_KEY_Escape: case XKB_KEY_XF86Back: return 4; /* Back */
    case XKB_KEY_Delete: return 112;
    case XKB_KEY_Home: return 3;
    case XKB_KEY_End: return 123;
    case XKB_KEY_Page_Up: return 92;
    case XKB_KEY_Page_Down: return 93;
    case XKB_KEY_Up: return 19;
    case XKB_KEY_Down: return 20;
    case XKB_KEY_Left: return 21;
    case XKB_KEY_Right: return 22;
    case XKB_KEY_Shift_L: return 59;
    case XKB_KEY_Shift_R: return 60;
    case XKB_KEY_Control_L: return 129;
    case XKB_KEY_Control_R: return 130;
    case XKB_KEY_Alt_L: return 57;
    case XKB_KEY_Alt_R: return 58;
    case XKB_KEY_Meta_L: case XKB_KEY_Super_L: return 117;
    case XKB_KEY_Meta_R: case XKB_KEY_Super_R: return 118;
    default: return 0;
    }
}

/* Android KeyEvent meta bits: SHIFT=1, ALT=2, CTRL=0x1000, META=0x10000. */
static uint32_t kb_android_meta(void)
{
    if (!xkb_state) return 0;
    uint32_t m = 0;
    if (xkb_state_mod_name_is_active(xkb_state, XKB_MOD_NAME_SHIFT, XKB_STATE_MODS_EFFECTIVE))
        m |= 1;
    if (xkb_state_mod_name_is_active(xkb_state, XKB_MOD_NAME_ALT, XKB_STATE_MODS_EFFECTIVE))
        m |= 2;
    if (xkb_state_mod_name_is_active(xkb_state, XKB_MOD_NAME_CTRL, XKB_STATE_MODS_EFFECTIVE))
        m |= 0x1000;
    if (xkb_state_mod_name_is_active(xkb_state, XKB_MOD_NAME_LOGO, XKB_STATE_MODS_EFFECTIVE))
        m |= 0x10000;
    return m;
}

static void kb_keymap(void *d, struct wl_keyboard *k, uint32_t fmt, int32_t fd, uint32_t sz)
{
    (void)d; (void)k; (void)fmt;
    char *map = malloc(sz + 1);
    if (map) {
        ssize_t got = 0;
        while (got < (ssize_t)sz) {
            ssize_t r = read(fd, map + got, sz - (size_t)got);
            if (r <= 0) break;
            got += r;
        }
        map[got] = 0;
        if (!xkb_ctx) xkb_ctx = xkb_context_new(XKB_CONTEXT_NO_FLAGS);
        if (xkb_state) { xkb_state_unref(xkb_state); xkb_state = NULL; }
        if (xkb_keymap_g) xkb_keymap_unref(xkb_keymap_g);
        xkb_keymap_g = xkb_ctx ? xkb_keymap_new_from_string(xkb_ctx, map,
                            XKB_KEYMAP_FORMAT_TEXT_V1, XKB_KEYMAP_COMPILE_NO_FLAGS) : NULL;
        if (xkb_keymap_g) xkb_state = xkb_state_new(xkb_keymap_g);
        fprintf(stderr, "[shell] xkb keymap loaded (%zd bytes, %s)\n", got,
                xkb_keymap_g ? "ok" : "FAILED");
    }
    free(map);
    close(fd);
}
static void kb_enter(void *d, struct wl_keyboard *k, uint32_t s, struct wl_surface *sf,
                     struct wl_array *keys)
{
    (void)d; (void)k; (void)s; (void)keys;
    keyboard_focus = window_for_surface(sf);
    if (debug_input) fprintf(stderr, "[shell] keyboard enter: %s\n", keyboard_focus ? "window" : "?");
}
static void kb_leave(void *d, struct wl_keyboard *k, uint32_t s, struct wl_surface *sf)
{ (void)d; (void)k; (void)s; (void)sf; keyboard_focus = NULL; repeat_key = -1; }

static long long now_ms(void)
{
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (long long)ts.tv_sec * 1000 + ts.tv_nsec / 1000000;
}

/* Sends one xkb-translated key event to the focused window. evdev = Linux key code. */
static void key_event(uint32_t evdev, int down)
{
    if (!keyboard_focus) return;
    if (!xkb_state) { send_input(keyboard_focus, EV_KEY, (int32_t)evdev, down); return; }
    xkb_keycode_t kc = evdev + 8; /* evdev -> xkb keycode */
    xkb_keysym_t sym = xkb_state_key_get_one_sym(xkb_state, kc);
    int32_t uni = 0;
    int32_t code = keysym_to_android(sym, &uni);
    uint32_t meta = kb_android_meta();
    if (sym == XKB_KEY_Left && (meta & 0x02)) code = 4; /* Alt+Left = Back */
    uni = (int32_t)xkb_state_key_get_utf32(xkb_state, kc); /* state-aware: shift, AltGr, dead keys */
    if (uni < 32 || uni == 127) uni = 0;                   /* control chars are not text */
    int32_t v[5] = { keyboard_focus->id, down ? 0 : 1, code, uni, (int32_t)meta };
    send_msg(keyboard_focus->client, MSG_KEY, v, 5);
}
static void kb_key(void *d, struct wl_keyboard *k, uint32_t s, uint32_t t, uint32_t key, uint32_t st)
{
    (void)d; (void)k; (void)s; (void)t;
    int down = st == WL_KEYBOARD_KEY_STATE_PRESSED;
    if (debug_input) fprintf(stderr, "[shell] key %u down=%d focus=%d\n", key, down, keyboard_focus != NULL);
    key_event(key, down);
    if (down && xkb_keymap_g && repeat_rate > 0 && xkb_keymap_key_repeats(xkb_keymap_g, key + 8)) {
        repeat_key = (int)key;
        repeat_next_ms = now_ms() + repeat_delay;
    } else if (!down && repeat_key == (int)key) {
        repeat_key = -1;
    }
}
static void kb_mods(void *d, struct wl_keyboard *k, uint32_t s, uint32_t dm, uint32_t lm,
                    uint32_t km, uint32_t group)
{
    (void)d; (void)k; (void)s;
    if (xkb_state) xkb_state_update_mask(xkb_state, dm, lm, km, 0, 0, group);
}
static void kb_repeat_info(void *d, struct wl_keyboard *k, int32_t rate, int32_t delay)
{ (void)d; (void)k; repeat_rate = rate; repeat_delay = delay; }
static const struct wl_keyboard_listener kb_listener = {
    .keymap = kb_keymap, .enter = kb_enter, .leave = kb_leave, .key = kb_key,
    .modifiers = kb_mods, .repeat_info = kb_repeat_info };

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
static void seat_name(void *d, struct wl_seat *st, const char *n)
{ (void)d; (void)st; (void)n; }
static const struct wl_seat_listener seat_listener = { .capabilities = seat_caps, .name = seat_name };

/* ---- output: integer scale feeds CONFIGURE (P2 HiDPI) ---- */

static void out_geometry(void *d, struct wl_output *o, int32_t x, int32_t y, int32_t pw,
                         int32_t ph, int32_t sub, const char *make, const char *model,
                         int32_t tf)
{ (void)d; (void)o; (void)x; (void)y; (void)pw; (void)ph; (void)sub; (void)make; (void)model; (void)tf; }
static void out_mode(void *d, struct wl_output *o, uint32_t f, int32_t w, int32_t h,
                     int32_t refresh)
{ (void)d; (void)o; (void)f; (void)w; (void)h; (void)refresh; }
static void out_scale(void *d, struct wl_output *o, int32_t s)
{ (void)d; (void)o; output_scale = s * 100; }
static void out_done(void *d, struct wl_output *o)
{
    (void)d; (void)o;
    /* Scale changed (HiDPI, or moved to another output): tell every app so it re-renders. */
    for (int i = 0; i < MAX_WINDOWS; i++) {
        struct window *w = &windows[i];
        if (!w->client || !w->configured || w->told_scale == output_scale) continue;
        int32_t v[4] = { w->id, w->w * 100 / w->scale, w->h * 100 / w->scale, output_scale };
        w->told_scale = output_scale;
        send_msg(w->client, MSG_CONFIGURE, v, 4);
    }
}
static const struct wl_output_listener out_listener = {
    .geometry = out_geometry, .mode = out_mode, .done = out_done, .scale = out_scale };

/* ---- globals ---- */

static void wm_ping(void *d, struct xdg_wm_base *wm, uint32_t s)
{ (void)d; xdg_wm_base_pong(wm, s); }
static const struct xdg_wm_base_listener wm_listener = { wm_ping };

static void registry(void *d, struct wl_registry *r, uint32_t name, const char *iface, uint32_t ver)
{
    (void)d;
    if (debug_input) fprintf(stderr, "[shell] global %s v%u\n", iface, ver);
    if (!strcmp(iface, wl_compositor_interface.name))
        compositor = wl_registry_bind(r, name, &wl_compositor_interface, 4);
    else if (!strcmp(iface, wl_subcompositor_interface.name))
        subcompositor = wl_registry_bind(r, name, &wl_subcompositor_interface, 1);
    else if (!strcmp(iface, wp_cursor_shape_manager_v1_interface.name))
        cursor_mgr = wl_registry_bind(r, name, &wp_cursor_shape_manager_v1_interface, 1);
    else if (!strcmp(iface, wl_shm_interface.name))
        shm = wl_registry_bind(r, name, &wl_shm_interface, 1);
    else if (!strcmp(iface, xdg_wm_base_interface.name)) {
        wm_base = wl_registry_bind(r, name, &xdg_wm_base_interface, 1);
        xdg_wm_base_add_listener(wm_base, &wm_listener, NULL);
    } else if (!strcmp(iface, wl_seat_interface.name) && !seat) {
        /* v5: pointer frame/axis events; every listener slot is filled. */
        seat = wl_registry_bind(r, name, &wl_seat_interface, ver < 5 ? ver : 5);
        wl_seat_add_listener(seat, &seat_listener, NULL);
    } else if (!strcmp(iface, wl_output_interface.name) && !output) {
        output = wl_registry_bind(r, name, &wl_output_interface, ver < 2 ? ver : 2); /* scale/done */
        wl_output_add_listener(output, &out_listener, NULL);
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
        if (len < 13 || len - 12 >= 1024) return;
        char text[1024];                        /* "path\0title", copied by length */
        memcpy(text, p + 12, len - 12);
        text[len - 12] = 0;
        size_t path_len = strlen(text);
        const char *title = path_len + 1 < len - 12 ? text + path_len + 1 : "";
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
    const char *dbg = getenv("APKLAYER_DEBUG_INPUT");
    debug_input = dbg && *dbg;
    display = wl_display_connect(NULL);
    if (!display) { fprintf(stderr, "[shell] no Wayland compositor\n"); return 1; }
    struct wl_registry *reg = wl_display_get_registry(display);
    wl_registry_add_listener(reg, &registry_listener, NULL);
    wl_display_roundtrip(display);
    decor_setup(compositor, subcompositor, shm);
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
        int timeout = -1;
        if (repeat_key >= 0) {
            long long wait = repeat_next_ms - now_ms();
            timeout = wait > 0 ? (int)wait : 0;
        }
        if (poll(pfd, (nfds_t)n, timeout) < 0 && errno != EINTR) { wl_display_cancel_read(display); break; }

        if (pfd[0].revents & POLLIN) {
            if (wl_display_read_events(display) < 0) break;
        } else {
            wl_display_cancel_read(display);
        }
        wl_display_dispatch_pending(display);
        if (repeat_key >= 0 && now_ms() >= repeat_next_ms) {
            key_event((uint32_t)repeat_key, 1);
            repeat_next_ms += 1000 / repeat_rate;
        }

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
