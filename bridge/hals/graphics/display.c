/* apklayer display HAL, host side: the shell protocol client (bridge/shell/shell.h) for dmabuf windows. */
#define _GNU_SOURCE
#include "apklayer/display.h"

#include <errno.h>
#include <poll.h>
#include <pthread.h>
#include <sys/inotify.h>
#include <stdatomic.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <time.h>
#include <unistd.h>

#define MSG_HELLO 1
#define MSG_WINDOW_CREATE 2
#define MSG_VSYNC_REQUEST 7
#define MSG_DMABUF_FRAME 8
#define MSG_VSYNC 113
#define MSG_DISPLAY 112
#define MSG_DMABUF_RELEASE 114
#define WINDOW_ID 1
#define SLOTS 8

struct apklayer_display {
    int fd;
    struct apklayer_display_callbacks cb;
    pthread_t reader;
    pthread_mutex_t send_lock;
    _Atomic uint32_t refresh_mhz;
    _Atomic int closing;
    uint8_t sent[SLOTS]; /* the slot's buffer was sent to the shell */
};

static int send_msg(struct apklayer_display *d, uint32_t type, const void *payload, uint32_t len, const int *fds, int nfds)
{
    uint8_t buf[8 + 256];
    if (len > 256) return -EMSGSIZE;
    memcpy(buf, &type, 4);
    memcpy(buf + 4, &len, 4);
    if (len) memcpy(buf + 8, payload, len);
    struct iovec iov = { buf, 8 + len };
    char control[CMSG_SPACE(sizeof(int) * 4)] = { 0 };
    struct msghdr mh = { .msg_iov = &iov, .msg_iovlen = 1 };
    if (nfds > 0) {
        mh.msg_control = control;
        mh.msg_controllen = CMSG_SPACE(sizeof(int) * nfds);
        struct cmsghdr *cm = CMSG_FIRSTHDR(&mh);
        cm->cmsg_level = SOL_SOCKET;
        cm->cmsg_type = SCM_RIGHTS;
        cm->cmsg_len = CMSG_LEN(sizeof(int) * nfds);
        memcpy(CMSG_DATA(cm), fds, sizeof(int) * nfds);
    }
    pthread_mutex_lock(&d->send_lock);
    ssize_t n = sendmsg(d->fd, &mh, MSG_NOSIGNAL);
    pthread_mutex_unlock(&d->send_lock);
    return n == (ssize_t)(8 + len) ? 0 : -(errno ? errno : EIO);
}

static int read_full(int fd, void *p, size_t n)
{
    uint8_t *b = p;
    while (n) {
        ssize_t r = read(fd, b, n);
        if (r <= 0) return -1;
        b += r;
        n -= (size_t)r;
    }
    return 0;
}

static int64_t now_ns(void)
{
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (int64_t)ts.tv_sec * 1000000000 + ts.tv_nsec;
}

static void *reader(void *arg)
{
    struct apklayer_display *d = arg;
    for (;;) {
        uint32_t head[2];
        if (read_full(d->fd, head, sizeof head) < 0) break;
        int32_t body[64] = { 0 };
        uint32_t len = head[1];
        if (len > sizeof body) { /* skip what we do not understand */
            char skip[256];
            while (len) { size_t n = len < sizeof skip ? len : sizeof skip; if (read_full(d->fd, skip, n) < 0) goto out; len -= (uint32_t)n; }
            continue;
        }
        if (len && read_full(d->fd, body, len) < 0) break;
        if (head[0] == MSG_DISPLAY && len >= 4) {
            atomic_store(&d->refresh_mhz, (uint32_t)body[0]);
            if (d->cb.refresh) d->cb.refresh(d->cb.user, (uint32_t)body[0]);
        } else if (head[0] == MSG_VSYNC && len >= 8) {
            /* the compositor's callback time is a 32 bit ms count of the monotonic clock: widen it to ns */
            int64_t now = now_ns(), ms = now / 1000000;
            int64_t ts = now - ((ms - (int64_t)(uint32_t)body[1]) & 0xffffffffLL) * 1000000;
            if (d->cb.vsync) d->cb.vsync(d->cb.user, ts);
        } else if (head[0] == MSG_DMABUF_RELEASE && len >= 8) {
            if (d->cb.released) d->cb.released(d->cb.user, body[1]);
        }
    }
out:
    return NULL;
}

