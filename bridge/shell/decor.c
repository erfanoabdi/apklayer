#define _GNU_SOURCE
#include "decor.h"

#include <stdint.h>
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
    if (!subcompositor) return;
    d->surface = wl_compositor_create_surface(compositor);
    d->sub = wl_subcompositor_get_subsurface(subcompositor, d->surface, parent);
    wl_subsurface_set_position(d->sub, 0, -DECOR_H);
    wl_subsurface_set_desync(d->sub); /* the bar redraws independently of app frames */
}

/* ---- software drawing into an ARGB8888 buffer (physical pixels) ---- */

struct canvas {
    uint32_t *px;
    int w, h;
};

static void rect(struct canvas *c, int x, int y, int w, int h, uint32_t color)
{
    for (int j = y < 0 ? 0 : y; j < y + h && j < c->h; j++)
        for (int i = x < 0 ? 0 : x; i < x + w && i < c->w; i++) c->px[j * c->w + i] = color;
}

/* Line with a square brush of t pixels (Bresenham). */
static void line(struct canvas *c, int x0, int y0, int x1, int y1, int t, uint32_t color)
{
    int dx = abs(x1 - x0), dy = -abs(y1 - y0);
    int sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1, err = dx + dy;
    for (;;) {
        rect(c, x0 - t / 2, y0 - t / 2, t, t, color);
        if (x0 == x1 && y0 == y1) break;
        int e2 = 2 * err;
        if (e2 >= dy) { err += dy; x0 += sx; }
        if (e2 <= dx) { err += dx; y0 += sy; }
    }
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

static void draw(struct canvas *c, int lw, int s, int maximized, int hover, int focused)
{
    const uint32_t bg = focused ? 0xFF2B2B2B : 0xFF3A3A3A, fg = focused ? 0xFFEDEDED : 0xFF9A9A9A;
    rect(c, 0, 0, c->w, c->h, bg);
    int u = s / 100 > 0 ? s / 100 : 1;          /* one logical pixel in buffer pixels */
    rect(c, 0, c->h - u, c->w, u, 0xFF1A1A1A);  /* separator to the content */
    int t = 2 * u;                               /* stroke: 2 logical px */
    for (int b = DECOR_BACK; b <= DECOR_CLOSE; b++) {
        int bx = button_x(lw, b) * s / 100, bw = DECOR_BTN_W * s / 100;
        if (b == hover) rect(c, bx, 0, bw, c->h - u, b == DECOR_CLOSE ? 0xFFC42B1C : 0xFF4A4A4A);
        int cx = bx + bw / 2, cy = (c->h - u) / 2;
        switch (b) {
        case DECOR_BACK: /* chevron */
            line(c, cx + 4 * u, cy - 8 * u, cx - 4 * u, cy, t, fg);
            line(c, cx - 4 * u, cy, cx + 4 * u, cy + 8 * u, t, fg);
            break;
        case DECOR_MIN:
            rect(c, cx - 6 * u, cy + 4 * u, 12 * u, t, fg);
            break;
        case DECOR_MAX: {
            int o = maximized ? 2 * u : 0, n = 11 * u;
            rect(c, cx - 5 * u + o, cy - 6 * u - o, n, t, fg);
            rect(c, cx - 5 * u + o, cy - 6 * u - o + n - t, n, t, fg);
            rect(c, cx - 5 * u + o, cy - 6 * u - o, t, n, fg);
            rect(c, cx - 5 * u + o + n - t, cy - 6 * u - o, t, n, fg);
            if (maximized) { /* second square behind */
                rect(c, cx - 5 * u, cy - 6 * u, t, n - 2 * u, fg);
                rect(c, cx - 5 * u, cy + 4 * u, n - 2 * u, t, fg);
            }
            break;
        }
        case DECOR_CLOSE:
            line(c, cx - 5 * u, cy - 5 * u, cx + 5 * u, cy + 5 * u, t, fg);
            line(c, cx - 5 * u, cy + 5 * u, cx + 5 * u, cy - 5 * u, t, fg);
            break;
        }
    }
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
    struct canvas c = { d->map, pw, ph };
    draw(&c, logical_w, s, maximized, hover, focused);
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
