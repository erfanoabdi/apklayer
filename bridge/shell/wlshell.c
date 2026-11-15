/* Windows on compositors without xdg-shell: Sailfish OS's Lipstick (QtWayland 5.6) offers only wl_shell (docs/HALIUM.md). Lipstick shows
 * every application full screen and sends no configure of its own: the window is laid out maximized at the output's size and active. */
#include "shell.h"
#include "surface-extension-client-protocol.h"

struct wl_shell *wl_shell_global;
struct qt_surface_extension *qt_surface_ext;  /* Lipstick's: a window's raise and lower (Home goes to the home screen) */

/* Shown again after Home: the app resumes (the window stayed active: Lipstick tells a client nothing of focus) */
static void resume(struct window *w)
{
    if (!w->minimized) return;
    w->minimized = 0;
    send_state(w);
}

/* Lipstick shows the window again (its cover tapped) */
static void ext_visibility(void *d, struct qt_extended_surface *e, int32_t visible)
{
    (void)e;
    struct window *w = d;
    fprintf(stderr, "[shell] %s: %s by the compositor\n", w->title, visible ? "shown" : "hidden");
    if (visible) resume(w);
    else if (!text_mgr) maliit_enable(w, 4);  /* another app is up: its keyboard goes (a field keeps focus; the next showSoftInput brings it back) */
}
static void ext_property(void *d, struct qt_extended_surface *e, const char *name, struct wl_array *value)
{ (void)d; (void)e; (void)name; (void)value; }
static void ext_close(void *d, struct qt_extended_surface *e)
{ (void)e; struct window *w = d; fprintf(stderr, "[shell] %s: closed by the compositor\n", w->title); send_msg(W_TARGET(w), MSG_CLOSE, (int32_t[]){ W_ID(w) }, 1); }
static const struct qt_extended_surface_listener ext_listener = { ext_visibility, ext_property, ext_close };

static void ping(void *d, struct wl_shell_surface *s, uint32_t serial)
{ (void)d; wl_shell_surface_pong(s, serial); }
static void configure(void *d, struct wl_shell_surface *s, uint32_t edges, int32_t cw, int32_t ch)
{ (void)s; (void)edges; if (cw > 0 && ch > 0) window_inject_maximize(d, 1, cw, ch); }
static void popup_done(void *d, struct wl_shell_surface *s)
{ (void)d; (void)s; }
static const struct wl_shell_surface_listener listener = { ping, configure, popup_done };

void wlshell_create(struct window *w, const char *label, const char *app_id)
{
    w->shell_surface = wl_shell_get_shell_surface(wl_shell_global, w->surface);
    wl_shell_surface_add_listener(w->shell_surface, &listener, w);
    wl_shell_surface_set_title(w->shell_surface, label);
    wl_shell_surface_set_class(w->shell_surface, app_id);
    wl_shell_surface_set_toplevel(w->shell_surface);
    if (!qt_surface_ext) return;
    w->qt_ext = qt_surface_extension_get_extended_surface(qt_surface_ext, w->surface);
    qt_extended_surface_add_listener(w->qt_ext, &ext_listener, w);
}

/* Home: Lipstick lowers the window to the home screen (jolla-home's onWindowLowered); raise: `apklayer run` of the running app */
void wlshell_lower(struct window *w) { if (w->qt_ext) qt_extended_surface_lower(w->qt_ext); }
void wlshell_raise(struct window *w)
{
    if (!w->qt_ext) return;
    qt_extended_surface_raise(w->qt_ext);
    resume(w);
}

void wlshell_destroy(struct window *w)
{
    if (w->qt_ext) qt_extended_surface_destroy(w->qt_ext);
    w->qt_ext = NULL;
}

/* Lipstick turns its whole screen to a window's content orientation (wl_surface.set_buffer_transform: QtWayland's contentOrientation) and draws
 * the window's buffer turned back, so the buffer is in the panel's own (portrait) frame: the app's landscape content goes into it turned, as
 * on Lomiri (geometry.c want_turn, the display's ROTATION_90/270); the window stays the output's size. Lipstick tells the output's orientation
 * (wl_output transform 3 or 1: the sensor, or the user's lock). The window follows the output; a fixed activity (lock: 1 landscape, 2
 * portrait) turns it to its own shape: a landscape-locked app on a portrait phone is shown turned, not letterboxed. */
static int want_tf(const struct window *w)
{
    int tf = output_tf == 2 ? 0 : output_tf;  /* upside down: shown upright (no turn of 180) */
    if (w->lock == 1) return tf & 1 ? tf : 3;
    if (w->lock == 2) return 0;
    return tf;
}

void wlshell_orient(struct window *w)
{
    int tf = want_tf(w);
    if (tf == w->tf || !w->configured) return;
    w->tf = tf;
    wl_surface_set_buffer_transform(w->surface, tf);  /* applies with the commit of the next frame */
    fprintf(stderr, "[shell] %s: turned, content orientation transform %d\n", w->title, tf);
    window_layout(w);
    wl_surface_commit(w->surface);
    window_send_configure(w);
}

/* The configure an xdg compositor would send first: maximized to the output, then activated (Lipstick tells a client nothing of focus). */
void wlshell_first_configure(struct window *w)
{
    w->tf = want_tf(w);
    if (w->tf) wl_surface_set_buffer_transform(w->surface, w->tf);
    window_inject_maximize(w, 1, output_mode_w, output_mode_h);
    window_inject_configure(w, 0, 0, 1);
}
