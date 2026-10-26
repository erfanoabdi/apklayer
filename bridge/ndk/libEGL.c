/* NDK libEGL shim (glibc): Mesa's EGL on the surfaceless platform; window surfaces are pbuffers.
 * The app JVM is not a Wayland client, so there is no native window to present to. eglSwapBuffers
 * flushes; delivering the pixels to the shell is the next step (see PLAN P4.3). */
#define _GNU_SOURCE
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <dlfcn.h>
#include <pthread.h>
#include <stdlib.h>
#include <string.h>

static void *mesa;
static pthread_once_t once = PTHREAD_ONCE_INIT;
static void open_mesa(void) { mesa = dlopen("libEGL.so.1", RTLD_NOW | RTLD_GLOBAL); }
static void *sym(const char *n) { pthread_once(&once, open_mesa); return mesa ? dlsym(mesa, n) : NULL; }

#define FWD(ret, name, params, args, fail) \
    ret name params { static ret (*real_) params; if (!real_) real_ = sym(#name); return real_ ? real_ args : (fail); }

EGLDisplay eglGetPlatformDisplay(EGLenum p, void *d, const EGLAttrib *a);

EGLDisplay eglGetDisplay(EGLNativeDisplayType id)
{
    (void)id;
    return eglGetPlatformDisplay(EGL_PLATFORM_SURFACELESS_MESA, EGL_DEFAULT_DISPLAY, NULL);
}
EGLDisplay eglGetPlatformDisplay(EGLenum p, void *d, const EGLAttrib *a)
{
    static EGLDisplay (*f)(EGLenum, void *, const EGLAttrib *);
    if (!f) f = sym("eglGetPlatformDisplay");
    (void)p;
    return f ? f(EGL_PLATFORM_SURFACELESS_MESA, d, a) : EGL_NO_DISPLAY;
}

/* Android apps ask for window-capable configs; the surfaceless platform only has pbuffer ones. */
EGLBoolean eglChooseConfig(EGLDisplay d, const EGLint *attr, EGLConfig *cfgs, EGLint n, EGLint *num)
{
    static EGLBoolean (*f)(EGLDisplay, const EGLint *, EGLConfig *, EGLint, EGLint *);
    if (!f) f = sym("eglChooseConfig");
    if (!f) return EGL_FALSE;
    EGLint buf[128]; int i = 0;
    for (; attr && attr[i] != EGL_NONE && i < 120; i += 2) {
        buf[i] = attr[i];
        buf[i + 1] = attr[i] == EGL_SURFACE_TYPE ? ((attr[i + 1] & ~EGL_WINDOW_BIT) | EGL_PBUFFER_BIT) : attr[i + 1];
    }
    buf[i] = EGL_NONE;
    return f(d, buf, cfgs, n, num);
}

EGLSurface eglCreateWindowSurface(EGLDisplay d, EGLConfig c, EGLNativeWindowType w, const EGLint *a)
{
    static EGLSurface (*f)(EGLDisplay, EGLConfig, const EGLint *);
    if (!f) f = sym("eglCreatePbufferSurface");
    (void)w; (void)a;
    const EGLint pa[] = { EGL_WIDTH, 480, EGL_HEIGHT, 800, EGL_NONE };
    return f ? f(d, c, pa) : EGL_NO_SURFACE;
}

FWD(EGLBoolean, eglSwapBuffers, (EGLDisplay d, EGLSurface s), (d, s), EGL_FALSE)
FWD(EGLBoolean, eglInitialize, (EGLDisplay d, EGLint *a, EGLint *b), (d, a, b), EGL_FALSE)
FWD(EGLBoolean, eglTerminate, (EGLDisplay d), (d), EGL_FALSE)
FWD(EGLBoolean, eglBindAPI, (EGLenum a), (a), EGL_FALSE)
FWD(EGLenum, eglQueryAPI, (void), (), 0)
FWD(EGLint, eglGetError, (void), (), EGL_NOT_INITIALIZED)
FWD(const char *, eglQueryString, (EGLDisplay d, EGLint n), (d, n), NULL)
FWD(EGLBoolean, eglGetConfigs, (EGLDisplay d, EGLConfig *c, EGLint n, EGLint *num), (d, c, n, num), EGL_FALSE)
FWD(EGLBoolean, eglGetConfigAttrib, (EGLDisplay d, EGLConfig c, EGLint a, EGLint *v), (d, c, a, v), EGL_FALSE)
FWD(EGLSurface, eglCreatePbufferSurface, (EGLDisplay d, EGLConfig c, const EGLint *a), (d, c, a), EGL_NO_SURFACE)
FWD(EGLBoolean, eglDestroySurface, (EGLDisplay d, EGLSurface s), (d, s), EGL_FALSE)
FWD(EGLBoolean, eglQuerySurface, (EGLDisplay d, EGLSurface s, EGLint a, EGLint *v), (d, s, a, v), EGL_FALSE)
FWD(EGLBoolean, eglSurfaceAttrib, (EGLDisplay d, EGLSurface s, EGLint a, EGLint v), (d, s, a, v), EGL_FALSE)
FWD(EGLContext, eglCreateContext, (EGLDisplay d, EGLConfig c, EGLContext sh, const EGLint *a), (d, c, sh, a), EGL_NO_CONTEXT)
FWD(EGLBoolean, eglDestroyContext, (EGLDisplay d, EGLContext x), (d, x), EGL_FALSE)
FWD(EGLBoolean, eglMakeCurrent, (EGLDisplay d, EGLSurface dr, EGLSurface rd, EGLContext x), (d, dr, rd, x), EGL_FALSE)
FWD(EGLContext, eglGetCurrentContext, (void), (), EGL_NO_CONTEXT)
FWD(EGLSurface, eglGetCurrentSurface, (EGLint r), (r), EGL_NO_SURFACE)
FWD(EGLDisplay, eglGetCurrentDisplay, (void), (), EGL_NO_DISPLAY)
FWD(EGLBoolean, eglQueryContext, (EGLDisplay d, EGLContext x, EGLint a, EGLint *v), (d, x, a, v), EGL_FALSE)
FWD(EGLBoolean, eglWaitGL, (void), (), EGL_TRUE)
FWD(EGLBoolean, eglWaitNative, (EGLint e), (e), EGL_TRUE)
FWD(EGLBoolean, eglSwapInterval, (EGLDisplay d, EGLint i), (d, i), EGL_TRUE)
FWD(EGLBoolean, eglReleaseThread, (void), (), EGL_TRUE)
FWD(EGLSurface, eglCreatePbufferFromClientBuffer, (EGLDisplay d, EGLenum t, EGLClientBuffer b, EGLConfig c, const EGLint *a), (d, t, b, c, a), EGL_NO_SURFACE)
FWD(EGLSurface, eglCreatePixmapSurface, (EGLDisplay d, EGLConfig c, EGLNativePixmapType p, const EGLint *a), (d, c, p, a), EGL_NO_SURFACE)
FWD(EGLBoolean, eglCopyBuffers, (EGLDisplay d, EGLSurface s, EGLNativePixmapType p), (d, s, p), EGL_FALSE)
FWD(EGLBoolean, eglBindTexImage, (EGLDisplay d, EGLSurface s, EGLint b), (d, s, b), EGL_FALSE)
FWD(EGLBoolean, eglReleaseTexImage, (EGLDisplay d, EGLSurface s, EGLint b), (d, s, b), EGL_FALSE)
FWD(EGLSyncKHR, eglCreateSyncKHR, (EGLDisplay d, EGLenum t, const EGLint *a), (d, t, a), EGL_NO_SYNC_KHR)
FWD(EGLBoolean, eglDestroySyncKHR, (EGLDisplay d, EGLSyncKHR s), (d, s), EGL_FALSE)
FWD(EGLint, eglClientWaitSyncKHR, (EGLDisplay d, EGLSyncKHR s, EGLint f, EGLTimeKHR t), (d, s, f, t), EGL_FALSE)

__eglMustCastToProperFunctionPointerType eglGetProcAddress(const char *name)
{
    static __eglMustCastToProperFunctionPointerType (*f)(const char *);
    if (!strcmp(name, "eglSwapBuffers")) return (void *)eglSwapBuffers;
    if (!strcmp(name, "eglCreateWindowSurface")) return (void *)eglCreateWindowSurface;
    if (!strcmp(name, "eglChooseConfig")) return (void *)eglChooseConfig;
    if (!f) f = sym("eglGetProcAddress");
    return f ? f(name) : NULL;
}
