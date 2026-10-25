/*
 * apklayer JNI bridge — com.macapk.loader.NativeOs on Linux/glibc.
 * Thin wrappers over libc; negative return = -errno (matches Mac.apk's convention).
 * Socketpair/socket natives are implemented too (framework LocalSocket paths);
 * mmap/prctl/pthread_setname_np included. open() flags are Linux-vs-bionic
 * translated for the common O_* bits.
 */
#include <jni.h>
#include <errno.h>
#include <fcntl.h>
#include <pthread.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/prctl.h>
#include <sys/types.h>
#include <sys/uio.h>
#include <sys/wait.h>
#include <unistd.h>

#define JNIP(name) Java_com_macapk_loader_NativeOs_##name

static jstring tojstr(JNIEnv *e, const char *s)
{
    return s ? (*e)->NewStringUTF(e, s) : NULL;
}

/* bionic O_* -> glibc O_* for the common bits (values differ on Android vs Linux
 * only for O_DIRECTORY/O_NOFOLLOW/O_CLOEXEC; O_RDWR/CREAT/EXCL/TRUNC/APPEND match). */
static int translate_flags(int f)
{
    int out = f & ~(O_RDONLY | O_WRONLY | O_RDWR | O_CREAT | O_EXCL | O_TRUNC | O_APPEND);
    out |= f & 3; /* access mode identical */
    if (f & 0x0200) out |= O_CREAT;      /* bionic O_CREAT   0x200 */
    if (f & 0x0080) out |= O_EXCL;       /* bionic O_EXCL    0x080 */
    if (f & 0x0400) out |= O_TRUNC;      /* bionic O_TRUNC   0x0400 */
    if (f & 0x0008) out |= O_APPEND;     /* bionic O_APPEND  0x0008 */
    if (f & 0x0100) out |= O_NOFOLLOW;   /* bionic O_NOFOLLOW 0x100 */
    if (f & 0x10000) out |= O_DIRECTORY; /* bionic O_DIRECTORY 0x10000 */
    if (f & 0x80000) out |= O_CLOEXEC;   /* bionic O_CLOEXEC  0x80000 */
    return out;
}

JNIEXPORT jint JNICALL JNIP(setenv)(JNIEnv *e, jclass c, jstring k, jstring v, jboolean over)
{
    (void)c;
    const char *ck = (*e)->GetStringUTFChars(e, k, NULL);
    const char *cv = (*e)->GetStringUTFChars(e, v, NULL);
    int r = setenv(ck, cv, over ? 1 : 0);
    int err = errno;
    (*e)->ReleaseStringUTFChars(e, k, ck);
    (*e)->ReleaseStringUTFChars(e, v, cv);
    return r == 0 ? 0 : -err;
}

JNIEXPORT jstring JNICALL JNIP(getenv)(JNIEnv *e, jclass c, jstring k)
{
    (void)c;
    const char *ck = (*e)->GetStringUTFChars(e, k, NULL);
    char *v = getenv(ck);
    jstring out = tojstr(e, v);
    (*e)->ReleaseStringUTFChars(e, k, ck);
    return out;
}

JNIEXPORT jint JNICALL JNIP(open)(JNIEnv *e, jclass c, jstring p, jint flags, jint mode)
{
    (void)c;
    const char *cp = (*e)->GetStringUTFChars(e, p, NULL);
    int fd = open(cp, translate_flags((int)flags), (mode_t)mode);
    int err = errno;
    (*e)->ReleaseStringUTFChars(e, p, cp);
    return fd >= 0 ? fd : -err;
}

JNIEXPORT jint JNICALL JNIP(close)(JNIEnv *e, jclass c, jint fd)
{
    (void)c;
    int r = close((int)fd);
    return r == 0 ? 0 : -errno;
}

JNIEXPORT jint JNICALL JNIP(write)(JNIEnv *e, jclass c, jint fd, jbyteArray buf, jint off, jint len)
{
    (void)c;
    jbyte *b = (*e)->GetByteArrayElements(e, buf, NULL);
    if (!b) return -ENOMEM;
    ssize_t r = write((int)fd, b + off, (size_t)len);
    int err = errno;
    (*e)->ReleaseByteArrayElements(e, buf, b, JNI_ABORT);
    return (int)(r >= 0 ? r : -err);
}

