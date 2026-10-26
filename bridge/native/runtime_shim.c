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
#include <signal.h>
#include <string.h>
#include <time.h>
#include <unistd.h>
#include <ucontext.h>

#include "register_table.h"

extern void dl_parse_library_path(const char *path, char *delim);
extern void *bionic_dlopen(const char *filename, int flag);
extern void *bionic_dlsym(void *handle, const char *symbol);
extern char *bionic_dlerror(void);
extern struct r_debug *_r_debug_ptr;

/* Debug aid (-Dapklayer.runtimetrace=true): on SIGABRT/SIGSEGV print the frame-pointer chain of the faulting thread and the
 * memory map lines of the Android libraries, so addresses can be resolved with readelf/nm (gdb cannot unwind libraries
 * that the bionic linker loaded). Android's arm64 code keeps frame pointers. */
static void crash_trace(int sig, siginfo_t *info, void *ctx)
{
    (void)info;
    ucontext_t *uc = ctx;
    uint64_t pc = uc->uc_mcontext.pc, *fp = (uint64_t *)uc->uc_mcontext.regs[29];
    fprintf(stderr, "[runtime-trace] signal %d pc=0x%llx\n", sig, (unsigned long long)pc);
    for (int i = 0; i < 32 && fp && ((uintptr_t)fp & 7) == 0; i++) {
        fprintf(stderr, "[runtime-trace] #%d lr=0x%llx\n", i, (unsigned long long)fp[1]);
        uint64_t *next = (uint64_t *)fp[0];
        if (next <= fp) break;
        fp = next;
    }
    FILE *maps = fopen("/proc/self/maps", "r");
    char line[512];
    while (maps && fgets(line, sizeof line, maps)) if (strstr(line, "android-arm64") || strstr(line, "android-libs")) fputs(line, stderr);
    fflush(stderr);
    signal(sig, SIG_DFL);
    raise(sig);
}

static jclass class_class;
/* Android's liblog writes to logd, which does not exist here; its logger hook (liblog's own API) sends warnings and worse
 * to stderr, everything with APKLAYER_RUNTIME_DEBUG, so a LOG_ALWAYS_FATAL message is not lost. */
struct android_log_message { size_t size; int32_t buffer_id; int32_t priority; const char *tag, *file; uint32_t line; const char *message; };
static void stderr_logger(const struct android_log_message *m)
{
    if (m->priority >= 5 || getenv("APKLAYER_RUNTIME_DEBUG")) fprintf(stderr, "[log %d] %s: %s\n", m->priority, m->tag, m->message);
    const char *dump = getenv("APKLAYER_DUMP_ON"); /* debug: the first log line containing this text prints the JVM's thread dump and exits */
    if (dump && strstr(m->message, dump)) {
        if (getenv("APKLAYER_DUMP_ABORT")) abort(); /* with -Dapklayer.runtimetrace=true: the native call chain instead */
        kill(getpid(), SIGQUIT);
        nanosleep(&(struct timespec){ 2, 0 }, NULL);
        _exit(3);
    }
}

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

/* ART calls @CriticalNative methods without JNIEnv and jclass; the device libraries are built that way (Robolectric's host
 * build adds both parameters back at compile time). The JVM passes them, so those natives are registered through a thunk
 * that shifts the integer arguments down two registers (floats travel in other registers; up to six integer arguments). */
#include <sys/mman.h>
static uint32_t *thunk_page; static size_t thunk_used;

