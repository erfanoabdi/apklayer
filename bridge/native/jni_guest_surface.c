/* The JNI surface guest code sees, as ART's (docs/runtime-identity-matrix.md, JNI rows):
 * - the invocation API in libnativehelper (JNI_GetCreatedJavaVMs, JNI_CreateJavaVM, JNI_GetDefaultJavaVMInitArgs): on a device
 *   AndroidRuntime binds them to libart through JniInvocationInit; this runtime never starts a VM through libnativehelper, so its table
 *   stayed empty: JNI_GetCreatedJavaVMs reported no VM (Berberis' proxy CHECK-aborts on that) and JNI_CreateJavaVM aborted. The table
 *   (JniInvocationCreate's instance) gets ART's answers here: the JVM (the JavaVM apps get in JNI_OnLoad), JNI_ERR, JNI_ERR.
 * - GetVersion: JNI 1.6 (0x10006) for guest callers; host callers keep the JVM's.
 * - FindClass on a thread with no Java frame (a natively attached thread, outside JNI_OnLoad): ART uses the system class loader, which
 *   does not see the app's classes, so a class an app's loader defined is not found (ClassNotFoundException), for guest callers.
 * - AttachCurrentThread(AsDaemon) of a new thread from guest code names the native thread after its Java thread, as ART does.
 * Guest or host: jni_system_route.c's classification of the JNI call's return address. */
#define _GNU_SOURCE
#include <dlfcn.h>
#include <jni.h>
#include <jvmti.h>
#include <pthread.h>
#include <stdio.h>
#include <string.h>

extern void *bionic_dlopen(const char *filename, int flag);
extern void *bionic_dlsym(void *handle, const char *symbol);
int apklayer_jni_guest_caller(JNIEnv *env, const void *pc);  /* jni_system_route.c */
void apklayer_art_load(JavaVM *vm, void *jni_table, void *invoke_table);  /* jni_art.c */

static jniNativeInterface next;  /* the table under this layer */
static jvmtiEnv *ti;
static jclass app_loader, not_found, thread_class;
static jmethodID current_thread, thread_name;

/* --- libnativehelper's invocation table --- */

static JavaVM *the_vm;

static jint JNICALL created_vms(JavaVM **vms, jsize size, jsize *count)
{
    if (size <= 0) *count = 0;  /* art/runtime/jni/java_vm_ext.cc JNI_GetCreatedJavaVMs */
    else { *count = 1; vms[0] = the_vm; }
    return JNI_OK;
}

static jint JNICALL default_init_args(void *args)
{
    (void)args;
    return JNI_ERR;  /* ART: historically unsupported */
}

static jint JNICALL create_vm(JavaVM **vm, JNIEnv **env, void *args)
{
    (void)vm; (void)env;
    const JavaVMInitArgs *a = args;
    if (a && a->version != JNI_VERSION_1_2 && a->version != JNI_VERSION_1_4 && a->version != JNI_VERSION_1_6) return JNI_EVERSION;
    return JNI_ERR;  /* ART: Runtime::Create fails, a runtime exists */
}

/* libnativehelper's struct JniInvocationImpl (JniInvocation.c at patches/aosp/libnativehelper/REVISION) */
struct jni_invocation {
    const char *library_name;
    void *library;
    jint (*get_default_init_args)(void *);
    jint (*create_vm)(JavaVM **, JNIEnv **, void *);
    jint (*get_created_vms)(JavaVM **, jsize, jsize *);
};

static void bind_invocation(JavaVM *vm)
{
    the_vm = vm;
    void *helper = bionic_dlopen("libnativehelper.so", RTLD_NOW);
    struct jni_invocation *(*create)(void) = helper ? (struct jni_invocation *(*)(void))bionic_dlsym(helper, "JniInvocationCreate") : NULL;
    struct jni_invocation *impl = create ? create() : NULL;  /* NULL when a provider is bound already */
    if (!impl) { fprintf(stderr, "[runtime] libnativehelper's JNI invocation table is unavailable: JNI_GetCreatedJavaVMs reports no VM\n"); return; }
    impl->get_default_init_args = default_init_args;
    impl->create_vm = create_vm;
    impl->get_created_vms = created_vms;
    impl->library_name = "libart.so";
    impl->library = helper;  /* marks it bound (JniInvocationCreate then refuses a second instance) */
}

/* --- JNIEnv entries --- */

static jint JNICALL guest_version(JNIEnv *env)
{
    jint version = next.GetVersion(env);
    return apklayer_jni_guest_caller(env, __builtin_return_address(0)) ? JNI_VERSION_1_6 : version;
}

static int no_java_frame(void)
{
    jint frames = -1;
    return (*ti)->GetFrameCount(ti, NULL, &frames) == JVMTI_ERROR_NONE && frames == 0;
}

static int app_defined(JNIEnv *env, jclass c)
{
    jobject loader = NULL;
    int app = (*ti)->GetClassLoader(ti, c, &loader) == JVMTI_ERROR_NONE && loader && next.IsInstanceOf(env, loader, app_loader);
    if (loader) next.DeleteLocalRef(env, loader);
    return app;
}

