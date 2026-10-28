/* Client-side window decoration for apklayer-shell: a title bar in a subsurface above the app's
 * content (GNOME has no server-side decorations). Buttons: Back (Android Back), Minimize,
 * Maximize/Restore, Close. All coordinates are logical pixels; the bar is DECOR_H high and sits at
 * y = -DECOR_H relative to the content surface. */
#ifndef APKLAYER_DECOR_H
#define APKLAYER_DECOR_H

#include <stddef.h>
#include <wayland-client.h>

#define DECOR_H 46  /* Adwaita header bar height */
#define DECOR_BTN_W 36

enum { DECOR_NONE = -1, DECOR_BAR = 0, DECOR_BACK, DECOR_MIN, DECOR_MAX, DECOR_CLOSE };

struct decor {
    struct wl_surface *surface;
    struct wl_subsurface *sub;
    struct wl_buffer *buffer;
    void *map;
    size_t map_len;
    int lw, scale, maximized, hover, focused; /* what the current buffer shows */
    int dark;        /* desktop colour scheme at creation */
    char title[64];
};

void decor_setup(struct wl_compositor *c, struct wl_subcompositor *sc, struct wl_shm *shm);
void decor_create(struct decor *d, struct wl_surface *parent);
/* Redraws and commits the bar if anything it shows changed. scale is in percent. */
void decor_update(struct decor *d, int logical_w, int scale, int maximized, int hover, int focused);
/* Which part of the bar is at (x, y) on the decor surface: a button, DECOR_BAR, or DECOR_NONE. */
int decor_hit(const struct decor *d, int x, int y);
void decor_set_title(struct decor *d, const char *title);
/* decor_draw.c: Adwaita-style drawing; decor_dark() = the desktop prefers the dark scheme */
int decor_dark(void);
void decor_draw(void *px, int pw, int ph, int lw, int s, int maximized, int hover, int focused,
                const char *title, int dark, int (*button_x)(int, int));
void decor_destroy(struct decor *d);

#endif