static void *critical_thunk(void *target)
{
    static const uint32_t code[] = { 0xaa0203e0, 0xaa0303e1, 0xaa0403e2, 0xaa0503e3, 0xaa0603e4, 0xaa0703e5, 0x58000050, 0xd61f0200 };
    if (!thunk_page || thunk_used + 40 > 4096) {
        thunk_page = mmap(NULL, 4096, PROT_READ | PROT_WRITE | PROT_EXEC, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
        thunk_used = 0;
        if (thunk_page == MAP_FAILED) { thunk_page = NULL; return target; }
    }
    uint32_t *t = (uint32_t *)((char *)thunk_page + thunk_used);
    memcpy(t, code, sizeof code);
    memcpy(t + 8, &target, 8);
    __builtin___clear_cache((char *)t, (char *)t + 40);
    thunk_used += 40;
    return t;
}

static jmethodID critical_of; static jclass critical_class; static int critical_count;

/* the class's @CriticalNative methods ("name"+"signature"), from apklayer.host.CriticalNatives */
static jobjectArray critical_methods(JNIEnv *env, jclass cls)
{
    if (!critical_of) return NULL;
    jmethodID get_name = (*env)->GetMethodID(env, class_class, "getName", "()Ljava/lang/String;");
    jobject name = (*env)->CallObjectMethod(env, cls, get_name);
    jobjectArray r = (*env)->CallStaticObjectMethod(env, critical_class, critical_of, name);
    if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); r = NULL; }
    return r;
}

static int is_critical(JNIEnv *env, jobjectArray list, const JNINativeMethod *m)
{
    char key[512];
    snprintf(key, sizeof key, "%s%s", m->name, m->signature);
    for (jsize i = 0; list && i < (*env)->GetArrayLength(env, list); i++) {
        jstring e = (*env)->GetObjectArrayElement(env, list, i);
        const char *c = (*env)->GetStringUTFChars(env, e, NULL);
        int same = !strcmp(c, key);
        (*env)->ReleaseStringUTFChars(env, e, c);
        (*env)->DeleteLocalRef(env, e);
        if (same) return 1;
    }
    return 0;
}

static jint JNICALL renaming_register_natives(JNIEnv *env, jclass cls, const JNINativeMethod *methods, jint n)
{
    jobjectArray critical = critical_methods(env, cls);
    for (jint i = 0; i < n; i++) {
        char name[256];
        snprintf(name, sizeof name, "%s%s%s", binding_prefix, methods[i].name, binding_suffix);
        void *fn = methods[i].fnPtr;
        if (is_critical(env, critical, &methods[i])) { fn = critical_thunk(fn); critical_count++; if (getenv("APKLAYER_RUNTIME_DEBUG")) fprintf(stderr, "[runtime] critical %s\n", methods[i].name); }
        JNINativeMethod one = { name, methods[i].signature, fn };
        if (real_table->RegisterNatives(env, cls, &one, 1) != 0) (*env)->ExceptionClear(env); /* not a method Robolectric instruments */
    }
    return 0; /* startReg aborts the process on failure; skipped methods are expected */
}

/* libcore.util.NativeAllocationRegistry.applyFreeFunction: part of ART's libcore on a device, a host-runtime function in
 * Google's HostRuntime.cpp; neither exists here, so it is provided (it just calls the free function). */
static void JNICALL apply_free_function(JNIEnv *env, jclass cls, jlong free_function, jlong ptr)
{
    (void)env; (void)cls;
    ((void (*)(void *))(uintptr_t)free_function)((void *)(uintptr_t)ptr);
}

/* HotSpot's FindClass runs the class's static initializer; ART's does not. Android registers a class's natives right after
 * finding it, so a <clinit> that calls a not-yet-registered native (com.google.android.gles_jni.EGLImpl) would fail.
 * This FindClass loads without initializing (Class.forName(name, false, loader)); initialization then happens on first use. */
static jobject class_loader;
static jmethodID for_name;

