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
 *                  6 TEXT_INPUT     {i32 id, i32 enabled}: a text field gained/lost focus (IME on/off)
 *                  8 DMABUF_FRAME   {i32 id, buf(0..7), fourcc, w, h, nplanes, mod_lo, mod_hi, stride[4], offset[4], damage x,y,w,h}
 *                                   + nplanes dmabuf fds as SCM_RIGHTS. First use of `buf` creates the buffer from the fds (the
 *                                   shell keeps it); nplanes = 0 shows the buffer already created. The window is created with
 *                                   WINDOW_CREATE and an EMPTY shm path (a dmabuf-only window). Zero copy: the compositor gets the
 *                                   dmabufs through zwp_linux_dmabuf_v1.
 *                  7 VSYNC_REQUEST  {i32 id}: tell me when the compositor shows the next frame of this window
 *                  5 CLIPBOARD_SET  utf8 text: the app copied something; the shell owns the selection
 *   shell -> app 101 INPUT          {i32 id, i32 type, i32 a, i32 b}
 *                    type 0/1/2 = MotionEvent DOWN/UP/MOVE at (a, b); 3 = hover move, 4 = right click (long press); 100 = key (evdev a, pressed b)
 *                103 CLOSE          {i32 id}
 *                104 RELEASE        {i32 id, i32 buf}
 *                105 KEY            {i32 id, i32 action(0 down,1 up), i32 android keycode,
 *                                    i32 unicode (UTF-32), i32 android meta}  (xkb-translated, P2)
 *                106 SCROLL         {i32 id, i32 x, i32 y, i32 vscroll*1000, i32 hscroll*1000}
 *                                    (x,y in buffer pixels; scroll in notches, +vscroll = up)
 *                111 TOUCH          {i32 id, i32 action(0 down,1 up,2 move,3 cancel), i32 finger, i32 x, i32 y}  (pixels)
 *                110 IME            {i32 id, i32 delete_before, i32 delete_after} utf8 commit "\0" utf8 preedit
 *                109 CLIPBOARD      utf8 text: the system selection changed (text only, < 60 KB)
 *                108 STATE          {i32 id, i32 flags}  bit0 = window active (focused), bit1 = minimized
 *                                    (apps pause on !active, stop on minimized, resume on active)
 *                114 DMABUF_RELEASE {i32 id, buf}: the compositor is done with that dmabuf buffer (it may be drawn into again)
 *                112 DISPLAY        {i32 refresh in mHz}: the output's refresh rate (sent at HELLO and when it changes)
 *                113 VSYNC          {i32 id, i32 ms}: answer to VSYNC_REQUEST, the compositor's frame callback time (ms)
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
#include "linux-dmabuf-v1-client-protocol.h"
#include "decor.h"
enum { MSG_HELLO = 1, MSG_WINDOW_CREATE, MSG_FRAME, MSG_WINDOW_DESTROY,
       MSG_INPUT = 101, MSG_CLOSE = 103, MSG_RELEASE = 104,
       MSG_KEY = 105, MSG_SCROLL = 106, MSG_CONFIGURE = 107, MSG_STATE = 108, MSG_CLIPBOARD = 109, MSG_IME = 110, MSG_TOUCH = 111, MSG_DISPLAY = 112, MSG_VSYNC = 113, MSG_DMABUF_RELEASE = 114 };
#define MSG_DMABUF_FRAME 8
#define MSG_ATTACH_INPUT 9   /* app -> shell: {own window id, title of the window whose input, state and size this app takes over} */
#define DBUF_MAX 8
#define CLIENT_MAX_FDS 16
#define MSG_VSYNC_REQUEST 7
#define MSG_TEXT_INPUT 6
/* app -> shell uses type 5 for MSG_CLIPBOARD_SET (text); shell -> app 109 pushes the selection */
#define MSG_CLIPBOARD_SET 5
enum { EV_DOWN = 0, EV_UP = 1, EV_MOVE = 2, EV_HOVER = 3, EV_SECONDARY = 4, EV_KEY = 100 };
#define MAX_CLIENTS 32
#define MAX_WINDOWS 64
#define NBUF 2
struct client {
    int fd, pid;
    char pkg[128];
    size_t last_clipboard_len;
    char *last_clipboard; /* per receiving JVM; focus changes must not suppress delivery */
    uint8_t rx[65536];
    size_t rx_len;
    int fds[CLIENT_MAX_FDS];   /* fds received with SCM_RIGHTS, consumed in order by the messages that declare them */
    int nfds;
    char attach_title[64];     /* MSG_ATTACH_INPUT waiting for a window of that title to appear */
    int attach_id;
};
struct window {
    struct client *client;
    int id, w, h, configured;   /* w,h: buffer pixels */
    int scale;                  /* percent the buffers were rendered at (wl buffer scale) */
    int told_scale;             /* scale last announced to the app */
    int cfg_w, cfg_h;           /* latest logical content size requested by the compositor, 0 = none */
    int maximized, activated;   /* xdg toplevel states */
    int text_input_enabled;
    int minimized;              /* set by our Minimize button, cleared when the window is activated again */
    struct decor decor;         /* title bar subsurface */
    int last_click_ms;          /* double click on the title bar = maximize */
    int bar_hover;              /* DECOR_* under the pointer, DECOR_NONE if none */
    struct wl_surface *surface;
    struct xdg_surface *xdg_surface;
    struct xdg_toplevel *toplevel;
    void *map;
    size_t map_len;
    struct wl_buffer *buffers[NBUF];
    struct wl_buffer *initial_buffer; /* blank display frame, freed on release */
    struct wl_buffer *dbufs[DBUF_MAX]; /* imported dmabuf buffers of a dmabuf window */
    int pending_buf;            /* frame received before the first configure */
    int32_t pending_damage[4];
    int pending_dbuf;           /* dmabuf frame received before the first configure (committing a buffer earlier is an xdg-shell error) */
    int32_t pending_ddamage[4];
    char title[64];
    struct client *proxy;       /* the app that took over this window's input (MSG_ATTACH_INPUT): the window belongs to the display HAL */
    int proxy_id;               /* the id that app knows the window by */
};
/* who an input, state or size message about a window goes to, and under which window id */
#define W_TARGET(w) ((w)->proxy ? (w)->proxy : (w)->client)
#define W_ID(w) ((w)->proxy ? (w)->proxy_id : (w)->id)

