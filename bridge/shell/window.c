/* App windows: xdg toplevels, shm buffers (rebuffered in place on resize), geometry. */
#define _GNU_SOURCE
#include "appinfo.h"
#include "shell.h"
#include <sys/mman.h>
#include "splash.h"

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

struct window *window_find_input(struct client *c, int id)
{
    struct window *own = window_find(c, id);
    if (own) return own;
    for (int i = 0; i < MAX_WINDOWS; i++)
        if (windows[i].proxy == c && windows[i].proxy_id == id) return &windows[i];
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

/* Map display windows before their producer's first frame, so the compositor
 * can focus them and deliver the frame callbacks that start Android's vsync. */
static void initial_release(void *data, struct wl_buffer *buffer)
{
    struct window *w = data;
    wl_buffer_destroy(buffer);
    w->initial_buffer = NULL;
}
static const struct wl_buffer_listener initial_listener = { initial_release };

static void show_initial(struct window *w)
{
    int width = w->cfg_w > 0 ? w->cfg_w * w->scale / 100 : w->w;
    int height = w->cfg_h > 0 ? w->cfg_h * w->scale / 100 : w->h;
    w->initial_buffer = splash_buffer(w, width, height);  /* the logo on its colour until the first frame */
    if (!w->initial_buffer) return;
    wl_buffer_add_listener(w->initial_buffer, &initial_listener, w);
    wl_surface_attach(w->surface, w->initial_buffer, 0, 0);
    wl_surface_damage_buffer(w->surface, 0, 0, width, height);
    wl_surface_commit(w->surface);
}

static void show(struct window *w, int buf, const int32_t *d)
{
    w->framed = 1;
    wl_surface_attach(w->surface, w->buffers[buf], 0, 0);
    wl_surface_damage_buffer(w->surface, d[0], d[1], d[2], d[3]);
    wl_surface_commit(w->surface);
}

/* Tells the app whether its window is active / minimized (Android lifecycle: pause, stop, resume). */
void send_state(struct window *w)
{
    int32_t v[2] = { W_ID(w), (w->activated ? 1 : 0) | (w->minimized ? 2 : 0) };
    send_msg(W_TARGET(w), MSG_STATE, v, 2);
    vsync_source_changed();
}

/* Window geometry = content + title bar; the bar follows the content width and scale. */
/* The size (logical) a window of w x h opens at: at most 85% of the output (less top bar and title bar), same aspect. */
static void fit_size(int w, int h, int *fw, int *fh)
{
    int maxh = (output_mode_h * 100 / output_scale - 32 - DECOR_H) * 85 / 100, maxw = output_mode_w * 100 / output_scale * 85 / 100;
    *fw = w; *fh = h;
    if (maxh > 0 && *fh > maxh) { *fw = *fw * maxh / *fh; *fh = maxh; }
    if (maxw > 0 && *fw > maxw) { *fh = *fh * maxw / *fw; *fw = maxw; }
}

void window_layout(struct window *w)
{
    int lw = w->w * 100 / w->scale, lh = w->h * 100 / w->scale;
    if (w->fit_sent && w->cfg_w > 0 && w->cfg_h > 0) {  /* apklayer patch: the window is never bigger than what the app was configured to (its buffers may still be the display's size) */
        if (w->cfg_w < lw) lw = w->cfg_w;
        if (w->cfg_h < lh) lh = w->cfg_h;
    }
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
        if (output_mode_h > 0) {  /* apklayer patch: the first size is the fitted one, not the compositor's clamp of the display's size */
            w->fit_sent = 1;
            fit_size(w->w * 100 / w->scale, w->h * 100 / w->scale, &w->cfg_w, &w->cfg_h);
        }
        fprintf(stderr, "[shell] first configure: window %dx%d, compositor %dx%d, output %dx%d @%d%%\n", w->w, w->h, w->cfg_w, w->cfg_h, output_mode_w, output_mode_h, output_scale);
        /* Tell the app its size and the output scale (P2 HiDPI: density = 160*scale). */
        int32_t lw = w->cfg_w > 0 ? w->cfg_w : w->w * 100 / w->scale;
        int32_t lh = w->cfg_h > 0 ? w->cfg_h : w->h * 100 / w->scale;
        int32_t v[4] = { W_ID(w), lw, lh, output_scale };
        w->told_scale = output_scale;
        send_msg(W_TARGET(w), MSG_CONFIGURE, v, 4);
        if (w->proxy) send_msg(w->client, MSG_CONFIGURE, (int32_t[]){ w->id, lw, lh, output_scale }, 4);
        if (w->pending_buf >= 0) show(w, w->pending_buf, w->pending_damage);
        else if (w->pending_dbuf >= 0) window_dmabuf_show(w, w->pending_dbuf, w->pending_ddamage);
        else if (!w->framed) show_initial(w);
        else wl_surface_commit(w->surface);
        vsync_source_changed();
    }
}
static const struct xdg_surface_listener xs_listener = { xs_configure };
static void top_configure(void *d, struct xdg_toplevel *t, int32_t cw, int32_t ch,
                          struct wl_array *st)
{
    struct window *w = d;
    int maximized = 0, activated = 0;
    uint32_t *state;
    wl_array_for_each(state, st) {
        if (*state == XDG_TOPLEVEL_STATE_MAXIMIZED || *state == XDG_TOPLEVEL_STATE_FULLSCREEN)
            maximized = 1;
        if (*state == XDG_TOPLEVEL_STATE_ACTIVATED) activated = 1;
    }
    if (maximized && !w->unmaximized_once) {  // windows open in their own size: only the first (automatic) maximize is undone, the user's own later ones stay
        w->unmaximized_once = 1;
        xdg_toplevel_unset_maximized(t);
        maximized = 0;
    }
    int changed = maximized != w->maximized || activated != w->activated;
    int active_changed = activated != w->activated;
    w->maximized = maximized;
    w->activated = activated;
    /* The configure size is the window geometry: content + title bar. 0x0 = "your preferred
     * size". Any other size is a resize request: tell the app the logical content size; it
     * relayouts and re-sends WINDOW_CREATE with the same id, and the shell swaps the buffers of
     * the existing toplevel (no re-map). */
    ch = ch > DECOR_H ? ch - DECOR_H : 0;
    w->cfg_w = cw > 0 ? cw : 0;
    w->cfg_h = ch > 0 ? ch : 0;
    if (activated) w->minimized = 0;
    if (w->configured && changed) window_layout(w);
    if (w->configured && active_changed) send_state(w);
    if (cw > 0 && ch > 0 && w->configured
        && (cw != w->w * 100 / w->scale || ch != w->h * 100 / w->scale)) {
        int32_t v[4] = { W_ID(w), cw, ch, output_scale };
        w->told_scale = output_scale;
        send_msg(W_TARGET(w), MSG_CONFIGURE, v, 4);
        if (w->proxy) send_msg(w->client, MSG_CONFIGURE, (int32_t[]){ w->id, cw, ch, output_scale }, 4);  /* the display's owner follows the window as well */
    }
}
static void top_close(void *data, struct xdg_toplevel *t)
{
    (void)t;
    struct window *w = data;
    int32_t v[1] = { W_ID(w) };
    send_msg(W_TARGET(w), MSG_CLOSE, v, 1);
}
static const struct xdg_toplevel_listener top_listener = {
    .configure = top_configure, .close = top_close };


