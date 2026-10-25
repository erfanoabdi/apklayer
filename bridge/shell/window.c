/* App windows: xdg toplevels, shm buffers (rebuffered in place on resize), geometry. */
#include "shell.h"

/* ---- windows ---- */

struct window *window_for_surface(struct wl_surface *s)
{
    for (int i = 0; i < MAX_WINDOWS; i++)
        if (windows[i].client && (windows[i].surface == s
                                  || (windows[i].decor.surface && windows[i].decor.surface == s)))
            return &windows[i];
    return NULL;
}

struct window *window_find(struct client *c, int id)
{
    for (int i = 0; i < MAX_WINDOWS; i++)
        if (windows[i].client == c && windows[i].id == id) return &windows[i];
    return NULL;
}

static void buffer_release(void *data, struct wl_buffer *b)
{
    struct window *w = data;
    for (int i = 0; i < NBUF; i++) {
        if (w->buffers[i] == b) {
            int32_t v[2] = { w->id, i };
            send_msg(w->client, MSG_RELEASE, v, 2);
        }
    }
}
static const struct wl_buffer_listener buffer_listener = { buffer_release };

static void show(struct window *w, int buf, const int32_t *d)
{
    wl_surface_attach(w->surface, w->buffers[buf], 0, 0);
    wl_surface_damage_buffer(w->surface, d[0], d[1], d[2], d[3]);
    wl_surface_commit(w->surface);
}

/* Window geometry = content + title bar; the bar follows the content width and scale. */
void window_layout(struct window *w)
{
    int lw = w->w * 100 / w->scale, lh = w->h * 100 / w->scale;
    decor_update(&w->decor, lw, w->scale, w->maximized,
                 w->bar_hover, w->activated);
    xdg_surface_set_window_geometry(w->xdg_surface, 0, -DECOR_H, lw, lh + DECOR_H);
}

static void xs_configure(void *data, struct xdg_surface *xs, uint32_t serial)
{
    struct window *w = data;
    xdg_surface_ack_configure(xs, serial);
    if (!w->configured) {
        w->configured = 1;
        /* Tell the app its size and the output scale (P2 HiDPI: density = 160*scale). */
        int32_t lw = w->cfg_w > 0 ? w->cfg_w : w->w * 100 / w->scale;
        int32_t lh = w->cfg_h > 0 ? w->cfg_h : w->h * 100 / w->scale;
        int32_t v[4] = { w->id, lw, lh, output_scale };
        w->told_scale = output_scale;
        send_msg(w->client, MSG_CONFIGURE, v, 4);
        if (w->pending_buf >= 0) show(w, w->pending_buf, w->pending_damage);
        else wl_surface_commit(w->surface);
    }
}
static const struct xdg_surface_listener xs_listener = { xs_configure };
static void top_configure(void *d, struct xdg_toplevel *t, int32_t cw, int32_t ch,
                          struct wl_array *st)
{
    (void)t;
    struct window *w = d;
    int maximized = 0, activated = 0;
    uint32_t *state;
    wl_array_for_each(state, st) {
        if (*state == XDG_TOPLEVEL_STATE_MAXIMIZED || *state == XDG_TOPLEVEL_STATE_FULLSCREEN)
            maximized = 1;
        if (*state == XDG_TOPLEVEL_STATE_ACTIVATED) activated = 1;
    }
    int changed = maximized != w->maximized || activated != w->activated;
    w->maximized = maximized;
    w->activated = activated;
    /* The configure size is the window geometry: content + title bar. 0x0 = "your preferred
     * size". Any other size is a resize request: tell the app the logical content size; it
     * relayouts and re-sends WINDOW_CREATE with the same id, and the shell swaps the buffers of
     * the existing toplevel (no re-map). */
    ch = ch > DECOR_H ? ch - DECOR_H : 0;
    w->cfg_w = cw > 0 ? cw : 0;
    w->cfg_h = ch > 0 ? ch : 0;
    if (w->configured && changed) window_layout(w);
    if (cw > 0 && ch > 0 && w->configured
        && (cw != w->w * 100 / w->scale || ch != w->h * 100 / w->scale)) {
        int32_t v[4] = { w->id, cw, ch, output_scale };
        w->told_scale = output_scale;
        send_msg(w->client, MSG_CONFIGURE, v, 4);
    }
}
static void top_close(void *data, struct xdg_toplevel *t)
{
    (void)t;
    struct window *w = data;
    int32_t v[1] = { w->id };
    send_msg(w->client, MSG_CLOSE, v, 1);
}
static const struct xdg_toplevel_listener top_listener = {
    .configure = top_configure, .close = top_close };


