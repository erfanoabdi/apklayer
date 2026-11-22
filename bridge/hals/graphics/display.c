/* apklayer display HAL, host side: the shell protocol client (bridge/shell/shell.h) for dmabuf windows. */
#define _GNU_SOURCE
#include "apklayer/display.h"

#include <errno.h>
#include <pthread.h>
#include <signal.h>
#include <stdatomic.h>
#include <stdbool.h>
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
#define MSG_CLOSE 103
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
    void (*_Atomic configure_fn)(void *, int32_t, int32_t, int32_t, int32_t);
    _Atomic(void *) configure_user;
    _Atomic int64_t pending_cfg[4];   /* a configure that arrived before the callback was set (w, h, scale, turn; w = 0: none) */
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

static int64_t now_ns(void)
{
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (int64_t)ts.tv_sec * 1000000000 + ts.tv_nsec;
}

static void reply(struct apklayer_display *d, uint32_t type, uint32_t len, const int32_t body[64], int mfd)
{
    if (type == MSG_DISPLAY && len >= 4) {
        atomic_store(&d->refresh_mhz, (uint32_t)body[0]);
        if (d->cb.refresh) d->cb.refresh(d->cb.user, (uint32_t)body[0]);
    } else if (type == MSG_VSYNC && len >= 8) {
        /* the compositor's callback time is a 32 bit ms count of the monotonic clock: widen it to ns */
        int64_t now = now_ns(), ms = now / 1000000;
        int64_t ts = now - ((ms - (int64_t)(uint32_t)body[1]) & 0xffffffffLL) * 1000000;
        if (d->cb.vsync) d->cb.vsync(d->cb.user, ts);
    } else if (type == MSG_CONFIGURE && len >= 16) {
        void (*fn)(void *, int32_t, int32_t, int32_t, int32_t) = atomic_load(&d->configure_fn);
        int32_t turn = len >= 44 ? body[10] : 0;  /* the shell's turn (bridge/shell/geometry.c want_turn) */
        /* apklayer patch: the app's render scale (`apklayer settings` render-scale, cli/run.cpp): the display the app renders into is that share of
         * the window's pixels; the shell shows the buffer over the whole window (its viewport). Here, per launch, not in the shell: the shell is shared
         * by the instances and outlives an upgrade, so an app restart is enough for a changed setting. The Java host scales its side (AppHost). */
        static int render_pct;
        if (!render_pct) { const char *e = getenv("APKLAYER_RENDER_SCALE"); int v = e ? atoi(e) : 100; render_pct = v >= 25 && v <= 200 ? v : 100; }
        int32_t scale = render_pct == 100 ? body[3] : (int32_t)(((int64_t)body[3] * render_pct + 50) / 100);
        if (scale < 1) scale = 1;
        if (fn) fn(atomic_load(&d->configure_user), body[1], body[2], scale, turn);
        else { atomic_store(&d->pending_cfg[1], body[2]); atomic_store(&d->pending_cfg[2], scale); atomic_store(&d->pending_cfg[3], turn); atomic_store(&d->pending_cfg[0], body[1]); }  /* apklayer patch: replayed by set_configure_callback */
    } else if (type == MSG_NAVBAR_PIXELS && len >= 12) {
        void (*fn)(void *, int, int32_t, int32_t) = atomic_load(&d->navbar_fn);
        if (fn) { fn(atomic_load(&d->navbar_user), mfd, body[1], body[2]); mfd = -1; }  /* the callback owns the fd */
    } else if (type == MSG_DMABUF_RELEASE && len >= 8) {
        if (d->cb.released) d->cb.released(d->cb.user, body[1]);
    } else if (type == MSG_CLOSE && len >= 4 && body[0] == WINDOW_ID) {
        /* apklayer patch: the user closed the window before the app took it over (the shell sends CLOSE to the app once it has): the launch
         * ends, as `apklayer stop` would. The launcher names itself (APKLAYER_CLOSE_ENDS, cli/run.cpp); it forwards the signal to the JVM.
         * Before, a JVM stuck at its splash ran on without a window and the next launch was handed to it (Play Store, 2026-11-21). */
        const char *ends = getenv("APKLAYER_CLOSE_ENDS");
        pid_t launcher = ends ? (pid_t)atoi(ends) : 0;
        if (launcher > 1 && launcher == getppid()) { fprintf(stderr, "apklayer.display: window closed before the app took it: ending launch %d\n", (int)launcher); kill(launcher, SIGTERM); }
    }
    if (mfd >= 0) close(mfd);
}

