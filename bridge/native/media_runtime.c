/* libmedia_jni owns media registration separately from libandroid_runtime.
 * Called under the shim's existing JNI naming/class-loader adapter. */
#include <jni.h>
#include <stdio.h>
#include <stdint.h>
#include <stdbool.h>
extern void *bionic_dlopen(const char *, int);
extern void *bionic_dlsym(void *, const char *);
extern char *bionic_dlerror(void);
int apklayer_register_media(JNIEnv *env, JavaVM *vm)
{
    void *lib = bionic_dlopen("libmedia_jni.so", 2);
    jint (*load)(JavaVM *, void *) = lib ? bionic_dlsym(lib, "JNI_OnLoad") : NULL;
    if (!load) {
        fprintf(stderr, "[runtime] cannot load media JNI: %s\n", bionic_dlerror());
        return -1;
    }
    void *binder = bionic_dlopen("libbinder_ndk.so", 2);
    bool (*set_pool)(uint32_t) = binder ? bionic_dlsym(binder, "ABinderProcess_setThreadPoolMaxThreadCount") : NULL;
    void (*start_pool)(void) = binder ? bionic_dlsym(binder, "ABinderProcess_startThreadPool") : NULL;
    if (!set_pool || !start_pool || !set_pool(8)) {
        fprintf(stderr, "[runtime] cannot configure media binder pool\n");
        return -1;
    }
    start_pool();
    if (load(vm, NULL) < 0 || (*env)->ExceptionCheck(env)) {
        fprintf(stderr, "[runtime] media JNI registration failed\n");
        if ((*env)->ExceptionCheck(env)) (*env)->ExceptionDescribe(env);
        return -1;
    }
    fprintf(stderr, "[runtime] Android media JNI registered\n");
    return 0;
}

/* The helper is an uninstrumented host class: called after the JNI naming adapter
 * has been restored, so these private methods keep their original names. */
int apklayer_register_attribution(JNIEnv *env, jclass helper)
{
    void *lib = bionic_dlopen("libapklayer_attribution.so", 2);
    if (!lib) {
        fprintf(stderr, "[runtime] cannot load attribution JNI: %s\n", bionic_dlerror());
        return -1;
    }
    JNINativeMethod methods[] = {
        {"nativeCreate", "(Ljava/lang/Object;)J", bionic_dlsym(lib, "apklayer_attribution_create")},
        {"nativeDestroy", "(J)V", bionic_dlsym(lib, "apklayer_attribution_destroy")},
        {"nativeMatches", "(JLjava/lang/Object;)Z", bionic_dlsym(lib, "apklayer_attribution_matches")},
        {"nativeActive", "()I", bionic_dlsym(lib, "apklayer_attribution_active")},
    };
    for (size_t i = 0; i < sizeof methods / sizeof *methods; i++) if (!methods[i].fnPtr) return -1;
    if ((*env)->RegisterNatives(env, helper, methods, sizeof methods / sizeof *methods) < 0) return -1;
    fprintf(stderr, "[runtime] native attribution Parcel adapter registered\n");
    return 0;
}
