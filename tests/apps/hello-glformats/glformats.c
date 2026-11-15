/* glformats on Android (tests/bench/glformats): the offscreen run as an executable entry (`btload <libs>:<dir> libhello.so --main`, a pbuffer
 * context) and, from the Activity's SurfaceView through JNI, the window surface colour-space checks (EGL_KHR_gl_colorspace) and the same format checks. */
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <android/log.h>
#include <dlfcn.h>
#include <android/native_window_jni.h>
#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "../../bench/glformats/formats.c"

#define EGL_GL_COLORSPACE_KHR_ 0x309D
#define EGL_GL_COLORSPACE_SRGB_KHR_ 0x3089
#define EGL_GL_COLORSPACE_LINEAR_KHR_ 0x308A

static void print(const char *line)
{
    printf("%s\n", line);
    fflush(stdout);
    __android_log_print(ANDROID_LOG_INFO, "glformats", "%s", line);
}

static const EGLint ctx_attr[] = { EGL_CONTEXT_CLIENT_VERSION, 3, EGL_NONE };

static EGLConfig choose(EGLDisplay dpy, EGLint surface_type)
{
    const EGLint attr[] = { EGL_SURFACE_TYPE, surface_type, EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT, EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_BLUE_SIZE, 8, EGL_ALPHA_SIZE, 8,
                            EGL_NONE };
    EGLConfig cfg = NULL;
    EGLint n = 0;
    eglChooseConfig(dpy, attr, &cfg, 1, &n);
    return n ? cfg : NULL;
}

/* What an NDK app that links libGLESv2 gets from the plain query functions (formats.c asks eglGetProcAddress instead): through Android's libGLESv2
 * glGetString goes to libEGL's egl_get_string_for_current_context (bridge/native/android_stubs.c answers 0) and then to the context's table, or to
 * libapklayer_gles (gles_filter.c, mapped for every app) which must answer in the window thread too: an engine that reads GL_EXTENSIONS this way
 * chooses its colour and format paths from them. */
#undef glGetString
#undef glGetStringi
#undef glGetIntegerv
#undef glClearColor
#undef glClear
#undef glReadPixels
static void linked_checks(void)
{
    const char *version = (const char *)glGetString(GL_VERSION), *ext = (const char *)glGetString(GL_EXTENSIONS);
    GLint n = -1, size = -1;
    glGetIntegerv(GL_NUM_EXTENSIONS, &n);
    glGetIntegerv(GL_MAX_TEXTURE_SIZE, &size);
    const char *first = n > 0 ? (const char *)glGetStringi(GL_EXTENSIONS, 0) : NULL;
    {  /* does any linked call reach the driver in this thread? a clear and its read back */
        unsigned char px[4] = { 9, 9, 9, 9 };
        glClearColor(0.25f, 0.5f, 0.75f, 1);
        glClear(GL_COLOR_BUFFER_BIT);
        glReadPixels(0, 0, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, px);
        out("glformats: linked clear+read %d,%d,%d,%d (want 64,128,191,255)", px[0], px[1], px[2], px[3]);
    }
    out("glformats: linked-glGetString version %s, GL_EXTENSIONS %s", version ? version : "NULL", ext ? "present" : "NULL");
    out("glformats: linked-glGetIntegerv GL_NUM_EXTENSIONS %d MAX_TEXTURE_SIZE %d, glGetStringi(0) %s", n, size, first ? first : "NULL");
    result("linked-glGetString", version != NULL && ext != NULL, "through the linked libGLESv2: version %s, extensions %s", version ? "answered" : "NULL", ext ? "answered" : "NULL");
    result("linked-glGetIntegerv", n > 0 && size > 0 && first != NULL, "GL_NUM_EXTENSIONS %d, MAX_TEXTURE_SIZE %d, glGetStringi(0) %s", n, size, first ? "answered" : "NULL");
}

/* A window surface made with each EGL_GL_COLORSPACE value: an app (Unity in linear colour space) that asks for an sRGB surface must get one that
 * encodes (FRAMEBUFFER_SRGB: 0.5 reads back 0xBC) or an error; a surface that says yes and stays linear darkens everything it draws. */
