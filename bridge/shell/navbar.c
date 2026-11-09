/* The navigation bar of a mobile-style window (navbar.h): its subsurface, buffer and drawing (cairo), like the title bar (decor.c). Android's
 * three-button bar without Recents: Back (a triangle) left of the centre, Home (a ring) in the centre; light or dark like the instance. */
#define _GNU_SOURCE
#include "navbar.h"

#include <cairo.h>
#include <math.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <sys/mman.h>
#include <unistd.h>
#include "viewporter-client-protocol.h"

static struct wl_compositor *compositor;
static struct wl_subcompositor *subcompositor;
static struct wl_shm *shm;
static struct wp_viewporter *viewporter;

void navbar_setup(struct wl_compositor *c, struct wl_subcompositor *sc, struct wl_shm *s, struct wp_viewporter *vp)
{
    compositor = c;
    subcompositor = sc;
    shm = s;
    viewporter = vp;
}

void navbar_create(struct navbar *n, struct wl_surface *parent)
{
    memset(n, 0, sizeof *n);
    n->pressed = NAV_NONE;
    if (!subcompositor) return;
    n->surface = wl_compositor_create_surface(compositor);
    n->sub = wl_subcompositor_get_subsurface(subcompositor, n->surface, parent);
    wl_subsurface_set_desync(n->sub);  /* redrawn independently of the app's frames */
    if (viewporter) n->viewport = wp_viewporter_get_viewport(viewporter, n->surface);
}

void navbar_set_remote(struct navbar *n, void (*send)(void *ctx, int fd, int pw, int ph), void *ctx)
{
    if (n->surface || n->remote) return;
    n->remote = 1;
    n->send = send;
    n->ctx = ctx;
}

/* The centre of a button, in logical pixels: Home in the middle, Back a quarter of the width left of it. */
static double button_cx(int lw, int which) { return which == NAV_BACK ? lw / 4.0 : lw / 2.0; }

int navbar_hit(const struct navbar *n, int x)
{
    if ((!n->surface && !n->remote) || x < 0 || x >= n->lw) return NAV_NONE;
    if (x < n->lw * 3 / 8) return NAV_BACK;
    if (x < n->lw * 5 / 8) return NAV_HOME;
    return NAV_BAR;
}

static void draw(void *px, int pw, int ph, int lw, int lh, int dark, int pressed)
{
    cairo_surface_t *sf = cairo_image_surface_create_for_data(px, CAIRO_FORMAT_ARGB32, pw, ph, pw * 4);
    cairo_t *cr = cairo_create(sf);
    cairo_scale(cr, (double)pw / lw, (double)ph / lh);
    double bg = dark ? 0.0 : 0.957, fg = dark ? 1.0 : 0.2, r = lh * 0.19;  /* Android: black bar, white icons; light: #f4f4f4, grey icons */
    cairo_set_operator(cr, CAIRO_OPERATOR_SOURCE);
    cairo_set_source_rgb(cr, bg, bg, bg);
    cairo_paint(cr);
    cairo_set_operator(cr, CAIRO_OPERATOR_OVER);
    cairo_set_line_width(cr, lh / 24.0);
    cairo_set_line_join(cr, CAIRO_LINE_JOIN_ROUND);
    for (int b = NAV_BACK; b <= NAV_HOME; b++) {
        double cx = button_cx(lw, b), cy = lh / 2.0;
        if (b == pressed) {  /* the ripple of a held button */
            cairo_set_source_rgba(cr, fg, fg, fg, 0.18);
            cairo_arc(cr, cx, cy, lh * 0.42, 0, 2 * M_PI);
            cairo_fill(cr);
        }
        cairo_set_source_rgba(cr, fg, fg, fg, 0.9);
        if (b == NAV_BACK) {
            cairo_move_to(cr, cx - r * 0.85, cy);
            cairo_line_to(cr, cx + r * 0.75, cy - r);
            cairo_line_to(cr, cx + r * 0.75, cy + r);
            cairo_close_path(cr);
        } else {
            cairo_new_sub_path(cr);
            cairo_arc(cr, cx, cy, r * 0.9, 0, 2 * M_PI);
        }
        cairo_stroke(cr);
    }
    cairo_destroy(cr);
    cairo_surface_destroy(sf);
}

