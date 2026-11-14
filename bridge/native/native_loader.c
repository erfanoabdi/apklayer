/* apklayer native loader (PLAN archive P4): loads an app's bionic-linked .so through bionic_translation's
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
#include <fcntl.h>
#include <pthread.h>
#include <signal.h>
#include <time.h>
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
 * shortcut: the shim linker keeps 32 directories (LDPATH_MAX) of 4096 bytes in all (bionic_translation 0051), the rest is dropped; raise those when an app needs more. */
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

/* A forked child (an app's createSubprocess: fork, a failed exec, exit(-1)) inherits the exit handlers of threads that do not exist in it, and libgallium's
 * joins one: exit() hung. Registered in the child at fork, the handler above runs first there and ends it at once (apklayer patch, docs/PATCHES.md). */
static void forked_child_ends_without_native_destructors(void) { on_exit(end_without_native_destructors, NULL); }
__attribute__((constructor)) static void register_forked_child_end(void) { pthread_atfork(NULL, NULL, forked_child_ends_without_native_destructors); }

/* The JVM's end began (a shutdown hook): a process still there `arg` seconds later is killed, as init and ActivityManager kill what SIGTERM did not end.
 * A thread of its own, not attached to the JVM: it runs while a safepoint or a stuck hook holds every Java thread (an app JVM once outlived SIGTERM for 9 min). */
static void *exit_deadline(void *arg)
{
    struct timespec left = { (time_t)(intptr_t)arg, 0 };
    while (nanosleep(&left, &left) != 0) {}
    static const char msg[] = "[apklayer] the JVM had not ended by its exit deadline: killed\n";
    (void)!write(2, msg, sizeof msg - 1);
    kill(getpid(), SIGKILL);
    return NULL;
}

static void start_thread(void *(*fn)(void *), int seconds)
{
    pthread_t thread;
    pthread_attr_t attr;
    pthread_attr_init(&attr);
    pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_DETACHED);
    pthread_attr_setstacksize(&attr, 64 * 1024);
    pthread_create(&thread, &attr, fn, (void *)(intptr_t)seconds);
    pthread_attr_destroy(&attr);
}

JNIEXPORT void JNICALL Java_apklayer_host_NativeLoader_exitDeadline(JNIEnv *env, jclass c, jint seconds)
{
    (void)env;
    (void)c;
    start_thread(exit_deadline, seconds);
}

/* SIGTERM's arrival starts the exit deadline itself, before any Java code runs: HotSpot's handler only wakes its signal dispatcher, which runs the
 * Java handler that begins the end; a safepoint (a CDS dump defers it by seconds) or a lock that Shutdown.exit waits for before any hook kept that
 * end from starting, so the hook's deadline never came (app JVMs outlived the corpus runner's SIGTERM for 9 and 20 min). */
static struct sigaction term_chained;  /* HotSpot's own (UserHandler) */
static int term_pipe[2] = {-1, -1};

static void on_term(int sig, siginfo_t *info, void *context)
{
    char one = 1;
    (void)!write(term_pipe[1], &one, 1);
    if (term_chained.sa_flags & SA_SIGINFO) term_chained.sa_sigaction(sig, info, context);
    else if (term_chained.sa_handler != SIG_IGN && term_chained.sa_handler != SIG_DFL) term_chained.sa_handler(sig);
    else { signal(sig, SIG_DFL); raise(sig); }  /* no JVM handler (-Xrs): the default end, once this handler returns */
}

static void *term_deadline(void *arg)
{
    char one;
    while (read(term_pipe[0], &one, 1) != 1) {}
    return exit_deadline(arg);
}

/* Once per process (the native runtime's load, runtime_shim.c). A component host or the hub that takes SIGTERM itself (sun.misc.Signal) replaces it. */
void apklayer_exit_deadline_on_term(int seconds)
{
    if (term_pipe[0] >= 0 || pipe2(term_pipe, O_CLOEXEC) != 0) return;
    fcntl(term_pipe[1], F_SETFL, O_NONBLOCK);  /* a second SIGTERM never blocks in the handler */
    start_thread(term_deadline, seconds);
    struct sigaction sa = {0};
    sigaction(SIGTERM, NULL, &term_chained);
    sa.sa_sigaction = on_term;
    sa.sa_flags = SA_SIGINFO | SA_RESTART;
    sa.sa_mask = term_chained.sa_mask;
    sigaction(SIGTERM, &sa, NULL);
}
