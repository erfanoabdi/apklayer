/* Registers the natives of apklayer.host.NativeLoader on the device-library route: the functions are native_loader.c's (the older route builds them into its own
 * libapklayer_native.so, where HotSpot finds them by name; here they are bound with RegisterNatives from the runtime shim). */
#include <jni.h>
#include <stdint.h>
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

/* apklayer.host.NativeLooper: the thread's native android::Looper (libandroid's ALooper). Chromium's message pump registers its eventfd/timerfd on it; Robolectric's
 * MessageQueue never polls it, so native tasks posted to a Java-looper thread would wait forever unless the host polls it too. */
extern void *bionic_dlopen(const char *filename, int flag);
extern void *bionic_dlsym(void *handle, const char *symbol);
static void *(*p_for_thread)(void);
static void *(*p_prepare)(int);
static int (*p_poll_once)(int, int *, int *, void **);
static void (*p_wake)(void *);

static int looper_symbols(void)
{
    if (p_for_thread) return 1;
    void *lib = bionic_dlopen("libandroid.so", 2);
    if (!lib) return 0;
    p_poll_once = bionic_dlsym(lib, "ALooper_pollOnce");
    p_wake = bionic_dlsym(lib, "ALooper_wake");
    p_for_thread = bionic_dlsym(lib, "ALooper_forThread");
    p_prepare = bionic_dlsym(lib, "ALooper_prepare");
    return p_for_thread && p_prepare && p_poll_once && p_wake;
}

static jlong looper_prepare(JNIEnv *env, jclass cls) { (void)env; (void)cls; return looper_symbols() ? (jlong)(uintptr_t)p_prepare(1 /* ALLOW_NON_CALLBACKS */) : 0; }
static jlong looper_for_thread(JNIEnv *env, jclass cls) { (void)env; (void)cls; return looper_symbols() ? (jlong)(uintptr_t)p_for_thread() : 0; }
static jint looper_poll_once(JNIEnv *env, jclass cls, jint timeout) { (void)env; (void)cls; return looper_symbols() ? p_poll_once(timeout, NULL, NULL, NULL) : -1; }
static void looper_wake(JNIEnv *env, jclass cls, jlong looper) { (void)env; (void)cls; if (looper_symbols() && looper) p_wake((void *)(uintptr_t)looper); }

int apklayer_register_nativelooper(JNIEnv *env, jclass looper)
{
    JNINativeMethod methods[] = {
        {"forThread", "()J", (void *)looper_for_thread},
        {"prepareForThread", "()J", (void *)looper_prepare},
        {"pollOnce", "(I)I", (void *)looper_poll_once},
        {"wake", "(J)V", (void *)looper_wake},
    };
    return (*env)->RegisterNatives(env, looper, methods, sizeof methods / sizeof *methods) == 0 ? 0 : -1;
}

/* libcore.io.Linux's file-descriptor natives (libcore's libjavacore registers a lot more, among it JVM-internal java.lang.invoke natives: too much to load here).
 * Descriptors are OpenJDK FileDescriptor objects (field "fd"); errors are android.system.ErrnoException(function, errno). */
#include <errno.h>
#include <fcntl.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <unistd.h>

static jfieldID fd_field(JNIEnv *env)
{
    static jfieldID field;
    if (!field) { jclass cls = (*env)->FindClass(env, "java/io/FileDescriptor"); field = (*env)->GetFieldID(env, cls, "fd", "I"); }
    return field;
}

static jint fd_of(JNIEnv *env, jobject descriptor) { return descriptor ? (*env)->GetIntField(env, descriptor, fd_field(env)) : -1; }

static jobject fd_make(JNIEnv *env, int fd)
{
    jclass cls = (*env)->FindClass(env, "java/io/FileDescriptor");
    jobject object = (*env)->NewObject(env, cls, (*env)->GetMethodID(env, cls, "<init>", "()V"));
    (*env)->SetIntField(env, object, fd_field(env), fd);
    return object;
}

static void throw_errno(JNIEnv *env, const char *function, int error)
{
    jclass cls = (*env)->FindClass(env, "android/system/ErrnoException");
    jmethodID init = (*env)->GetMethodID(env, cls, "<init>", "(Ljava/lang/String;I)V");
    jstring name = (*env)->NewStringUTF(env, function);
    (*env)->Throw(env, (jthrowable)(*env)->NewObject(env, cls, init, name, error));
}

