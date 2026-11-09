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
 *                 14 GRALLOC_FRAME  {i32 id, buf(0..7), w, h, stride (pixels), android format, usage (low 32 bits), num_fds, num_ints,
 *                                   damage x,y,w,h, ints[num_ints]} + num_fds fds as SCM_RIGHTS: a device gralloc buffer's native_handle
 *                                   (Halium: wlegl.c, through libhybris's android_wlegl). As DMABUF_FRAME: first use of `buf` creates
 *                                   the buffer, num_fds = num_ints = 0 shows it again; released with 114 DMABUF_RELEASE.
 *                  7 VSYNC_REQUEST  {i32 id}: tell me when the compositor shows the next frame of this window
 *                  5 CLIPBOARD_SET  utf8 text: the app copied something; the shell owns the selection
 *                 11 TEST_INPUT     {i32 kind, a, b, c} utf8 window "\0" utf8 text: test input into a window (testinput.c, only with
 *                                   APKLAYER_TEST_INPUT=1); answered with 115 TEST_DONE {i32 result}
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
 *                107 CONFIGURE      {i32 id, i32 logical w, i32 logical h, i32 scale*100, i32 density dpi, i32 navigation bar px, i32 mobile style}
 *                                    the app renders w*scale x h*scale pixels and re-sends
 *                                    WINDOW_CREATE with the same id to rebuffer in place; density: the dpi the app's resources take
 *                                    (160 * scale on a desktop, the handheld's physical density bucket: mobile.c); navigation bar: the
 *                                    bottom inset the shell's navigation bar covers in mobile style (navbar.c), 0 without one
 *   app -> shell  15 WINDOW_MODE    {i32 id, i32 mode}: 0 auto, 1 desktop (floating, title bar), 2 mobile (maximized, navigation bar);
 *                                   sent before WINDOW_CREATE it sets the new window's style, later it switches it (mobile.c)
 *                 16 NAVBAR         {i32 id, i32 visible}: the app shows or hides the navigation bar (immersive mode)
 *                 17 NAVBAR_LAYER   {i32 id}: the window's owner (the virtual display, aosp/hals/graphics/vdisplay) composes the navigation bar
 *                                   into its frames; used where the compositor has no wl_subcompositor (Lipstick), answered with
 *   shell -> app 116 NAVBAR_PIXELS  {i32 id, i32 w, i32 h} + a memfd (w x h px XRGB8888, SCM_RIGHTS): the bar to show over the bottom of
 *                                   the frame, sent on every change (size, theme, a held button); 0 x 0 and no fd: hidden
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
#include "viewporter-client-protocol.h"
#include "fractional-scale-v1-client-protocol.h"
#include "decor.h"
#include "navbar.h"
enum { MSG_HELLO = 1, MSG_WINDOW_CREATE, MSG_FRAME, MSG_WINDOW_DESTROY,
       MSG_INPUT = 101, MSG_CLOSE = 103, MSG_RELEASE = 104,
       MSG_KEY = 105, MSG_SCROLL = 106, MSG_CONFIGURE = 107, MSG_STATE = 108, MSG_CLIPBOARD = 109, MSG_IME = 110, MSG_TOUCH = 111, MSG_DISPLAY = 112, MSG_VSYNC = 113, MSG_DMABUF_RELEASE = 114 };
#define MSG_DMABUF_FRAME 8
#define MSG_GRALLOC_FRAME 14
#define MSG_ATTACH_INPUT 9   /* app -> shell: {own window id, title of the window whose input, state and size this app takes over} */
#define DBUF_MAX 8
#define CLIENT_MAX_FDS 16
#define MSG_WINDOW_SIZE 10   /* app -> shell: {own window id, logical w, h}: the app's activity is fixed to this shape (orientation); the shell fits it to the screen and answers with CONFIGURE (0 x 0: no fixed orientation) */
#define MSG_VSYNC_REQUEST 7
#define MSG_TEST_INPUT 11    /* test -> shell (testinput.c) */
#define MSG_TEST_DONE 115    /* shell -> test: {i32 result} */
#define MSG_DECOR_DARK 13    /* app -> shell: {own window id, 1 light | 2 dark}: the instance's colour scheme (its host settings), for the title bar */
#define MSG_WINDOW_MODE 15   /* app -> shell: {own window id, WM_*} (mobile.c) */
#define MSG_NAVBAR 16        /* app -> shell: {own window id, visible}: the app's navigation bar visibility (immersive mode) */
#define MSG_NAVBAR_LAYER 17  /* app -> shell: {own window id}: the window's owner composes the navigation bar (mobile.c) */
#define MSG_NAVBAR_PIXELS 116 /* shell -> app: {id, w, h} + memfd: that bar's pixels */
enum { WM_AUTO = 0, WM_DESKTOP = 1, WM_MOBILE = 2 };
#define MSG_ACTIVATE 12      /* `apklayer run` of a running app -> shell: {utf8 window title "\0" utf8 xdg-activation token or ""} (activation.c) */
/* TEST_INPUT kinds: focus; key {evdev, down}; pointer {x, y, button (0 move, +code press, -code release)}; touch {action, x, y};
 * text {preedit} + utf8; resize {logical w, h}; scale {percent}: the compositor's preferred scale;
 * maximize {on, w, h}: the compositor's (un)maximize to window geometry w x h (0 = the size is the client's); close: the compositor's close
 * (xdg_toplevel.close, Lipstick's qt_extended_surface.close). x, y: logical content pixels, y < 0 = the title bar. */
enum { TEST_FOCUS, TEST_KEY, TEST_POINTER, TEST_TOUCH, TEST_TEXT, TEST_RESIZE, TEST_SCALE, TEST_MAXIMIZE, TEST_CLOSE };
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
    int dark;                  /* MSG_DECOR_DARK: 0 not said (the bar keeps the desktop's), 1 light, 2 dark */
    int window_mode;           /* MSG_WINDOW_MODE before its window exists: WM_* + 1 of the windows it creates or takes over, 0 = not said */
};
struct window {
    struct client *client;
    int id, w, h, configured;   /* w,h: pixels of the shown buffer */
    int scale;                  /* percent: buffer pixels per logical pixel of what is shown (input mapping), set by window_layout */
    int pref_scale;             /* percent: the compositor's preferred scale for this surface (wp_fractional_scale_v1), 0 = output_scale */
    int told_scale;             /* scale last announced to the app */
    int cfg_w, cfg_h;           /* logical content size (window geometry less the title bar) the app was told, 0 = not yet */
    int float_w, float_h;       /* content size before a maximize/fullscreen/tile: restored when the compositor leaves the size to us */
    int lock;                   /* the top activity's fixed orientation (MSG_WINDOW_SIZE): 1 landscape, 2 portrait, 0 none */
    int bounds_w, bounds_h;     /* xdg_toplevel.configure_bounds: the largest window geometry that fits the work area, 0 = unknown */
    int maximized, activated;   /* xdg toplevel states (maximized: also fullscreen) */
    int test_focus;             /* test input set its activation (testinput.c): the desktop's activation changes are ignored from then on */
    int constrained;            /* maximized, fullscreen or tiled: the compositor decides the size */
    int resized, state_changed; /* what the toplevel configure changed, applied at the xdg_surface configure (geometry.c) */
    int framed;                 /* the app has presented a frame (the splash is gone) */
    struct wp_viewport *viewport;            /* buffer -> logical size (any scale, fractional included) */
    struct wp_fractional_scale_v1 *fscale;
    int text_input_enabled;
    int minimized;              /* set by our Minimize button, cleared when the window is activated again */
    struct decor decor;         /* title bar subsurface (desktop style without server-side decorations only) */
    struct zxdg_toplevel_decoration_v1 *deco;  /* decoration.c */
    int ssd;                    /* the compositor draws the title bar (decoration.c): no bar of ours */
    int styled;                 /* window_set_mode has applied a style (mobile.c) */
    int mode;                   /* WM_* asked for (WM_AUTO: by the session and the output, mobile.c) */
    int mobile;                 /* mobile style: maximized, no title bar, a navigation bar over the content's bottom (navbar.c) */
    struct navbar nav;
    int nav_hidden;             /* the app hid the navigation bar (immersive) */
    int nav_h;                  /* logical height of the navigation bar, 0 without one */
    int nav_layer;              /* the window's owner composes the bar (MSG_NAVBAR_LAYER): used without subsurfaces */
    int dpi, told_dpi, told_nav;/* density the app is told (mobile.c), and what it was last told */
    char label[64];             /* the app's name: the title bar's text */
    int dark;                   /* the bars' colour scheme: the desktop's, then the instance's (window_set_dark) */
    int last_click_ms;          /* double click on the title bar = maximize */
    int bar_hover;              /* DECOR_* under the pointer, DECOR_NONE if none */
    struct wl_surface *surface;
    struct xdg_surface *xdg_surface;
    struct xdg_toplevel *toplevel;
    struct wl_shell_surface *shell_surface;  /* instead of the two above where the compositor has no xdg-shell (wlshell.c) */
    struct qt_extended_surface *qt_ext;      /* with it, Lipstick's qt_surface_extension (raise, lower) */
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
/* logical height of our title bar above the content: none in mobile style or under the compositor's (server-side) bar */
#define W_BAR(w) ((w)->mobile || (w)->ssd ? 0 : DECOR_H)

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
extern struct wl_shell *wl_shell_global;  /* wlshell.c: Lipstick has only wl_shell */
extern struct qt_surface_extension *qt_surface_ext;  /* wlshell.c */
extern struct wl_seat *seat;
extern struct wp_viewporter *viewporter;                     /* registry.c */
extern struct wp_fractional_scale_manager_v1 *fscale_mgr;
extern int output_refresh_mhz;       /* the output's current mode (wl_output.mode), 60000 until known */
extern int output_mode_w, output_mode_h;  /* logical size of the largest output, 0 until known (fallback without configure_bounds) */
extern int output_scale;                  /* percent: the largest integer wl_output scale (fallback without wp_fractional_scale_v1) */
extern int output_px_w, output_px_h, output_mm_w, output_mm_h;  /* the largest output's mode in pixels and its physical size (0: unknown) */
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
void send_msg_fd(struct client *c, uint32_t type, const int32_t *v, int n, int fd);  /* with fd (SCM_RIGHTS) unless it is < 0 */
void send_input(struct window *w, int32_t type, int32_t a, int32_t b);  /* client.c */
void client_read(struct client *c);  /* client.c */
uint32_t kb_android_meta(void);  /* keyboard.c */
long long now_ms(void);  /* keyboard.c */
void key_event(uint32_t evdev, int down);  /* keyboard.c */
struct window *window_for_surface(struct wl_surface *s);  /* window.c */
struct window *window_find(struct client *c, int id);
struct window *window_find_input(struct client *c, int id);  /* window.c */
void window_layout(struct window *w);  /* geometry.c */
void window_set_dark(struct window *w, int dark);  /* the title bar's colour scheme */
/* geometry.c: configures, scale, the app's CONFIGURE */
int window_scale(const struct window *w);  /* the scale the window is drawn at (percent) */
void window_scale_changed(struct window *w, int pref);  /* the compositor prefers another scale (pref percent, 0 = output_scale) */
void window_send_configure(struct window *w);
void window_geometry_init(struct window *w);
void window_geometry_destroy(struct window *w);
void window_show_first(struct window *w);  /* window.c: the frame that came before the first configure, or the splash */
void pointer_forget_window(struct window *w); /* pointer.c: also clear title-bar hover on destroy */
void window_create(struct client *c, int id, int width, int height, const char *path, const char *title);  /* window.c */
void window_destroy(struct window *w);  /* window.c */
void window_frame(struct window *w, int buf, const int32_t *damage);  /* window.c */
void client_vsync_request(struct client *c, int id); /* vsync.c */
void client_vsync_forget(struct client *c);
void vsync_source_changed(void);
#define DMABUF_LINEAR_MAX 64
extern uint32_t dmabuf_linear[DMABUF_LINEAR_MAX]; /* formats the compositor takes with the linear modifier (feedback.c) */
extern int dmabuf_linear_n;
void dmabuf_bind(struct wl_registry *r, uint32_t name, uint32_t ver);  /* feedback.c */
int print_compositor_gpu(void);  /* feedback.c: the compositor's main device (--compositor-gpu) */
void window_request_size(struct window *w, int lw, int lh);
void window_attach_input(struct client *c, int own_id, const char *title);  /* window.c */
void window_drop_proxy(struct client *c);  /* window.c */
void window_dmabuf_show(struct window *w, int buf, const int32_t *damage);
void window_dmabuf_frame(struct window *w, const int32_t *m, const int *fds, int nfds);  /* dmabuf.c */
void wlshell_create(struct window *w, const char *label, const char *app_id);  /* wlshell.c */
void wlshell_first_configure(struct window *w);
void wlshell_lower(struct window *w);
void wlshell_raise(struct window *w);
void wlshell_destroy(struct window *w);
/* damage in buffer coordinates; wl_compositor v3 (Lipstick) has only surface coordinates: the whole surface then */
static inline void surface_damage(struct wl_surface *s, int32_t x, int32_t y, int32_t w, int32_t h)
{
    if (wl_surface_get_version(s) >= WL_SURFACE_DAMAGE_BUFFER_SINCE_VERSION) wl_surface_damage_buffer(s, x, y, w, h);
    else wl_surface_damage(s, 0, 0, INT32_MAX, INT32_MAX);
}
void wlegl_bind(struct wl_registry *r, uint32_t name, uint32_t ver);  /* wlegl.c: libhybris's android_wlegl (Halium compositors) */
void window_gralloc_frame(struct window *w, const int32_t *m, uint32_t len, const int *fds, int nfds);  /* wlegl.c */
void window_dmabuf_destroy(struct window *w);  /* dmabuf.c */
/* test input (testinput.c): the seat's and the compositor's events, injected into one window */
void test_input(struct client *c, const uint8_t *p, uint32_t len);
void activate_message(const uint8_t *p, uint32_t len);  /* activation.c */
extern struct xdg_activation_v1 *activation;  /* activation.c */
extern struct zxdg_decoration_manager_v1 *deco_mgr;  /* decoration.c */
void decoration_create(struct window *w);
void decoration_destroy(struct window *w);
void keyboard_inject_focus(struct window *w);  /* keyboard.c */
void keyboard_inject_leave(struct window *w);  /* keyboard.c */
void keyboard_inject_key(struct window *w, uint32_t evdev, int down);  /* keyboard.c */
void pointer_inject(struct window *w, int x, int y, int button);  /* pointer.c */
void touch_inject(struct window *w, int action, int x, int y);  /* touch.c */
int text_input_inject(struct window *w, const char *text, int preedit);  /* textinput.c */
void window_inject_configure(struct window *w, int cw, int ch, int activated);  /* geometry.c */
void window_inject_maximize(struct window *w, int on, int gw, int gh);  /* geometry.c */
/* mobile.c: window style (desktop or mobile) and density */
int display_density(int scale);                  /* the dpi apps get at that scale (percent): physical bucket on a handheld, else 160 * scale */
int display_mobile(void);                        /* WM_AUTO's answer: a mobile session or a phone-sized output */
void window_set_mode(struct window *w, int mode); /* WM_*: (re)applies the style, before the first configure or live */
void window_display_changed(struct window *w);   /* the outputs changed: style (auto), density and navigation bar follow */
void window_nav_visible(struct window *w, int visible);  /* MSG_NAVBAR */
void window_nav_layer(struct window *w);  /* MSG_NAVBAR_LAYER */
void window_nav_action(struct window *w, int button);    /* a navigation bar button (NAV_*) was pressed */
void window_send_back(struct window *w);         /* pointer.c: KEYCODE_BACK down and up */
void window_minimize(struct window *w);          /* pointer.c: the app stops, then the compositor minimizes */
int print_display_info(void);                    /* --display-info (cli/stack.cpp: ro.sf.lcd_density) */
extern const struct wl_pointer_listener ptr_listener;  /* pointer.c */
extern const struct wl_keyboard_listener kb_listener;  /* keyboard.c */
extern const struct wl_registry_listener registry_listener;  /* registry.c */

#endif