/* Connects to the shell's socket; if it does not exist yet (the shell is starting) waits for it to appear, up to 5 s. */
static int connect_shell(int fd, const struct sockaddr_un *a)
{
    int inotify = -1;
    int64_t deadline = now_ns() + 5000000000LL;
    for (;;) {
        if (connect(fd, (const struct sockaddr *)a, sizeof *a) == 0) { if (inotify >= 0) close(inotify); return 0; }
        if (errno != ENOENT && errno != ECONNREFUSED) break;
        int64_t left = deadline - now_ns();
        if (left <= 0) break;
        if (inotify < 0) {
            char dir[sizeof a->sun_path];
            snprintf(dir, sizeof dir, "%s", a->sun_path);
            char *slash = strrchr(dir, '/');
            if (!slash) break;
            *slash = 0;
            inotify = inotify_init1(IN_CLOEXEC);
            if (inotify < 0 || inotify_add_watch(inotify, dir, IN_CREATE | IN_MOVED_TO) < 0) break;
            continue; /* the socket may have appeared before the watch: try again first */
        }
        struct pollfd p = { inotify, POLLIN, 0 };
        if (poll(&p, 1, (int)(left / 1000000)) <= 0) break;
        char ev[512];
        if (read(inotify, ev, sizeof ev) < 0) break;
    }
    int e = errno;
    if (inotify >= 0) close(inotify);
    errno = e;
    return -1;
}

struct apklayer_display *apklayer_display_open(const char *name, uint32_t width, uint32_t height,
                                               const struct apklayer_display_callbacks *callbacks)
{
    struct apklayer_display *d = calloc(1, sizeof *d);
    if (!d) return NULL;
    if (callbacks) d->cb = *callbacks;
    pthread_mutex_init(&d->send_lock, NULL);
    d->fd = socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0);
    struct sockaddr_un a = { .sun_family = AF_UNIX };
    const char *path = getenv("APKLAYER_SHELL"), *rt = getenv("XDG_RUNTIME_DIR");
    if (path) snprintf(a.sun_path, sizeof a.sun_path, "%s", path);
    else snprintf(a.sun_path, sizeof a.sun_path, "%s/apklayer/shell.sock", rt ? rt : "/tmp");
    if (d->fd < 0 || connect_shell(d->fd, &a) < 0) { int e = errno; close(d->fd); free(d); errno = e; return NULL; }

    char hello[4 + 128];
    int32_t pid = getpid();
    memcpy(hello, &pid, 4);
    size_t nl = strnlen(name, 100);
    memcpy(hello + 4, name, nl);
    send_msg(d, MSG_HELLO, hello, (uint32_t)(4 + nl), NULL, 0);
    char create[12 + 2 + 64];
    int32_t hdr[3] = { WINDOW_ID, (int32_t)width, (int32_t)height };
    memcpy(create, hdr, 12);
    create[12] = 0; /* empty shm path: a dmabuf-only window */
    size_t tl = strnlen(name, 60);
    memcpy(create + 13, name, tl);
    send_msg(d, MSG_WINDOW_CREATE, create, (uint32_t)(13 + tl), NULL, 0);
    pthread_create(&d->reader, NULL, reader, d);
    return d;
}

void apklayer_display_close(struct apklayer_display *d)
{
    if (!d) return;
    shutdown(d->fd, SHUT_RDWR);
    pthread_join(d->reader, NULL);
    close(d->fd);
    free(d);
}

uint32_t apklayer_display_refresh_mhz(struct apklayer_display *d) { return atomic_load(&d->refresh_mhz); }

int apklayer_display_request_vsync(struct apklayer_display *d)
{
    int32_t id = WINDOW_ID;
    return send_msg(d, MSG_VSYNC_REQUEST, &id, 4, NULL, 0);
}

void apklayer_display_invalidate_slot(struct apklayer_display *d, int slot)
{
    if (slot >= 0 && slot < SLOTS) d->sent[slot] = 0;
}

int apklayer_display_present(struct apklayer_display *d, int slot, const struct apklayer_gfx_buffer *buf, int32_t damage[4])
{
    if (slot < 0 || slot >= SLOTS) return -EINVAL;
    int32_t m[20] = { 0 };
    m[0] = WINDOW_ID;
    m[1] = slot;
    m[2] = (int32_t)buf->fourcc;
    m[3] = (int32_t)buf->width;
    m[4] = (int32_t)buf->height;
    m[5] = d->sent[slot] ? 0 : buf->num_planes;
    m[6] = (int32_t)(buf->modifier & 0xffffffff);
    m[7] = (int32_t)(buf->modifier >> 32);
    for (int i = 0; i < buf->num_planes && i < 4; i++) { m[8 + i] = (int32_t)buf->stride[i]; m[12 + i] = (int32_t)buf->offset[i]; }
    if (damage) memcpy(m + 16, damage, 16);
    else { m[18] = (int32_t)buf->width; m[19] = (int32_t)buf->height; }
    if (!m[18] || !m[19]) { m[18] = (int32_t)buf->width; m[19] = (int32_t)buf->height; }
    int r = send_msg(d, MSG_DMABUF_FRAME, m, sizeof m, m[5] ? buf->fd : NULL, m[5]);
    if (r == 0) d->sent[slot] = 1;
    return r;
}
