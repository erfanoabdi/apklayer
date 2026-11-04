#define _GNU_SOURCE  /* pipe2 */
/* Registers the natives of apklayer.host.NativeLoader on the device-library route: the functions are native_loader.c's (the older route builds them into its own
 * libapklayer_native.so, where HotSpot finds them by name; here they are bound with RegisterNatives from the runtime shim). */
#include <jni.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>

extern void Java_apklayer_host_NativeLoader_setLibraryPath(JNIEnv *, jclass, jstring, jstring);
extern jlong Java_apklayer_host_NativeLoader_dlopen(JNIEnv *, jclass, jstring);
extern jlong Java_apklayer_host_NativeLoader_dlsym(JNIEnv *, jclass, jlong, jstring);
extern jstring Java_apklayer_host_NativeLoader_dlerror(JNIEnv *, jclass);
extern jint Java_apklayer_host_NativeLoader_callOnLoad(JNIEnv *, jclass, jlong, jobject);
extern jint Java_apklayer_host_NativeLoader_registerNative(JNIEnv *, jclass, jclass, jstring, jstring, jlong);
extern void Java_apklayer_host_NativeLoader_skipNativeExitHandlers(JNIEnv *, jclass);
extern void Java_apklayer_host_NativeLoader_setProcessName(JNIEnv *, jclass, jstring);
extern jstring Java_apklayer_host_NativeLoader_receiveTarget(JNIEnv *, jclass, jint);

