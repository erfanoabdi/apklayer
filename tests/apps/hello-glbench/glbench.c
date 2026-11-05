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

static EGLConfig choose(EGLDisplay dpy, EGLint surface_type)
{
    const EGLint attr[] = { EGL_SURFACE_TYPE, surface_type, EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT, EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8,
                            EGL_BLUE_SIZE, 8, EGL_ALPHA_SIZE, 8, EGL_NONE };
    EGLConfig cfg = NULL;
    EGLint n = 0;
    eglChooseConfig(dpy, attr, &cfg, 1, &n);
    return n ? cfg : NULL;
}

JNIEXPORT jint JNICALL Java_hello_glbench_Main_run(JNIEnv *env, jclass cls, jobject jsurface, jstring jmode, jstring jscenes, jdouble seconds)
{
    const char *mode = (*env)->GetStringUTFChars(env, jmode, NULL);
    const char *scenes = jscenes ? (*env)->GetStringUTFChars(env, jscenes, NULL) : NULL;
    ANativeWindow *win = ANativeWindow_fromSurface(env, jsurface);
    struct bench_target t = { "android", mode, ANativeWindow_getWidth(win), ANativeWindow_getHeight(win), EGL_NO_DISPLAY, EGL_NO_SURFACE, 0, print };
    t.dpy = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    eglInitialize(t.dpy, NULL, NULL);
    EGLConfig cfg = choose(t.dpy, EGL_WINDOW_BIT);
    EGLContext ctx = eglCreateContext(t.dpy, cfg, EGL_NO_CONTEXT, ctx_attr);
    t.surface = eglCreateWindowSurface(t.dpy, cfg, win, NULL);
    int r = 1;
    if (t.surface != EGL_NO_SURFACE && eglMakeCurrent(t.dpy, t.surface, t.surface, ctx)) {
        eglSwapInterval(t.dpy, strcmp(mode, "window-novsync") ? 1 : 0);
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
    EGLConfig cfg = choose(t.dpy, EGL_PBUFFER_BIT);
    EGLContext ctx = eglCreateContext(t.dpy, cfg, EGL_NO_CONTEXT, ctx_attr);
    const EGLint pb[] = { EGL_WIDTH, 1, EGL_HEIGHT, 1, EGL_NONE };
    t.surface = eglCreatePbufferSurface(t.dpy, cfg, pb);
    if (!eglMakeCurrent(t.dpy, t.surface, t.surface, ctx)) { print("glbench: make current failed"); return 1; }
    return bench_run(&t, argc > 3 ? argv[3] : NULL, argc > 4 ? atof(argv[4]) : 3);
}
