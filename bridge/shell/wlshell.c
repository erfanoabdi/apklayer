/* Windows on compositors without xdg-shell: Sailfish OS's Lipstick (QtWayland 5.6) offers only wl_shell (docs/HALIUM.md). Lipstick shows
 * every application full screen and sends no configure of its own: the window is laid out maximized at the output's size and active. */
#include "shell.h"

struct wl_shell *wl_shell_global;

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
}

/* The configure an xdg compositor would send first: maximized to the output, then activated (Lipstick tells a client nothing of focus). */
void wlshell_first_configure(struct window *w)
{
    window_inject_maximize(w, 1, output_mode_w, output_mode_h);
    window_inject_configure(w, 0, 0, 1);
}