void navbar_update(struct navbar *n, int y, int lw, int lh, int scale, int dark, int pressed)
{
    if ((!n->surface && !n->remote) || lw <= 0 || lh <= 0) return;
    if (n->sub && (y != n->y || !n->shown)) { wl_subsurface_set_position(n->sub, 0, y); n->y = y; }
    if (n->shown && n->lw == lw && n->lh == lh && n->scale == scale && n->dark == dark && n->pressed == pressed) return;
    int s = scale > 0 ? scale : 100;
    if (!n->viewport && !n->remote) s = (s + 50) / 100 * 100;  /* without wp_viewporter: whole multiples only (wl_surface.set_buffer_scale) */
    int pw = (lw * s + 99) / 100, ph = (lh * s + 99) / 100;
    size_t len = (size_t)pw * ph * 4;
    if (!n->map || n->lw != lw || n->lh != lh || n->scale != scale) {
        int fd = memfd_create("apklayer-navbar", MFD_CLOEXEC);
        if (fd < 0 || ftruncate(fd, (off_t)len) < 0) { if (fd >= 0) close(fd); return; }
        void *map = mmap(NULL, len, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);
        if (map == MAP_FAILED) { close(fd); return; }
        if (n->map) munmap(n->map, n->map_len);
        n->map = map;
        n->map_len = len;
        if (n->remote) {  /* the memfd itself goes to the display owner */
            if (n->fd > 0) close(n->fd);
            n->fd = fd;
        } else {
            struct wl_shm_pool *pool = wl_shm_create_pool(shm, fd, (int32_t)len);
            struct wl_buffer *buf = wl_shm_pool_create_buffer(pool, 0, pw, ph, pw * 4, WL_SHM_FORMAT_XRGB8888);
            wl_shm_pool_destroy(pool);
            close(fd);
            if (n->buffer) wl_buffer_destroy(n->buffer);
            n->buffer = buf;
            if (n->viewport) wp_viewport_set_destination(n->viewport, lw, lh);
            else wl_surface_set_buffer_scale(n->surface, s / 100);
        }
    }
    n->lw = lw;
    n->lh = lh;
    n->scale = scale;
    n->dark = dark;
    n->pressed = pressed;
    draw(n->map, pw, ph, lw, lh, dark, pressed);
    n->shown = 1;
    if (n->remote) { n->send(n->ctx, n->fd, pw, ph); return; }
    wl_surface_attach(n->surface, n->buffer, 0, 0);
    wl_surface_damage_buffer(n->surface, 0, 0, pw, ph);
    wl_surface_commit(n->surface);
}

void navbar_hide(struct navbar *n)
{
    if ((!n->surface && !n->remote) || !n->shown) return;
    n->shown = 0;
    if (n->remote) { n->send(n->ctx, -1, 0, 0); return; }
    wl_surface_attach(n->surface, NULL, 0, 0);  /* unmapped: the content under it shows and takes the input */
    wl_surface_commit(n->surface);
}

void navbar_destroy(struct navbar *n)
{
    if (n->remote) {
        navbar_hide(n);  /* its layer goes */
        if (n->fd > 0) close(n->fd);
    }
    if (n->buffer) wl_buffer_destroy(n->buffer);
    if (n->map) munmap(n->map, n->map_len);
    if (n->viewport) wp_viewport_destroy(n->viewport);
    if (n->sub) wl_subsurface_destroy(n->sub);
    if (n->surface) wl_surface_destroy(n->surface);
    memset(n, 0, sizeof *n);
    n->pressed = NAV_NONE;
}
