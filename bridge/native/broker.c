/* The sandbox's file broker, the app's side (cli/broker.cpp is the daemon's): an open the app's uid is refused (EACCES) that the user may make, of a
 * GPU node, of its own app log ($APKLAYER_BROKER_LOG) or in the granted home or the drives ($APKLAYER_BROKER_HOME), is made by apklayerd ($APKLAYER_BROKER, its
 * socket; all set by cli/sandbox.cpp) and its fd handed over; there also mkdir, unlink, rmdir, rename (renameat2 flags too), access, chmod, symlink, link, utimes and xattrs. A path relative to a
 * directory fd (or the working directory) is made absolute first. Any other refusal stays the caller's. */
#define _GNU_SOURCE
#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/stat.h>
#include <time.h>
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

/* One request: 0 ("ok", *fd set when one came with it) or the daemon's errno; -1 when apklayerd could not be asked.
 * With data: the reply is "ok <n>" NUL and up to cap bytes of data (an xattr's value or list); *ret = n */
static int ask_data(const char *request, int len, int *fd, char *data, size_t cap, long *ret)
{
    const char *socket_path = getenv("APKLAYER_BROKER");
    int s = socket_path ? socket(AF_UNIX, SOCK_SEQPACKET | SOCK_CLOEXEC, 0) : -1;
    if (s < 0) return -1;
    struct sockaddr_un addr = {.sun_family = AF_UNIX};
    snprintf(addr.sun_path, sizeof addr.sun_path, "%s", socket_path);
    int result = -1;
    if (connect(s, (struct sockaddr *)&addr, sizeof addr) == 0 && send(s, request, len, MSG_NOSIGNAL) == len) {
        size_t room = ret ? cap + 32 : 32;
        char *reply = calloc(1, room), control[CMSG_SPACE(sizeof(int))];
        struct iovec iov = {reply, room - 1};
        struct msghdr msg = {.msg_iov = &iov, .msg_iovlen = 1, .msg_control = control, .msg_controllen = sizeof control};
        ssize_t got = recvmsg(s, &msg, MSG_CMSG_CLOEXEC);
        struct cmsghdr *c = got > 0 ? CMSG_FIRSTHDR(&msg) : NULL;
        if (got >= 2 && !strncmp(reply, "ok", 2)) {
            result = 0;
            if (fd && c && c->cmsg_type == SCM_RIGHTS) memcpy(fd, CMSG_DATA(c), sizeof *fd);
            if (ret) {
                size_t head = strnlen(reply, got) + 1;
                *ret = got > 3 ? atol(reply + 3) : 0;
                if (data && head < (size_t)got) memcpy(data, reply + head, (size_t)got - head < cap ? (size_t)got - head : cap);
            }
        } else if (got > 4 && !strncmp(reply, "err ", 4)) {
            result = atoi(reply + 4);
        }
        free(reply);
    }
    close(s);
    return result;
}

static int ask(const char *request, int len, int *fd) { return ask_data(request, len, fd, NULL, 0, NULL); }

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

/* mkdir, unlink, rmdir, access, chmod (x: the mode), rename and link (to: its second path), symlink (to: the content) in the granted home; failed: the caller's result, 0 in its place
 * when apklayerd did it, else failed with its errno */
int apklayer_broker_change(const char *op, int dirfd, const char *path, int dirfd2, const char *to, int x, int failed)
{
    char full[4096], full2[4096], request[8400];
    if (failed == 0 || (errno != EACCES && errno != EPERM) || !getenv("APKLAYER_BROKER_HOME") || !absolute(dirfd, path, full, sizeof full) || !in_home(full))
        return failed;
    int sym = !strcmp(op, "symlink");  /* its second argument is the link's content, no path of the home */
    if (to && sym) snprintf(full2, sizeof full2, "%s", to);
    else if (to && (!absolute(dirfd2, to, full2, sizeof full2) || !in_home(full2))) return failed;
    int saved = errno, len;
    if (to) len = snprintf(request, sizeof request, "%s%c%s%c%s%c%d%c", op, 0, full, 0, full2, 0, x, 0);  /* x: renameat2's flags */
    else if (!strcmp(op, "mkdir") || !strcmp(op, "access") || !strcmp(op, "chmod")) len = snprintf(request, sizeof request, "%s%c%s%c%d%c", op, 0, full, 0, x, 0);
    else len = snprintf(request, sizeof request, "%s%c%s%c", op, 0, full, 0);
    int rc = len > 0 && len < (int)sizeof request ? ask(request, len, NULL) : -1;
    if (rc == 0) return 0;
    errno = rc > 0 ? rc : saved;
    return failed;
}

