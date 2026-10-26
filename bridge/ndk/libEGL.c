/* NDK libEGL shim (glibc): Mesa's EGL on the surfaceless platform; window surfaces are pbuffers.
 * The app JVM is not a Wayland client, so eglSwapBuffers reads the pixels back and writes them to
 * $APKLAYER_GLDIR/<window id>.rgba; the host composites that file into the window (HostSurfaces). */
#define _GNU_SOURCE
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <dlfcn.h>
#include <fcntl.h>
#include <stdio.h>
#include <unistd.h>
#include <GLES2/gl2.h>
#include "anwindow.h"
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
static void log_if(int bad, const char *what);

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
    EGLBoolean r = f(d, buf, cfgs, n, num);
    log_if(!r || (num && !*num), "eglChooseConfig (no config)");
    return r;
}

static struct { EGLSurface s; struct ANativeWindow *w; unsigned seq; } wins[16];

EGLSurface eglCreateWindowSurface(EGLDisplay d, EGLConfig c, EGLNativeWindowType w, const EGLint *a)
{
    static EGLSurface (*f)(EGLDisplay, EGLConfig, const EGLint *);
    if (!f) f = sym("eglCreatePbufferSurface");
    (void)a;
    struct ANativeWindow *win = (struct ANativeWindow *)w;
    const EGLint pa[] = { EGL_WIDTH, win->width, EGL_HEIGHT, win->height, EGL_NONE };
    EGLSurface s = f ? f(d, c, pa) : EGL_NO_SURFACE;
    log_if(!s, "eglCreateWindowSurface");
    for (int i = 0; s && i < 16; i++) if (!wins[i].s) { wins[i].s = s; wins[i].w = win; break; }
    return s;
}

/* Header {w, h, seq} + bottom-up RGBA rows flipped to top-down; written atomically via rename. */
static void publish(unsigned i)
{
    const char *dir = getenv("APKLAYER_GLDIR");
    if (!dir) { fprintf(stderr, "[egl] APKLAYER_GLDIR not set\n"); return; }
    static void (*readpx)(GLint, GLint, GLsizei, GLsizei, GLenum, GLenum, void *);
    if (!readpx) readpx = dlsym(dlopen("libGLESv2.so.2", RTLD_LAZY), "glReadPixels");
    if (!readpx) { fprintf(stderr, "[egl] glReadPixels not found\n"); return; }
    int w = wins[i].w->width, h = wins[i].w->height;
    unsigned char *px = malloc((size_t)w * h * 4 + 12);
    unsigned *hd = (unsigned *)px;
    hd[0] = w; hd[1] = h; hd[2] = ++wins[i].seq;
    unsigned char *rows = px + 12, *tmp = malloc((size_t)w * h * 4);
    readpx(0, 0, w, h, GL_RGBA, GL_UNSIGNED_BYTE, tmp);
    for (int y = 0; y < h; y++) memcpy(rows + (size_t)y * w * 4, tmp + (size_t)(h - 1 - y) * w * 4, (size_t)w * 4);
    free(tmp);
    char path[512], part[520];
    snprintf(path, sizeof path, "%s/%d.rgba", dir, wins[i].w->id);
    snprintf(part, sizeof part, "%s.part", path);
    int fd = open(part, O_WRONLY | O_CREAT | O_TRUNC, 0600);
    if (fd >= 0) { if (write(fd, px, (size_t)w * h * 4 + 12) > 0) rename(part, path); close(fd); }
    free(px);
}

EGLBoolean eglSwapBuffers(EGLDisplay d, EGLSurface s)
{
    for (unsigned i = 0; i < 16; i++) if (wins[i].s == s) { publish(i); break; }
    static EGLBoolean (*f)(EGLDisplay, EGLSurface);
    if (!f) f = sym("eglSwapBuffers");
    return f ? f(d, s) : EGL_FALSE;
}

static void log_if(int bad, const char *what)
{
    static EGLint (*err)(void);
    if (!err) err = sym("eglGetError");
    if (bad) fprintf(stderr, "[egl] %s failed (0x%x)\n", what, err ? err() : 0);
}
EGLBoolean eglInitialize(EGLDisplay d, EGLint *a, EGLint *b)
{
    static EGLBoolean (*f)(EGLDisplay, EGLint *, EGLint *);
    if (!f) f = sym("eglInitialize");
    EGLBoolean r = f ? f(d, a, b) : EGL_FALSE;
    log_if(!r, "eglInitialize");
    return r;
}
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
EGLBoolean eglMakeCurrent(EGLDisplay d, EGLSurface dr, EGLSurface rd, EGLContext x)
{
    static EGLBoolean (*f)(EGLDisplay, EGLSurface, EGLSurface, EGLContext);
    if (!f) f = sym("eglMakeCurrent");
    EGLBoolean r = f ? f(d, dr, rd, x) : EGL_FALSE;
    log_if(!r, "eglMakeCurrent");
    return r;
}
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
