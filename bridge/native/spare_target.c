#define _GNU_SOURCE  /* MSG_CMSG_CLOEXEC */
/* apklayer.host.NativeLoader.receiveTarget: an app spare's launch (cli/spare.cpp, AppSpare). One message on the holder's socket: the launch's JVM options and
 * files (text, one per line) with its stdout and stderr (SCM_RIGHTS), which replace this process's own, for native code's output too (dup2). */
#include <errno.h>
#include <jni.h>
#include <string.h>
#include <sys/socket.h>
#include <unistd.h>

jstring Java_apklayer_host_NativeLoader_receiveTarget(JNIEnv *env, jclass cls, jint fd)
{
    (void)cls;
    static char text[65536];
    char control[CMSG_SPACE(2 * sizeof(int))];
    struct iovec iov = {text, sizeof text - 1};
    struct msghdr msg = {.msg_iov = &iov, .msg_iovlen = 1, .msg_control = control, .msg_controllen = sizeof control};
    ssize_t n;
    while ((n = recvmsg(fd, &msg, MSG_CMSG_CLOEXEC)) < 0 && errno == EINTR) {}
    close(fd);
    if (n <= 0) return NULL;
    text[n] = 0;
    struct cmsghdr *c = CMSG_FIRSTHDR(&msg);
    if (c && c->cmsg_level == SOL_SOCKET && c->cmsg_type == SCM_RIGHTS && c->cmsg_len == CMSG_LEN(2 * sizeof(int))) {
        int out[2];
        memcpy(out, CMSG_DATA(c), sizeof out);
        dup2(out[0], 1);
        dup2(out[1], 2);
        close(out[0]);
        close(out[1]);
    }
    return (*env)->NewStringUTF(env, text);
}
