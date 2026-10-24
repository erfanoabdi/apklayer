/*
 * apklayer JNI bridge — com.macapk.loader.NativeCocoa on Linux/Wayland.
 *
 * The java_mac framework base was written against Mac.apk's AppKit runtime. Its host
 * seam is a single class, NativeCocoa (71 static natives). Here we implement the
 * presentation subset over Wayland (xdg_toplevel + wl_shm, fixed-size phone canvas for
 * now); everything else is a safe stub so a Boot-style driver can get to first frame.
 * Input injection, dynamic resize, IME, overlays land in later increments.
 */
#include <jni.h>
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

#define WIN_W 480
#define WIN_H 800
#define STRIDE (WIN_W * 4)

static struct wl_display *display;
static struct wl_compositor *compositor;
static struct wl_shm *shm;
static struct xdg_wm_base *wm_base;
static struct wl_surface *surface;
static struct xdg_surface *xdg_surface;
static struct xdg_toplevel *toplevel;
static struct wl_buffer *buffer;
static uint32_t *pixels;           /* mmap'd SHM backing */
static int phone_w = WIN_W, phone_h = WIN_H;
static int frames_presented;

static void frame_done(void *d, struct wl_callback *cb, uint32_t t)
{
    (void)d; (void)t;
    wl_callback_destroy(cb);
}
static const struct wl_callback_listener frame_listener = { frame_done };

static void wm_base_ping(void *d, struct xdg_wm_base *wm, uint32_t serial)
{
    (void)d;
    xdg_wm_base_pong(wm, serial);
}
static const struct xdg_wm_base_listener wm_base_listener = { wm_base_ping };

static void xdg_configure(void *d, struct xdg_surface *xs, uint32_t serial)
{
    (void)d;
    xdg_surface_ack_configure(xs, serial);
}
static const struct xdg_surface_listener xdg_surface_listener = { xdg_configure };

static void top_configure(void *d, struct xdg_toplevel *t, int32_t w, int32_t h,
                          struct wl_array *s)
{
    (void)d; (void)t; (void)w; (void)h; (void)s;
}
static void top_close(void *d, struct xdg_toplevel *t)
{
    (void)d; (void)t;
}
static const struct xdg_toplevel_listener top_listener = { top_configure, top_close };

static void registry_global(void *d, struct wl_registry *r, uint32_t name,
                            const char *iface, uint32_t ver)
{
    (void)d; (void)ver;
    if (!strcmp(iface, wl_compositor_interface.name))
        compositor = wl_registry_bind(r, name, &wl_compositor_interface, 4);
    else if (!strcmp(iface, wl_shm_interface.name))
        shm = wl_registry_bind(r, name, &wl_shm_interface, 1);
    else if (!strcmp(iface, xdg_wm_base_interface.name))
        wm_base = wl_registry_bind(r, name, &xdg_wm_base_interface, 1);
}
static void registry_remove(void *d, struct wl_registry *r, uint32_t n)
{
    (void)d; (void)r; (void)n;
}
static const struct wl_registry_listener registry_listener = {
    registry_global, registry_remove,
};

