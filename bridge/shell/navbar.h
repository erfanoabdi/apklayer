/* The navigation bar of a mobile-style window (mobile.c): a subsurface over the bottom of the app's content, as Android's three-button
 * navigation bar: Back and Home (Recents: none, the compositor has its own switcher). The app is told its height as the navigation bar
 * inset (CONFIGURE), so its content stays above it. Coordinates are logical pixels of the window. */
#ifndef APKLAYER_NAVBAR_H
#define APKLAYER_NAVBAR_H

#include <stddef.h>
#include <wayland-client.h>

enum { NAV_NONE = -1, NAV_BAR = 0, NAV_BACK, NAV_HOME };

struct navbar {
    struct wl_surface *surface;
    struct wl_subsurface *sub;
    struct wp_viewport *viewport;
    struct wl_buffer *buffer;
    void *map;
    size_t map_len;
    int lw, lh, scale, dark, pressed;  /* what the current buffer shows */
    int y;                             /* position under the window's content surface */
    int shown;                         /* a buffer is attached */
};

struct wp_viewporter;
void navbar_setup(struct wl_compositor *c, struct wl_subcompositor *sc, struct wl_shm *shm, struct wp_viewporter *vp);
void navbar_create(struct navbar *n, struct wl_surface *parent);
/* Shows the bar lw x lh logical at y (the content's height less lh), drawn at scale (percent); redraws only what changed. The position
 * applies with the parent's next commit. */
void navbar_update(struct navbar *n, int y, int lw, int lh, int scale, int dark, int pressed);
void navbar_hide(struct navbar *n);
/* Which button is at x (logical, on the bar). */
int navbar_hit(const struct navbar *n, int x);
void navbar_destroy(struct navbar *n);

#endif