int apklayer_register_nativeloader(JNIEnv *env, jclass loader)
{
    JNINativeMethod methods[] = {
        {"setLibraryPath", "(Ljava/lang/String;Ljava/lang/String;)V", (void *)Java_apklayer_host_NativeLoader_setLibraryPath},
        {"dlopen", "(Ljava/lang/String;)J", (void *)Java_apklayer_host_NativeLoader_dlopen},
        {"dlsym", "(JLjava/lang/String;)J", (void *)Java_apklayer_host_NativeLoader_dlsym},
        {"dlerror", "()Ljava/lang/String;", (void *)Java_apklayer_host_NativeLoader_dlerror},
        {"callOnLoad", "(JLjava/lang/ClassLoader;)I", (void *)Java_apklayer_host_NativeLoader_callOnLoad},
        {"registerNative", "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/String;J)I", (void *)Java_apklayer_host_NativeLoader_registerNative},
        {"skipNativeExitHandlers", "()V", (void *)Java_apklayer_host_NativeLoader_skipNativeExitHandlers},
        {"setProcessName", "(Ljava/lang/String;)V", (void *)Java_apklayer_host_NativeLoader_setProcessName},
        {"receiveTarget", "(I)Ljava/lang/String;", (void *)Java_apklayer_host_NativeLoader_receiveTarget},
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
static jstring linux_strerror(JNIEnv *env, jobject self, jint error) { (void)self; char buf[128]; return (*env)->NewStringUTF(env, strerror_r(error, buf, sizeof buf)); }  /* GNU strerror_r (_GNU_SOURCE) */
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

/* Os.socket/listen/accept/shutdown: LocalServerSocket and LocalSocket (AF_UNIX) run on these and LocalSocketImpl's own natives (SimpleX's stdout pipe) */
static jobject linux_socket(JNIEnv *env, jobject self, jint domain, jint type, jint protocol)
{
    (void)self;
    int n = socket(domain, type, protocol);
    if (n < 0) { throw_errno(env, "socket", errno); return NULL; }
    return fd_make(env, n);
}
static void linux_listen(JNIEnv *env, jobject self, jobject fd, jint backlog) { (void)self; if (listen(fd_of(env, fd), backlog) < 0) throw_errno(env, "listen", errno); }
static void linux_shutdown(JNIEnv *env, jobject self, jobject fd, jint how) { (void)self; if (shutdown(fd_of(env, fd), how) < 0) throw_errno(env, "shutdown", errno); }
/* shortcut: the peer address is not filled in (LocalSocketImpl passes null); an InetSocketAddress caller gets it unchanged, fill it when one needs it */
static jobject linux_accept(JNIEnv *env, jobject self, jobject fd, jobject peer)
{
    (void)self; (void)peer;
    int n = accept(fd_of(env, fd), NULL, NULL);
    if (n < 0) { throw_errno(env, "accept", errno); return NULL; }
    return fd_make(env, n);
}

/* Os.poll(StructPollfd[], timeout): what HostFdEvents waits in for a MessageQueue's file descriptor listeners (ParcelFileDescriptor's close listeners among them) */
#include <poll.h>
#include <stdlib.h>
static jint linux_poll(JNIEnv *env, jobject self, jobjectArray array, jint timeout)
{
    (void)self;
    jsize n = (*env)->GetArrayLength(env, array);
    jclass cls = (*env)->FindClass(env, "android/system/StructPollfd");
    jfieldID fd = (*env)->GetFieldID(env, cls, "fd", "Ljava/io/FileDescriptor;"), events = (*env)->GetFieldID(env, cls, "events", "S"), revents = (*env)->GetFieldID(env, cls, "revents", "S");
    struct pollfd *fds = calloc(n ? n : 1, sizeof *fds);
    for (jsize i = 0; i < n; i++) {
        jobject one = (*env)->GetObjectArrayElement(env, array, i);
        fds[i].fd = one ? fd_of(env, (*env)->GetObjectField(env, one, fd)) : -1;
        fds[i].events = one ? (*env)->GetShortField(env, one, events) : 0;
    }
    int r = poll(fds, n, timeout), error = errno;
    if (r < 0) { free(fds); throw_errno(env, "poll", error); return -1; }
    for (jsize i = 0; i < n; i++) {
        jobject one = (*env)->GetObjectArrayElement(env, array, i);
        if (one) (*env)->SetShortField(env, one, revents, fds[i].revents);
    }
    free(fds);
    return r;
}

/* Os.read/Os.write (libcore's readBytes/writeBytes): a byte[] or a direct ByteBuffer. ParcelFileDescriptor's comm channel tells the other side how its descriptor was closed this way. */
static jint linux_rw(JNIEnv *env, jobject fd, jobject buffer, jint offset, jint count, int writing)
{
    jclass bytes = (*env)->FindClass(env, "[B");
    int n, error;
    if ((*env)->IsInstanceOf(env, buffer, bytes)) {
        jbyte *data = (*env)->GetByteArrayElements(env, (jbyteArray)buffer, NULL);
        n = writing ? (int)write(fd_of(env, fd), data + offset, count) : (int)read(fd_of(env, fd), data + offset, count);
        error = errno;
        (*env)->ReleaseByteArrayElements(env, (jbyteArray)buffer, data, writing ? JNI_ABORT : 0);
    } else {
        char *data = (*env)->GetDirectBufferAddress(env, buffer);
        if (!data) { throw_errno(env, writing ? "write" : "read", EINVAL); return -1; }
        n = writing ? (int)write(fd_of(env, fd), data + offset, count) : (int)read(fd_of(env, fd), data + offset, count);
        error = errno;
    }
    if (n < 0) throw_errno(env, writing ? "write" : "read", error);
    return n;
}
static jint linux_read_bytes(JNIEnv *env, jobject self, jobject fd, jobject buffer, jint offset, jint count) { (void)self; return linux_rw(env, fd, buffer, offset, count, 0); }
static jint linux_write_bytes(JNIEnv *env, jobject self, jobject fd, jobject buffer, jint offset, jint count) { (void)self; return linux_rw(env, fd, buffer, offset, count, 1); }

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
        {"strerror", "(I)Ljava/lang/String;", (void *)linux_strerror},
        {"open", "(Ljava/lang/String;II)Ljava/io/FileDescriptor;", (void *)linux_open},
        {"pipe2", "(I)[Ljava/io/FileDescriptor;", (void *)linux_pipe2},
        {"socketpair", "(IIILjava/io/FileDescriptor;Ljava/io/FileDescriptor;)V", (void *)linux_socketpair},
        {"socket", "(III)Ljava/io/FileDescriptor;", (void *)linux_socket},
        {"listen", "(Ljava/io/FileDescriptor;I)V", (void *)linux_listen},
        {"accept", "(Ljava/io/FileDescriptor;Ljava/net/SocketAddress;)Ljava/io/FileDescriptor;", (void *)linux_accept},
        {"shutdown", "(Ljava/io/FileDescriptor;I)V", (void *)linux_shutdown},
        {"poll", "([Landroid/system/StructPollfd;I)I", (void *)linux_poll},
        {"readBytes", "(Ljava/io/FileDescriptor;Ljava/lang/Object;II)I", (void *)linux_read_bytes},
        {"writeBytes", "(Ljava/io/FileDescriptor;Ljava/lang/Object;II)I", (void *)linux_write_bytes},
    };
    return (*env)->RegisterNatives(env, libcore_linux, methods, sizeof methods / sizeof *methods) == 0 ? 0 : -1;
}
