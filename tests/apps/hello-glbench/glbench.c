/* glbench on Android (tests/bench/glbench): the window modes on the app's SurfaceView through JNI (Main.run), and the offscreen mode as
 * a plain executable entry for `apklayer exec <this .so> offscreen [WxH] [scenes] [seconds]` (no app process, no SurfaceFlinger). */
#include <EGL/egl.h>
#include <android/log.h>
#include <android/native_window_jni.h>
#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "../../bench/glbench/bench.c"

static void print(const char *line)
{
    printf("%s\n", line);
    fflush(stdout);
    __android_log_print(ANDROID_LOG_INFO, "glbench", "%s", line);
}

static const EGLint ctx_attr[] = { EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE };

/* depth: 8, 10 (RGB10_A2) or 16 (half float, EGL_EXT_pixel_format_float) bits per channel; opaque: the first matching config without alpha
 * (XR30, XB4H), else the first one (AR30/AB30, AB4H) */
static EGLConfig choose(EGLDisplay dpy, EGLint surface_type, int depth, int opaque)
{
    const EGLint attr[] = { EGL_SURFACE_TYPE, surface_type, EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT, EGL_RED_SIZE, depth, EGL_GREEN_SIZE, depth,
                            EGL_BLUE_SIZE, depth, EGL_ALPHA_SIZE, opaque ? 0 : depth == 10 ? 2 : depth,
                            depth == 16 ? 0x3339 /* EGL_COLOR_COMPONENT_TYPE_EXT */ : EGL_NONE, 0x333B /* EGL_COLOR_COMPONENT_TYPE_FLOAT_EXT */, EGL_NONE };
    EGLConfig cfg[64];
    EGLint n = 0;
    eglChooseConfig(dpy, attr, cfg, 64, &n);
    for (EGLint i = 0; i < n; i++) {
        EGLint red = 0, alpha = 0;
        eglGetConfigAttrib(dpy, cfg[i], EGL_RED_SIZE, &red);
        eglGetConfigAttrib(dpy, cfg[i], EGL_ALPHA_SIZE, &alpha);
        if (red == depth && (!opaque || alpha == 0)) return cfg[i];
    }
    return NULL;
}

JNIEXPORT jint JNICALL Java_hello_glbench_Main_run(JNIEnv *env, jclass cls, jobject jsurface, jstring jmode, jstring jscenes, jdouble seconds)
{
    const char *mode = (*env)->GetStringUTFChars(env, jmode, NULL);
    const char *scenes = jscenes ? (*env)->GetStringUTFChars(env, jscenes, NULL) : NULL;
    ANativeWindow *win = ANativeWindow_fromSurface(env, jsurface);
    struct bench_target t = { "android", mode, ANativeWindow_getWidth(win), ANativeWindow_getHeight(win), EGL_NO_DISPLAY, EGL_NO_SURFACE, 0, print };
    t.dpy = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    eglInitialize(t.dpy, NULL, NULL);
    /* window-10bit[-opaque], window-fp16[-opaque]: the same with a deep config (the window present reads those buffers back) */
    int depth = strstr(mode, "10bit") ? 10 : strstr(mode, "fp16") ? 16 : 8;
    EGLConfig cfg = choose(t.dpy, EGL_WINDOW_BIT, depth, strstr(mode, "opaque") != NULL);
    if (!cfg) { print("glbench: no config of that depth"); return 1; }
    EGLint red = 0, alpha = 0, visual = 0;
    eglGetConfigAttrib(t.dpy, cfg, EGL_RED_SIZE, &red);
    eglGetConfigAttrib(t.dpy, cfg, EGL_ALPHA_SIZE, &alpha);
    eglGetConfigAttrib(t.dpy, cfg, EGL_NATIVE_VISUAL_ID, &visual);
    char line[96];
    snprintf(line, sizeof line, "glbench: %s config red=%d alpha=%d visual=0x%x", mode, red, alpha, visual);
    print(line);
    EGLContext ctx = eglCreateContext(t.dpy, cfg, EGL_NO_CONTEXT, ctx_attr);
    t.surface = eglCreateWindowSurface(t.dpy, cfg, win, NULL);
    int r = 1;
    if (t.surface != EGL_NO_SURFACE && eglMakeCurrent(t.dpy, t.surface, t.surface, ctx)) {
        eglSwapInterval(t.dpy, strstr(mode, "novsync") ? 0 : 1);
        r = bench_run(&t, scenes, seconds);
        eglMakeCurrent(t.dpy, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        eglDestroySurface(t.dpy, t.surface);
    } else {
        print("glbench: window surface failed");
    }
    eglDestroyContext(t.dpy, ctx);
    ANativeWindow_release(win);
    (*env)->ReleaseStringUTFChars(env, jmode, mode);
    if (scenes) (*env)->ReleaseStringUTFChars(env, jscenes, scenes);
    return r;
}

int main(int argc, char **argv)
{
    struct bench_target t = { "android", "offscreen", 800, 600, EGL_NO_DISPLAY, EGL_NO_SURFACE, 1, print };
    if (argc > 2) sscanf(argv[2], "%dx%d", &t.width, &t.height);
    t.dpy = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    eglInitialize(t.dpy, NULL, NULL);
    EGLConfig cfg = choose(t.dpy, EGL_PBUFFER_BIT, 8, 0);
    EGLContext ctx = eglCreateContext(t.dpy, cfg, EGL_NO_CONTEXT, ctx_attr);
    const EGLint pb[] = { EGL_WIDTH, 1, EGL_HEIGHT, 1, EGL_NONE };
    t.surface = eglCreatePbufferSurface(t.dpy, cfg, pb);
    if (!eglMakeCurrent(t.dpy, t.surface, t.surface, ctx)) { print("glbench: make current failed"); return 1; }
    return bench_run(&t, argc > 3 ? argv[3] : NULL, argc > 4 ? atof(argv[4]) : 3);
}
