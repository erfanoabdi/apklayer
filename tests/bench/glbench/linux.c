/* glbench on native Linux: an xdg_toplevel with a wl_egl_window (window: eglSwapInterval 1; window-novsync: 0), or offscreen on the GBM
 * platform of a render node with a surfaceless context, the same host EGL path apklayer's Android libEGL takes.
 * Usage: glbench window|window-novsync|offscreen [WxH] [scenes] [seconds]   (build and run: tests/bench/glbench/README) */
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <fcntl.h>
#include <gbm.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <wayland-client.h>
#include <wayland-egl.h>

#include "bench.h"
#include "xdg-shell-client-protocol.h"

static struct wl_compositor *compositor;
static struct xdg_wm_base *wm;
static int configured;

static void print(const char *line) { printf("%s\n", line); fflush(stdout); }

static void global(void *d, struct wl_registry *r, uint32_t name, const char *iface, uint32_t v)
{
    if (!strcmp(iface, "wl_compositor")) compositor = wl_registry_bind(r, name, &wl_compositor_interface, 4);
    if (!strcmp(iface, "xdg_wm_base")) wm = wl_registry_bind(r, name, &xdg_wm_base_interface, 1);
}
static void global_remove(void *d, struct wl_registry *r, uint32_t name) {}
static const struct wl_registry_listener registry_listener = { global, global_remove };
static void ping(void *d, struct xdg_wm_base *b, uint32_t serial) { xdg_wm_base_pong(b, serial); }
static const struct xdg_wm_base_listener wm_listener = { ping };
static void surface_configure(void *d, struct xdg_surface *s, uint32_t serial) { xdg_surface_ack_configure(s, serial); configured = 1; }
static const struct xdg_surface_listener xdg_surface_listener = { surface_configure };
static void top_configure(void *d, struct xdg_toplevel *t, int32_t w, int32_t h, struct wl_array *states) {}
static void top_close(void *d, struct xdg_toplevel *t) {}
static const struct xdg_toplevel_listener top_listener = { top_configure, top_close };

static EGLConfig choose(EGLDisplay dpy, EGLint surface_type)
{
    const EGLint attr[] = { EGL_SURFACE_TYPE, surface_type, EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT, EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8,
                            EGL_BLUE_SIZE, 8, EGL_ALPHA_SIZE, 0, EGL_NONE };
    EGLConfig cfg = NULL;
    EGLint n = 0;
    eglChooseConfig(dpy, attr, &cfg, 1, &n);
    return n ? cfg : NULL;
}

int main(int argc, char **argv)
{
    const char *mode = argc > 1 ? argv[1] : "window";
    int w = 800, h = 600;
    if (argc > 2) sscanf(argv[2], "%dx%d", &w, &h);
    const char *scenes = argc > 3 ? argv[3] : NULL;
    double seconds = argc > 4 ? atof(argv[4]) : 5;
    const EGLint ctx_attr[] = { EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE };
    struct bench_target t = { "linux", mode, w, h, EGL_NO_DISPLAY, EGL_NO_SURFACE, 0, print };

    if (!strcmp(mode, "offscreen")) {
        const char *node = getenv("APKLAYER_RENDER_NODE") ? getenv("APKLAYER_RENDER_NODE") : "/dev/dri/renderD128";
        struct gbm_device *gbm = gbm_create_device(open(node, O_RDWR | O_CLOEXEC));
        t.dpy = eglGetPlatformDisplay(EGL_PLATFORM_GBM_KHR, gbm, NULL);
        eglInitialize(t.dpy, NULL, NULL);
        eglBindAPI(EGL_OPENGL_ES_API);
        EGLContext ctx = eglCreateContext(t.dpy, choose(t.dpy, 0), EGL_NO_CONTEXT, ctx_attr);
        if (!eglMakeCurrent(t.dpy, EGL_NO_SURFACE, EGL_NO_SURFACE, ctx)) { fprintf(stderr, "glbench: surfaceless make current failed\n"); return 1; }
        t.offscreen = 1;
        return bench_run(&t, scenes, seconds);
    }

    struct wl_display *display = wl_display_connect(NULL);
    if (!display) { fprintf(stderr, "glbench: no Wayland display\n"); return 1; }
    struct wl_registry *registry = wl_display_get_registry(display);
    wl_registry_add_listener(registry, &registry_listener, NULL);
    wl_display_roundtrip(display);
    if (!compositor || !wm) { fprintf(stderr, "glbench: no wl_compositor or xdg_wm_base\n"); return 1; }
    xdg_wm_base_add_listener(wm, &wm_listener, NULL);
    struct wl_surface *surface = wl_compositor_create_surface(compositor);
    struct xdg_surface *xs = xdg_wm_base_get_xdg_surface(wm, surface);
    xdg_surface_add_listener(xs, &xdg_surface_listener, NULL);
    struct xdg_toplevel *top = xdg_surface_get_toplevel(xs);
    xdg_toplevel_add_listener(top, &top_listener, NULL);
    xdg_toplevel_set_title(top, "glbench");
    wl_surface_commit(surface);
    while (!configured) wl_display_dispatch(display);

    t.dpy = eglGetPlatformDisplay(EGL_PLATFORM_WAYLAND_KHR, display, NULL);
    eglInitialize(t.dpy, NULL, NULL);
    eglBindAPI(EGL_OPENGL_ES_API);
    EGLConfig cfg = choose(t.dpy, EGL_WINDOW_BIT);
    EGLContext ctx = eglCreateContext(t.dpy, cfg, EGL_NO_CONTEXT, ctx_attr);
    struct wl_egl_window *native = wl_egl_window_create(surface, w, h);
    t.surface = eglCreatePlatformWindowSurface(t.dpy, cfg, native, NULL);
    if (!eglMakeCurrent(t.dpy, t.surface, t.surface, ctx)) { fprintf(stderr, "glbench: make current failed 0x%x\n", eglGetError()); return 1; }
    eglSwapInterval(t.dpy, strcmp(mode, "window-novsync") ? 1 : 0);
    int r = bench_run(&t, scenes, seconds);
    eglMakeCurrent(t.dpy, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    eglDestroySurface(t.dpy, t.surface);
    wl_egl_window_destroy(native);
    xdg_toplevel_destroy(top);
    xdg_surface_destroy(xs);
    wl_surface_destroy(surface);
    wl_display_disconnect(display);
    return r;
}
