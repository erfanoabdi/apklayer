/* apklayer display HAL, host side: the shell protocol client (bridge/shell/shell.h) for dmabuf windows. */
#define _GNU_SOURCE
#include "apklayer/display.h"

#include <errno.h>
#include <pthread.h>
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
#define MSG_GRALLOC_FRAME 14
#define MSG_VSYNC 113
#define MSG_DISPLAY 112
#define MSG_DMABUF_RELEASE 114
#define MSG_CONFIGURE 107
#define MSG_NAVBAR_LAYER 17
#define MSG_NAVBAR_PIXELS 116
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
    int headless;
    void (*_Atomic configure_fn)(void *, int32_t, int32_t, int32_t);
    _Atomic(void *) configure_user;
    _Atomic int64_t pending_cfg[3];   /* a configure that arrived before the callback was set (w, h, scale; w = 0: none) */
    void (*_Atomic navbar_fn)(void *, int, int32_t, int32_t);
    _Atomic(void *) navbar_user;
};

static int send_msg(struct apklayer_display *d, uint32_t type, const void *payload, uint32_t len, const int *fds, int nfds)
{
    uint8_t buf[8 + 1024];  /* GRALLOC_FRAME carries a native_handle's ints */
    if (len > 1024 || nfds > 8) return -EMSGSIZE;
    memcpy(buf, &type, 4);
    memcpy(buf + 4, &len, 4);
    if (len) memcpy(buf + 8, payload, len);
    struct iovec iov = { buf, 8 + len };
    char control[CMSG_SPACE(sizeof(int) * 8)] = { 0 };
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

/* A message's header, with the fd that came along (SCM_RIGHTS arrives with a message's first byte; NAVBAR_PIXELS carries one), else -1. */
static int read_head(int fd, uint32_t head[2], int *rfd)
{
    char control[CMSG_SPACE(sizeof(int) * 8)];
    struct iovec iov = { head, 8 };
    struct msghdr mh = { .msg_iov = &iov, .msg_iovlen = 1, .msg_control = control, .msg_controllen = sizeof control };
    ssize_t r = recvmsg(fd, &mh, MSG_CMSG_CLOEXEC);
    if (r <= 0) return -1;
    *rfd = -1;
    for (struct cmsghdr *cm = CMSG_FIRSTHDR(&mh); cm; cm = CMSG_NXTHDR(&mh, cm)) {
        if (cm->cmsg_level != SOL_SOCKET || cm->cmsg_type != SCM_RIGHTS) continue;
        int n = (int)((cm->cmsg_len - CMSG_LEN(0)) / sizeof(int));
        for (int i = 0; i < n; i++) {
            int f;
            memcpy(&f, CMSG_DATA(cm) + i * sizeof(int), sizeof f);
            if (*rfd < 0) *rfd = f; else close(f);
        }
    }
    return r < 8 ? read_full(fd, (uint8_t *)head + r, 8 - (size_t)r) : 0;
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
        int mfd = -1;
        if (read_head(d->fd, head, &mfd) < 0) break;
        int32_t body[64] = { 0 };
        uint32_t len = head[1];
        if (mfd >= 0 && head[0] != MSG_NAVBAR_PIXELS) { close(mfd); mfd = -1; }
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
        } else if (head[0] == MSG_CONFIGURE && len >= 16) {
            void (*fn)(void *, int32_t, int32_t, int32_t) = atomic_load(&d->configure_fn);
            if (fn) fn(atomic_load(&d->configure_user), body[1], body[2], body[3]);
            else { atomic_store(&d->pending_cfg[1], body[2]); atomic_store(&d->pending_cfg[2], body[3]); atomic_store(&d->pending_cfg[0], body[1]); }  /* apklayer patch: replayed by set_configure_callback */
        } else if (head[0] == MSG_NAVBAR_PIXELS && len >= 12) {
            void (*fn)(void *, int, int32_t, int32_t) = atomic_load(&d->navbar_fn);
            if (fn) { fn(atomic_load(&d->navbar_user), mfd, body[1], body[2]); mfd = -1; }  /* the callback owns the fd */
        } else if (head[0] == MSG_DMABUF_RELEASE && len >= 8) {
            if (d->cb.released) d->cb.released(d->cb.user, body[1]);
        }
        if (mfd >= 0) close(mfd);
    }
out:
    return NULL;
}