/* ---- state shared between the modules (defined in main.c) ---- */
extern struct wl_display *display;
extern struct zwp_text_input_manager_v3 *text_mgr;  /* textinput.c */
extern struct wl_data_device_manager *data_mgr;  /* clipboard.c */
extern uint32_t last_input_serial;  /* clipboard.c: serial of the last key/button, for set_selection */
extern struct wl_compositor *compositor;
extern struct wl_subcompositor *subcompositor;
extern struct wp_cursor_shape_manager_v1 *cursor_mgr;
extern struct wl_shm *shm;
extern struct zwp_linux_dmabuf_v1 *dmabuf_mgr;  /* dmabuf.c */
extern struct xdg_wm_base *wm_base;
extern struct wl_seat *seat;
extern struct wl_output *output;
extern int output_refresh_mhz;       /* the output's current mode (wl_output.mode), 60000 until known */
extern int output_scale;            /* percent: 100 = 1x, 200 = 2x (P2 HiDPI) */
extern struct window windows[MAX_WINDOWS];
extern struct window *pointer_focus, *keyboard_focus;
extern int debug_input;               /* APKLAYER_DEBUG_INPUT=1: log focus changes and key codes */
extern int repeat_rate, repeat_delay;   /* keys/s, ms (wl_keyboard.repeat_info) */
extern struct client clients[MAX_CLIENTS];
extern int repeat_key;                        /* evdev code of the held repeating key */
extern long long repeat_next_ms;

/* ---- functions shared between the modules ---- */
void send_state(struct window *w);  /* window.c */
void send_bytes(struct client *c, uint32_t type, const char *data, size_t len);  /* client.c */
void text_input_enable(struct window *w, int enable);
void text_input_forget_window(struct window *w);  /* textinput.c */
void clipboard_init(void);
extern const struct wl_touch_listener touch_listener;  /* touch.c */  /* clipboard.c */
void clipboard_push(struct window *w);  /* clipboard.c */
#define CLIPBOARD_FDS 9 /* one incoming transfer, up to eight outgoing readers */
void clipboard_set(struct client *c, const char *text, size_t len);
void clipboard_client_drop(struct client *c);
void clipboard_pollfds(struct pollfd *p);
void clipboard_dispatch(struct pollfd *p);  /* clipboard.c */
void send_msg(struct client *c, uint32_t type, const int32_t *v, int n);  /* client.c */
void send_input(struct window *w, int32_t type, int32_t a, int32_t b);  /* client.c */
void client_read(struct client *c);  /* client.c */
uint32_t kb_android_meta(void);  /* keyboard.c */
long long now_ms(void);  /* keyboard.c */
void key_event(uint32_t evdev, int down);  /* keyboard.c */
struct window *window_for_surface(struct wl_surface *s);  /* window.c */
struct window *window_find(struct client *c, int id);
struct window *window_find_input(struct client *c, int id);  /* window.c */
void window_layout(struct window *w);  /* window.c */
void pointer_forget_window(struct window *w); /* pointer.c: also clear title-bar hover on destroy */
void window_create(struct client *c, int id, int width, int height, const char *path, const char *title);  /* window.c */
void window_destroy(struct window *w);  /* window.c */
void window_frame(struct window *w, int buf, const int32_t *damage);  /* window.c */
void client_vsync_request(struct client *c, int id); /* vsync.c */
void client_vsync_forget(struct client *c);
void vsync_source_changed(void);
#define DMABUF_LINEAR_MAX 64
extern uint32_t dmabuf_linear[DMABUF_LINEAR_MAX]; /* formats the compositor takes with the linear modifier (registry.c) */
extern int dmabuf_linear_n;
void window_attach_input(struct client *c, int own_id, const char *title);  /* window.c */
void window_drop_proxy(struct client *c);  /* window.c */
void window_dmabuf_show(struct window *w, int buf, const int32_t *damage);
void window_dmabuf_frame(struct window *w, const int32_t *m, const int *fds, int nfds);  /* dmabuf.c */
void window_dmabuf_destroy(struct window *w);  /* dmabuf.c */
extern const struct wl_pointer_listener ptr_listener;  /* pointer.c */
extern const struct wl_keyboard_listener kb_listener;  /* keyboard.c */
extern const struct wl_registry_listener registry_listener;  /* registry.c */

#endif
