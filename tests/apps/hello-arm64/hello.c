/* An arm64-only JNI library (hello-arm64): on an x86_64 host it runs only through the native bridge (PLAN "apps built for another CPU"). */
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
