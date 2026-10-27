/* Registers the natives of apklayer.host.NativeLoader on the device-library route: the functions are native_loader.c's (the older route builds them into its own
 * libapklayer_native.so, where HotSpot finds them by name; here they are bound with RegisterNatives from the runtime shim). */
#include <jni.h>
#include <stdio.h>

extern void Java_apklayer_host_NativeLoader_setLibraryPath(JNIEnv *, jclass, jstring);
extern jlong Java_apklayer_host_NativeLoader_dlopen(JNIEnv *, jclass, jstring);
extern jlong Java_apklayer_host_NativeLoader_dlsym(JNIEnv *, jclass, jlong, jstring);
extern jstring Java_apklayer_host_NativeLoader_dlerror(JNIEnv *, jclass);
extern jint Java_apklayer_host_NativeLoader_callOnLoad(JNIEnv *, jclass, jlong);
extern jint Java_apklayer_host_NativeLoader_registerNative(JNIEnv *, jclass, jclass, jstring, jstring, jlong);

int apklayer_register_nativeloader(JNIEnv *env, jclass loader)
{
    JNINativeMethod methods[] = {
        {"setLibraryPath", "(Ljava/lang/String;)V", (void *)Java_apklayer_host_NativeLoader_setLibraryPath},
        {"dlopen", "(Ljava/lang/String;)J", (void *)Java_apklayer_host_NativeLoader_dlopen},
        {"dlsym", "(JLjava/lang/String;)J", (void *)Java_apklayer_host_NativeLoader_dlsym},
        {"dlerror", "()Ljava/lang/String;", (void *)Java_apklayer_host_NativeLoader_dlerror},
        {"callOnLoad", "(J)I", (void *)Java_apklayer_host_NativeLoader_callOnLoad},
        {"registerNative", "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/String;J)I", (void *)Java_apklayer_host_NativeLoader_registerNative},
    };
    if ((*env)->RegisterNatives(env, loader, methods, sizeof methods / sizeof *methods) != 0) {
        fprintf(stderr, "[runtime] cannot register the NativeLoader natives\n");
        if ((*env)->ExceptionCheck(env)) (*env)->ExceptionDescribe(env);
        return -1;
    }
    return 0;
}