static jclass JNICALL guest_find_class(JNIEnv *env, const char *name)
{
    const void *pc = __builtin_return_address(0);
    jclass c = next.FindClass(env, name);
    if (!c || !no_java_frame() || !apklayer_jni_guest_caller(env, pc) || !app_defined(env, c)) return c;
    next.DeleteLocalRef(env, c);
    char dotted[512];
    size_t i = 0;
    for (; name[i] && i < sizeof dotted - 1; i++) dotted[i] = name[i] == '/' ? '.' : name[i];
    dotted[i] = 0;
    static int logged;
    if (logged < 8) {
        logged++;
        fprintf(stderr, "[runtime] FindClass(%s) on a thread without Java frames: not found, as with Android's system class loader\n", name);
    }
    next.ThrowNew(env, not_found, dotted);
    return NULL;
}

/* --- JavaVM: AttachCurrentThread names the native thread --- */

static struct JNIInvokeInterface_ invoke;
static const struct JNIInvokeInterface_ *invoke_next;

/* art/libartbase/base/utils.cc SetThreadName: the last 15 characters of a long dotted name without '@', else the first 15 */
static void set_native_name(const char *name)
{
    size_t len = strlen(name);
    int dot = strchr(name, '.') != NULL, at = strchr(name, '@') != NULL;
    const char *s = (len < 15 || at || !dot) ? name : name + len - 15;
    char buf[16];
    snprintf(buf, sizeof buf, "%s", s);
    pthread_setname_np(pthread_self(), buf);
}

static void name_attached(JNIEnv *env)
{
    jobject self = next.CallStaticObjectMethod(env, thread_class, current_thread);
    jstring name = self ? (jstring)next.CallObjectMethod(env, self, thread_name) : NULL;
    const char *chars = name ? next.GetStringUTFChars(env, name, NULL) : NULL;
    if (chars) { set_native_name(chars); next.ReleaseStringUTFChars(env, name, chars); }
    if (next.ExceptionCheck(env)) next.ExceptionClear(env);
    if (name) next.DeleteLocalRef(env, name);
    if (self) next.DeleteLocalRef(env, self);
}

static int host_module(const void *pc)
{
    Dl_info info;
    return dladdr(pc, &info) != 0;  /* glibc-loaded: the JVM, this shim, host libraries */
}

extern void *apklayer_art_jni_table;  /* jni_art.c */

static jint attach(JavaVM *vm, void **penv, void *args, int daemon, const void *pc)
{
    void *existing = NULL;
    int fresh = invoke_next->GetEnv(vm, &existing, JNI_VERSION_1_6) == JNI_EDETACHED;
    jint r = daemon ? invoke_next->AttachCurrentThreadAsDaemon(vm, penv, args) : invoke_next->AttachCurrentThread(vm, penv, args);
    if (r == JNI_OK && fresh && apklayer_art_jni_table) *(void **)*(void **)penv = apklayer_art_jni_table;  /* a fresh JNIEnv: functions = libart.so's table */
    if (r == JNI_OK && fresh && !host_module(pc)) name_attached(*(JNIEnv **)penv);
    return r;
}
static jint JNICALL attach_thread(JavaVM *vm, void **penv, void *args) { return attach(vm, penv, args, 0, __builtin_return_address(0)); }
static jint JNICALL attach_daemon(JavaVM *vm, void **penv, void *args) { return attach(vm, penv, args, 1, __builtin_return_address(0)); }

int apklayer_jni_guest_surface(JNIEnv *env, JavaVM *vm, jvmtiEnv *jvmti, jniNativeInterface *table)
{
    ti = jvmti;
    next = *table;
    jclass loader = next.FindClass(env, "dalvik/system/BaseDexClassLoader");
    jclass cnf = next.FindClass(env, "java/lang/ClassNotFoundException");
    jclass thread = next.FindClass(env, "java/lang/Thread");
    if (!loader || !cnf || !thread) return -1;
    app_loader = next.NewGlobalRef(env, loader);
    not_found = next.NewGlobalRef(env, cnf);
    thread_class = next.NewGlobalRef(env, thread);
    current_thread = next.GetStaticMethodID(env, thread, "currentThread", "()Ljava/lang/Thread;");
    thread_name = next.GetMethodID(env, thread, "getName", "()Ljava/lang/String;");
    if (!current_thread || !thread_name) return -1;
    table->GetVersion = guest_version;
    table->FindClass = guest_find_class;

    invoke_next = *vm;
    invoke = *invoke_next;
    invoke.AttachCurrentThread = attach_thread;
    invoke.AttachCurrentThreadAsDaemon = attach_daemon;
    *vm = &invoke;  /* the JVM's one JavaVM (main_vm): every holder of the pointer gets these entries */

    bind_invocation(vm);
    apklayer_art_load(vm, table, &invoke);  /* last: the thunks wrap the entries above */
    return 0;
}