static void linux_close(JNIEnv *env, jobject self, jobject fd) { (void)self; if (close(fd_of(env, fd)) < 0) throw_errno(env, "close", errno); }
static jobject linux_dup(JNIEnv *env, jobject self, jobject fd) { (void)self; int n = dup(fd_of(env, fd)); if (n < 0) { throw_errno(env, "dup", errno); return NULL; } return fd_make(env, n); }
static jobject linux_dup2(JNIEnv *env, jobject self, jobject fd, jint to) { (void)self; int n = dup2(fd_of(env, fd), to); if (n < 0) { throw_errno(env, "dup2", errno); return NULL; } return fd_make(env, n); }
static jint linux_fcntl_int(JNIEnv *env, jobject self, jobject fd, jint cmd, jint arg) { (void)self; int n = fcntl(fd_of(env, fd), cmd, arg); if (n < 0) throw_errno(env, "fcntl", errno); return n; }
static jint linux_fcntl_void(JNIEnv *env, jobject self, jobject fd, jint cmd) { (void)self; int n = fcntl(fd_of(env, fd), cmd); if (n < 0) throw_errno(env, "fcntl", errno); return n; }
static jlong linux_lseek(JNIEnv *env, jobject self, jobject fd, jlong offset, jint whence) { (void)self; off_t n = lseek(fd_of(env, fd), offset, whence); if (n < 0) throw_errno(env, "lseek", errno); return n; }
static void linux_ftruncate(JNIEnv *env, jobject self, jobject fd, jlong length) { (void)self; if (ftruncate(fd_of(env, fd), length) < 0) throw_errno(env, "ftruncate", errno); }

static jobject linux_open(JNIEnv *env, jobject self, jstring path, jint flags, jint mode)
{
    (void)self;
    const char *p = (*env)->GetStringUTFChars(env, path, NULL);
    int n = open(p, flags, mode);
    int error = errno;
    (*env)->ReleaseStringUTFChars(env, path, p);
    if (n < 0) { throw_errno(env, "open", error); return NULL; }
    return fd_make(env, n);
}

static jobjectArray linux_pipe2(JNIEnv *env, jobject self, jint flags)
{
    (void)self;
    int fds[2];
    if (pipe2(fds, flags) < 0) { throw_errno(env, "pipe2", errno); return NULL; }
    jclass cls = (*env)->FindClass(env, "java/io/FileDescriptor");
    jobjectArray result = (*env)->NewObjectArray(env, 2, cls, NULL);
    (*env)->SetObjectArrayElement(env, result, 0, fd_make(env, fds[0]));
    (*env)->SetObjectArrayElement(env, result, 1, fd_make(env, fds[1]));
    return result;
}

static void linux_socketpair(JNIEnv *env, jobject self, jint domain, jint type, jint protocol, jobject one, jobject two)
{
    (void)self;
    int fds[2];
    if (socketpair(domain, type, protocol, fds) < 0) { throw_errno(env, "socketpair", errno); return; }
    (*env)->SetIntField(env, one, fd_field(env), fds[0]);
    (*env)->SetIntField(env, two, fd_field(env), fds[1]);
}

int apklayer_register_linux(JNIEnv *env, jclass libcore_linux)
{
    JNINativeMethod methods[] = {
        {"close", "(Ljava/io/FileDescriptor;)V", (void *)linux_close},
        {"dup", "(Ljava/io/FileDescriptor;)Ljava/io/FileDescriptor;", (void *)linux_dup},
        {"dup2", "(Ljava/io/FileDescriptor;I)Ljava/io/FileDescriptor;", (void *)linux_dup2},
        {"fcntlInt", "(Ljava/io/FileDescriptor;II)I", (void *)linux_fcntl_int},
        {"fcntlVoid", "(Ljava/io/FileDescriptor;I)I", (void *)linux_fcntl_void},
        {"lseek", "(Ljava/io/FileDescriptor;JI)J", (void *)linux_lseek},
        {"ftruncate", "(Ljava/io/FileDescriptor;J)V", (void *)linux_ftruncate},
        {"open", "(Ljava/lang/String;II)Ljava/io/FileDescriptor;", (void *)linux_open},
        {"pipe2", "(I)[Ljava/io/FileDescriptor;", (void *)linux_pipe2},
        {"socketpair", "(IIILjava/io/FileDescriptor;Ljava/io/FileDescriptor;)V", (void *)linux_socketpair},
    };
    return (*env)->RegisterNatives(env, libcore_linux, methods, sizeof methods / sizeof *methods) == 0 ? 0 : -1;
}
