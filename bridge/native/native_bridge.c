/* The native bridge route (docs/history/PLAN-2026-11-10.md "apps built for another CPU"): an app whose libraries exist only for another CPU (arm64 on x86_64) loads them
 * through Android's NativeBridge interface, as libnativeloader and ART do on a device with a native bridge. The bridge is Digitalis's
 * libberberis_arm64.so (an arm64 guest for AOSP's Berberis translator), an Android x86_64 library that bionic_translation loads like any
 * other; its guest world (linker64, libc, proxies) is under the Android root's system/{bin,lib64}/arm64. Java side: apklayer.host.NativeLoader. */
#define _GNU_SOURCE
#include <jni.h>
#include <dlfcn.h>
#include <errno.h>
#include "guest_environment.h"
#include <jvmti.h>
#include <pthread.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

extern void *bionic_dlopen(const char *filename, int flag);
extern void *bionic_dlsym(void *handle, const char *symbol);
extern char *bionic_dlerror(void);

/* art/libnativebridge/include/nativebridge/native_bridge.h (version 7 of the interface) */
struct NativeBridgeRuntimeCallbacks {
    const char *(*getMethodShorty)(JNIEnv *env, jmethodID mid);
    uint32_t (*getNativeMethodCount)(JNIEnv *env, jclass clazz);
    uint32_t (*getNativeMethods)(JNIEnv *env, jclass clazz, JNINativeMethod *methods, uint32_t method_count);
};
struct native_bridge_namespace_t;
struct NativeBridgeCallbacks {
    uint32_t version;
    bool (*initialize)(const struct NativeBridgeRuntimeCallbacks *runtime_cbs, const char *private_dir, const char *instruction_set);
    void *(*loadLibrary)(const char *libpath, int flag);
    void *(*getTrampoline)(void *handle, const char *name, const char *shorty, uint32_t len);
    bool (*isSupported)(const char *libpath);
    const void *(*getAppEnv)(const char *instruction_set);
    bool (*isCompatibleWith)(uint32_t bridge_version);
    void *(*getSignalHandler)(int signal);
    int (*unloadLibrary)(void *handle);
    const char *(*getError)(void);
    bool (*isPathSupported)(const char *library_path);
    bool (*unused_initAnonymousNamespace)(const char *, const char *);
    struct native_bridge_namespace_t *(*createNamespace)(const char *name, const char *ld_library_path, const char *default_library_path,
                                                         uint64_t type, const char *permitted_when_isolated_path,
                                                         struct native_bridge_namespace_t *parent_ns);
    bool (*linkNamespaces)(struct native_bridge_namespace_t *from, struct native_bridge_namespace_t *to, const char *shared_libs_sonames);
    void *(*loadLibraryExt)(const char *libpath, int flag, struct native_bridge_namespace_t *ns);
    struct native_bridge_namespace_t *(*getVendorNamespace)(void);
    struct native_bridge_namespace_t *(*getExportedNamespace)(const char *name);
    void (*preZygoteFork)(void);
    void *(*getTrampolineWithJNICallType)(void *handle, const char *name, const char *shorty, uint32_t len, int jni_call_type);
    void *(*getTrampolineForFunctionPointer)(const void *method, const char *shorty, uint32_t len, int jni_call_type);
    bool (*isNativeBridgeFunctionPointer)(const void *method);
};
enum { kJNICallTypeRegular = 1, RTLD_NOW_BIONIC = 2 };
/* bionic/libc/platform/bionic/dlext_namespaces.h: an app's classloader namespace, isolated and the anonymous one (libnativeloader's first) */
static const uint64_t kAppNamespaceType = 1 | 0x10000000;

static struct NativeBridgeCallbacks *nb;
static struct native_bridge_namespace_t *app_ns;
static jvmtiEnv *ti;
void apklayer_jni_trace_install(JNIEnv *env, jvmtiEnv *jvmti);  /* jni_trace.c */

/* The translated libc owns its environment and errno; CriticalNative wrappers have ordinary C arguments. */
static char *(*guest_env_get)(const char *);
static int (*guest_env_set)(const char *, const char *, int);
static int (*guest_env_unset)(const char *);
static int (*guest_env_put)(char *);
static int (*guest_env_clear)(void);
static int *(*guest_env_errno)(void);