static struct apklayer_display *display_open(const char *name, uint32_t width, uint32_t height,
                                             const struct apklayer_display_callbacks *callbacks, int headless)
{
    struct apklayer_display *d = calloc(1, sizeof *d);
    if (!d) return NULL;
    d->headless = headless;
    if (callbacks) d->cb = *callbacks;
    pthread_mutex_init(&d->send_lock, NULL);
    d->fd = socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0);
    struct sockaddr_un a = { .sun_family = AF_UNIX };
    const char *path = getenv("APKLAYER_SHELL"), *rt = getenv("XDG_RUNTIME_DIR");
    if (path) snprintf(a.sun_path, sizeof a.sun_path, "%s", path);
    else snprintf(a.sun_path, sizeof a.sun_path, "%s/apklayer/shell.sock", rt ? rt : "/tmp");
    /* the CLI starts the shell and returns once it listens (ensureShell): no waiting here */
    if (d->fd < 0 || connect(d->fd, (const struct sockaddr *)&a, sizeof a) < 0) { int e = errno; close(d->fd); free(d); errno = e; return NULL; }

    char hello[4 + 128];
    int32_t pid = getpid();
    memcpy(hello, &pid, 4);
    size_t nl = strnlen(name, 100);
    memcpy(hello + 4, name, nl);
    send_msg(d, MSG_HELLO, hello, (uint32_t)(4 + nl), NULL, 0);
    /* apklayer patch: the window's style (`apklayer settings` window-mode, cli/run.cpp), before the window, so it is created in it */
    const char *mode = getenv("APKLAYER_WINDOW_MODE");
    if (mode && *mode) {
        int32_t wm[2] = { WINDOW_ID, !strcmp(mode, "mobile") ? 2 : !strcmp(mode, "desktop") ? 1 : 0 };
        send_msg(d, 15 /* MSG_WINDOW_MODE */, wm, sizeof wm, NULL, 0);
    }
    char create[12 + 2 + 64];
    int32_t hdr[3] = { WINDOW_ID, (int32_t)width, (int32_t)height };
    memcpy(create, hdr, 12);
    create[12] = 0; /* empty shm path: a dmabuf-only window */
    size_t tl = strnlen(name, 60);
    memcpy(create + 13, name, tl);
    if (!headless) send_msg(d, MSG_WINDOW_CREATE, create, (uint32_t)(13 + tl), NULL, 0);
    pthread_create(&d->reader, NULL, reader, d);
    return d;
}

struct apklayer_display *apklayer_display_open(const char *name, uint32_t width, uint32_t height,
                                               const struct apklayer_display_callbacks *callbacks)
{
    return display_open(name, width, height, callbacks, 0);
}

struct apklayer_display *apklayer_display_open_headless(const char *name,
                                                      const struct apklayer_display_callbacks *callbacks)
{
    return display_open(name, 0, 0, callbacks, 1);
}

void apklayer_display_close(struct apklayer_display *d)
{
    if (!d) return;
    shutdown(d->fd, SHUT_RDWR);
    pthread_join(d->reader, NULL);
    close(d->fd);
    free(d);
}

void apklayer_display_set_configure_callback(struct apklayer_display *d, void (*configure)(void *, int32_t, int32_t, int32_t), void *user)
{
    atomic_store(&d->configure_user, user);
    atomic_store(&d->configure_fn, configure);
    int64_t pw = atomic_exchange(&d->pending_cfg[0], 0);
    if (pw > 0 && configure) configure(user, (int32_t)pw, (int32_t)atomic_load(&d->pending_cfg[1]), (int32_t)atomic_load(&d->pending_cfg[2]));
}

void apklayer_display_set_navbar_callback(struct apklayer_display *d, void (*navbar)(void *, int, int32_t, int32_t), void *user)
{
    atomic_store(&d->navbar_user, user);
    atomic_store(&d->navbar_fn, navbar);
    int32_t id = WINDOW_ID;
    if (!d->headless) send_msg(d, MSG_NAVBAR_LAYER, &id, 4, NULL, 0);
}

uint32_t apklayer_display_refresh_mhz(struct apklayer_display *d) { return atomic_load(&d->refresh_mhz); }

int apklayer_display_request_vsync(struct apklayer_display *d)
{
    int32_t id = WINDOW_ID;
    return send_msg(d, MSG_VSYNC_REQUEST, &id, 4, NULL, 0);
}

int apklayer_display_present_native(struct apklayer_display *d, int slot, const struct apklayer_native_buffer *buf, int32_t damage[4])
{
    if (slot < 0 || slot >= SLOTS || buf->num_fds < 0 || buf->num_fds > 8 || buf->num_ints < 0 || buf->num_ints > 240) return -EINVAL;
    if (d->headless) return 0;
    int32_t m[13 + 240] = { 0 };
    int first = !d->sent[slot];
    m[0] = WINDOW_ID;
    m[1] = slot;
    m[2] = (int32_t)buf->width;
    m[3] = (int32_t)buf->height;
    m[4] = (int32_t)buf->stride;
    m[5] = (int32_t)buf->format;
    m[6] = (int32_t)(uint32_t)buf->usage;
    m[7] = first ? buf->num_fds : 0;
    m[8] = first ? buf->num_ints : 0;
    if (damage && damage[2] && damage[3]) memcpy(m + 9, damage, 16);
    else { m[11] = (int32_t)buf->width; m[12] = (int32_t)buf->height; }
    if (first) memcpy(m + 13, buf->ints, sizeof(int32_t) * (size_t)buf->num_ints);
    int r = send_msg(d, MSG_GRALLOC_FRAME, m, (uint32_t)(13 + m[8]) * 4, first ? buf->fds : NULL, m[7]);
    if (r == 0) d->sent[slot] = 1;
    return r;
}

void apklayer_display_invalidate_slot(struct apklayer_display *d, int slot)
{
    if (slot >= 0 && slot < SLOTS) d->sent[slot] = 0;
}

int apklayer_display_present(struct apklayer_display *d, int slot, const struct apklayer_gfx_buffer *buf, int32_t damage[4])
{
    if (slot < 0 || slot >= SLOTS) return -EINVAL;
    if (d->headless) return 0; /* no consumer takes ownership of an invisible primary's buffers */
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
