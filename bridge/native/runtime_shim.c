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
#include <pthread.h>
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
void apklayer_log_to_stderr(void), apklayer_crash_trace(void); /* android_platform.c */

static int runtime_debug(void)
{
    const char *value = getenv("APKLAYER_RUNTIME_DEBUG");
    return value && !strcmp(value, "1");
}

static jclass class_class;
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
 * build adds both parameters back at compile time). The JVM passes them, so those natives are registered through a thunk that
 * shifts the integer arguments down two registers, pulls the 7th and 8th (on the stack in the JVM's call, in x6 and x7 for the
 * callee) from the stack and moves the stack arguments (the 9th on, up to the 12th) down two slots. Floats travel in their own
 * registers and need no change. The thunk template is copied per target; its last word holds the target's address. */
#include <sys/mman.h>
__asm__(
    ".text\n.balign 4\n.globl apklayer_critical_tpl_start\n.globl apklayer_critical_tpl_end\n"
    "apklayer_critical_tpl_start:\n"
    "  stp x29, x30, [sp, #-16]!\n"
    "  mov x29, sp\n"
    "  sub sp, sp, #32\n"
    "  ldp x9, x10, [x29, #32]\n"   /* a8, a9 */
    "  stp x9, x10, [sp, #0]\n"
    "  ldp x9, x10, [x29, #48]\n"   /* a10, a11 */
    "  stp x9, x10, [sp, #16]\n"
    "  mov x0, x2\n  mov x1, x3\n  mov x2, x4\n  mov x3, x5\n  mov x4, x6\n  mov x5, x7\n"
    "  ldp x6, x7, [x29, #16]\n"    /* a6, a7 */
    "  ldr x16, 1f\n"
    "  blr x16\n"
    "  mov sp, x29\n"
    "  ldp x29, x30, [sp], #16\n"
    "  ret\n"
    ".balign 8\n1: .quad 0\n"
    "apklayer_critical_tpl_end:\n");
extern char apklayer_critical_tpl_start[], apklayer_critical_tpl_end[];
static char *thunk_page; static size_t thunk_used;

