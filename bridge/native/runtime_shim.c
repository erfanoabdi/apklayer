/* apklayer native runtime shim (PLAN P7): the library Robolectric loads as "native/<os>/<arch>/libandroid_runtime.so".
 * Instead of Google's host build, its JNI_OnLoad loads the *device* libandroid_runtime.so (arm64, from an AOSP build
 * of ~/los24) through bionic_translation and lets Android's own AndroidRuntime::startReg register every framework
 * native. Robolectric instruments the framework classes, so a native `foo` is bound as `$$robo$$foo$nativeBinding`
 * (the system property method_binding_format); RegisterNatives is intercepted during startReg to apply that renaming.
 * Needs: -Dapklayer.androidlibs=<dir of the Android libraries>, LD_LIBRARY_PATH to bionic_translation's libs and
 * XDG_DATA_DIRS to its cfg.d (bin/apklayer sets them). */
#define _GNU_SOURCE
#include <dlfcn.h>
#include <jni.h>
#include <jvmti.h>
#include <link.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

extern void dl_parse_library_path(const char *path, char *delim);
extern void *bionic_dlopen(const char *filename, int flag);
extern void *bionic_dlsym(void *handle, const char *symbol);
extern char *bionic_dlerror(void);
extern struct r_debug *_r_debug_ptr;

static char binding_prefix[64] = "", binding_suffix[64] = "";
static jniNativeInterface *real_table;

/* the renaming: "$$robo$$${method}$nativeBinding" -> prefix "$$robo$$", suffix "$nativeBinding" */
static void parse_binding_format(const char *fmt)
{
    const char *at = strstr(fmt, "${method}");
    if (!at) return;
    snprintf(binding_prefix, sizeof binding_prefix, "%.*s", (int)(at - fmt), fmt);
    snprintf(binding_suffix, sizeof binding_suffix, "%s", at + strlen("${method}"));
}

static jint JNICALL renaming_register_natives(JNIEnv *env, jclass cls, const JNINativeMethod *methods, jint n)
{
    int bound = 0, skipped = 0;
    for (jint i = 0; i < n; i++) {
        char name[256];
        snprintf(name, sizeof name, "%s%s%s", binding_prefix, methods[i].name, binding_suffix);
        JNINativeMethod one = { name, methods[i].signature, methods[i].fnPtr };
        if (real_table->RegisterNatives(env, cls, &one, 1) == 0) bound++;
        else { (*env)->ExceptionClear(env); skipped++; } /* not a method Robolectric instruments */
    }
    (void)bound; (void)skipped;
    return 0; /* startReg aborts the process on failure; skipped methods are expected */
}

static char *system_property(JNIEnv *env, const char *key)
{
    jclass sys = (*env)->FindClass(env, "java/lang/System");
    jmethodID get = (*env)->GetStaticMethodID(env, sys, "getProperty", "(Ljava/lang/String;)Ljava/lang/String;");
    jstring k = (*env)->NewStringUTF(env, key);
    jstring v = (jstring)(*env)->CallStaticObjectMethod(env, sys, get, k);
    if (!v) return NULL;
    const char *c = (*env)->GetStringUTFChars(env, v, NULL);
    char *copy = strdup(c);
    (*env)->ReleaseStringUTFChars(env, v, c);
    return copy;
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved)
{
    (void)reserved;
    JNIEnv *env;
    if ((*vm)->GetEnv(vm, (void **)&env, JNI_VERSION_1_6) != JNI_OK) return -1;

    /* make this library's symbols (android_platform.c, the generated stubs) visible to the bionic linker's fallback */
    Dl_info self;
    if (dladdr((void *)JNI_OnLoad, &self)) dlopen(self.dli_fname, RTLD_NOW | RTLD_GLOBAL);

    char *libs = system_property(env, "apklayer.androidlibs"), *format = system_property(env, "method_binding_format");
    if (!libs) { fprintf(stderr, "[runtime] -Dapklayer.androidlibs is not set\n"); return -1; }
    parse_binding_format(format ? format : "$$robo$$${method}$nativeBinding");

    _r_debug_ptr = &_r_debug;
    dl_parse_library_path(libs, ":");
    void *rt = bionic_dlopen("libandroid_runtime.so", 2 /* RTLD_NOW */);
    if (!rt) { fprintf(stderr, "[runtime] cannot load libandroid_runtime.so: %s\n", bionic_dlerror()); return -1; }
    int (*start_reg)(JNIEnv *) = bionic_dlsym(rt, "_ZN7android14AndroidRuntime8startRegEP7_JNIEnv");
    if (!start_reg) { fprintf(stderr, "[runtime] AndroidRuntime::startReg not found\n"); return -1; }

    /* intercept RegisterNatives for the duration of startReg (JVMTI's supported JNI function table swap) */
    jvmtiEnv *ti;
    if ((*vm)->GetEnv(vm, (void **)&ti, JVMTI_VERSION_1_2) != JNI_OK) { fprintf(stderr, "[runtime] no JVMTI\n"); return -1; }
    jniNativeInterface *orig, patched;
    (*ti)->GetJNIFunctionTable(ti, &orig);
    patched = *orig;
    real_table = orig;
    patched.RegisterNatives = renaming_register_natives;
    (*ti)->SetJNIFunctionTable(ti, &patched);
    int rc = start_reg(env);
    (*ti)->SetJNIFunctionTable(ti, orig);
    if (rc < 0) { fprintf(stderr, "[runtime] AndroidRuntime::startReg failed (%d)\n", rc); return -1; }
    fprintf(stderr, "[runtime] Android's framework natives registered (startReg=%d)\n", rc);
    return JNI_VERSION_1_6;
}