static int *environment_errno(void)
{
    int saved = errno;
    int *guest = guest_env_errno();
    if (guest) *guest = saved;
    else errno = EFAULT;
    return guest;
}
static char *environment_get(const char *name)
{
    int *error = environment_errno(); if (!error) return NULL;
    char *result = guest_env_get(name); errno = *error; return result;
}
static int environment_set(const char *name, const char *value, int overwrite)
{
    int *error = environment_errno(); if (!error) return -1;
    int result = guest_env_set(name, value, overwrite); errno = *error; return result;
}
static int environment_unset(const char *name)
{
    int *error = environment_errno(); if (!error) return -1;
    int result = guest_env_unset(name); errno = *error; return result;
}
static int environment_put(char *value)
{
    int *error = environment_errno(); if (!error) return -1;
    int result = guest_env_put(value); errno = *error; return result;
}
static int environment_clear(void)
{
    int *error = environment_errno(); if (!error) return -1;
    int result = guest_env_clear(); errno = *error; return result;
}
static bool install_guest_environment(void *bridge)
{
    void *(*symbol)(const char *) = bionic_dlsym(bridge, "apklayer_guest_environment_symbol");
    int (*install)(const struct libc_bio_environment_backend *) = dlsym(RTLD_DEFAULT, "libc_bio_set_environment_backend");
    if (!symbol || !install || !nb->getTrampolineForFunctionPointer || !nb->isNativeBridgeFunctionPointer) return false;
    struct { const char *name, *shorty; void **target; } methods[] = {
        {"getenv", "JJ", (void **)&guest_env_get}, {"setenv", "IJJI", (void **)&guest_env_set},
        {"unsetenv", "IJ", (void **)&guest_env_unset}, {"putenv", "IJ", (void **)&guest_env_put},
        {"clearenv", "I", (void **)&guest_env_clear}, {"__errno", "J", (void **)&guest_env_errno}
    };
    /* This provider serves the currently supported 64-bit ARM guest; raw pointers use the J ABI slot. */
    if (sizeof(void *) != 8) return false;
    for (size_t i = 0; i < sizeof methods / sizeof *methods; i++) {
        void *guest = symbol(methods[i].name);
        if (!guest || !nb->isNativeBridgeFunctionPointer(guest)) return false;
        *methods[i].target = nb->getTrampolineForFunctionPointer(guest, methods[i].shorty,
                (uint32_t)strlen(methods[i].shorty), 2 /* kJNICallTypeCriticalNative */);
        if (!*methods[i].target) return false;
    }
    char ***slot = symbol("environ");
    extern char **environ;
    if (!slot || slot == &environ) return false;
    const struct libc_bio_environment_backend provider = {
        environment_get, environment_set, environment_unset, environment_put, environment_clear, slot
    };
    return install(&provider) == 0;
}

/* A JNI signature's shorty (return type, then parameters; L for any reference), malloc'd */
static char *shorty_of(const char *sig)
{
    char *shorty = malloc(strlen(sig) + 2);
    if (!shorty || sig[0] != '(') { free(shorty); return NULL; }
    const char *p = sig + 1;
    size_t n = 1;
    for (; *p && *p != ')'; p++) {
        char c = *p;
        while (*p == '[') p++;
        if (*p == 'L') p = strchr(p, ';');
        shorty[n++] = c == '[' ? 'L' : *p == ';' ? 'L' : *p;
    }
    shorty[0] = p[1] == '[' || p[1] == 'L' ? 'L' : p[1];
    shorty[n] = 0;
    return shorty;
}

/* A method's shorty, from its JNI signature as JVMTI gives it: Berberis reads the arguments of a translated CallXxxMethod(env, obj, mid, ...)
 * by it. Shorties live as long as the process (one per method, cached). */
static const char *method_shorty(JNIEnv *env, jmethodID mid)
{
    (void)env;
    static pthread_mutex_t lock = PTHREAD_MUTEX_INITIALIZER;
    struct entry { jmethodID mid; char *shorty; struct entry *next; };
    static struct entry *buckets[4096];  /* asked on every translated Call*Method: a hash by the method id */
    size_t b = ((uintptr_t)mid >> 3) % 4096;
    pthread_mutex_lock(&lock);
    for (struct entry *e = buckets[b]; e; e = e->next)
        if (e->mid == mid) { pthread_mutex_unlock(&lock); return e->shorty; }
    char *name = NULL, *sig = NULL, *shorty = NULL;
    if (ti && (*ti)->GetMethodName(ti, mid, &name, &sig, NULL) == JVMTI_ERROR_NONE && (shorty = shorty_of(sig))) {
        struct entry *e = malloc(sizeof *e);
        if (!e) abort();
        *e = (struct entry){mid, shorty, buckets[b]};
        buckets[b] = e;
    }
    if (name) (*ti)->Deallocate(ti, (unsigned char *)name);
    if (sig) (*ti)->Deallocate(ti, (unsigned char *)sig);
    pthread_mutex_unlock(&lock);
    return shorty;
}