static int window_open(void)
{
    if (display) return 1;
    display = wl_display_connect(NULL);
    if (!display) {
        fprintf(stderr, "apklayer-jni: no wayland compositor\n");
        return 0;
    }
    struct wl_registry *reg = wl_display_get_registry(display);
    wl_registry_add_listener(reg, &registry_listener, NULL);
    wl_display_roundtrip(display);
    if (!compositor || !shm || !wm_base) {
        fprintf(stderr, "apklayer-jni: missing wayland globals\n");
        return 0;
    }
    xdg_wm_base_add_listener(wm_base, &wm_base_listener, NULL);
    surface = wl_compositor_create_surface(compositor);
    xdg_surface = xdg_wm_base_get_xdg_surface(wm_base, surface);
    xdg_surface_add_listener(xdg_surface, &xdg_surface_listener, NULL);
    toplevel = xdg_surface_get_toplevel(xdg_surface);
    xdg_toplevel_add_listener(toplevel, &top_listener, NULL);
    xdg_toplevel_set_title(toplevel, "apklayer");
    xdg_toplevel_set_app_id(toplevel, "dev.apklayer.app");

    int fd = (int)syscall(SYS_memfd_create, "apklayer-present", 0);
    if (fd < 0) return 0;
    if (ftruncate(fd, STRIDE * WIN_H) < 0) return 0;
    pixels = mmap(NULL, STRIDE * WIN_H, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);
    if (pixels == MAP_FAILED) return 0;
    struct wl_shm_pool *pool = wl_shm_create_pool(shm, fd, STRIDE * WIN_H);
    buffer = wl_shm_pool_create_buffer(pool, 0, WIN_W, WIN_H, STRIDE,
                                       WL_SHM_FORMAT_ARGB8888);
    wl_surface_commit(surface);
    wl_display_roundtrip(display);
    return 1;
}

static void present_copy_and_commit(const int32_t *src, int w, int h)
{
    int cw = w < WIN_W ? w : WIN_W;
    int ch = h < WIN_H ? h : WIN_H;
    for (int y = 0; y < ch; y++)
        memcpy(pixels + (size_t)y * WIN_W, src + (size_t)y * w, (size_t)cw * 4);
    wl_surface_attach(surface, buffer, 0, 0);
    wl_surface_damage(surface, 0, 0, cw, ch);
    struct wl_callback *cb = wl_surface_frame(surface);
    wl_callback_add_listener(cb, &frame_listener, NULL);
    wl_surface_commit(surface);
    wl_display_flush(display);
    frames_presented++;
}

static void pump(int timeout_ms)
{
    if (!display) return;
    struct pollfd pfd = { .fd = wl_display_get_fd(display), .events = POLLIN };
    if (poll(&pfd, 1, timeout_ms) > 0) {
        wl_display_dispatch(display);
    }
    wl_display_dispatch_pending(display);
}

/* ------------------------------------------------------------------ */
/* JNI: com.macapk.loader.NativeCocoa                                  */
/* ------------------------------------------------------------------ */