/* (Re)creates the buffers of w from the shared file. */
static int window_buffers(struct window *w, int width, int height, const char *path)
{
    if (!path[0]) { /* a dmabuf-only window: its buffers arrive with DMABUF_FRAME */
        w->w = width;
        w->h = height;
        w->pending_buf = -1;
    w->pending_dbuf = -1;
        w->scale = w->told_scale ? w->told_scale : 100;
        wl_surface_set_buffer_scale(w->surface, (w->scale + 50) / 100);
        if (w->xdg_surface) window_layout(w);
        return 0;
    }
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
    w->pending_dbuf = -1;
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
    const char *package = appinfo_package(title[0] ? title : c->pkg);
    char label[256];
    appinfo_label(package, label, sizeof label);   /* apklayer patch: the window carries the app's real name, and the app id is its package (the desktop entry's StartupWMClass) */
    decor_set_title(&w->decor, label);
    w->toplevel = xdg_surface_get_toplevel(w->xdg_surface);
    xdg_toplevel_add_listener(w->toplevel, &top_listener, w);
    snprintf(w->title, sizeof w->title, "%s", title);
    xdg_toplevel_set_title(w->toplevel, label);
    xdg_toplevel_set_app_id(w->toplevel, package);
    xdg_toplevel_set_min_size(w->toplevel, 240, 240 + DECOR_H);
    if (output_mode_h > 0) {   /* apklayer patch: open at a size that fits the screen, so the compositor has nothing to clamp (a clamp is a configure that disagrees with the app) */
        fit_size(width * 100 / w->scale, height * 100 / w->scale, &w->cfg_w, &w->cfg_h);
        w->fit_sent = 1;
    }
    window_layout(w); /* geometry needs the toplevel role */ /* desktop-resizable (P2) */
    wl_surface_commit(w->surface);
    fprintf(stderr, "[shell] %s: window %d %dx%d \"%s\"\n", c->pkg, id, width, height, title);
    for (int i = 0; i < MAX_CLIENTS; i++)   /* an app that asked for a window of this title before it existed */
        if (clients[i].fd >= 0 && clients[i].attach_title[0] && !strcmp(clients[i].attach_title, w->title))
            window_attach_input(&clients[i], clients[i].attach_id, w->title);
}

