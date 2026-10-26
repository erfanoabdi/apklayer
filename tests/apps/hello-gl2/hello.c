/* hello-gl2 (P4 exit sample): a native GLES2 renderer on a SurfaceView's ANativeWindow. */
#include <EGL/egl.h>
#include <GLES2/gl2.h>
#include <android/native_window_jni.h>
#include <android/log.h>
#include <jni.h>
#include <pthread.h>
#include <time.h>
#include <unistd.h>

static volatile int running;
static pthread_t thread;
static ANativeWindow *window;

#define LOG(...) __android_log_print(ANDROID_LOG_INFO, "hello-gl2", __VA_ARGS__)

static void *render(void *arg)
{
    LOG("render thread: window %p", (void *)window);
    EGLDisplay d = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    eglInitialize(d, 0, 0);
    const EGLint ca[] = { EGL_SURFACE_TYPE, EGL_WINDOW_BIT, EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT, EGL_RED_SIZE, 8, EGL_NONE };
    EGLConfig cfg; EGLint n;
    eglChooseConfig(d, ca, &cfg, 1, &n);
    EGLSurface s = eglCreateWindowSurface(d, cfg, window, NULL);
    const EGLint xa[] = { EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE };
    EGLContext x = eglCreateContext(d, cfg, EGL_NO_CONTEXT, xa);
    LOG("surface %p context %p", s, x);
    eglMakeCurrent(d, s, s, x);
    int w = ANativeWindow_getWidth(window), h = ANativeWindow_getHeight(window);
    for (int t = 0; running; t++) {
        glViewport(0, 0, w, h);
        glClearColor(0.1f, 0.2f + 0.002f * (t % 100), 0.5f, 1);
        glClear(GL_COLOR_BUFFER_BIT);
        glEnable(GL_SCISSOR_TEST);
        glScissor((t * 6) % (w - 60), h / 2 - 30, 60, 60);
        glClearColor(1, 0.8f, 0.1f, 1);
        glClear(GL_COLOR_BUFFER_BIT);
        glDisable(GL_SCISSOR_TEST);
        if (t < 3) LOG("frame %d", t);
        eglSwapBuffers(d, s);
        if (t < 3) LOG("swapped %d", t);
        usleep(33000);
    }
    eglMakeCurrent(d, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    eglDestroySurface(d, s);
    eglDestroyContext(d, x);
    return arg;
}

JNIEXPORT void JNICALL Java_hello_gl2_Main_nativeStart(JNIEnv *env, jclass c, jobject surface)
{
    (void)c;
    window = ANativeWindow_fromSurface(env, surface);
    LOG("nativeStart: window %p", (void *)window);
    running = 1;
    pthread_create(&thread, NULL, render, NULL);
}

JNIEXPORT void JNICALL Java_hello_gl2_Main_nativeStop(JNIEnv *env, jclass c)
{
    (void)env; (void)c;
    running = 0;
    pthread_join(thread, NULL);
    ANativeWindow_release(window);
}
