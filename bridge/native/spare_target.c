#define _GNU_SOURCE  /* MSG_CMSG_CLOEXEC */
/* apklayer.host.NativeLoader.receiveTarget: the instance spare's launch (cli/spare.cpp, AppSpare). One message on the holder's socket: the launch's JVM
 * options and files (text, one per line) with its stdout and stderr, which replace this process's own, for native code's output too (dup2), and the pipe on
 * which the launch tells its launcher that it has started (named to Java as -Dapklayer.startedfd, a line before the text). The launch's own environment
 * comes as -Dapklayer.env.<name>=<value> lines: set here for native readers too (bionic_translation's kill() reads APKLAYER_COMPONENT_HOST). */
#include <errno.h>
#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/prctl.h>
#include <sys/socket.h>
#include <unistd.h>

extern void apklayer_log_follow_stderr(void);

static void set_launch_env(const char *text)
{
    static const char prefix[] = "-Dapklayer.env.";
    for (const char *line = text; *line;) {
        const char *end = strchr(line, '\n');
        size_t len = end ? (size_t)(end - line) : strlen(line);
        if (len > sizeof prefix - 1 && !strncmp(line, prefix, sizeof prefix - 1)) {
            char entry[4096];
            size_t n = len - (sizeof prefix - 1);
            char *eq;
            if (n < sizeof entry) {
                memcpy(entry, line + sizeof prefix - 1, n);
                entry[n] = 0;
                if ((eq = strchr(entry, '='))) { *eq = 0; setenv(entry, eq + 1, 1); }
            }
        }
        if (!end) break;
        line = end + 1;
    }
}

jstring Java_apklayer_host_NativeLoader_receiveTarget(JNIEnv *env, jclass cls, jint fd)
{
    (void)cls;
    static char text[65536];
    int prefix = 64;  /* room for the started pipe's line */
    char control[CMSG_SPACE(3 * sizeof(int))];
    struct iovec iov = {text + prefix, sizeof text - prefix - 1};
    struct msghdr msg = {.msg_iov = &iov, .msg_iovlen = 1, .msg_control = control, .msg_controllen = sizeof control};
    ssize_t n;
    while ((n = recvmsg(fd, &msg, MSG_CMSG_CLOEXEC)) < 0 && errno == EINTR) {}
    close(fd);
    if (n <= 0) return NULL;
    prctl(PR_SET_PDEATHSIG, 0);  /* the launch's process from here on: it outlives its holder, as an app outlives the zygote that forked it */
    text[prefix + n] = 0;
    char *start = text + prefix;
    set_launch_env(start);
    struct cmsghdr *c = CMSG_FIRSTHDR(&msg);
    int count = c && c->cmsg_level == SOL_SOCKET && c->cmsg_type == SCM_RIGHTS ? (int)((c->cmsg_len - CMSG_LEN(0)) / sizeof(int)) : 0;
    if (count >= 2) {
        int got[3];
        memcpy(got, CMSG_DATA(c), count * sizeof(int));
        dup2(got[0], 1);
        dup2(got[1], 2);
        close(got[0]);
        close(got[1]);
        apklayer_log_follow_stderr();  /* liblog writes to its own copy of stderr (android_platform.c) */
        if (count == 3) {  /* kept open (close-on-exec) for AppSpare.launchStarted */
            char line[64];
            int len = snprintf(line, sizeof line, "-Dapklayer.startedfd=%d\n", got[2]);
            start -= len;
            memcpy(start, line, len);
        }
    }
    return (*env)->NewStringUTF(env, start);
}
