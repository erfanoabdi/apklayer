#ifndef APKLAYER_SHELL_H
#define APKLAYER_SHELL_H

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

/* ---- state shared between the modules (defined in main.c) ---- */
extern struct wl_display *display;
extern struct wl_compositor *compositor;
extern struct wl_subcompositor *subcompositor;
extern struct wp_cursor_shape_manager_v1 *cursor_mgr;
extern struct wl_shm *shm;
extern struct xdg_wm_base *wm_base;
extern struct wl_seat *seat;
extern struct wl_output *output;
extern int output_scale;            /* percent: 100 = 1x, 200 = 2x (P2 HiDPI) */
extern struct window windows[MAX_WINDOWS];
extern struct window *pointer_focus, *keyboard_focus;
extern int debug_input;               /* APKLAYER_DEBUG_INPUT=1: log focus changes and key codes */
extern int repeat_rate, repeat_delay;   /* keys/s, ms (wl_keyboard.repeat_info) */
extern struct client clients[MAX_CLIENTS];
extern int repeat_key;                        /* evdev code of the held repeating key */
extern long long repeat_next_ms;

/* ---- functions shared between the modules ---- */
void send_msg(struct client *c, uint32_t type, const int32_t *v, int n);  /* client.c */
void send_input(struct window *w, int32_t type, int32_t a, int32_t b);  /* client.c */
void client_read(struct client *c);  /* client.c */
uint32_t kb_android_meta(void);  /* keyboard.c */
long long now_ms(void);  /* keyboard.c */
void key_event(uint32_t evdev, int down);  /* keyboard.c */
struct window *window_for_surface(struct wl_surface *s);  /* window.c */
struct window *window_find(struct client *c, int id);  /* window.c */
void window_layout(struct window *w);  /* window.c */
void window_create(struct client *c, int id, int width, int height, const char *path, const char *title);  /* window.c */
void window_destroy(struct window *w);  /* window.c */
void window_frame(struct window *w, int buf, const int32_t *damage);  /* window.c */
extern const struct wl_pointer_listener ptr_listener;  /* pointer.c */
extern const struct wl_keyboard_listener kb_listener;  /* keyboard.c */
extern const struct wl_registry_listener registry_listener;  /* registry.c */

#endif