static void *reader(void *arg)
{
    struct apklayer_display *d = arg;
    uint8_t rx[4096];
    size_t used = 0;
    uint32_t skip = 0;
    int32_t body[64];
    int fds[8];
    size_t nfds = 0;

    /* Release and vsync traffic usually fits in a single receive. Preserve partial messages
     * on the stream and queue descriptors in order, just as the shell queues DMA-BUF planes. */
    for (;;) {
        char control[CMSG_SPACE(sizeof fds)];
        struct iovec iov = { rx + used, sizeof rx - used };
        struct msghdr mh = { .msg_iov = &iov, .msg_iovlen = 1, .msg_control = control, .msg_controllen = sizeof control };
        ssize_t n = recvmsg(d->fd, &mh, MSG_CMSG_CLOEXEC);
        if (n < 0 && errno == EINTR) continue;
        if (n <= 0) break;
        used += (size_t)n;
        size_t previous_nfds = nfds;
        bool overflow = false;
        for (struct cmsghdr *cm = CMSG_FIRSTHDR(&mh); cm; cm = CMSG_NXTHDR(&mh, cm)) {
            if (cm->cmsg_level != SOL_SOCKET || cm->cmsg_type != SCM_RIGHTS) continue;
            size_t count = (cm->cmsg_len - CMSG_LEN(0)) / sizeof(int);
            for (size_t i = 0; i < count; ++i) {
                int fd;
                memcpy(&fd, CMSG_DATA(cm) + i * sizeof fd, sizeof fd);
                if (nfds < sizeof fds / sizeof *fds) fds[nfds++] = fd;
                else { close(fd); overflow = true; }
            }
        }
        if (overflow) break;
        if (mh.msg_flags & MSG_CTRUNC) {
            /* Linux can discard the navbar's sole fd when this process hits its fd limit.
             * Keep its place in the queue so a later fd still goes to its own reply. */
            if (nfds != previous_nfds || nfds == sizeof fds / sizeof *fds) break;
            fds[nfds++] = -1;
        }

        size_t off = skip < used ? skip : used;
        skip -= (uint32_t)off;
        while (used - off >= 8) {
            uint32_t head[2];
            memcpy(head, rx + off, sizeof head);
            uint32_t len = head[1];
            size_t available = used - off - 8;
            if (len > sizeof body && len > available) {
                /* The display ignores larger clipboard and other messages. Drain them
                 * incrementally so a long payload never fills the receive buffer. */
                skip = len - (uint32_t)available;
                off = used;
                break;
            }
            if (len > available) break;
            if (len <= sizeof body) {
                memcpy(body, rx + off + 8, len);
                int mfd = -1;
                /* Only a visible navbar carries an fd. A hide has zero dimensions. */
                if (head[0] == MSG_NAVBAR_PIXELS && len >= 12 && body[1] > 0 && body[2] > 0 && nfds) {
                    mfd = fds[0];
                    --nfds;
                    memmove(fds, fds + 1, nfds * sizeof *fds);
                }
                reply(d, head[0], len, body, mfd);
            }
            off += 8 + len;
        }
        used -= off;
        if (used && off) memmove(rx, rx + off, used);
    }
    for (size_t i = 0; i < nfds; ++i)
        if (fds[i] >= 0) close(fds[i]);
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
    const char *mode = getenv("APKLAYER_WINDOW_MODE"), *nb = getenv("APKLAYER_NAVBAR");  /* nb: the navigation bar on desktop windows: auto, on, off */
    if ((mode && *mode) || (nb && *nb)) {
        if (!mode) mode = "auto";
        int32_t wm[3] = { WINDOW_ID, !strcmp(mode, "mobile") ? 2 : !strcmp(mode, "desktop") ? 1 : 0, nb && !strcmp(nb, "on") ? 1 : nb && !strcmp(nb, "off") ? 2 : 0 };
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

void apklayer_display_set_configure_callback(struct apklayer_display *d, void (*configure)(void *, int32_t, int32_t, int32_t, int32_t), void *user)
{
    atomic_store(&d->configure_user, user);
    atomic_store(&d->configure_fn, configure);
    int64_t pw = atomic_exchange(&d->pending_cfg[0], 0);
    if (pw > 0 && configure)
        configure(user, (int32_t)pw, (int32_t)atomic_load(&d->pending_cfg[1]), (int32_t)atomic_load(&d->pending_cfg[2]), (int32_t)atomic_load(&d->pending_cfg[3]));
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
    int32_t m[21] = { 0 };
    /* apklayer patch: the render filter (`apklayer settings` render-filter sharp, cli/run.cpp): a flags word after the damage; a shell that does not know
     * it (len 80) ignores it, one that does resamples a buffer that is not the window's pixels with the upscale pass (bridge/shell/sharpen.c) */
    { static int filter = -1; if (filter < 0) { const char *e = getenv("APKLAYER_RENDER_FILTER"); filter = e && !strcmp(e, "sharp") ? 1 : 0;
        if (e && !strcmp(e, "nis")) filter = 3;  /* bit 0: on; bits 4-6: phases; bits 1-3: 0 FSR 1, 1 NIS, 2 SGSR 1, 3 Lanczos 3, 4 Anime4K */
        if (e && !strcmp(e, "sgsr")) filter = 5;
        if (e && !strcmp(e, "lanczos")) filter = 7;
        if (e && !strcmp(e, "anime4k")) filter = 9;
        if (e && !strcmp(e, "auto")) filter = 11;  /* 5: the shell picks the mode by the ratio window/frame (sharpen_win.c) */
        const char *ph = getenv("APKLAYER_RENDER_PHASES");  /* bits 4-6: the phases of the NIS/SGSR/Lanczos upscale (1..4, 0: the shell's default) */
        if (ph && atoi(ph) >= 1 && atoi(ph) <= 4) filter |= atoi(ph) << 4;
        const char *aa = getenv("APKLAYER_RENDER_AA");  /* bits 7-8: the anti-aliasing after an upscale: 1 off, 2 low, 3 high (0: the shell's default, low) */
        if (aa) filter |= (!strcmp(aa, "off") ? 1 : !strcmp(aa, "low") ? 2 : !strcmp(aa, "high") ? 3 : 0) << 7;
    } m[20] = filter; }
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
