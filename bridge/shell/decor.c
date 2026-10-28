#define _GNU_SOURCE
#include "decor.h"

#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>
#include <unistd.h>

static struct wl_compositor *compositor;
static struct wl_subcompositor *subcompositor;
static struct wl_shm *shm;

void decor_setup(struct wl_compositor *c, struct wl_subcompositor *sc, struct wl_shm *s)
{
    compositor = c;
    subcompositor = sc;
    shm = s;
}

void decor_create(struct decor *d, struct wl_surface *parent)
{
    memset(d, 0, sizeof *d);
    d->hover = DECOR_NONE;
    d->dark = decor_dark();
    if (!subcompositor) return;
    d->surface = wl_compositor_create_surface(compositor);
    d->sub = wl_subcompositor_get_subsurface(subcompositor, d->surface, parent);
    wl_subsurface_set_position(d->sub, 0, -DECOR_H);
    wl_subsurface_set_desync(d->sub); /* the bar redraws independently of app frames */
}

static int button_x(int lw, int which)
{
    switch (which) {
    case DECOR_BACK: return 0;
    case DECOR_MIN: return lw - 3 * DECOR_BTN_W;
    case DECOR_MAX: return lw - 2 * DECOR_BTN_W;
    default: return lw - DECOR_BTN_W; /* DECOR_CLOSE */
    }
}

void decor_set_title(struct decor *d, const char *title)
{
    const char *colon = title ? strrchr(title, 0x3a) : NULL; /* instance windows are "apklayer:<pid>:<instance>:<package>" */
    if (colon && !strncmp(title, "apklayer:", 9)) title = colon + 1;
    snprintf(d->title, sizeof d->title, "%s", title ? title : "");
    d->focused = -1; /* force a redraw on the next update */
}

void decor_update(struct decor *d, int logical_w, int scale, int maximized, int hover, int focused)
{
    if (!d->surface || logical_w <= 0) return;
    if (d->buffer && d->lw == logical_w && d->scale == scale && d->maximized == maximized
        && d->hover == hover && d->focused == focused)
        return;
    int s = scale > 0 ? scale : 100;
    int pw = logical_w * s / 100, ph = DECOR_H * s / 100;
    size_t len = (size_t)pw * ph * 4;
    if (!d->buffer || d->lw != logical_w || d->scale != scale) { /* new size: new pool + buffer */
        int fd = memfd_create("apklayer-decor", MFD_CLOEXEC);
        if (fd < 0 || ftruncate(fd, (off_t)len) < 0) { if (fd >= 0) close(fd); return; }
        void *map = mmap(NULL, len, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);
        if (map == MAP_FAILED) { close(fd); return; }
        struct wl_shm_pool *pool = wl_shm_create_pool(shm, fd, (int32_t)len);
        struct wl_buffer *buf = wl_shm_pool_create_buffer(pool, 0, pw, ph, pw * 4, WL_SHM_FORMAT_ARGB8888);
        wl_shm_pool_destroy(pool);
        close(fd);
        if (d->buffer) wl_buffer_destroy(d->buffer);
        if (d->map) munmap(d->map, d->map_len);
        d->buffer = buf;
        d->map = map;
        d->map_len = len;
        wl_surface_set_buffer_scale(d->surface, (s + 50) / 100);
    }
    d->lw = logical_w;
    d->scale = scale;
    d->maximized = maximized;
    d->hover = hover;
    d->focused = focused;
    decor_draw(d->map, pw, ph, logical_w, s, maximized, hover, focused, d->title, d->dark, button_x);
    wl_surface_attach(d->surface, d->buffer, 0, 0);
    wl_surface_damage_buffer(d->surface, 0, 0, pw, ph);
    wl_surface_commit(d->surface);
}

int decor_hit(const struct decor *d, int x, int y)
{
    if (!d->surface || y < 0 || y >= DECOR_H || x < 0 || x >= d->lw) return DECOR_NONE;
    for (int b = DECOR_BACK; b <= DECOR_CLOSE; b++) {
        int bx = button_x(d->lw, b);
        if (x >= bx && x < bx + DECOR_BTN_W) return b;
    }
    return DECOR_BAR;
}

void decor_destroy(struct decor *d)
{
    if (d->buffer) wl_buffer_destroy(d->buffer);
    if (d->map) munmap(d->map, d->map_len);
    if (d->sub) wl_subsurface_destroy(d->sub);
    if (d->surface) wl_surface_destroy(d->surface);
    memset(d, 0, sizeof *d);
}