/* utimes, utimensat, futimens: atime and mtime as sec/nsec (UTIME_NOW, UTIME_OMIT as they are); NULL t: both now */
int apklayer_broker_utimes(int dirfd, const char *path, const struct timespec t[2], int nofollow, int failed)
{
    char full[4096], request[4400];
    if (failed == 0 || (errno != EACCES && errno != EPERM) || !getenv("APKLAYER_BROKER_HOME") || !absolute(dirfd, path, full, sizeof full) || !in_home(full)) return failed;
    int saved = errno;
    struct timespec now[2] = {{0, UTIME_NOW}, {0, UTIME_NOW}};
    if (!t) t = now;
    int len = snprintf(request, sizeof request, "utimes%c%s%c%ld %ld %ld %ld %d%c", 0, full, 0, (long)t[0].tv_sec, t[0].tv_nsec, (long)t[1].tv_sec, t[1].tv_nsec, nofollow, 0);
    int rc = len > 0 && len < (int)sizeof request ? ask(request, len, NULL) : -1;
    if (rc == 0) return 0;
    errno = rc > 0 ? rc : saved;
    return failed;
}

/* the path an open fd names (futimens, fsetxattr ...); 0 when it names none */
int apklayer_broker_fdpath(int fd, char *out, size_t n)
{
    char link[64];
    snprintf(link, sizeof link, "/proc/self/fd/%d", fd);
    ssize_t got = readlink(link, out, n - 1);
    if (got <= 0) return 0;
    out[got] = 0;
    return out[0] == '/';
}

/* setxattr, getxattr, listxattr, removexattr (op) of path in the granted home; nofollow: the l* forms. set: value, size, flags; get: buf, size (0: only the
 * length); list: buf, size; remove: name. The call's result (a length or 0), else failed with its errno.
 * shortcut: a value over 3000 bytes cannot be set (the request is one datagram, hex), a read one is cut at the caller's size; send the value apart when an app needs more. */
long apklayer_broker_xattr(const char *op, const char *path, const char *name, const void *value, void *buf, size_t size, int flags, int nofollow, long failed)
{
    char full[4096], request[8400];
    if (failed >= 0 || (errno != EACCES && errno != EPERM) || !getenv("APKLAYER_BROKER_HOME") || !absolute(AT_FDCWD, path, full, sizeof full) || !in_home(full)) return failed;
    int saved = errno, len;
    if (!strcmp(op, "setxattr")) {
        if (size > 3000) { errno = E2BIG; return failed; }
        len = snprintf(request, sizeof request, "%s%c%s%c%s%c%d%c%d%c", op, 0, full, 0, name, 0, nofollow, 0, flags, 0);
        if (len > 0 && len < (int)sizeof request - 7000) {
            for (size_t i = 0; i < size; i++) len += snprintf(request + len, sizeof request - len, "%02x", ((const unsigned char *)value)[i]);
            if (!size) request[len++] = '-';  /* an empty value is no empty field */
            request[len++] = 0;
        }
    } else if (!strcmp(op, "removexattr")) len = snprintf(request, sizeof request, "%s%c%s%c%s%c%d%c", op, 0, full, 0, name, 0, nofollow, 0);
    else if (!strcmp(op, "getxattr")) len = snprintf(request, sizeof request, "%s%c%s%c%s%c%d%c%zu%c", op, 0, full, 0, name, 0, nofollow, 0, size, 0);
    else len = snprintf(request, sizeof request, "%s%c%s%c%d%c%zu%c", op, 0, full, 0, nofollow, 0, size, 0);  /* listxattr */
    long ret = 0;
    int rc = len > 0 && len < (int)sizeof request ? ask_data(request, len, NULL, buf, size, &ret) : -1;
    if (rc == 0) return ret;
    errno = rc > 0 ? rc : saved;
    return failed;
}
