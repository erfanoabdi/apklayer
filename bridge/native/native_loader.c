/* apklayer native loader (PLAN P4): loads an app's bionic-linked .so through bionic_translation's
 * linker and wires its JNI functions into the running HotSpot JVM.
 * Java side: apklayer.host.NativeLoader. */
#define _GNU_SOURCE
#include <jni.h>
#include <link.h>
#include <stdint.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>
#include <sys/stat.h>

/* bionic_translation (libdl_bio) */
extern void dl_parse_library_path(const char *path, char *delim);
extern void dl_parse_app_library_path(const char *path, char *delim);
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

/* The app namespace's directories: NativeLoader's, then those of android_update_LD_LIBRARY_PATH (below) */
static char app_paths[4096], app_extra[4096];
static void apply_app_paths(void)
{
    char all[sizeof app_paths + sizeof app_extra + 1];
    snprintf(all, sizeof all, "%s%s%s", app_paths, *app_extra ? ":" : "", app_extra);
    dl_parse_app_library_path(all, ":");
}

/* The linker's private API Pojav/FCL call through libdl.so (their game's JRE directories): on Android the default namespace's search path; here the
 * app namespace's, after the app's own directories. Only existing directories outside /system and /vendor (the device's, which the system namespace has).
 * shortcut: the shim linker keeps 8 directories (LDPATH_MAX) of 512 bytes in all, the rest is dropped; raise those in bionic_translation when an app needs more. */
void bionic_android_update_LD_LIBRARY_PATH(const char *path)
{
    char copy[sizeof app_extra], *save = NULL;
    snprintf(copy, sizeof copy, "%s", path ? path : "");
    app_extra[0] = 0;
    for (char *d = strtok_r(copy, ":", &save); d; d = strtok_r(NULL, ":", &save)) {
        struct stat st;
        if (!strncmp(d, "/system/", 8) || !strncmp(d, "/vendor/", 8) || stat(d, &st) != 0 || !S_ISDIR(st.st_mode)) continue;
        size_t n = strlen(app_extra);
        snprintf(app_extra + n, sizeof app_extra - n, "%s%s", n ? ":" : "", d);
    }
    apply_app_paths();
}
void bionic___loader_android_update_LD_LIBRARY_PATH(const char *path) { bionic_android_update_LD_LIBRARY_PATH(path); }

JNIEXPORT void JNICALL Java_apklayer_host_NativeLoader_setLibraryPath(JNIEnv *env, jclass c, jstring system, jstring app)
{
    (void)c;
    const char *s = (*env)->GetStringUTFChars(env, system, NULL), *a = (*env)->GetStringUTFChars(env, app, NULL);
    dl_parse_library_path(s, ":");
    snprintf(app_paths, sizeof app_paths, "%s", a);
    apply_app_paths();
    (*env)->ReleaseStringUTFChars(env, system, s);
    (*env)->ReleaseStringUTFChars(env, app, a);
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

/* The loader FindClass answers through on this thread while a library's JNI_OnLoad runs (runtime_shim.c): the one that loads the library, as ART does. */
__thread jobject apklayer_onload_loader;

/* Calls the library's JNI_OnLoad(JavaVM*, void*): returns its JNI version, or -1. */
JNIEXPORT jint JNICALL Java_apklayer_host_NativeLoader_callOnLoad(JNIEnv *env, jclass c, jlong fn, jobject loader)
{
    (void)c;
    JavaVM *vm;
    if ((*env)->GetJavaVM(env, &vm) != JNI_OK) return -1;
    jint (*on_load)(JavaVM *, void *) = (jint (*)(JavaVM *, void *))(intptr_t)fn;
    jobject outer = apklayer_onload_loader;  /* a JNI_OnLoad that loads another library */
    apklayer_onload_loader = loader;
    jint version = on_load(vm, NULL);
    apklayer_onload_loader = outer;
    return version;
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

/* An app process ends the way Android ends it, killed: its native libraries' static destructors and atexit handlers do not run. Under HotSpot they would run on
 * the VM thread, which is not attached to the JVM (fbjni's destructors abort: "Unable to retrieve jni environment"), while the app's threads still use what they
 * destroy. Registered last, this handler runs first in exit(): stdio is flushed, then the process ends with the status. */
static void end_without_native_destructors(int status, void *arg)
{
    (void)arg;
    fflush(NULL);
    _exit(status);
}

JNIEXPORT void JNICALL Java_apklayer_host_NativeLoader_skipNativeExitHandlers(JNIEnv *env, jclass c)
{
    (void)env;
    (void)c;
    on_exit(end_without_native_destructors, NULL);
}