/* RegisterNatives as ART does it under a native bridge (art/runtime/jni/jni_internal.cc): a guest function pointer is replaced by its
 * host-callable trampoline. Translated JNI_OnLoads register their natives through the host's RegisterNatives (Berberis passes the guest's
 * methods on unchanged), so HotSpot's entry is wrapped for the process (JVMTI's JNI function table, as runtime_shim.c does). */
static jint (*host_register_natives)(JNIEnv *, jclass, const JNINativeMethod *, jint);
static jint bridge_register_natives(JNIEnv *env, jclass c, const JNINativeMethod *methods, jint n)
{
    JNINativeMethod *own = NULL;
    for (jint i = 0; nb && i < n; i++) {
        if (!methods[i].fnPtr || !nb->isNativeBridgeFunctionPointer(methods[i].fnPtr)) continue;
        if (!own) {
            if (!(own = malloc((size_t)n * sizeof *own))) return JNI_ERR;
            memcpy(own, methods, (size_t)n * sizeof *own);
        }
        char *shorty = shorty_of(methods[i].signature);  /* kept: the trampoline may refer to it */
        void *fn = shorty ? nb->getTrampolineForFunctionPointer(methods[i].fnPtr, shorty, (uint32_t)strlen(shorty), kJNICallTypeRegular) : NULL;
        if (fn) own[i].fnPtr = fn;
    }
    jint r = host_register_natives(env, c, own ? own : methods, n);
    free(own);
    return r;
}

static void wrap_register_natives(void)
{
    static jniNativeInterface table;
    const jniNativeInterface *current;
    if (!ti || host_register_natives || (*ti)->GetJNIFunctionTable(ti, (jniNativeInterface **)&current) != JVMTI_ERROR_NONE) return;
    table = *current;
    host_register_natives = table.RegisterNatives;
    table.RegisterNatives = bridge_register_natives;
    (*ti)->SetJNIFunctionTable(ti, &table);
    (*ti)->Deallocate(ti, (unsigned char *)current);
}

/* What ART lists of a class's native methods; Berberis asks only on paths apklayer does not take (preZygoteFork). */
static uint32_t native_method_count(JNIEnv *env, jclass c) { (void)env; (void)c; return 0; }
static uint32_t native_methods(JNIEnv *env, jclass c, JNINativeMethod *m, uint32_t n) { (void)env; (void)c; (void)m; (void)n; return 0; }
static const struct NativeBridgeRuntimeCallbacks runtime_cbs = {method_shorty, native_method_count, native_methods};

/* The public libraries the app's namespace sees in the guest's system namespace (Android's /system/etc/public.libraries.txt, read through the path layer). */
static char *public_libraries(void)
{
    static const char fallback[] = "libandroid.so:libaaudio.so:libamidi.so:libbinder_ndk.so:libc.so:libcamera2ndk.so:libdl.so:libEGL.so:libGLESv1_CM.so:"
        "libGLESv2.so:libGLESv3.so:libicu.so:libjnigraphics.so:liblog.so:libmediandk.so:libm.so:libnativehelper.so:libnativewindow.so:libneuralnetworks.so:"
        "libOpenMAXAL.so:libOpenSLES.so:libRS.so:libstdc++.so:libsync.so:libvulkan.so:libwebviewchromium_plat_support.so:libz.so";
    FILE *f = fopen("/system/etc/public.libraries.txt", "r");
    if (!f) return strdup(fallback);
    char line[256], *out = calloc(1, 1);
    size_t len = 0;
    while (out && fgets(line, sizeof line, f)) {
        char *name = line + strspn(line, " \t");
        name[strcspn(name, " \t\r\n#")] = 0;  /* "libfoo.so 64" and comments */
        if (!*name) continue;
        size_t n = strlen(name);
        if (!(out = realloc(out, len + n + 2))) break;
        if (len) out[len++] = ':';
        memcpy(out + len, name, n + 1);
        len += n;
    }
    fclose(f);
    return out ? out : strdup(fallback);
}

/* Loads the bridge and sets up the app's guest namespace (libnativeloader's classloader namespace: isolated, its own library dir,
 * linked to the guest system namespace for the public libraries). Returns NULL or an error message. */