JNIEXPORT jboolean JNICALL
Java_com_macapk_loader_NativeCocoa_init(JNIEnv *e, jclass c)
{
    (void)e; (void)c;
    return window_open() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_macapk_loader_NativeCocoa_setPhoneSize(JNIEnv *e, jclass c, jint w, jint h)
{
    (void)e; (void)c;
    phone_w = w; phone_h = h;
}

JNIEXPORT jint JNICALL
Java_com_macapk_loader_NativeCocoa_phoneWidth(JNIEnv *e, jclass c)
{ (void)e; (void)c; return phone_w; }

JNIEXPORT jint JNICALL
Java_com_macapk_loader_NativeCocoa_phoneHeight(JNIEnv *e, jclass c)
{ (void)e; (void)c; return phone_h; }

JNIEXPORT jfloat JNICALL
Java_com_macapk_loader_NativeCocoa_backingScale(JNIEnv *e, jclass c)
{ (void)e; (void)c; return 1.0f; }

JNIEXPORT jint JNICALL
Java_com_macapk_loader_NativeCocoa_displayHz(JNIEnv *e, jclass c)
{ (void)e; (void)c; return 60; }

JNIEXPORT void JNICALL
Java_com_macapk_loader_NativeCocoa_presentBitmap(JNIEnv *e, jclass c,
                                                 jintArray argb, jint w, jint h)
{
    (void)c;
    if (!display || !argb || w <= 0 || h <= 0) return;
    jint *src = (*e)->GetIntArrayElements(e, argb, NULL);
    if (!src) return;
    present_copy_and_commit(src, w, h);
    (*e)->ReleaseIntArrayElements(e, argb, src, JNI_ABORT);
}

JNIEXPORT void JNICALL
Java_com_macapk_loader_NativeCocoa_presentSurfaceBitmap(JNIEnv *e, jclass c,
                                                        jint key, jintArray argb,
                                                        jint w, jint h)
{
    (void)key;
    Java_com_macapk_loader_NativeCocoa_presentBitmap(e, c, argb, w, h);
}

JNIEXPORT void JNICALL
Java_com_macapk_loader_NativeCocoa_presentOverlayBitmap(JNIEnv *e, jclass c,
                                                        jintArray argb, jint w, jint h)
{
    Java_com_macapk_loader_NativeCocoa_presentBitmap(e, c, argb, w, h);
}

JNIEXPORT void JNICALL
Java_com_macapk_loader_NativeCocoa_clearOverlayBitmap(JNIEnv *e, jclass c)
{ (void)e; (void)c; }

JNIEXPORT void JNICALL
Java_com_macapk_loader_NativeCocoa_runLoop(JNIEnv *e, jclass c)
{
    (void)e; (void)c;
    pump(16);
}

JNIEXPORT void JNICALL
Java_com_macapk_loader_NativeCocoa_setSystemProperty(JNIEnv *e, jclass c,
                                                     jstring k, jstring v)
{
    (void)c;
    const char *ck = k ? (*e)->GetStringUTFChars(e, k, NULL) : "";
    const char *cv = v ? (*e)->GetStringUTFChars(e, v, NULL) : "";
    fprintf(stderr, "[apklayer.prop] %s=%s\n", ck, cv);
    if (k) (*e)->ReleaseStringUTFChars(e, k, ck);
    if (v) (*e)->ReleaseStringUTFChars(e, v, cv);
}

JNIEXPORT void JNICALL
Java_com_macapk_loader_NativeCocoa_exitProcess(JNIEnv *e, jclass c, jint code,
                                               jstring relaunch)
{
    (void)e; (void)c; (void)relaunch;
    exit((int)code);
}

JNIEXPORT jlong JNICALL
Java_com_macapk_loader_NativeCocoa_getPlaceholderWindow(JNIEnv *e, jclass c)
{ (void)e; (void)c; return 1; }

JNIEXPORT jlong JNICALL
Java_com_macapk_loader_NativeCocoa_createWindow(JNIEnv *e, jclass c, jstring title)
{ (void)e; (void)c; (void)title; return 1; }

JNIEXPORT jlong JNICALL
Java_com_macapk_loader_NativeCocoa_createMetalSurfaceView(JNIEnv *e, jclass c,
                                                          jint w, jint h)
{ (void)e; (void)c; (void)w; (void)h; return 1; }

JNIEXPORT jlong JNICALL
Java_com_macapk_loader_NativeCocoa_createMetalSurfaceViewForGl(JNIEnv *e, jclass c,
                                                               jint w, jint h)
{ (void)e; (void)c; (void)w; (void)h; return 1; }

JNIEXPORT void JNICALL
Java_com_macapk_loader_NativeCocoa_setContentView(JNIEnv *e, jclass c, jlong w, jlong v)
{ (void)e; (void)c; (void)w; (void)v; }

JNIEXPORT void JNICALL
Java_com_macapk_loader_NativeCocoa_showWindow(JNIEnv *e, jclass c, jlong w)
{ (void)e; (void)c; (void)w; }

JNIEXPORT void JNICALL
Java_com_macapk_loader_NativeCocoa_closeWindow(JNIEnv *e, jclass c, jlong w)
{ (void)e; (void)c; (void)w; }

JNIEXPORT void JNICALL
Java_com_macapk_loader_NativeCocoa_setWindowTitle(JNIEnv *e, jclass c, jlong w,
                                                  jstring t)
{
    (void)c; (void)w;
    if (t && display) {
        const char *ct = (*e)->GetStringUTFChars(e, t, NULL);
        xdg_toplevel_set_title(toplevel, ct);
        wl_display_flush(display);
        (*e)->ReleaseStringUTFChars(e, t, ct);
    }
}

JNIEXPORT void JNICALL
Java_com_macapk_loader_NativeCocoa_setRenderer(JNIEnv *e, jclass c, jlong v, jobject cb)
{ (void)e; (void)c; (void)v; (void)cb; }

JNIEXPORT void JNICALL
Java_com_macapk_loader_NativeCocoa_releaseRenderer(JNIEnv *e, jclass c, jlong v)
{ (void)e; (void)c; (void)v; }

JNIEXPORT void JNICALL
Java_com_macapk_loader_NativeCocoa_registerTouchDispatcher(JNIEnv *e, jclass c,
                                                           jobject d)
{ (void)e; (void)c; (void)d; /* input injection: later increment */ }

JNIEXPORT jboolean JNICALL
Java_com_macapk_loader_NativeCocoa_postHostMouse(JNIEnv *e, jclass c, jint a,
                                                 jfloat x, jfloat y)
{ (void)e; (void)c; (void)a; (void)x; (void)y; return JNI_FALSE; }

JNIEXPORT jboolean JNICALL
Java_com_macapk_loader_NativeCocoa_debugTrackpad(JNIEnv *e, jclass c, jint a,
                                                 jfloatArray v)
{ (void)e; (void)c; (void)a; (void)v; return JNI_FALSE; }

JNIEXPORT jboolean JNICALL
Java_com_macapk_loader_NativeCocoa_hostRenderTick(JNIEnv *e, jclass c)
{ (void)e; (void)c; pump(0); return JNI_TRUE; }

JNIEXPORT jboolean JNICALL
Java_com_macapk_loader_NativeCocoa_beginWithheldTick(JNIEnv *e, jclass c, jboolean m)
{ (void)e; (void)c; (void)m; return JNI_FALSE; }

JNIEXPORT jboolean JNICALL
Java_com_macapk_loader_NativeCocoa_hostHideWindowsForQuit(JNIEnv *e, jclass c)
{ (void)e; (void)c; return JNI_TRUE; }

JNIEXPORT void JNICALL
Java_com_macapk_loader_NativeCocoa_quitBackstopExit(JNIEnv *e, jclass c)
{ (void)e; (void)c; exit(0); }

JNIEXPORT void JNICALL
Java_com_macapk_loader_NativeCocoa_moveTaskToBack(JNIEnv *e, jclass c)
{ (void)e; (void)c; }

JNIEXPORT void JNICALL
Java_com_macapk_loader_NativeCocoa_moveTaskToFront(JNIEnv *e, jclass c)
{ (void)e; (void)c; }

JNIEXPORT void JNICALL
Java_com_macapk_loader_NativeCocoa_setMaxAspectRatio(JNIEnv *e, jclass c, jfloat f)
{ (void)e; (void)c; (void)f; }

JNIEXPORT void JNICALL
Java_com_macapk_loader_NativeCocoa_setStartingWindowColor(JNIEnv *e, jclass c, jint argb)
{ (void)e; (void)c; (void)argb; }

JNIEXPORT void JNICALL
Java_com_macapk_loader_NativeCocoa_setStartingWindowFromBitmap(JNIEnv *e, jclass c,
                                                               jintArray a, jint w, jint h)
{ (void)e; (void)c; (void)a; (void)w; (void)h; }

JNIEXPORT void JNICALL
Java_com_macapk_loader_NativeCocoa_setMaxScreenLengthPx(JNIEnv *e, jclass c, jint px)
{ (void)e; (void)c; (void)px; }

JNIEXPORT void JNICALL
Java_com_macapk_loader_NativeCocoa_applyRequestedOrientation(JNIEnv *e, jclass c, jboolean b)
{ (void)e; (void)c; (void)b; }

JNIEXPORT void JNICALL
Java_com_macapk_loader_NativeCocoa_restoreUserOrientation(JNIEnv *e, jclass c)
{ (void)e; (void)c; }

JNIEXPORT void JNICALL
Java_com_macapk_loader_NativeCocoa_setSecureContent(JNIEnv *e, jclass c, jboolean s)
{ (void)e; (void)c; (void)s; }

JNIEXPORT void JNICALL
Java_com_macapk_loader_NativeCocoa_setWindowModeForToken(JNIEnv *e, jclass c,
                                                         jstring s, jint a, jint b)
{ (void)e; (void)c; (void)s; (void)a; (void)b; }

JNIEXPORT void JNICALL
Java_com_macapk_loader_NativeCocoa_setWindowCornerRadius(JNIEnv *e, jclass c, jfloat f)
{ (void)e; (void)c; (void)f; }

JNIEXPORT void JNICALL
Java_com_macapk_loader_NativeCocoa_setWindowActivityToken(JNIEnv *e, jclass c,
                                                          jstring s, jint t)
{ (void)e; (void)c; (void)s; (void)t; }

JNIEXPORT jstring JNICALL
Java_com_macapk_loader_NativeCocoa_windowActivityToken(JNIEnv *e, jclass c, jlong w)
{ (void)c; (void)w; return (*e)->NewStringUTF(e, ""); }

JNIEXPORT jint JNICALL
Java_com_macapk_loader_NativeCocoa_windowOverrideOrientation(JNIEnv *e, jclass c,
                                                             jstring s, jint o)
{ (void)e; (void)c; (void)s; (void)o; return 0; }

JNIEXPORT jint JNICALL
Java_com_macapk_loader_NativeCocoa_windowTokenAtCorner(JNIEnv *e, jclass c, jboolean l)
{ (void)e; (void)c; (void)l; return 0; }

JNIEXPORT jboolean JNICALL
Java_com_macapk_loader_NativeCocoa_openHostUrl(JNIEnv *e, jclass c, jstring url)
{ (void)e; (void)c; (void)url; return JNI_FALSE; }

JNIEXPORT jobjectArray JNICALL
Java_com_macapk_loader_NativeCocoa_takeHostUrls(JNIEnv *e, jclass c)
{ (void)e; (void)c; return NULL; }

JNIEXPORT jobjectArray JNICALL
Java_com_macapk_loader_NativeCocoa_urlSchemeHandlers(JNIEnv *e, jclass c, jstring s)
{ (void)e; (void)c; (void)s; return NULL; }

JNIEXPORT jint JNICALL
Java_com_macapk_loader_NativeCocoa_setUrlSchemeDefault(JNIEnv *e, jclass c,
                                                       jstring b, jstring s)
{ (void)e; (void)c; (void)b; (void)s; return 0; }

JNIEXPORT void JNICALL
Java_com_macapk_loader_NativeCocoa_setDataSubdir(JNIEnv *e, jclass c, jstring s)
{ (void)e; (void)c; (void)s; }

JNIEXPORT void JNICALL
Java_com_macapk_loader_NativeCocoa_setNativeLibDir(JNIEnv *e, jclass c, jstring s)
{ (void)e; (void)c; (void)s; }

JNIEXPORT void JNICALL
Java_com_macapk_loader_NativeCocoa_setSystemFontsDir(JNIEnv *e, jclass c, jstring s)
{ (void)e; (void)c; (void)s; }

JNIEXPORT jstring JNICALL
Java_com_macapk_loader_NativeCocoa_hostFontFile(JNIEnv *e, jclass c, jstring n)
{ (void)c; (void)n; return NULL; }

JNIEXPORT void JNICALL
Java_com_macapk_loader_NativeCocoa_setSharedStorageRoot(JNIEnv *e, jclass c, jstring s)
{ (void)e; (void)c; (void)s; }

JNIEXPORT void JNICALL
Java_com_macapk_loader_NativeCocoa_showTranslationOverlay(JNIEnv *e, jclass c, jstring s)
{ (void)e; (void)c; (void)s; }

JNIEXPORT void JNICALL
Java_com_macapk_loader_NativeCocoa_updateTranslationOverlay(JNIEnv *e, jclass c, jstring s)
{ (void)e; (void)c; (void)s; }

JNIEXPORT void JNICALL
Java_com_macapk_loader_NativeCocoa_hideTranslationOverlay(JNIEnv *e, jclass c)
{ (void)e; (void)c; }

JNIEXPORT void JNICALL
Java_com_macapk_loader_NativeCocoa_registerAppIconBitmap(JNIEnv *e, jclass c,
                                                         jintArray a, jint w, jint h,
                                                         jstring p)
{ (void)e; (void)c; (void)a; (void)w; (void)h; (void)p; }

JNIEXPORT void JNICALL
Java_com_macapk_loader_NativeCocoa_setDockBadgeCount(JNIEnv *e, jclass c, jint n)
{ (void)e; (void)c; (void)n; }

JNIEXPORT void JNICALL
Java_com_macapk_loader_NativeCocoa_setAppTrackingStatus(JNIEnv *e, jclass c,
                                                        jstring p, jint s)
{ (void)e; (void)c; (void)p; (void)s; }

JNIEXPORT void JNICALL
Java_com_macapk_loader_NativeCocoa_registerAppActivities(JNIEnv *e, jclass c,
                                                         jstring p, jobjectArray acts)
{ (void)e; (void)c; (void)p; (void)acts; }

JNIEXPORT void JNICALL
Java_com_macapk_loader_NativeCocoa_registerLaunchRecord(JNIEnv *e, jclass c,
                                                        jstring p, jstring a, jint u)
{ (void)e; (void)c; (void)p; (void)a; (void)u; }

JNIEXPORT void JNICALL
Java_com_macapk_loader_NativeCocoa_reportInsets(JNIEnv *e, jclass c,
                                                jfloat a, jfloat b, jfloat f, jfloat g)
{ (void)e; (void)c; (void)a; (void)b; (void)f; (void)g; }

JNIEXPORT void JNICALL
Java_com_macapk_loader_NativeCocoa_reportCompatOpt(JNIEnv *e, jclass c, jstring o)
{ (void)e; (void)c; (void)o; }

JNIEXPORT void JNICALL
Java_com_macapk_loader_NativeCocoa_reportSystemUiVisibility(JNIEnv *e, jclass c, jint v)
{ (void)e; (void)c; (void)v; }

JNIEXPORT void JNICALL
Java_com_macapk_loader_NativeCocoa_setAppSoundEnabled(JNIEnv *e, jclass c, jboolean s)
{ (void)e; (void)c; (void)s; }

JNIEXPORT void JNICALL
Java_com_macapk_loader_NativeCocoa_playStartupSound(JNIEnv *e, jclass c)
{ (void)e; (void)c; }

JNIEXPORT void JNICALL
Java_com_macapk_loader_NativeCocoa_vibrateStrong(JNIEnv *e, jclass c)
{ (void)e; (void)c; }

JNIEXPORT void JNICALL
Java_com_macapk_loader_NativeCocoa_showUserAlert(JNIEnv *e, jclass c, jstring t, jstring m)
{
    (void)c;
    const char *ct = t ? (*e)->GetStringUTFChars(e, t, NULL) : "";
    const char *cm = m ? (*e)->GetStringUTFChars(e, m, NULL) : "";
    fprintf(stderr, "ALERT/%s: %s\n", ct, cm);
    if (t) (*e)->ReleaseStringUTFChars(e, t, ct);
    if (m) (*e)->ReleaseStringUTFChars(e, m, cm);
}

JNIEXPORT void JNICALL
Java_com_macapk_loader_NativeCocoa_debugQuitGesture(JNIEnv *e, jclass c, jstring k)
{ (void)e; (void)c; (void)k; }

JNIEXPORT void JNICALL
Java_com_macapk_loader_NativeCocoa_setDisplayLinkFps(JNIEnv *e, jclass c, jint fps)
{ (void)e; (void)c; (void)fps; }