/* (Re)creates the buffers of w from the shared file. */
static int window_buffers(struct window *w, int width, int height, const char *path)
{
    int fd = open(path, O_RDWR);
    if (fd < 0) { fprintf(stderr, "[shell] open %s: %s\n", path, strerror(errno)); return -1; }
    size_t len = (size_t)width * height * 4 * NBUF;
    struct stat st;
    if (fstat(fd, &st) < 0 || (size_t)st.st_size < len) { close(fd); return -1; }
    for (int i = 0; i < NBUF; i++) {
        if (w->buffers[i]) wl_buffer_destroy(w->buffers[i]);
        w->buffers[i] = NULL;
    }
    struct wl_shm_pool *pool = wl_shm_create_pool(shm, fd, (int32_t)len);
    for (int i = 0; i < NBUF; i++) {
        w->buffers[i] = wl_shm_pool_create_buffer(pool, i * width * height * 4, width, height,
                                                  width * 4, WL_SHM_FORMAT_ARGB8888);
        wl_buffer_add_listener(w->buffers[i], &buffer_listener, w);
    }
    wl_shm_pool_destroy(pool);
    close(fd);
    w->w = width;
    w->h = height;
    w->pending_buf = -1;
    w->scale = w->told_scale ? w->told_scale : 100;
    wl_surface_set_buffer_scale(w->surface, (w->scale + 50) / 100);
    if (w->xdg_surface) window_layout(w);
    return 0;
}

void window_create(struct client *c, int id, int width, int height, const char *path,
                          const char *title)
{
    if (width <= 0 || height <= 0) return;
    struct window *w = window_find(c, id);
    if (w) {
        /* Resize / DPI change (P2): same id = rebuffer the existing toplevel in place. The next
         * FRAME attaches the new buffers; the old attached buffer stays valid until then. */
        if (window_buffers(w, width, height, path) == 0)
            fprintf(stderr, "[shell] %s: window %d rebuffered %dx%d @%d%%\n", c->pkg, id, width,
                    height, w->scale);
        return;
    }
    for (int i = 0; i < MAX_WINDOWS && !w; i++)
        if (!windows[i].client) w = &windows[i];
    if (!w) return;
    memset(w, 0, sizeof *w);
    w->client = c; w->id = id; w->scale = 100;
    w->surface = wl_compositor_create_surface(compositor);
    if (window_buffers(w, width, height, path) < 0) {
        wl_surface_destroy(w->surface);
        memset(w, 0, sizeof *w);
        return;
    }
    w->xdg_surface = xdg_wm_base_get_xdg_surface(wm_base, w->surface);
    xdg_surface_add_listener(w->xdg_surface, &xs_listener, w);
    decor_create(&w->decor, w->surface);
    w->bar_hover = DECOR_NONE;
    w->toplevel = xdg_surface_get_toplevel(w->xdg_surface);
    xdg_toplevel_add_listener(w->toplevel, &top_listener, w);
    xdg_toplevel_set_title(w->toplevel, title[0] ? title : c->pkg);
    xdg_toplevel_set_app_id(w->toplevel, c->pkg);
    xdg_toplevel_set_min_size(w->toplevel, 240, 240 + DECOR_H);
    window_layout(w); /* geometry needs the toplevel role */ /* desktop-resizable (P2) */
    wl_surface_commit(w->surface);
    fprintf(stderr, "[shell] %s: window %d %dx%d \"%s\"\n", c->pkg, id, width, height, title);
}

void window_destroy(struct window *w)
{
    if (pointer_focus == w) pointer_focus = NULL;
    if (keyboard_focus == w) keyboard_focus = NULL;
    for (int i = 0; i < NBUF; i++) if (w->buffers[i]) wl_buffer_destroy(w->buffers[i]);
    decor_destroy(&w->decor);
    xdg_toplevel_destroy(w->toplevel);
    xdg_surface_destroy(w->xdg_surface);
    wl_surface_destroy(w->surface);
    memset(w, 0, sizeof *w);
}

void window_frame(struct window *w, int buf, const int32_t *damage)
{
    if (buf < 0 || buf >= NBUF) return;
    if (!w->configured) {
        w->pending_buf = buf;
        memcpy(w->pending_damage, damage, sizeof w->pending_damage);
        return;
    }
    show(w, buf, damage);
}