static jclass JNICALL lazy_find_class(JNIEnv *env, const char *name)
{
    char dotted[512];
    size_t i = 0;
    for (; name[i] && i < sizeof dotted - 1; i++) dotted[i] = name[i] == '/' ? '.' : name[i];
    dotted[i] = 0;
    jstring s = (*env)->NewStringUTF(env, dotted);
    jobject c = (*env)->CallStaticObjectMethod(env, class_class, for_name, s, JNI_FALSE, class_loader);
    (*env)->DeleteLocalRef(env, s);
    return (jclass)c; /* NULL with the pending exception, like FindClass */
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

/* The registration functions are C++: some in the global namespace, some in android::. Tries both manglings in both libraries. */
static void *find_register_function(void *rt, void *hwui, const char *name)
{
    char global[256], in_android[256];
    snprintf(global, sizeof global, "_Z%zu%sP7_JNIEnv", strlen(name), name);
    snprintf(in_android, sizeof in_android, "_ZN7android%zu%sEP7_JNIEnv", strlen(name), name);
    void *libs[2] = { rt, hwui };
    for (int i = 0; i < 2; i++) {
        if (!libs[i]) continue;
        void *fn = bionic_dlsym(libs[i], global);
        if (!fn) fn = bionic_dlsym(libs[i], in_android);
        if (fn) return fn;
    }
    return NULL;
}

/* Registers what Google's HostRuntime would: the classes named by core_native_classes (one function each), the
 * libcore NativeAllocationRegistry native, then HWUI's graphics classes. The device libraries have the functions as
 * global C++ symbols. Returns the number of registrations that could not be run. */
static int register_classes(JNIEnv *env, JavaVM *vm, void *rt)
{
    int failed = 0;
    void **java_vm_slot = bionic_dlsym(rt, "_ZN7android14AndroidRuntime8mJavaVME"); /* AndroidRuntime::mJavaVM */
    if (java_vm_slot) *java_vm_slot = vm;
    void *hwui = bionic_dlopen("libhwui.so", 2);

    char *classes = system_property(env, "core_native_classes");
    for (char *save, *c = classes ? strtok_r(classes, ",", &save) : NULL; c; c = strtok_r(NULL, ",", &save)) {
        if (!strcmp(c, "libcore.util.NativeAllocationRegistry")) continue; /* registered below */
        const char *name = NULL;
        for (size_t i = 0; i < sizeof register_table / sizeof *register_table; i++)
            if (!strcmp(register_table[i].java_class, c)) name = register_table[i].symbol;
        int (*fn)(JNIEnv *) = name ? find_register_function(rt, hwui, name) : NULL;
        if (!fn) { fprintf(stderr, "[runtime] no registration function for %s\n", c); failed++; continue; }
        if (fn(env) < 0) { fprintf(stderr, "[runtime] registering %s failed\n", c); failed++; }
    }

    JNINativeMethod registry = { "applyFreeFunction", "(JJ)V", (void *)apply_free_function };
    jclass nar = (*env)->FindClass(env, "libcore/util/NativeAllocationRegistry");
    if (nar) (*env)->GetMethodID(env, nar, "<init>", "()V"), (*env)->ExceptionClear(env), (*env)->RegisterNatives(env, nar, &registry, 1);
    else { (*env)->ExceptionClear(env); failed++; }

    /* declared ANDROID_API in jni_runtime.h: extern "C" (unmangled) or not, depending on the build */
    void (*init_graphics)(void) = hwui ? bionic_dlsym(hwui, "init_android_graphics") : NULL;
    if (hwui && !init_graphics) init_graphics = bionic_dlsym(hwui, "_Z20init_android_graphicsv");
    int (*register_graphics)(JNIEnv *) = hwui ? bionic_dlsym(hwui, "register_android_graphics_classes") : NULL;
    if (hwui && !register_graphics) register_graphics = bionic_dlsym(hwui, "_Z33register_android_graphics_classesP7_JNIEnv");
    if (init_graphics) init_graphics();
    if (!register_graphics || register_graphics(env) < 0) { fprintf(stderr, "[runtime] graphics registration failed\n"); failed++; }
    return failed;
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved)
{
    (void)reserved;
    JNIEnv *env;
    if ((*vm)->GetEnv(vm, (void **)&env, JNI_VERSION_1_6) != JNI_OK) return -1;

    /* make this library's symbols (android_platform.c, the generated stubs) visible to the bionic linker's fallback */
    Dl_info self;
    if (dladdr((void *)JNI_OnLoad, &self)) dlopen(self.dli_fname, RTLD_NOW | RTLD_GLOBAL);

    if (system_property(env, "apklayer.runtimetrace")) {
        struct sigaction sa = { .sa_sigaction = crash_trace, .sa_flags = SA_SIGINFO | SA_RESETHAND };
        sigaction(SIGABRT, &sa, NULL);
    }
    char *libs = system_property(env, "apklayer.androidlibs"), *format = system_property(env, "method_binding_format");
    if (!libs) { fprintf(stderr, "[runtime] -Dapklayer.androidlibs is not set\n"); return -1; }
    parse_binding_format(format ? format : "$$robo$$${method}$nativeBinding");

    char *probe = system_property(env, "apklayer.runtimeprobe"); /* debug: -Dapklayer.runtimeprobe=a/b/C loads that class and prints the exception */
    if (probe) {
        jclass c = (*env)->FindClass(env, probe);
        fprintf(stderr, "[runtime] probe %s: %s\n", probe, c ? "found" : "NOT FOUND");
        if (!c) { (*env)->ExceptionDescribe(env); (*env)->ExceptionClear(env); }
    }

    _r_debug_ptr = &_r_debug;
    dl_parse_library_path(libs, ":");
    void *rt = bionic_dlopen("libandroid_runtime.so", 2 /* RTLD_NOW */);
    if (!rt) { fprintf(stderr, "[runtime] cannot load libandroid_runtime.so: %s\n", bionic_dlerror()); return -1; }
    void *liblog = bionic_dlopen("liblog.so", 2);
    void (*set_logger)(void (*)(const struct android_log_message *)) = liblog ? bionic_dlsym(liblog, "__android_log_set_logger") : NULL;
    if (set_logger) set_logger(stderr_logger);
    /* intercept RegisterNatives for the duration of startReg (JVMTI's supported JNI function table swap) */
    jvmtiEnv *ti;
    if ((*vm)->GetEnv(vm, (void **)&ti, JVMTI_VERSION_1_2) != JNI_OK) { fprintf(stderr, "[runtime] no JVMTI\n"); return -1; }
    jniNativeInterface *orig, patched;
    (*ti)->GetJNIFunctionTable(ti, &orig);
    patched = *orig;
    real_table = orig;
    patched.RegisterNatives = renaming_register_natives;
    {   /* the loader the sandbox's classes come from: the thread's context class loader */
        jclass thread = (*env)->FindClass(env, "java/lang/Thread");
        jobject cur = (*env)->CallStaticObjectMethod(env, thread, (*env)->GetStaticMethodID(env, thread, "currentThread", "()Ljava/lang/Thread;"));
        jobject loader = (*env)->CallObjectMethod(env, cur, (*env)->GetMethodID(env, thread, "getContextClassLoader", "()Ljava/lang/ClassLoader;"));
        class_loader = (*env)->NewGlobalRef(env, loader);
        class_class = (*env)->NewGlobalRef(env, (*env)->FindClass(env, "java/lang/Class"));
        for_name = (*env)->GetStaticMethodID(env, class_class, "forName", "(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;");
        if (class_loader && for_name) patched.FindClass = lazy_find_class;
        jstring cn = (*env)->NewStringUTF(env, "apklayer.host.CriticalNatives");
        jobject cc = (*env)->CallStaticObjectMethod(env, class_class, for_name, cn, JNI_TRUE, class_loader);
        if (cc) {
            critical_class = (*env)->NewGlobalRef(env, cc);
            critical_of = (*env)->GetStaticMethodID(env, critical_class, "of", "(Ljava/lang/String;)[Ljava/lang/String;");
        }
        (*env)->ExceptionClear(env);
    }
    (*ti)->SetJNIFunctionTable(ti, &patched);
    int failed = register_classes(env, vm, rt);
    (*ti)->SetJNIFunctionTable(ti, orig);
    if (failed) fprintf(stderr, "[runtime] %d registrations did not run (see above)\n", failed);
    fprintf(stderr, "[runtime] Android's framework natives registered (%d critical natives adapted%s)\n", critical_count, critical_of ? "" : "; CriticalNatives unavailable");
    return JNI_VERSION_1_6;
}
