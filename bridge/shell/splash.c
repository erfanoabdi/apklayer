/* Splash screen of a window that has not presented its first frame yet: the app's logo (icon.png of the installed app) centered on a
 * background colour derived from the logo (its saturated average, darkened). Only the icon decides the colours (no theme). */
#define _GNU_SOURCE
#include "shell.h"
#include "splash.h"
#include <cairo.h>
#include <math.h>
#include <sys/mman.h>

/* Average colour of the icon weighted by opacity and saturation, then softened: value 0.58, saturation 0.35..0.7. Greys give a neutral dark. */
static void pick_color(cairo_surface_t *icon, double *r, double *g, double *b)
{
    int w = cairo_image_surface_get_width(icon), h = cairo_image_surface_get_height(icon), st = cairo_image_surface_get_stride(icon);
    unsigned char *d = cairo_image_surface_get_data(icon);
    double sr = 0, sg = 0, sb = 0, sw = 0;
    for (int y = 0; y < h; y++)
        for (int x = 0; x < w; x++) {
            uint32_t p = *(uint32_t *)(d + y * st + x * 4);
            double a = (p >> 24) / 255.0;
            if (a < 0.1) continue;
            double pr = ((p >> 16) & 255) / 255.0 / a, pg = ((p >> 8) & 255) / 255.0 / a, pb = (p & 255) / 255.0 / a;
            double mx = fmax(pr, fmax(pg, pb)), mn = fmin(pr, fmin(pg, pb)), sat = mx > 0 ? (mx - mn) / mx : 0;
            double wt = a * sat * sat * (0.3 + mx);  /* strongly colourful, not-too-dark pixels decide */
            sr += pr * wt; sg += pg * wt; sb += pb * wt; sw += wt;
        }
    if (sw < 1e-3) { *r = *g = *b = 0.17; return; }
    double cr = fmin(sr / sw, 1), cg = fmin(sg / sw, 1), cb = fmin(sb / sw, 1);
    double mx = fmax(cr, fmax(cg, cb)), mn = fmin(cr, fmin(cg, cb)), sat = mx > 0 ? (mx - mn) / mx : 0;
    double sat2 = fmin(fmax(sat, 0.35), 0.7), v = 0.58, lo = v * (1 - sat2);
    double span = mx - mn;
    if (span < 1e-6) { *r = *g = *b = v; return; }
    *r = lo + (cr - mn) / span * (v - lo);
    *g = lo + (cg - mn) / span * (v - lo);
    *b = lo + (cb - mn) / span * (v - lo);
}

/* The package of a window: the last field of an "apklayer:<pid>:<instance>:<package>" title, else the client's package. */
static const char *title_pkg(const struct window *w)
{
    const char *p = w->title;
    for (int i = 0; i < 3; i++) { p = strchr(p, ':'); if (!p) return w->client->pkg; p++; }
    return *p ? p : w->client->pkg;
}

struct wl_buffer *splash_buffer(const struct window *w, int width, int height)
{
    char path[512];
    const char *xdg = getenv("XDG_DATA_HOME"), *home = getenv("HOME");
    if (xdg && *xdg) snprintf(path, sizeof path, "%s/apklayer/apps/%s/icon.png", xdg, title_pkg(w));
    else snprintf(path, sizeof path, "%s/.local/share/apklayer/apps/%s/icon.png", home ? home : "", title_pkg(w));
    int fd = memfd_create("apklayer-splash", MFD_CLOEXEC);
    size_t size = (size_t)width * height * 4;
    if (fd < 0 || ftruncate(fd, size) < 0) { if (fd >= 0) close(fd); return NULL; }
    void *map = mmap(NULL, size, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);
    if (map == MAP_FAILED) { close(fd); return NULL; }
    cairo_surface_t *dst = cairo_image_surface_create_for_data(map, CAIRO_FORMAT_RGB24, width, height, width * 4);
    cairo_t *cr = cairo_create(dst);
    cairo_surface_t *icon = cairo_image_surface_create_from_png(path);
    double r = 0.17, g = 0.17, b = 0.17;
    int have = cairo_surface_status(icon) == CAIRO_STATUS_SUCCESS;
    if (have) pick_color(icon, &r, &g, &b);
    cairo_pattern_t *bg = cairo_pattern_create_radial(width / 2.0, height / 2.0, 0, width / 2.0, height / 2.0, hypot(width, height) / 2);
    cairo_pattern_add_color_stop_rgb(bg, 0, fmin(r * 1.18, 1), fmin(g * 1.18, 1), fmin(b * 1.18, 1));  /* a soft glow behind the logo */
    cairo_pattern_add_color_stop_rgb(bg, 1, r * 0.82, g * 0.82, b * 0.82);
    cairo_set_source(cr, bg);
    cairo_paint(cr);
    cairo_pattern_destroy(bg);
    if (have) {
        double sz = fmin(width, height) * 0.3, iw = cairo_image_surface_get_width(icon), ih = cairo_image_surface_get_height(icon);
        if (sz > 192.0 * w->scale / 100) sz = 192.0 * w->scale / 100;
        cairo_translate(cr, (width - sz) / 2, (height - sz) / 2);
        cairo_scale(cr, sz / iw, sz / ih);
        cairo_set_source_surface(cr, icon, 0, 0);
        cairo_pattern_set_filter(cairo_get_source(cr), CAIRO_FILTER_BEST);
        cairo_paint(cr);
        fprintf(stderr, "[shell] splash %s: background #%02x%02x%02x\n", title_pkg(w), (int)(r * 255), (int)(g * 255), (int)(b * 255));
    }
    cairo_surface_destroy(icon);
    cairo_destroy(cr);
    cairo_surface_destroy(dst);
    munmap(map, size);
    struct wl_shm_pool *pool = wl_shm_create_pool(shm, fd, (int32_t)size);
    struct wl_buffer *buf = wl_shm_pool_create_buffer(pool, 0, width, height, width * 4, WL_SHM_FORMAT_XRGB8888);
    wl_shm_pool_destroy(pool);
    close(fd);
    return buf;
}
