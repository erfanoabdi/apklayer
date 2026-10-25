/*
 * apklayer minimal Wayland presenter (for the Robolectric-based runner).
 * Exports: apklayer_wayland_init(w,h), apklayer_wayland_present(int* argb,w,h),
 *          apklayer_wayland_pump(ms), apklayer_wayland_should_close(),
 *          apklayer_wayland_next_event(int32_t ev[4]) -> 1 if an input event was dequeued:
 *            {0=down,1=up,2=move (MotionEvent action codes), x, y, 0} for the pointer,
 *            {100, evdev keycode, 1=pressed/0=released, 0} for the keyboard.
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
static struct wl_seat *seat;
static volatile int close_requested;
static int frames_presented;

/* Input queue filled by the seat listeners, drained by apklayer_wayland_next_event(). */
#define EVQ 256
static int32_t evq[EVQ][4];
static unsigned evq_head, evq_tail;
static int ptr_x, ptr_y, ptr_down;

static void push_event(int32_t a, int32_t b, int32_t c)
{
    if (evq_tail - evq_head >= EVQ) return;
    int32_t *e = evq[evq_tail++ % EVQ];
    e[0] = a; e[1] = b; e[2] = c; e[3] = 0;
}

static void ptr_enter(void *d, struct wl_pointer *p, uint32_t s, struct wl_surface *sf,
                      wl_fixed_t x, wl_fixed_t y)
{ (void)d; (void)p; (void)s; (void)sf; ptr_x = wl_fixed_to_int(x); ptr_y = wl_fixed_to_int(y); }
static void ptr_leave(void *d, struct wl_pointer *p, uint32_t s, struct wl_surface *sf)
{
    (void)d; (void)p; (void)s; (void)sf;
    if (ptr_down) { ptr_down = 0; push_event(1, ptr_x, ptr_y); }
}
static void ptr_motion(void *d, struct wl_pointer *p, uint32_t t, wl_fixed_t x, wl_fixed_t y)
{
    (void)d; (void)p; (void)t;
    ptr_x = wl_fixed_to_int(x); ptr_y = wl_fixed_to_int(y);
    if (ptr_down) push_event(2, ptr_x, ptr_y);
}
static void ptr_button(void *d, struct wl_pointer *p, uint32_t s, uint32_t t, uint32_t b,
                       uint32_t st)
{
    (void)d; (void)p; (void)s; (void)t;
    if (b != 0x110 /* BTN_LEFT */) return;
    ptr_down = st == WL_POINTER_BUTTON_STATE_PRESSED;
    push_event(ptr_down ? 0 : 1, ptr_x, ptr_y);
}
static void ptr_axis(void *d, struct wl_pointer *p, uint32_t t, uint32_t a, wl_fixed_t v)
{ (void)d; (void)p; (void)t; (void)a; (void)v; }
static const struct wl_pointer_listener ptr_listener = {
    ptr_enter, ptr_leave, ptr_motion, ptr_button, ptr_axis };

static void kb_keymap(void *d, struct wl_keyboard *k, uint32_t f, int32_t fd, uint32_t sz)
{ (void)d; (void)k; (void)f; (void)sz; close(fd); }
static void kb_enter(void *d, struct wl_keyboard *k, uint32_t s, struct wl_surface *sf,
                     struct wl_array *keys)
{ (void)d; (void)k; (void)s; (void)sf; (void)keys; }
static void kb_leave(void *d, struct wl_keyboard *k, uint32_t s, struct wl_surface *sf)
{ (void)d; (void)k; (void)s; (void)sf; }
static void kb_key(void *d, struct wl_keyboard *k, uint32_t s, uint32_t t, uint32_t key,
                   uint32_t st)
{ (void)d; (void)k; (void)s; (void)t; push_event(100, key, st == WL_KEYBOARD_KEY_STATE_PRESSED); }
static void kb_mods(void *d, struct wl_keyboard *k, uint32_t s, uint32_t a, uint32_t b,
                    uint32_t c, uint32_t g)
{ (void)d; (void)k; (void)s; (void)a; (void)b; (void)c; (void)g; }
static const struct wl_keyboard_listener kb_listener = {
    kb_keymap, kb_enter, kb_leave, kb_key, kb_mods };

static void seat_caps(void *d, struct wl_seat *st, uint32_t caps)
{
    (void)d;
    static int have_ptr, have_kb;
    if ((caps & WL_SEAT_CAPABILITY_POINTER) && !have_ptr) {
        have_ptr = 1;
        wl_pointer_add_listener(wl_seat_get_pointer(st), &ptr_listener, NULL);
    }
    if ((caps & WL_SEAT_CAPABILITY_KEYBOARD) && !have_kb) {
        have_kb = 1;
        wl_keyboard_add_listener(wl_seat_get_keyboard(st), &kb_listener, NULL);
    }
}
static const struct wl_seat_listener seat_listener = { seat_caps };

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
    else if (!strcmp(iface, wl_seat_interface.name) && !seat) {
        /* v1: only the events our listeners above implement are ever sent. */
        seat = wl_registry_bind(r, name, &wl_seat_interface, 1);
        wl_seat_add_listener(seat, &seat_listener, NULL);
    }
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

int apklayer_wayland_next_event(int32_t ev[4])
{
    if (evq_head == evq_tail) return 0;
    memcpy(ev, evq[evq_head++ % EVQ], sizeof(evq[0]));
    return 1;
}

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

JNIEXPORT jboolean JNICALL Java_apklayer_wayland_WlWindow_nextEvent(JNIEnv *e, jclass c,
                                                                    jintArray out)
{
    (void)c;
    int32_t ev[4];
    if (!apklayer_wayland_next_event(ev)) return JNI_FALSE;
    (*e)->SetIntArrayRegion(e, out, 0, 4, (const jint *)ev);
    return JNI_TRUE;
}
