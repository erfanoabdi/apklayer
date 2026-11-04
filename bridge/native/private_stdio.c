#define _GNU_SOURCE  /* dup3 */
/* The runtime's own output (System.out and System.err: "[apklayer] started ...", exceptions, the run deadline's thread dump) on private copies of stdout and
 * stderr, as liblog has its own (android_platform.c). Apps point fds 1 and 2 elsewhere, which on Android only moves /dev/null: gomobile's Go runtime pipes them
 * into logcat at INFO (Rethink DNS), SimpleX's Haskell core into a socket the app reads back and logs. The app's own writes to fds 1 and 2 are left alone. */
#include <fcntl.h>
#include <jni.h>
#include <stdio.h>
#include <unistd.h>

static int out_fd = -1, err_fd = -1;

static void set_stream(JNIEnv *env, const char *setter, int fd)
{
    jclass fdc = (*env)->FindClass(env, "java/io/FileDescriptor"), fos = (*env)->FindClass(env, "java/io/FileOutputStream");
    jclass bos = (*env)->FindClass(env, "java/io/BufferedOutputStream"), ps = (*env)->FindClass(env, "java/io/PrintStream");
    jclass sys = (*env)->FindClass(env, "java/lang/System");
    jobject descriptor = (*env)->NewObject(env, fdc, (*env)->GetMethodID(env, fdc, "<init>", "()V"));
    (*env)->SetIntField(env, descriptor, (*env)->GetFieldID(env, fdc, "fd", "I"), fd);
    jobject file = (*env)->NewObject(env, fos, (*env)->GetMethodID(env, fos, "<init>", "(Ljava/io/FileDescriptor;)V"), descriptor);
    jobject buffered = (*env)->NewObject(env, bos, (*env)->GetMethodID(env, bos, "<init>", "(Ljava/io/OutputStream;)V"), file);
    jobject stream = (*env)->NewObject(env, ps, (*env)->GetMethodID(env, ps, "<init>", "(Ljava/io/OutputStream;Z)V"), buffered, JNI_TRUE);  /* autoflush, as System.out */
    (*env)->CallStaticVoidMethod(env, sys, (*env)->GetStaticMethodID(env, sys, setter, "(Ljava/io/PrintStream;)V"), stream);
    if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
}

/* before app code runs (the shim's JNI_OnLoad); the old streams are flushed so nothing printed so far is lost */
void apklayer_private_stdio(JNIEnv *env)
{
    if (out_fd >= 0) return;
    fflush(NULL);
    out_fd = fcntl(1, F_DUPFD_CLOEXEC, 100);
    err_fd = fcntl(2, F_DUPFD_CLOEXEC, 100);
    if (out_fd >= 0) set_stream(env, "setOut", out_fd);
    if (err_fd >= 0) set_stream(env, "setErr", err_fd);
}

/* stdout and stderr were replaced (an app spare's launch, spare_target.c): the private copies follow */
void apklayer_private_stdio_follow(void)
{
    if (out_fd >= 0) dup3(1, out_fd, O_CLOEXEC);
    if (err_fd >= 0) dup3(2, err_fd, O_CLOEXEC);
}
