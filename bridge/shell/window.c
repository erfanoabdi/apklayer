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
        if (windows[i].client && (windows[i].surface == s || (windows[i].decor.surface && windows[i].decor.surface == s)
                                  || (windows[i].nav.surface && windows[i].nav.surface == s)))
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
    int width = w->cfg_w * window_scale(w) / 100, height = w->cfg_h * window_scale(w) / 100;
    w->initial_buffer = splash_buffer(w, width, height);  /* the logo on its colour until the first frame */
    if (!w->initial_buffer) return;
    wl_buffer_add_listener(w->initial_buffer, &initial_listener, w);
    wl_surface_attach(w->surface, w->initial_buffer, 0, 0);
    surface_damage(w->surface, 0, 0, width, height);
    wl_surface_commit(w->surface);
}

static void show(struct window *w, int buf, const int32_t *d)
{
    w->framed = 1;
    wl_surface_attach(w->surface, w->buffers[buf], 0, 0);
    surface_damage(w->surface, d[0], d[1], d[2], d[3]);
    wl_surface_commit(w->surface);
}

void window_show_first(struct window *w)
{
    if (w->pending_buf >= 0) show(w, w->pending_buf, w->pending_damage);
    else if (w->pending_dbuf >= 0) window_dmabuf_show(w, w->pending_dbuf, w->pending_ddamage);
    else if (!w->framed) show_initial(w);
    else wl_surface_commit(w->surface);
}

/* Tells the app whether its window is active / minimized (Android lifecycle: pause, stop, resume). */
void send_state(struct window *w)
{
    int32_t v[2] = { W_ID(w), (w->activated ? 1 : 0) | (w->minimized ? 2 : 0) };
    send_msg(W_TARGET(w), MSG_STATE, v, 2);
    vsync_source_changed();
}

/* (Re)creates the buffers of w from the shared file. */
static int window_buffers(struct window *w, int width, int height, const char *path)
{
    if (!path[0]) { /* a dmabuf-only window: its buffers arrive with DMABUF_FRAME */
        w->w = width;
        w->h = height;
        w->pending_buf = -1;
        w->pending_dbuf = -1;
        if (w->configured) window_layout(w);
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
    if (w->configured) window_layout(w);
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
    w->bar_hover = DECOR_NONE;
    w->nav.pressed = NAV_NONE;
    w->dark = decor_dark();
    const char *package = appinfo_package(title[0] ? title : c->pkg);
    char label[256];
    appinfo_label(package, label, sizeof label);   /* apklayer patch: the window carries the app's real name, and the app id is its package (the desktop entry's StartupWMClass) */
    snprintf(w->label, sizeof w->label, "%s", label);
    if (wm_base) {
        w->xdg_surface = xdg_wm_base_get_xdg_surface(wm_base, w->surface);
        w->toplevel = xdg_surface_get_toplevel(w->xdg_surface);
        xdg_toplevel_set_title(w->toplevel, label);
        xdg_toplevel_set_app_id(w->toplevel, package);
    } else wlshell_create(w, label, package);
    window_geometry_init(w);
    snprintf(w->title, sizeof w->title, "%s", title);
    window_set_mode(w, c->window_mode ? c->window_mode - 1 : WM_AUTO);  /* mobile.c: title bar or navigation bar, min size, maximized */
    if (w->toplevel) wl_surface_commit(w->surface);  /* no buffer: asks for the first configure, whose size and bounds decide the window's (geometry.c) */
    else wlshell_first_configure(w);
    fprintf(stderr, "[shell] %s: window %d %dx%d \"%s\", %s style\n", c->pkg, id, width, height, title, w->mobile ? "mobile" : "desktop");
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
        if (c->dark) window_set_dark(w, c->dark == 2);
        if (c->window_mode && w->mode != c->window_mode - 1) window_set_mode(w, c->window_mode - 1);
        if (w->configured) {  // its size, scale and state, now that someone listens
            window_send_configure(w);
            send_state(w);
        }
        return;
    }
}

/* The title bar follows the colour scheme of the app's instance (MSG_DECOR_DARK), redrawn at once. */
void window_set_dark(struct window *w, int dark)
{
    if (w->dark == dark) return;
    w->dark = w->decor.dark = dark;
    w->decor.focused = -1;  /* force the redraw */
    fprintf(stderr, "[shell] %s: %s %s\n", w->title, w->mobile ? "navigation bar" : "title bar", dark ? "dark" : "light");
    if (w->decor.lw > 0) decor_update(&w->decor, w->decor.lw, window_scale(w), w->maximized, w->bar_hover, w->activated);
    if (w->mobile && w->configured) { window_layout(w); wl_surface_commit(w->surface); }
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
    navbar_destroy(&w->nav);
    window_geometry_destroy(w);
    if (w->toplevel) xdg_toplevel_destroy(w->toplevel);
    if (w->xdg_surface) xdg_surface_destroy(w->xdg_surface);
    if (w->shell_surface) wl_shell_surface_destroy(w->shell_surface);
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


/* Android's Back: KEYCODE_BACK down and up to the app (the title bar's and the navigation bar's Back). */
void window_send_back(struct window *w)
{
    int32_t down[5] = { W_ID(w), 0, 4 /* KEYCODE_BACK */, 0, 0 };
    int32_t up[5] = { W_ID(w), 1, 4, 0, 0 };
    send_msg(W_TARGET(w), MSG_KEY, down, 5);
    send_msg(W_TARGET(w), MSG_KEY, up, 5);
}

/* Minimize (the title bar's button, the navigation bar's Home): the app stops before the window disappears. */
void window_minimize(struct window *w)
{
    w->minimized = 1;
    send_state(w);
    if (w->toplevel) xdg_toplevel_set_minimized(w->toplevel);
}
