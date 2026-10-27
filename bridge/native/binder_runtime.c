/* Real android.os.Binder / BinderProxy in the app JVM (-Dapklayer.realbinder=true): the framework's own JNI registration (register_android_os_Binder), the
 * binder thread pool, and a small helper to hand a Java binder to the service manager and fetch one back (AIBinder_fromJavaBinder / toJavaBinder), so two
 * JVMs of an instance can call each other over the kernel binder like two processes on a phone. */
#include <jni.h>
#include <stdint.h>
#include <stdbool.h>
#include <pthread.h>
#include <time.h>
#include <errno.h>
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
typedef struct AServiceManager_NotificationRegistration Notification;
static Notification *(*p_notify)(const char *, void (*)(const char *, AIBinder *, void *), void *);
static void (*p_unnotify)(Notification *);
struct service_wait { pthread_mutex_t lock; pthread_cond_t changed; AIBinder *binder; };
static void service_ready(const char *name, AIBinder *binder, void *opaque)
{
    (void)name;
    struct service_wait *wait = opaque;
    pthread_mutex_lock(&wait->lock);
    if (!wait->binder) wait->binder = binder;
    else p_dec_strong(binder);
    pthread_cond_signal(&wait->changed);
    pthread_mutex_unlock(&wait->lock);
}
static jobject wait_service(JNIEnv *env, jclass cls, jstring name, jlong timeout_ms)
{
    (void)cls;
    if (timeout_ms <= 0 || timeout_ms > 30000) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/IllegalArgumentException"), "Invalid service deadline");
        return NULL;
    }
    const char *text = (*env)->GetStringUTFChars(env, name, NULL);
    if (!text) return NULL;
    struct service_wait wait = {.lock = PTHREAD_MUTEX_INITIALIZER};
    pthread_condattr_t attr;
    pthread_condattr_init(&attr);
    pthread_condattr_setclock(&attr, CLOCK_MONOTONIC);
    pthread_cond_init(&wait.changed, &attr);
    pthread_condattr_destroy(&attr);
    struct timespec deadline;
    clock_gettime(CLOCK_MONOTONIC, &deadline);
    deadline.tv_sec += timeout_ms / 1000;
    deadline.tv_nsec += (timeout_ms % 1000) * 1000000;
    if (deadline.tv_nsec >= 1000000000) { deadline.tv_sec++; deadline.tv_nsec -= 1000000000; }
    Notification *registration = p_notify(text, service_ready, &wait);
    if (registration) {
        pthread_mutex_lock(&wait.lock);
        int status = 0;
        while (!wait.binder && !status) status = pthread_cond_timedwait(&wait.changed, &wait.lock, &deadline);
        pthread_mutex_unlock(&wait.lock);
        // Original API joins any in-flight callback before returning: the stack cookie stays valid.
        p_unnotify(registration);
    }
    // Registration borrows the instance-name storage until deletion.
    (*env)->ReleaseStringUTFChars(env, name, text);
    jobject result = wait.binder ? p_to_java(env, wait.binder) : NULL;
    if (wait.binder) p_dec_strong(wait.binder);
    pthread_cond_destroy(&wait.changed); pthread_mutex_destroy(&wait.lock);
    return result;
}

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
    p_notify = bionic_dlsym(ndk, "AServiceManager_registerForServiceNotifications");
    p_unnotify = bionic_dlsym(ndk, "AServiceManager_NotificationRegistration_delete");
    if (!p_from_java || !p_to_java || !p_add_service || !p_check_service || !p_dec_strong || !p_notify || !p_unnotify) {
        fprintf(stderr, "[runtime] libbinder_ndk lacks the Java binder bridge functions\n");
        return -1;
    }
    JNINativeMethod methods[] = {
        {"publish0", "(Ljava/lang/String;Landroid/os/IBinder;)Z", (void *)publish},
        {"lookup0", "(Ljava/lang/String;)Landroid/os/IBinder;", (void *)lookup},
        {"startPool", "()V", (void *)start_pool},
        {"wait0", "(Ljava/lang/String;J)Landroid/os/IBinder;", (void *)wait_service},
    };
    if ((*env)->RegisterNatives(env, helper, methods, sizeof methods / sizeof *methods) < 0) return -1;
    return 0;
}