JNIEXPORT jstring JNICALL Java_apklayer_host_NativeLoader_bridgeInit(JNIEnv *env, jclass c, jstring jbridge, jstring jisa, jstring jlibdir, jstring jcache)
{
    (void)c;
    char err[512] = "";
    const char *bridge = (*env)->GetStringUTFChars(env, jbridge, NULL), *isa = (*env)->GetStringUTFChars(env, jisa, NULL);
    const char *libdir = (*env)->GetStringUTFChars(env, jlibdir, NULL), *cache = (*env)->GetStringUTFChars(env, jcache, NULL);
    JavaVM *vm;
    if ((*env)->GetJavaVM(env, &vm) == JNI_OK) (*vm)->GetEnv(vm, (void **)&ti, JVMTI_VERSION_1_2);
    /* Berberis seeds a guest thread's bionic TLS from the host's thread slots; on glibc those are glibc's (bionic_translation): this flag
     * gives each guest thread its own records instead (Digitalis's config_globals.h; patches/aosp/frameworks/libs/binary_translation). */
    setenv("BERBERIS_FLAGS", "glibc-host-thread-id-handoff", 0);
    void *h = bionic_dlopen(bridge, RTLD_NOW_BIONIC);
    nb = h ? bionic_dlsym(h, "NativeBridgeItf") : NULL;
    if (!nb) snprintf(err, sizeof err, "%s: %s", bridge, h ? "no NativeBridgeItf" : bionic_dlerror());
    else if (!nb->initialize(&runtime_cbs, cache, isa)) snprintf(err, sizeof err, "%s: initialize(%s) failed", bridge, isa);
    else {
        wrap_register_natives();
        apklayer_jni_trace_install(env, ti);  /* APKLAYER_JNI_TRACE=1 only (jni_trace.c) */
        char *pub = public_libraries();
        struct native_bridge_namespace_t *system = nb->getExportedNamespace("system");
        app_ns = nb->createNamespace("classloader-namespace", NULL, libdir, kAppNamespaceType, libdir, system);
        if (!app_ns || !nb->linkNamespaces(app_ns, system, pub))
            snprintf(err, sizeof err, "%s: app namespace: %s", bridge, nb->getError() ? nb->getError() : "?");
        else if (!install_guest_environment(h))
            snprintf(err, sizeof err, "%s: guest environment authority unavailable", bridge);
        else if (!nb->loadLibraryExt("/apex/com.android.art/lib64/libart.so", RTLD_NOW_BIONIC, system))  /* libart.so's maps line and file in the guest (libart_symbols.c; best effort) */
            fprintf(stderr, "[runtime] guest libart.so did not load: %s\n", nb->getError() ? nb->getError() : "?");
        free(pub);
    }
    if (*err) nb = NULL;
    (*env)->ReleaseStringUTFChars(env, jbridge, bridge);
    (*env)->ReleaseStringUTFChars(env, jisa, isa);
    (*env)->ReleaseStringUTFChars(env, jlibdir, libdir);
    (*env)->ReleaseStringUTFChars(env, jcache, cache);
    return *err ? (*env)->NewStringUTF(env, err) : NULL;
}

JNIEXPORT jlong JNICALL Java_apklayer_host_NativeLoader_bridgeLoad(JNIEnv *env, jclass c, jstring path)
{
    (void)c;
    const char *p = (*env)->GetStringUTFChars(env, path, NULL);
    void *h = nb ? nb->loadLibraryExt(p, RTLD_NOW_BIONIC, app_ns) : NULL;
    (*env)->ReleaseStringUTFChars(env, path, p);
    return (jlong)(intptr_t)h;
}

JNIEXPORT jstring JNICALL Java_apklayer_host_NativeLoader_bridgeError(JNIEnv *env, jclass c)
{
    (void)c;
    const char *e = nb ? nb->getError() : "no native bridge";
    return e ? (*env)->NewStringUTF(env, e) : NULL;
}

/* A host-callable trampoline for a guest function: a JNI method by its shorty, or JNI_OnLoad (no shorty: Berberis wraps it as a known function). */
JNIEXPORT jlong JNICALL Java_apklayer_host_NativeLoader_bridgeSymbol(JNIEnv *env, jclass c, jlong handle, jstring name, jstring shorty)
{
    (void)c;
    const char *n = (*env)->GetStringUTFChars(env, name, NULL), *s = shorty ? (*env)->GetStringUTFChars(env, shorty, NULL) : NULL;
    void *fn = nb ? nb->getTrampolineWithJNICallType((void *)(intptr_t)handle, n, s, s ? (uint32_t)strlen(s) : 0, kJNICallTypeRegular) : NULL;
    (*env)->ReleaseStringUTFChars(env, name, n);
    if (s) (*env)->ReleaseStringUTFChars(env, shorty, s);
    return (jlong)(intptr_t)fn;
}
