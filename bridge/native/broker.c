/* The sandbox's file broker, the app's side (cli/broker.cpp is the daemon's): an open the app's uid is refused (EACCES) that the user may make, of a
 * GPU node, of its own app log ($APKLAYER_BROKER_LOG) or in the granted home or the drives ($APKLAYER_BROKER_HOME), is made by apklayerd ($APKLAYER_BROKER, its
 * socket; all set by cli/sandbox.cpp) and its fd handed over; there also mkdir, unlink, rmdir, rename and access. A path relative to a
 * directory fd (or the working directory) is made absolute first. Any other refusal stays the caller's. */
#define _GNU_SOURCE
#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>

/* under one of $APKLAYER_BROKER_HOME's folders (':'-separated: the granted home, the desktop's drives) */
static int in_home(const char *path)
{
    for (const char *home = getenv("APKLAYER_BROKER_HOME"); home && *home;) {
        const char *end = strchr(home, ':');
        size_t n = end ? (size_t)(end - home) : strlen(home);
        if (n > 1 && !strncmp(path, home, n) && path[n] == '/') return 1;
        home = end ? end + 1 : home + n;
    }
    return 0;
}

static int eligible(const char *path)
{
    const char *log = getenv("APKLAYER_BROKER_LOG");  /* the app's own app log, appended */
    return path[0] == '/' && (!strncmp(path, "/dev/", 5) || in_home(path) || (log && !strcmp(path, log)));
}

/* path as an absolute one in out (relative ones against dirfd, AT_FDCWD: the working directory); 0 when it cannot be */
static int absolute(int dirfd, const char *path, char *out, size_t n)
{
    if (!path || !*path) return 0;
    if (path[0] == '/') return snprintf(out, n, "%s", path) < (int)n;
    char base[4096];
    if (dirfd == AT_FDCWD) {
        if (!getcwd(base, sizeof base)) return 0;
    } else {
        char link[64];
        snprintf(link, sizeof link, "/proc/self/fd/%d", dirfd);
        ssize_t got = readlink(link, base, sizeof base - 1);
        if (got <= 0) return 0;
        base[got] = 0;
    }
    return snprintf(out, n, "%s/%s", strcmp(base, "/") ? base : "", path) < (int)n;
}

/* One request: 0 ("ok", *fd set when one came with it) or the daemon's errno; -1 when apklayerd could not be asked */
static int ask(const char *request, int len, int *fd)
{
    const char *socket_path = getenv("APKLAYER_BROKER");
    int s = socket_path ? socket(AF_UNIX, SOCK_SEQPACKET | SOCK_CLOEXEC, 0) : -1;
    if (s < 0) return -1;
    struct sockaddr_un addr = {.sun_family = AF_UNIX};
    snprintf(addr.sun_path, sizeof addr.sun_path, "%s", socket_path);
    int result = -1;
    if (connect(s, (struct sockaddr *)&addr, sizeof addr) == 0 && send(s, request, len, MSG_NOSIGNAL) == len) {
        char reply[32] = {0}, control[CMSG_SPACE(sizeof(int))];
        struct iovec iov = {reply, sizeof reply - 1};
        struct msghdr msg = {.msg_iov = &iov, .msg_iovlen = 1, .msg_control = control, .msg_controllen = sizeof control};
        ssize_t got = recvmsg(s, &msg, MSG_CMSG_CLOEXEC);
        struct cmsghdr *c = got > 0 ? CMSG_FIRSTHDR(&msg) : NULL;
        if (got >= 2 && !strncmp(reply, "ok", 2)) {
            result = 0;
            if (fd && c && c->cmsg_type == SCM_RIGHTS) memcpy(fd, CMSG_DATA(c), sizeof *fd);
        } else if (got > 4 && !strncmp(reply, "err ", 4)) {
            result = atoi(reply + 4);
        }
    }
    close(s);
    return result;
}

/* failed: the caller's own result; a broker fd in its place, else failed with its errno */
int apklayer_broker_openat(int dirfd, const char *path, int flags, int mode, int failed)
{
    char full[4096], request[4300];
    if (failed >= 0 || errno != EACCES || !getenv("APKLAYER_BROKER") || !absolute(dirfd, path, full, sizeof full) || !eligible(full)) return failed;
    int saved = errno, fd = -1;
    int len = snprintf(request, sizeof request, "open%c%s%c%d%c%d%c", 0, full, 0, flags, 0, mode, 0);
    int rc = len > 0 && len < (int)sizeof request ? ask(request, len, &fd) : -1;
    if (rc == 0 && fd >= 0) {
        if (!(flags & O_CLOEXEC)) fcntl(fd, F_SETFD, 0);
        return fd;
    }
    errno = rc > 0 ? rc : saved;
    return failed;
}

int apklayer_broker_open(const char *path, int flags, int mode, int failed) { return apklayer_broker_openat(AT_FDCWD, path, flags, mode, failed); }

/* mkdir, unlink, rmdir, access (x: the mode) and rename (to: its second path) in the granted home; failed: the caller's result, 0 in its place
 * when apklayerd did it, else failed with its errno */
int apklayer_broker_change(const char *op, int dirfd, const char *path, int dirfd2, const char *to, int x, int failed)
{
    char full[4096], full2[4096], request[8400];
    if (failed == 0 || (errno != EACCES && errno != EPERM) || !getenv("APKLAYER_BROKER_HOME") || !absolute(dirfd, path, full, sizeof full) || !in_home(full))
        return failed;
    if (to && (!absolute(dirfd2, to, full2, sizeof full2) || !in_home(full2))) return failed;
    int saved = errno, len;
    if (to) len = snprintf(request, sizeof request, "%s%c%s%c%s%c", op, 0, full, 0, full2, 0);
    else if (!strcmp(op, "mkdir") || !strcmp(op, "access")) len = snprintf(request, sizeof request, "%s%c%s%c%d%c", op, 0, full, 0, x, 0);
    else len = snprintf(request, sizeof request, "%s%c%s%c", op, 0, full, 0);
    int rc = len > 0 && len < (int)sizeof request ? ask(request, len, NULL) : -1;
    if (rc == 0) return 0;
    errno = rc > 0 ? rc : saved;
    return failed;
}
