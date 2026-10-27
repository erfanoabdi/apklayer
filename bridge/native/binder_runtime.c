/* Real android.os.Binder / BinderProxy in the app JVM (-Dapklayer.realbinder=true): the framework's own JNI registration (register_android_os_Binder), the
 * binder thread pool, and a small helper to hand a Java binder to the service manager and fetch one back (AIBinder_fromJavaBinder / toJavaBinder), so two
 * JVMs of an instance can call each other over the kernel binder like two processes on a phone. */
#include <jni.h>
#include <stdint.h>
#include <stdbool.h>
#include <stdio.h>
#include <string.h>

extern void *bionic_dlopen(const char *, int);
extern void *bionic_dlsym(void *, const char *);
extern char *bionic_dlerror(void);

typedef struct AIBinder AIBinder;
static AIBinder *(*p_from_java)(JNIEnv *, jobject);
static jobject (*p_to_java)(JNIEnv *, AIBinder *);
static int32_t (*p_add_service)(AIBinder *, const char *);
static AIBinder *(*p_check_service)(const char *);
static void (*p_dec_strong)(AIBinder *);

/* Inside the shim's RegisterNatives renaming window: the framework registers Binder, BinderInternal and BinderProxy. */
int apklayer_register_binder(JNIEnv *env, void *runtime)
{
    int (*reg)(JNIEnv *) = bionic_dlsym(runtime, "_Z26register_android_os_BinderP7_JNIEnv");
    if (!reg || reg(env) < 0 || (*env)->ExceptionCheck(env)) {
        fprintf(stderr, "[runtime] cannot register android.os.Binder natives\n");
        if ((*env)->ExceptionCheck(env)) (*env)->ExceptionDescribe(env);
        return -1;
    }
    fprintf(stderr, "[runtime] real android.os.Binder natives registered\n");
    return 0;
}

static jboolean publish(JNIEnv *env, jclass cls, jstring name, jobject binder)
{
    (void)cls;
    AIBinder *ai = p_from_java(env, binder);
    if (!ai) return JNI_FALSE;
    const char *n = (*env)->GetStringUTFChars(env, name, NULL);
    if (!n) { p_dec_strong(ai); return JNI_FALSE; }
    int32_t status = p_add_service(ai, n);
    p_dec_strong(ai);
    (*env)->ReleaseStringUTFChars(env, name, n);
    fprintf(stderr, "[runtime] publish: addService -> %d\n", status);
    return status == 0;
}

static jobject lookup(JNIEnv *env, jclass cls, jstring name)
{
    (void)cls;
    const char *n = (*env)->GetStringUTFChars(env, name, NULL);
    if (!n) return NULL;
    AIBinder *ai = p_check_service(n);
    (*env)->ReleaseStringUTFChars(env, name, n);
    if (!ai) return NULL;
    jobject result = p_to_java(env, ai);
    p_dec_strong(ai);
    return result;
}

static void start_pool(JNIEnv *env, jclass cls)
{
    (void)env; (void)cls;
    void *ndk = bionic_dlopen("libbinder_ndk.so", 2);
    bool (*set_max)(uint32_t) = ndk ? bionic_dlsym(ndk, "ABinderProcess_setThreadPoolMaxThreadCount") : NULL;
    void (*start)(void) = ndk ? bionic_dlsym(ndk, "ABinderProcess_startThreadPool") : NULL;
    if (!set_max || !start || !set_max(8)) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/IllegalStateException"), "Cannot start native Binder pool");
        return;
    }
    start();
}

/* The helper class apklayer.host.RealBinder (an uninstrumented host class; natives registered under their plain names). */
int apklayer_register_binderhelper(JNIEnv *env, jclass helper)
{
    void *ndk = bionic_dlopen("libbinder_ndk.so", 2);
    if (!ndk) { fprintf(stderr, "[runtime] cannot load libbinder_ndk: %s\n", bionic_dlerror()); return -1; }
    p_from_java = bionic_dlsym(ndk, "AIBinder_fromJavaBinder");
    p_to_java = bionic_dlsym(ndk, "AIBinder_toJavaBinder");
    p_add_service = bionic_dlsym(ndk, "AServiceManager_addService");
    p_check_service = bionic_dlsym(ndk, "AServiceManager_checkService");
    p_dec_strong = bionic_dlsym(ndk, "AIBinder_decStrong");
    if (!p_from_java || !p_to_java || !p_add_service || !p_check_service || !p_dec_strong) {
        fprintf(stderr, "[runtime] libbinder_ndk lacks the Java binder bridge functions\n");
        return -1;
    }
    JNINativeMethod methods[] = {
        {"publish0", "(Ljava/lang/String;Landroid/os/IBinder;)Z", (void *)publish},
        {"lookup0", "(Ljava/lang/String;)Landroid/os/IBinder;", (void *)lookup},
        {"startPool", "()V", (void *)start_pool},
    };
    if ((*env)->RegisterNatives(env, helper, methods, sizeof methods / sizeof *methods) < 0) return -1;
    return 0;
}