JNIEXPORT jint JNICALL JNIP(read)(JNIEnv *e, jclass c, jint fd, jbyteArray buf, jint off, jint len)
{
    (void)c;
    jbyte *b = (*e)->GetByteArrayElements(e, buf, NULL);
    if (!b) return -ENOMEM;
    ssize_t r = read((int)fd, b + off, (size_t)len);
    int err = errno;
    (*e)->ReleaseByteArrayElements(e, buf, b, 0);
    return (int)(r >= 0 ? r : -err);
}

JNIEXPORT jlong JNICALL JNIP(lseek)(JNIEnv *e, jclass c, jint fd, jlong off, jint whence)
{
    (void)c;
    off_t r = lseek((int)fd, (off_t)off, (int)whence);
    return r >= 0 ? (jlong)r : -(jlong)errno;
}

JNIEXPORT jint JNICALL JNIP(lseekInt)(JNIEnv *e, jclass c, jint fd, jint off, jint whence)
{
    return (jint)JNIP(lseek)(e, c, fd, (jlong)off, whence);
}

JNIEXPORT jint JNICALL JNIP(dup)(JNIEnv *e, jclass c, jint fd)
{
    (void)e; (void)c;
    int r = dup((int)fd);
    return r >= 0 ? r : -errno;
}

JNIEXPORT jint JNICALL JNIP(dup2)(JNIEnv *e, jclass c, jint a, jint b)
{
    (void)e; (void)c;
    int r = dup2((int)a, (int)b);
    return r >= 0 ? r : -errno;
}

JNIEXPORT jint JNICALL JNIP(ftruncate)(JNIEnv *e, jclass c, jint fd, jlong len)
{
    (void)e; (void)c;
    int r = ftruncate((int)fd, (off_t)len);
    return r == 0 ? 0 : -errno;
}

JNIEXPORT jint JNICALL JNIP(ftruncateInt)(JNIEnv *e, jclass c, jint fd, jint len)
{
    return JNIP(ftruncate)(e, c, fd, (jlong)len);
}

JNIEXPORT jlong JNICALL JNIP(fstatSize)(JNIEnv *e, jclass c, jint fd)
{
    (void)c;
    struct stat st;
    int r = fstat((int)fd, &st);
    return r == 0 ? (jlong)st.st_size : -(jlong)errno;
}

JNIEXPORT jint JNICALL JNIP(fstatMode)(JNIEnv *e, jclass c, jint fd)
{
    (void)c;
    struct stat st;
    int r = fstat((int)fd, &st);
    return r == 0 ? (jint)st.st_mode : -(jint)errno;
}

JNIEXPORT jint JNICALL JNIP(errnoValue)(JNIEnv *e, jclass c)
{
    (void)e; (void)c;
    return errno;
}

JNIEXPORT jint JNICALL JNIP(getpid)(JNIEnv *e, jclass c)
{ (void)e; (void)c; return getpid(); }

JNIEXPORT jint JNICALL JNIP(getppid)(JNIEnv *e, jclass c)
{ (void)e; (void)c; return getppid(); }

JNIEXPORT jint JNICALL JNIP(getuid)(JNIEnv *e, jclass c)
{ (void)e; (void)c; return (jint)getuid(); }

JNIEXPORT jint JNICALL JNIP(geteuid)(JNIEnv *e, jclass c)
{ (void)e; (void)c; return (jint)geteuid(); }

JNIEXPORT jint JNICALL JNIP(kill)(JNIEnv *e, jclass c, jint pid, jint sig)
{
    (void)e; (void)c;
    int r = kill((pid_t)pid, (int)sig);
    return r == 0 ? 0 : -errno;
}

JNIEXPORT jint JNICALL JNIP(fork)(JNIEnv *e, jclass c)
{ (void)e; (void)c; return fork(); }