static void window_checks(EGLDisplay dpy, EGLConfig cfg, ANativeWindow *win, EGLContext ctx)
{
    const char *eglext = eglQueryString(dpy, EGL_EXTENSIONS);
    int listed = eglext && strstr(eglext, "EGL_KHR_gl_colorspace") != NULL;
    out("glformats: EGL_EXTENSIONS %s", eglext);
    static const struct { EGLint value; const char *name; } V[] = { { 0, "none" }, { EGL_GL_COLORSPACE_LINEAR_KHR_, "linear" }, { EGL_GL_COLORSPACE_SRGB_KHR_, "srgb" } };
    for (int i = 0; i < 3; i++) {
        char name[48];
        snprintf(name, sizeof name, "window-colorspace-%s", V[i].name);
        EGLint attr[] = { EGL_GL_COLORSPACE_KHR_, V[i].value, EGL_NONE };
        while (eglGetError() != EGL_SUCCESS) {}
        EGLSurface s = eglCreateWindowSurface(dpy, cfg, win, V[i].value ? attr : NULL);
        EGLint err = eglGetError();
        if (s == EGL_NO_SURFACE) {
            /* refusing an sRGB request is correct (EGL_BAD_MATCH on a config that cannot, EGL_BAD_ATTRIBUTE without the extension) */
            result(name, V[i].value == EGL_GL_COLORSPACE_SRGB_KHR_, "eglCreateWindowSurface failed with 0x%x (extension %s)", err, listed ? "listed" : "not listed");
            continue;
        }
        EGLint cs = -1;
        eglQuerySurface(dpy, s, EGL_GL_COLORSPACE_KHR_, &cs);
        eglMakeCurrent(dpy, s, s, ctx);
        GLint enc = 0;
        glGetFramebufferAttachmentParameteriv(GL_FRAMEBUFFER, GL_BACK, GL_COLOR_ENCODING, &enc);
        if (has("GL_EXT_sRGB_write_control")) glEnable(GL_FRAMEBUFFER_SRGB_EXT);
        glViewport(0, 0, 8, 8);
        glDisable(GL_BLEND);
        quad(1, 0, 0.5f, 0.5f, 0.5f, 1);
        unsigned char p[4];
        px8(4, 4, p);
        eglSwapBuffers(dpy, s);
        eglMakeCurrent(dpy, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        eglDestroySurface(dpy, s);
        int srgb_asked = V[i].value == EGL_GL_COLORSPACE_SRGB_KHR_;
        int encoded = abs(p[0] - 188) <= 2;
        int pass = srgb_asked ? (encoded && cs == EGL_GL_COLORSPACE_SRGB_KHR_) : (abs(p[0] - 128) <= 1 && cs != EGL_GL_COLORSPACE_SRGB_KHR_);
        result(name, pass, "surface made; EGL_GL_COLORSPACE_KHR query 0x%x, back buffer encoding 0x%x, 0.5 with FRAMEBUFFER_SRGB reads %d (sRGB: 188, linear: 128)", cs, enc, p[0]);
    }
}

JNIEXPORT jint JNICALL Java_hello_glformats_Main_run(JNIEnv *env, jclass cls, jobject jsurface)
{
    ANativeWindow *win = ANativeWindow_fromSurface(env, jsurface);
    EGLDisplay dpy = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    eglInitialize(dpy, NULL, NULL);
    EGLConfig cfg = choose(dpy, EGL_WINDOW_BIT);
    EGLContext ctx = cfg ? eglCreateContext(dpy, cfg, EGL_NO_CONTEXT, ctx_attr) : EGL_NO_CONTEXT;
    EGLSurface s = ctx ? eglCreateWindowSurface(dpy, cfg, win, NULL) : EGL_NO_SURFACE;
    if (s == EGL_NO_SURFACE || !eglMakeCurrent(dpy, s, s, ctx)) { print("glformats: no window ES 3 context"); return 99; }
    say = print;
    formats_proc = (void *(*)(const char *))eglGetProcAddress;
    linked_checks();
    if (!formats_prepare(print)) return 98;
    /* the window takes one EGL surface at a time: the colour-space variants each get it in turn, then the format checks */
    eglMakeCurrent(dpy, EGL_NO_SURFACE, EGL_NO_SURFACE, ctx);
    eglDestroySurface(dpy, s);
    window_checks(dpy, cfg, win, ctx);
    s = eglCreateWindowSurface(dpy, cfg, win, NULL);
    eglMakeCurrent(dpy, s, s, ctx);
    int fails = formats_tests();
    eglMakeCurrent(dpy, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    eglDestroySurface(dpy, s);
    eglDestroyContext(dpy, ctx);
    ANativeWindow_release(win);
    return fails;
}

int main(void)
{
    EGLDisplay dpy = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    eglInitialize(dpy, NULL, NULL);
    EGLConfig cfg = choose(dpy, EGL_PBUFFER_BIT);
    EGLContext ctx = eglCreateContext(dpy, cfg, EGL_NO_CONTEXT, ctx_attr);
    const EGLint pb[] = { EGL_WIDTH, 1, EGL_HEIGHT, 1, EGL_NONE };
    EGLSurface s = eglCreatePbufferSurface(dpy, cfg, pb);
    if (!eglMakeCurrent(dpy, s, s, ctx)) { print("glformats: make current failed"); return 99; }
    say = print;
    formats_proc = (void *(*)(const char *))eglGetProcAddress;
    linked_checks();
    out("glformats: EGL_EXTENSIONS %s", eglQueryString(dpy, EGL_EXTENSIONS));
    return formats_run(print);
}
