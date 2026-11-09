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
}
static void ext_property(void *d, struct qt_extended_surface *e, const char *name, struct wl_array *value)
{ (void)d; (void)e; (void)name; (void)value; }
static void ext_close(void *d, struct qt_extended_surface *e)
{ (void)e; struct window *w = d; send_msg(W_TARGET(w), MSG_CLOSE, (int32_t[]){ W_ID(w) }, 1); }
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

/* The configure an xdg compositor would send first: maximized to the output, then activated (Lipstick tells a client nothing of focus). */
void wlshell_first_configure(struct window *w)
{
    window_inject_maximize(w, 1, output_mode_w, output_mode_h);
    window_inject_configure(w, 0, 0, 1);
}