JNIEXPORT jint JNICALL JNIP(waitpid)(JNIEnv *e, jclass c, jint pid, jintArray statusOut, jint opts)
{
    (void)c;
    int st = 0;
    pid_t r = waitpid((pid_t)pid, &st, (int)opts);
    if (r >= 0 && statusOut) {
        jint one = st;
        (*e)->SetIntArrayRegion(e, statusOut, 0, 1, &one);
    }
    return (int)r;
}

JNIEXPORT void JNICALL JNIP(exitProcess)(JNIEnv *e, jclass c, jint code)
{ (void)e; (void)c; exit((int)code); }

JNIEXPORT jint JNICALL JNIP(pipe)(JNIEnv *e, jclass c, jintArray fds)
{
    (void)c;
    int f[2];
    int r = pipe(f);
    if (r == 0 && fds) {
        jint both[2] = { f[0], f[1] };
        (*e)->SetIntArrayRegion(e, fds, 0, 2, both);
    }
    return r == 0 ? 0 : -errno;
}

JNIEXPORT jint JNICALL JNIP(socketpair)(JNIEnv *e, jclass c, jint domain, jint type, jint proto, jintArray fds)
{
    (void)c;
    int f[2];
    int r = socketpair((int)domain, (int)type, (int)proto, f);
    if (r == 0 && fds) {
        jint both[2] = { f[0], f[1] };
        (*e)->SetIntArrayRegion(e, fds, 0, 2, both);
    }
    return r == 0 ? 0 : -errno;
}

JNIEXPORT jint JNICALL JNIP(fcntl)(JNIEnv *e, jclass c, jint fd, jint cmd, jint arg)
{
    (void)e; (void)c;
    long r = fcntl((int)fd, cmd, arg);
    return (int)(r >= 0 ? r : -errno);
}

JNIEXPORT jint JNICALL JNIP(fcntlLong)(JNIEnv *e, jclass c, jint fd, jint cmd, jlong arg)
{
    (void)e; (void)c;
    long r = fcntl((int)fd, cmd, (long)arg);
    return (int)(r >= 0 ? r : -errno);
}

JNIEXPORT void JNICALL JNIP(setcloexec)(JNIEnv *e, jclass c, jint fd, jboolean on)
{
    (void)e; (void)c;
    fcntl((int)fd, F_SETFD, on ? FD_CLOEXEC : 0);
}

JNIEXPORT jint JNICALL JNIP(sysconfPgsz)(JNIEnv *e, jclass c)
{ (void)e; (void)c; return (jint)sysconf(_SC_PAGESIZE); }

JNIEXPORT jlong JNICALL JNIP(sysconfSc)(JNIEnv *e, jclass c, jint name)
{ (void)e; (void)c; return (jlong)sysconf(name); }

JNIEXPORT void JNICALL JNIP(pthreadSetnameNp)(JNIEnv *e, jclass c, jstring name)
{
    (void)c;
    const char *cn = (*e)->GetStringUTFChars(e, name, NULL);
    pthread_setname_np(pthread_self(), cn);
    (*e)->ReleaseStringUTFChars(e, name, cn);
}

JNIEXPORT jint JNICALL JNIP(prctl)(JNIEnv *e, jclass c, jlong buf, jlong len, jlong arg2)
{
    (void)c; (void)arg2;
    int r = prctl((int)PR_GET_NAME, (unsigned long)buf, (unsigned long)len, 0, 0, 0);
    /* only used for PR_GET_NAME-style calls by the framework; generic passthrough: */
    return r == 0 ? 0 : -errno;
}

/* ---- GamepadBridge: host gamepad polling — no-op on Linux for now ---- */
JNIEXPORT void JNICALL Java_com_macapk_loader_GamepadBridge_nativeStart(JNIEnv *e, jclass c)
{ (void)e; (void)c; }
JNIEXPORT void JNICALL Java_com_macapk_loader_GamepadBridge_nativeStop(JNIEnv *e, jclass c)
{ (void)e; (void)c; }
