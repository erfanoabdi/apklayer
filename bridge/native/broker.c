/* The sandbox's file broker, the app's side (cli/broker.cpp is the daemon's): an open the app's uid is refused (EACCES) that the user may make, of a
 * GPU node or of the granted home ($APKLAYER_BROKER_HOME), is made by apklayerd ($APKLAYER_BROKER, its socket; both set by cli/sandbox.cpp) and its
 * fd handed over. Any other refusal stays the caller's. */
#define _GNU_SOURCE
#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>

static int eligible(const char *path)
{
    const char *home = getenv("APKLAYER_BROKER_HOME");
    size_t n = home ? strlen(home) : 0;
    return path && path[0] == '/' && (!strncmp(path, "/dev/", 5) || (n > 1 && !strncmp(path, home, n) && path[n] == '/'));
}

/* failed: the caller's own result; a broker fd in its place, else failed with its errno */
int apklayer_broker_open(const char *path, int flags, int mode, int failed)
{
    const char *socket_path = getenv("APKLAYER_BROKER");
    if (failed >= 0 || errno != EACCES || !socket_path || !eligible(path)) return failed;
    int saved = errno;
    int s = socket(AF_UNIX, SOCK_SEQPACKET | SOCK_CLOEXEC, 0);
    struct sockaddr_un addr = {.sun_family = AF_UNIX};
    snprintf(addr.sun_path, sizeof addr.sun_path, "%s", socket_path);
    char request[4200];
    int len = snprintf(request, sizeof request, "open%c%s%c%d%c%d%c", 0, path, 0, flags, 0, mode, 0);
    int fd = -1;
    if (s >= 0 && len > 0 && len < (int)sizeof request && connect(s, (struct sockaddr *)&addr, sizeof addr) == 0 && send(s, request, len, MSG_NOSIGNAL) == len) {
        char reply[32] = {0}, control[CMSG_SPACE(sizeof(int))];
        struct iovec iov = {reply, sizeof reply - 1};
        struct msghdr msg = {.msg_iov = &iov, .msg_iovlen = 1, .msg_control = control, .msg_controllen = sizeof control};
        ssize_t got = recvmsg(s, &msg, MSG_CMSG_CLOEXEC);
        struct cmsghdr *c = got > 0 ? CMSG_FIRSTHDR(&msg) : NULL;
        if (got >= 2 && !strncmp(reply, "ok", 2) && c && c->cmsg_type == SCM_RIGHTS) memcpy(&fd, CMSG_DATA(c), sizeof fd);
        else if (got > 4 && !strncmp(reply, "err ", 4)) saved = atoi(reply + 4);
    }
    if (s >= 0) close(s);
    if (fd >= 0 && !(flags & O_CLOEXEC)) fcntl(fd, F_SETFD, 0);
    if (fd < 0) errno = saved;
    return fd < 0 ? failed : fd;
}