/* An app takes over the input, state and size messages of the window titled `title` (the display HAL's window: the display belongs to
 * the instance's SurfaceFlinger, the app is what its touches and keys are for). If there is no such window yet it is remembered. */
void window_attach_input(struct client *c, int own_id, const char *title)
{
    snprintf(c->attach_title, sizeof c->attach_title, "%s", title);
    c->attach_id = own_id;
    for (int i = 0; i < MAX_WINDOWS; i++) {
        struct window *w = &windows[i];
        if (!w->client || w->client == c || strcmp(w->title, title) != 0) continue;
        w->proxy = c;
        w->proxy_id = own_id;
        clipboard_push(w);
        fprintf(stderr, "[shell] %s takes the input of window \"%s\" (as its window %d)\n", c->pkg, title, own_id);
        if (w->configured) {  // its size, scale and state, now that someone listens
            int32_t v[4] = { own_id, w->cfg_w > 0 ? w->cfg_w : w->w * 100 / w->scale, w->cfg_h > 0 ? w->cfg_h : w->h * 100 / w->scale, output_scale };  /* apklayer patch: the fitted size, not the display's */
            w->told_scale = output_scale;
            send_msg(c, MSG_CONFIGURE, v, 4);
            send_state(w);
            send_msg(w->client, MSG_CONFIGURE, (int32_t[]){ w->id, v[1], v[2], output_scale }, 4);
        }
        return;
    }
}

/* apklayer patch: the app asks for another shape (portrait <-> landscape): fitted to the screen, sent to the app and to the display's owner. */
void window_request_size(struct window *w, int lw, int lh)
{
    int fw, fh;
    if (!w->configured) return;
    fit_size(lw, lh, &fw, &fh);
    if (fw == w->w * 100 / w->scale && fh == w->h * 100 / w->scale) return;
    w->cfg_w = fw; w->cfg_h = fh;
    int32_t v[4] = { W_ID(w), fw, fh, output_scale };
    send_msg(W_TARGET(w), MSG_CONFIGURE, v, 4);
    if (w->proxy) send_msg(w->client, MSG_CONFIGURE, (int32_t[]){ w->id, fw, fh, output_scale }, 4);
}

void window_drop_proxy(struct client *c)
{
    for (int i = 0; i < MAX_WINDOWS; i++)
        if (windows[i].proxy == c) {
            text_input_forget_window(&windows[i]);
            windows[i].proxy = NULL; windows[i].proxy_id = 0;
        }
}

void window_destroy(struct window *w)
{
    pointer_forget_window(w);
    text_input_forget_window(w);
    if (keyboard_focus == w) keyboard_focus = NULL;
    for (int i = 0; i < NBUF; i++) if (w->buffers[i]) wl_buffer_destroy(w->buffers[i]);
    if (w->initial_buffer) wl_buffer_destroy(w->initial_buffer);
    window_dmabuf_destroy(w);
    decor_destroy(&w->decor);
    xdg_toplevel_destroy(w->toplevel);
    xdg_surface_destroy(w->xdg_surface);
    wl_surface_destroy(w->surface);
    memset(w, 0, sizeof *w);
    vsync_source_changed();
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

