/* An arm64-only JNI library (hello-arm64): on an x86_64 host it runs only through the native bridge (PLAN "apps built for another CPU"). */
#include <dlfcn.h>
#include <jni.h>
#include <math.h>
#include <pthread.h>
#include <stdint.h>
#include <unistd.h>

static JavaVM *vm;

/* Which CPU this code was built for: proof that the translated library, not a host one, answered. */
JNIEXPORT jstring JNICALL Java_hello_arm64_Main_cpu(JNIEnv *env, jclass c)
{
    (void)c;
#if defined(__aarch64__)
    return (*env)->NewStringUTF(env, "aarch64");
#else
    return (*env)->NewStringUTF(env, "other");
#endif
}

/* Integer and floating point work (libm through the guest's libm). */
JNIEXPORT jdouble JNICALL Java_hello_arm64_Main_work(JNIEnv *env, jclass c, jint n)
{
    (void)env; (void)c;
    uint64_t acc = 1469598103934665603ull;
    double f = 0;
    for (jint i = 1; i <= n; i++) {
        acc = (acc ^ (uint64_t)i) * 1099511628211ull;
        f += sqrt((double)i) / (double)i;
    }
    return f + (double)(acc % 1000);
}

/* A call from native code into Java: Main.twice(int). */
JNIEXPORT jint JNICALL Java_hello_arm64_Main_callJava(JNIEnv *env, jclass c, jint v)
{
    jmethodID m = (*env)->GetStaticMethodID(env, c, "twice", "(I)I");
    return m ? (*env)->CallStaticIntMethod(env, c, m, v) : -1;
}

static jclass main_class;

static void *thread_main(void *arg)
{
    JNIEnv *env;
    if ((*vm)->AttachCurrentThread(vm, &env, NULL) != JNI_OK) return (void *)(intptr_t)-1;
    jmethodID m = (*env)->GetStaticMethodID(env, main_class, "twice", "(I)I");
    intptr_t r = m ? (*env)->CallStaticIntMethod(env, main_class, m, (jint)(intptr_t)arg) : -2;
    (*vm)->DetachCurrentThread(vm);
    return (void *)r;
}

/* A native thread that attaches to the VM and calls Java. */
static jint thread_call(JNIEnv *env, jclass c, jint v)
{
    (void)env; (void)c;
    pthread_t t;
    void *r;
    if (pthread_create(&t, NULL, thread_main, (void *)(intptr_t)v) != 0) return -3;
    pthread_join(t, &r);
    return (jint)(intptr_t)r;
}

/* GLES through the bridge's EGL/GLES proxies: a pbuffer cleared to a colour and read back (0xAABBGGRR), 0 on failure. The libraries are
 * opened here, not linked, so the other checks do not depend on the graphics proxies. */
JNIEXPORT jint JNICALL Java_hello_arm64_Main_gl(JNIEnv *env, jclass c)
{
    (void)env; (void)c;
    void *egl = dlopen("libEGL.so", RTLD_NOW), *gles = dlopen("libGLESv2.so", RTLD_NOW);
    if (!egl || !gles) return 0;
#define F(lib, ret, name, ...) ret (*name)(__VA_ARGS__) = (ret (*)(__VA_ARGS__))dlsym(lib, #name)
    F(egl, void *, eglGetDisplay, void *);
    F(egl, unsigned, eglInitialize, void *, int *, int *);
    F(egl, unsigned, eglChooseConfig, void *, const int *, void **, int, int *);
    F(egl, void *, eglCreatePbufferSurface, void *, void *, const int *);
    F(egl, void *, eglCreateContext, void *, void *, void *, const int *);
    F(egl, unsigned, eglMakeCurrent, void *, void *, void *, void *);
    F(gles, void, glClearColor, float, float, float, float);
    F(gles, void, glClear, unsigned);
    F(gles, void, glReadPixels, int, int, int, int, unsigned, unsigned, void *);
#undef F
    if (!eglGetDisplay || !glReadPixels) return 0;
    void *dpy = eglGetDisplay(0), *cfg = 0;
    int n = 0;
    const int cfg_attr[] = {0x3033 /* SURFACE_TYPE */, 0x0001 /* PBUFFER_BIT */, 0x3040 /* RENDERABLE_TYPE */, 0x0004 /* ES2 */, 0x3038};
    const int pb_attr[] = {0x3057 /* WIDTH */, 4, 0x3056 /* HEIGHT */, 4, 0x3038};
    const int ctx_attr[] = {0x3098 /* CONTEXT_CLIENT_VERSION */, 2, 0x3038};
    if (!eglInitialize(dpy, 0, 0) || !eglChooseConfig(dpy, cfg_attr, &cfg, 1, &n) || n < 1) return 0;
    void *surf = eglCreatePbufferSurface(dpy, cfg, pb_attr), *ctx = eglCreateContext(dpy, cfg, 0, ctx_attr);
    if (!surf || !ctx || !eglMakeCurrent(dpy, surf, surf, ctx)) return 0;
    glClearColor(1.0f, 0.5f, 0.0f, 1.0f);
    glClear(0x4000 /* COLOR_BUFFER_BIT */);
    uint32_t px = 0;
    glReadPixels(0, 0, 1, 1, 0x1908 /* RGBA */, 0x1401 /* UNSIGNED_BYTE */, &px);
    return (jint)px;
}

/* JNI_OnLoad registers threadCall itself (RegisterNatives through the bridge); the others are bound by name. */
JNIEXPORT jint JNI_OnLoad(JavaVM *jvm, void *reserved)
{
    (void)reserved;
    vm = jvm;
    JNIEnv *env;
    if ((*vm)->GetEnv(vm, (void **)&env, JNI_VERSION_1_6) != JNI_OK) return -1;
    jclass c = (*env)->FindClass(env, "hello/arm64/Main");
    if (!c) return -1;
    main_class = (*env)->NewGlobalRef(env, c);
    static const JNINativeMethod methods[] = {{"threadCall", "(I)I", (void *)thread_call}};
    if ((*env)->RegisterNatives(env, c, methods, 1) != 0) return -1;
    return JNI_VERSION_1_6;
}
