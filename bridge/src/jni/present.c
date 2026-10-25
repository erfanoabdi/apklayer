/*
 * apklayer minimal Wayland presenter (for the Robolectric-based runner).
 * Exports: apklayer_wayland_init(w,h), apklayer_wayland_present(int* argb,w,h),
 *          apklayer_wayland_pump(ms), apklayer_wayland_should_close().
 * Plain C ABI, plus JNI wrappers (bottom) for apklayer.wayland.WlWindow, which lives in
 * the NON-instrumented classpath.
 */
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <poll.h>
#include <sys/mman.h>
#include <sys/syscall.h>
#include <fcntl.h>
#include <wayland-client.h>
#include "xdg-shell-client-protocol.h"

static int W = 480, H = 800, STRIDE;
static struct wl_display *display;
static struct wl_compositor *compositor;
static struct wl_shm *shm;
static struct xdg_wm_base *wm_base;
static struct wl_surface *surface;
static struct xdg_surface *xdg_surface;
static struct xdg_toplevel *toplevel;
static struct wl_buffer *buffer;
static uint32_t *pixels;
static volatile int close_requested;
static int frames_presented;

static void frame_done(void *d, struct wl_callback *cb, uint32_t t)
{ (void)d; (void)t; wl_callback_destroy(cb); }
static const struct wl_callback_listener frame_listener = { frame_done };

static void wm_ping(void *d, struct xdg_wm_base *wm, uint32_t s)
{ (void)d; xdg_wm_base_pong(wm, s); }
static const struct xdg_wm_base_listener wm_listener = { wm_ping };

static void xs_configure(void *d, struct xdg_surface *xs, uint32_t s)
{ (void)d; xdg_surface_ack_configure(xs, s); }
static const struct xdg_surface_listener xs_listener = { xs_configure };

static void top_configure(void *d, struct xdg_toplevel *t, int32_t w, int32_t h,
                          struct wl_array *s)
{ (void)d; (void)t; (void)w; (void)h; (void)s; }
static void top_close(void *d, struct xdg_toplevel *t)
{ (void)d; (void)t; close_requested = 1; }
static const struct xdg_toplevel_listener top_listener = { top_configure, top_close };

static void registry(void *d, struct wl_registry *r, uint32_t name, const char *iface,
                     uint32_t ver)
{
    (void)d; (void)ver;
    if (!strcmp(iface, wl_compositor_interface.name))
        compositor = wl_registry_bind(r, name, &wl_compositor_interface, 4);
    else if (!strcmp(iface, wl_shm_interface.name))
        shm = wl_registry_bind(r, name, &wl_shm_interface, 1);
    else if (!strcmp(iface, xdg_wm_base_interface.name))
        wm_base = wl_registry_bind(r, name, &xdg_wm_base_interface, 1);
}
static void registry_rm(void *d, struct wl_registry *r, uint32_t n)
{ (void)d; (void)r; (void)n; }
static const struct wl_registry_listener registry_listener = { registry, registry_rm };

int apklayer_wayland_init(int w, int h)
{
    if (display) return 1;
    if (w > 0) W = w;
    if (h > 0) H = h;
    STRIDE = W * 4;
    display = wl_display_connect(NULL);
    if (!display) { fprintf(stderr, "[wl] no compositor\n"); return 0; }
    struct wl_registry *reg = wl_display_get_registry(display);
    wl_registry_add_listener(reg, &registry_listener, NULL);
    wl_display_roundtrip(display);
    if (!compositor || !shm || !wm_base) { fprintf(stderr, "[wl] missing globals\n"); return 0; }
    xdg_wm_base_add_listener(wm_base, &wm_listener, NULL);
    surface = wl_compositor_create_surface(compositor);
    xdg_surface = xdg_wm_base_get_xdg_surface(wm_base, surface);
    xdg_surface_add_listener(xdg_surface, &xs_listener, NULL);
    toplevel = xdg_surface_get_toplevel(xdg_surface);
    xdg_toplevel_add_listener(toplevel, &top_listener, NULL);
    xdg_toplevel_set_title(toplevel, "apklayer");
    xdg_toplevel_set_app_id(toplevel, "dev.apklayer.app");
    int fd = (int)syscall(SYS_memfd_create, "apklayer-present", 0);
    if (fd < 0) return 0;
    if (ftruncate(fd, STRIDE * H) < 0) return 0;
    pixels = mmap(NULL, STRIDE * H, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);
    if (pixels == MAP_FAILED) return 0;
    struct wl_shm_pool *pool = wl_shm_create_pool(shm, fd, STRIDE * H);
    buffer = wl_shm_pool_create_buffer(pool, 0, W, H, STRIDE, WL_SHM_FORMAT_ARGB8888);
    wl_surface_commit(surface);
    wl_display_roundtrip(display);
    return 1;
}

int apklayer_wayland_present(const int32_t *src, int w, int h)
{
    if (!display || !src || w <= 0 || h <= 0) return 0;
    int cw = w < W ? w : W;
    int ch = h < H ? h : H;
    for (int y = 0; y < ch; y++)
        memcpy(pixels + (size_t)y * W, src + (size_t)y * w, (size_t)cw * 4);
    wl_surface_attach(surface, buffer, 0, 0);
    wl_surface_damage(surface, 0, 0, cw, ch);
    struct wl_callback *cb = wl_surface_frame(surface);
    wl_callback_add_listener(cb, &frame_listener, NULL);
    wl_surface_commit(surface);
    wl_display_flush(display);
    frames_presented++;
    if (frames_presented <= 3 || frames_presented % 60 == 0)
        fprintf(stderr, "[wl] PRESENT frame %d %dx%d\n", frames_presented, cw, ch);
    return frames_presented;
}

void apklayer_wayland_pump(int timeout_ms)
{
    if (!display) return;
    struct pollfd pfd = { .fd = wl_display_get_fd(display), .events = POLLIN };
    if (poll(&pfd, 1, timeout_ms) > 0)
        wl_display_dispatch(display);
    wl_display_dispatch_pending(display);
}

int apklayer_wayland_should_close(void) { return close_requested; }

/* JNI entry points for apklayer.wayland.WlWindow (loaded only by the plain, non-sandbox
 * classloader, so these bind exactly once). */
#include <jni.h>

JNIEXPORT jboolean JNICALL Java_apklayer_wayland_WlWindow_init(JNIEnv *e, jclass c, jint w, jint h)
{ (void)e; (void)c; return apklayer_wayland_init(w, h) ? JNI_TRUE : JNI_FALSE; }

JNIEXPORT jint JNICALL Java_apklayer_wayland_WlWindow_present(JNIEnv *e, jclass c,
                                                              jintArray argb, jint w, jint h)
{
    (void)c;
    if (!argb) return 0;
    jint *px = (*e)->GetPrimitiveArrayCritical(e, argb, NULL);
    int n = apklayer_wayland_present((const int32_t *)px, w, h);
    (*e)->ReleasePrimitiveArrayCritical(e, argb, px, JNI_ABORT);
    return n;
}

JNIEXPORT void JNICALL Java_apklayer_wayland_WlWindow_pump(JNIEnv *e, jclass c, jint ms)
{ (void)e; (void)c; apklayer_wayland_pump(ms); }

JNIEXPORT jboolean JNICALL Java_apklayer_wayland_WlWindow_shouldClose(JNIEnv *e, jclass c)
{ (void)e; (void)c; return apklayer_wayland_should_close() ? JNI_TRUE : JNI_FALSE; }
