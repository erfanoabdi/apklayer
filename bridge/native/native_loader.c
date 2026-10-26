/* apklayer native loader (PLAN P4): loads an app's bionic-linked .so through bionic_translation's
 * linker and wires its JNI functions into the running HotSpot JVM.
 * Java side: apklayer.host.NativeLoader. */
#define _GNU_SOURCE
#include <jni.h>
#include <link.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

/* bionic_translation (libdl_bio) */
extern void dl_parse_library_path(const char *path, char *delim);
extern void *bionic_dlopen(const char *filename, int flag);
extern void *bionic_dlsym(void *handle, const char *symbol);
extern char *bionic_dlerror(void);

/* The bionic linker registers loaded libraries with debuggers through glibc's _r_debug; it expects
 * the main executable to point it there (bionic_translation/main_executable/bionic_compat.c). */
extern struct r_debug *_r_debug_ptr;
__attribute__((constructor)) static void init_r_debug(void)
{
    _r_debug_ptr = &_r_debug;
}

JNIEXPORT void JNICALL Java_apklayer_host_NativeLoader_setLibraryPath(JNIEnv *env, jclass c, jstring dir)
{
    (void)c;
    const char *d = (*env)->GetStringUTFChars(env, dir, NULL);
    dl_parse_library_path(d, ":");
    (void)c;
    (*env)->ReleaseStringUTFChars(env, dir, d);
}

JNIEXPORT jlong JNICALL Java_apklayer_host_NativeLoader_dlopen(JNIEnv *env, jclass c, jstring path)
{
    (void)c;
    const char *p = (*env)->GetStringUTFChars(env, path, NULL);
    void *h = bionic_dlopen(p, 2 /* RTLD_NOW */);
    (*env)->ReleaseStringUTFChars(env, path, p);
    return (jlong)(intptr_t)h;
}

JNIEXPORT jlong JNICALL Java_apklayer_host_NativeLoader_dlsym(JNIEnv *env, jclass c, jlong handle, jstring name)
{
    (void)c;
    const char *n = (*env)->GetStringUTFChars(env, name, NULL);
    void *s = bionic_dlsym((void *)(intptr_t)handle, n);
    (*env)->ReleaseStringUTFChars(env, name, n);
    return (jlong)(intptr_t)s;
}

JNIEXPORT jstring JNICALL Java_apklayer_host_NativeLoader_dlerror(JNIEnv *env, jclass c)
{
    (void)c;
    const char *e = bionic_dlerror();
    return e ? (*env)->NewStringUTF(env, e) : NULL;
}

/* Calls the library's JNI_OnLoad(JavaVM*, void*): returns its JNI version, or -1. */
JNIEXPORT jint JNICALL Java_apklayer_host_NativeLoader_callOnLoad(JNIEnv *env, jclass c, jlong fn)
{
    (void)c;
    JavaVM *vm;
    if ((*env)->GetJavaVM(env, &vm) != JNI_OK) return -1;
    jint (*on_load)(JavaVM *, void *) = (jint (*)(JavaVM *, void *))(intptr_t)fn;
    return on_load(vm, NULL);
}

/* Binds one native method to a function found by name (JNI naming convention). */
JNIEXPORT jint JNICALL Java_apklayer_host_NativeLoader_registerNative(JNIEnv *env, jclass c, jclass target,
                                                                      jstring name, jstring sig, jlong fn)
{
    (void)c;
    const char *n = (*env)->GetStringUTFChars(env, name, NULL);
    const char *s = (*env)->GetStringUTFChars(env, sig, NULL);
    JNINativeMethod m = { (char *)n, (char *)s, (void *)(intptr_t)fn };
    jint r = (*env)->RegisterNatives(env, target, &m, 1);
    (*env)->ReleaseStringUTFChars(env, name, n);
    (*env)->ReleaseStringUTFChars(env, sig, s);
    return r;
}