static void *critical_thunk(void *target)
{
    size_t size = (size_t)(apklayer_critical_tpl_end - apklayer_critical_tpl_start);
    if (!thunk_page || thunk_used + size > 4096) {
        thunk_page = mmap(NULL, 4096, PROT_READ | PROT_WRITE | PROT_EXEC, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
        thunk_used = 0;
        if (thunk_page == MAP_FAILED) { thunk_page = NULL; return target; }
    }
    char *t = thunk_page + thunk_used;
    memcpy(t, apklayer_critical_tpl_start, size);
    memcpy(t + size - 8, &target, 8);
    __builtin___clear_cache(t, t + size);
    thunk_used += (size + 15) & ~(size_t)15;
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

static int early_audio_capabilities;

static jint JNICALL renaming_register_natives(JNIEnv *env, jclass cls, const JNINativeMethod *methods, jint n)
{
    jobjectArray critical = critical_methods(env, cls);
    for (jint i = 0; i < n; i++) {
        char name[256];
        snprintf(name, sizeof name, "%s%s%s", binding_prefix, methods[i].name, binding_suffix);
        void *fn = methods[i].fnPtr;
        if (is_critical(env, critical, &methods[i])) { fn = critical_thunk(fn); critical_count++; if (runtime_debug()) fprintf(stderr, "[runtime] critical %s\n", methods[i].name); }
        JNINativeMethod one = { name, methods[i].signature, fn };
        if (runtime_debug() && !strcmp(methods[i].name, "native_setup")) fprintf(stderr, "[runtime] registering %s %s\n", name, methods[i].signature);
        if (real_table->RegisterNatives(env, cls, &one, 1) != 0) {
            if (runtime_debug()) fprintf(stderr, "[runtime] not registered (no such native binding): %s %s\n", name, methods[i].signature);
            (*env)->ExceptionClear(env); /* not a method Robolectric instruments... */
            JNINativeMethod plain = { methods[i].name, methods[i].signature, methods[i].fnPtr };
            if (real_table->RegisterNatives(env, cls, &plain, 1) != 0) (*env)->ExceptionClear(env); /* ...but an uninstrumented class (an app library's, a host helper) has it under its own name */
        }
    }
    /* AudioSystem registers these three capabilities first specifically so its
     * initializer runs before AudioFormat caches their values. Finish the saved
     * Java initializer here instead of deferring it past dependent JNI caches. */
    if (early_audio_capabilities) {
        for (jint i = 0; i < n; i++) if (!strcmp(methods[i].name, "native_getMaxChannelCount")) {
            early_audio_capabilities = 0;
            jmethodID init = real_table->GetStaticMethodID(env, cls, "__staticInitializer__", "()V");
            if (!init) return -1;
            real_table->CallStaticVoidMethod(env, cls, init);
            if ((*env)->ExceptionCheck(env)) return -1;
            break;
        }
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
    if (name[0] == '[') { /* an array class: the sandbox's class loader cannot load array names; find the element class (by this FindClass) and take the class of a zero-length array */
        int dims = 0;
        while (name[dims] == '[') dims++;
        const char *element = name + dims;
        jclass elementClass;
        if (element[0] == 'L') {
            char inner[512];
            size_t length = strlen(element);
            if (length < 3 || length - 2 >= sizeof inner) return NULL;
            memcpy(inner, element + 1, length - 2); inner[length - 2] = 0;
            elementClass = lazy_find_class(env, inner);
        } else {
            return real_table->FindClass(env, name); /* primitives: the JVM's own */
        }
        if (!elementClass) return NULL;
        jclass array = (*env)->FindClass(env, "java/lang/reflect/Array");
        jmethodID make = (*env)->GetStaticMethodID(env, array, "newInstance", "(Ljava/lang/Class;[I)Ljava/lang/Object;");
        jintArray lengths = (*env)->NewIntArray(env, dims);
        jobject instance = (*env)->CallStaticObjectMethod(env, array, make, elementClass, lengths);
        (*env)->DeleteLocalRef(env, lengths); (*env)->DeleteLocalRef(env, elementClass);
        if (!instance) return NULL;
        jclass result = (*env)->GetObjectClass(env, instance);
        (*env)->DeleteLocalRef(env, instance);
        return result;
    }
    char dotted[512];
    size_t i = 0;
    for (; name[i] && i < sizeof dotted - 1; i++) dotted[i] = name[i] == '/' ? '.' : name[i];
    dotted[i] = 0;
    jstring s = (*env)->NewStringUTF(env, dotted);
    jobject c = (*env)->CallStaticObjectMethod(env, class_class, for_name, s, JNI_FALSE, class_loader);
    (*env)->DeleteLocalRef(env, s);
    if (!c && runtime_debug()) fprintf(stderr, "[runtime] FindClass(%s) failed\n", name);
    return (jclass)c; /* NULL with the pending exception, like FindClass */
}

/* Diagnostic (APKLAYER_JNI_EXC=1): a Java exception left pending by a JNI call from native code is printed with its stack. Gecko aborts with "MOZ_CRASH(JNI exception)" and no
 * message when a call it makes into Java throws; this shows what was thrown (the call is then left pending, as before). */
static void trace_pending(JNIEnv *env, const char *where)
{
    jthrowable ex = real_table->ExceptionOccurred(env);
    if (!ex) return;
    real_table->ExceptionClear(env);
    jclass tc = real_table->FindClass(env, "java/lang/Throwable");
    jmethodID pst = real_table->GetMethodID(env, tc, "printStackTrace", "()V");
    fprintf(stderr, "[jni-exc] pending after %s:\n", where);
    if (pst) real_table->CallVoidMethod(env, ex, pst);
    real_table->ExceptionClear(env);
    real_table->Throw(env, ex);
}
static void JNICALL w_CallVoidMethodA(JNIEnv *env, jobject o, jmethodID m, const jvalue *a) { real_table->CallVoidMethodA(env, o, m, a); trace_pending(env, "CallVoidMethodA"); }
static void JNICALL w_CallStaticVoidMethodA(JNIEnv *env, jclass c, jmethodID m, const jvalue *a) { real_table->CallStaticVoidMethodA(env, c, m, a); trace_pending(env, "CallStaticVoidMethodA"); }
static jobject JNICALL w_CallObjectMethodA(JNIEnv *env, jobject o, jmethodID m, const jvalue *a) { jobject r = real_table->CallObjectMethodA(env, o, m, a); trace_pending(env, "CallObjectMethodA"); return r; }
static jobject JNICALL w_CallStaticObjectMethodA(JNIEnv *env, jclass c, jmethodID m, const jvalue *a) { jobject r = real_table->CallStaticObjectMethodA(env, c, m, a); trace_pending(env, "CallStaticObjectMethodA"); return r; }

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
    void **java_vm_slot = bionic_dlsym(rt, "_ZN7android14AndroidRuntime7mJavaVME"); /* AndroidRuntime::mJavaVM */
    if (!java_vm_slot) {
        fprintf(stderr, "[runtime] AndroidRuntime::mJavaVM is unavailable\n");
        return -1;
    }
    *java_vm_slot = vm;
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
        else if (runtime_debug()) fprintf(stderr, "[runtime] registered %s\n", c);
    }

    /* apklayer patch: device GL producers are absent from HostRuntime's core list. */
    static const char *const device_gl[] = {
        "register_android_graphics_SurfaceTexture", "register_android_view_TextureView",
        "register_com_google_android_gles_jni_GLImpl", "register_com_google_android_gles_jni_EGLImpl",
        "register_android_opengl_jni_GLES20", "register_android_opengl_classes", NULL
    };
    for (int i = 0; device_gl[i]; i++) {
        int (*fn)(JNIEnv *) = find_register_function(rt, hwui, device_gl[i]);
        if (!fn || fn(env) < 0 || (*env)->ExceptionCheck(env)) {
            fprintf(stderr, "[runtime] registering %s failed\n", device_gl[i]); failed++;
            if ((*env)->ExceptionCheck(env)) return failed;
        }
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

/* Threads that libutils/libbinder create (the binder pool, threads calling back into Java) must be attached to the JVM, as
 * AndroidRuntime::startReg arranges on a device; this runtime registers natives itself and never runs startReg, and the threads
 * start while libandroid_runtime loads, so the hook is installed before that. */
static JavaVM *g_vm;
struct thread_start { int (*entry)(void *); void *user; char name[32]; };
static void *attached_thread(void *arg)
{
    struct thread_start ts = *(struct thread_start *)arg;
    free(arg);
    JNIEnv *env = NULL;
    JavaVMAttachArgs args = { JNI_VERSION_1_4, ts.name, NULL };
    (*g_vm)->AttachCurrentThreadAsDaemon(g_vm, (void **)&env, &args);
    ts.entry(ts.user);
    (*g_vm)->DetachCurrentThread(g_vm);
    return NULL;
}
static int create_attached(int (*entry)(void *), void *user, const char *name, int prio, size_t stack, void **id)
{
    (void)prio;
    struct thread_start *ts = calloc(1, sizeof *ts);
    ts->entry = entry;
    ts->user = user;
    snprintf(ts->name, sizeof ts->name, "%s", name ? name : "native");
    pthread_attr_t attr;
    pthread_attr_init(&attr);
    pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_DETACHED);
    if (stack) pthread_attr_setstacksize(&attr, stack);
    pthread_t t;
    int r = pthread_create(&t, &attr, attached_thread, ts);
    pthread_attr_destroy(&attr);
    if (r != 0) { free(ts); return 0; }
    if (id) *id = (void *)(uintptr_t)t;
    return 1;
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved)
{
    (void)reserved;
    JNIEnv *env;
    if ((*vm)->GetEnv(vm, (void **)&env, JNI_VERSION_1_6) != JNI_OK) return -1;

    /* make this library's symbols (android_platform.c, the generated stubs) visible to the bionic linker's fallback */
    Dl_info self;
    if (dladdr((void *)JNI_OnLoad, &self)) dlopen(self.dli_fname, RTLD_NOW | RTLD_GLOBAL);

    /* host libraries the Android code calls into (APKLAYER_HOST_LIBS, colon separated: the graphics HAL), global like btload does */
    if (getenv("APKLAYER_HOST_LIBS")) {
        char *hl = strdup(getenv("APKLAYER_HOST_LIBS"));
        for (char *save, *l = strtok_r(hl, ":", &save); l; l = strtok_r(NULL, ":", &save))
            if (!dlopen(l, RTLD_NOW | RTLD_GLOBAL)) fprintf(stderr, "[runtime] %s\n", dlerror());
    }

    if (system_property(env, "apklayer.runtimetrace")) {
        apklayer_crash_trace();
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
    g_vm = vm;
    {
        void *utils = bionic_dlopen("libutils.so", 2);
        void (*set_create)(int (*)(int (*)(void *), void *, const char *, int, size_t, void **)) = utils ? bionic_dlsym(utils, "androidSetCreateThreadFunc") : NULL;
        if (set_create) set_create(create_attached);
        else fprintf(stderr, "[runtime] cannot hook thread creation: no androidSetCreateThreadFunc\n");
    }
    void *rt = bionic_dlopen("libandroid_runtime.so", 2 /* RTLD_NOW */);
    if (!rt) { fprintf(stderr, "[runtime] cannot load libandroid_runtime.so: %s\n", bionic_dlerror()); return -1; }
    { extern void apklayer_install_path_layer(void); apklayer_install_path_layer(); } /* libc_bio is loaded now: Android paths (/system, /vendor...) as btload does */
    apklayer_log_to_stderr();
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
    if (getenv("APKLAYER_JNI_EXC")) {
        patched.CallVoidMethodA = w_CallVoidMethodA; patched.CallStaticVoidMethodA = w_CallStaticVoidMethodA;
        patched.CallObjectMethodA = w_CallObjectMethodA; patched.CallStaticObjectMethodA = w_CallStaticObjectMethodA;
    }
    (*ti)->SetJNIFunctionTable(ti, &patched);
    int failed = register_classes(env, vm, rt);
    /* the native audio, binder, media and attribution registrations are always on: apps run on Android's own services */
    int audio_failed = 0;
    {
        extern int apklayer_register_audio(JNIEnv *, void *);
        early_audio_capabilities = 1;
        audio_failed = apklayer_register_audio(env, rt);
        early_audio_capabilities = 0;
    }
    int binder_failed = 0;
    {
        extern int apklayer_register_binder(JNIEnv *, void *);
        binder_failed = apklayer_register_binder(env, rt);
    }
    if (binder_failed) audio_failed = 1;
    if (audio_failed) { (*ti)->SetJNIFunctionTable(ti, orig); return -1; }
    int media_failed = audio_failed;
    {
        extern int apklayer_register_media(JNIEnv *, JavaVM *);
        media_failed = apklayer_register_media(env, vm);
    }

    /* the renaming table stays: an app library's JNI_OnLoad (the WebView provider's) registers natives of Robolectric-instrumented classes later */
    if (media_failed) { (*ti)->SetJNIFunctionTable(ti, orig); return -1; }
    int attribution_failed = 0;
    {
        extern int apklayer_register_attribution(JNIEnv *, jclass);
        jclass helper = lazy_find_class(env, "apklayer/host/NativeAttribution");
        attribution_failed = !helper || apklayer_register_attribution(env, helper);
        if (helper) (*env)->DeleteLocalRef(env, helper);
    }
    if (!attribution_failed) {
        extern int apklayer_register_binderhelper(JNIEnv *, jclass);
        jclass helper = lazy_find_class(env, "apklayer/host/RealBinder");
        attribution_failed = !helper || apklayer_register_binderhelper(env, helper);
        if (helper) (*env)->DeleteLocalRef(env, helper);
    }
    if (!attribution_failed) {
        extern int apklayer_register_nativeloader(JNIEnv *, jclass);
        jclass loader = lazy_find_class(env, "apklayer/host/NativeLoader");
        attribution_failed = !loader || apklayer_register_nativeloader(env, loader);
        /* Classes the sandbox does not acquire (org.conscrypt: an app's own Conscrypt) see the JVM's own copy of NativeLoader, a different class: its natives too */
        jclass system = NULL;
        if (!attribution_failed) {
            jclass cl = (*env)->FindClass(env, "java/lang/ClassLoader");
            jmethodID getter = (*env)->GetStaticMethodID(env, cl, "getSystemClassLoader", "()Ljava/lang/ClassLoader;");
            jobject app = getter ? (*env)->CallStaticObjectMethod(env, cl, getter) : NULL;
            jstring dotted = (*env)->NewStringUTF(env, "apklayer.host.NativeLoader");
            system = app ? (jclass)(*env)->CallStaticObjectMethod(env, class_class, for_name, dotted, JNI_FALSE, app) : NULL;
            if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
            if (system && !(*env)->IsSameObject(env, system, loader)) attribution_failed = apklayer_register_nativeloader(env, system);
        }
        if (system) (*env)->DeleteLocalRef(env, system);
        if (loader) (*env)->DeleteLocalRef(env, loader);
    }
    if (!attribution_failed) {
        extern int apklayer_register_nativelooper(JNIEnv *, jclass);
        jclass looper = lazy_find_class(env, "apklayer/host/NativeLooper");
        attribution_failed = !looper || apklayer_register_nativelooper(env, looper);
        if (looper) (*env)->DeleteLocalRef(env, looper);
    }
    if (!attribution_failed) { /* libcore's descriptor natives, for Parcels that carry file descriptors (Chromium's renderer gets its IPC channel and ICU data that way) */
        extern int apklayer_register_linux(JNIEnv *, jclass);
        jclass libcore_linux = lazy_find_class(env, "libcore/io/Linux");
        if (libcore_linux) { if (apklayer_register_linux(env, libcore_linux) != 0) (*env)->ExceptionClear(env); (*env)->DeleteLocalRef(env, libcore_linux); }
        else (*env)->ExceptionClear(env);
    }
    if (attribution_failed) media_failed = 1;
    if (media_failed) return -1;
    if (failed) fprintf(stderr, "[runtime] %d registrations did not run (see above)\n", failed);
    fprintf(stderr, "[runtime] Android's framework natives registered (%d critical natives adapted%s)\n", critical_count, critical_of ? "" : "; CriticalNatives unavailable");
    return JNI_VERSION_1_6;
}
