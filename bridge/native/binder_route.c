/* The userspace binder's side in every Android process (docs/BINDER.md): with APKLAYER_BINDER=userspace, bionic_translation's libc hands
 * open/ioctl/mmap/close of binder fds to these hooks (its fd hooks, patches/bionic_translation/0046; the path layer's glibc-level opens too)
 * and they talk to the instance's daemon (bridge/binderd, protocol.h) instead of a kernel driver. Opening /dev/binder (as the path layer maps
 * it) connects a proc socket; each thread gets a socket of its own on first use, so blocking reads of several threads wait side by side, and a
 * shared buffer (a sealed memfd the daemon maps read-only): a BINDER_WRITE_READ's write stream is marshalled into it (transaction data, offsets
 * and scatter-gather buffers inline, fds as SCM_RIGHTS on the request) and the daemon copies from there into the target's receive area. The
 * receive area is the daemon's memfd: mapped read-only where libbinder reads it, writable once more here for the fd fixups. In the LD_PRELOAD
 * library: one copy per process. */
#define _GNU_SOURCE
#include <errno.h>
#include <fcntl.h>
#include <linux/android/binder.h>
#include <pthread.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <sys/socket.h>
#include <sys/syscall.h>
#include <sys/un.h>
#include <unistd.h>

#include "../binderd/protocol.h"

#define MAX_CONNS 8

struct fda_entry { uint64_t buffer; int fd; };

struct conn {
    int fd;          /* the proc socket: the binder fd the caller holds; -1: free slot */
    unsigned gen;    /* bumped when the slot is reused: threads' sockets of an old generation are stale */
    uint64_t cookie;
    char path[sizeof(((struct sockaddr_un *)0)->sun_path)];
    bool nonblock;
    void *ro, *rw;   /* the receive area: libbinder's mapping, ours */
    size_t size;
    pthread_mutex_t lock; /* fdas */
    struct fda_entry *fdas;
    size_t nfdas, cap;
};

static struct conn conns[MAX_CONNS] = {[0 ... MAX_CONNS - 1] = {.fd = -1, .lock = PTHREAD_MUTEX_INITIALIZER}};
static pthread_mutex_t conns_lock = PTHREAD_MUTEX_INITIALIZER;
static int enabled = -1, debug;
static const char *sock_dir, *instance;

static void fda_add(struct conn *c, uint64_t buffer, int fd)
{
    pthread_mutex_lock(&c->lock);
    if (c->nfdas == c->cap) {
        size_t cap = c->cap ? c->cap * 2 : 16;
        struct fda_entry *n = realloc(c->fdas, cap * sizeof *n);
        if (n) { c->fdas = n; c->cap = cap; }
    }
    if (c->nfdas < c->cap) c->fdas[c->nfdas++] = (struct fda_entry){buffer, fd};
    pthread_mutex_unlock(&c->lock);
}

/* BC_FREE_BUFFER: the fds of the buffer's fd arrays are closed, as the kernel does */
static void fda_free(struct conn *c, uint64_t buffer)
{
    pthread_mutex_lock(&c->lock);
    for (size_t i = 0; i < c->nfdas;) {
        if (c->fdas[i].buffer == buffer) { close(c->fdas[i].fd); c->fdas[i] = c->fdas[--c->nfdas]; }
        else i++;
    }
    pthread_mutex_unlock(&c->lock);
}

#include "binder_route_write.h"

/* a thread's side: per binder fd its socket and shared buffer, and buffers reused by every request */
struct tslot { int sock; unsigned gen; uint8_t *tx; };
struct tstate {
    struct tslot slot[MAX_CONNS];
    uint8_t *rx;   /* a reply (BD_MAX_MSG) */
    int *fds;      /* a reply's fds */
    uint32_t fcap;
    struct wbuf w; /* the marshalling's fd and command lists */
};
static __thread struct tstate *ts;
static pthread_key_t thread_key;
static pthread_once_t key_once = PTHREAD_ONCE_INIT;

static void drop_slot(struct tslot *s)
{
    if (s->sock >= 0) close(s->sock);
    if (s->tx) munmap(s->tx, BD_TX_SIZE);
    s->sock = -1;
    s->tx = NULL;
}

static void thread_gone(void *p)
{
    struct tstate *s = p;
    for (int i = 0; i < MAX_CONNS; i++) drop_slot(&s->slot[i]);  /* the daemon releases the thread */
    free(s->rx);
    free(s->fds);
    wbuf_free(&s->w);
    free(s);
    ts = NULL;
}

static void make_key(void) { pthread_key_create(&thread_key, thread_gone); }

static struct tstate *tstate(void)
{
    if (ts) return ts;
    struct tstate *s = calloc(1, sizeof *s);
    if (!s || !(s->rx = malloc(BD_MAX_MSG)) || !(s->fds = malloc(sizeof(int) * BD_MAX_FDS))) { if (s) free(s->rx); free(s); return NULL; }
    s->fcap = BD_MAX_FDS;
    for (int i = 0; i < MAX_CONNS; i++) s->slot[i].sock = -1;
    pthread_once(&key_once, make_key);
    pthread_setspecific(thread_key, s);
    return ts = s;
}

static bool on(void)
{
    if (enabled < 0) {
        const char *b = getenv("APKLAYER_BINDER");
        sock_dir = getenv("APKLAYER_BINDER_DIR");
        instance = getenv("APKLAYER_INSTANCE");
        if (!instance || !instance[0]) instance = "default";
        enabled = b && !strcmp(b, "userspace") && sock_dir && sock_dir[0];
        debug = getenv("APKLAYER_BINDER_DEBUG") != NULL;
    }
    return enabled;
}

static struct conn *find(int fd)
{
    if (fd < 0) return NULL;
    for (int i = 0; i < MAX_CONNS; i++)
        if (conns[i].fd == fd) return &conns[i];
    return NULL;
}

static int request(int sock, uint32_t op, uint64_t a0, uint64_t a1, uint64_t a2, uint64_t a3, const void *payload, uint32_t plen,
                   const int *fds, uint32_t nfds)
{
    struct bd_req h = {.len = sizeof h + plen, .op = op, .nfds = nfds, .a0 = a0, .a1 = a1, .a2 = a2, .a3 = a3};
    return sizeof h + plen > BD_MAX_MSG ? -EINVAL : bd_send(sock, &h, sizeof h, payload, plen, fds, nfds);
}

/* a reply into the thread's buffers: *h, its payload (in s->rx) and fds (s->fds) */
static int reply(struct tstate *s, int sock, struct bd_rep *h, const uint8_t **payload)
{
    uint32_t got = 0;
    int r = bd_recv(sock, s->rx, BD_MAX_MSG, s->fds, &got);
    memset(h, 0, sizeof *h);
    if (r >= 0 && (uint32_t)r >= sizeof *h) memcpy(h, s->rx, sizeof *h);
    if (r < 0 || (uint32_t)r < sizeof *h || h->len != (uint32_t)r || h->nfds < got ||
        (uint64_t)h->read_consumed + (uint64_t)h->nfix * sizeof(struct bd_fix) > r - sizeof *h)
        r = r < 0 ? r : -EPROTO;
    else if (h->nfds > s->fcap) {
        int *f = realloc(s->fds, sizeof(int) * h->nfds);
        if (f) { s->fds = f; s->fcap = h->nfds; }
        else r = -ENOMEM;
    }
    if (r >= 0) r = bd_recv_more_fds(sock, s->fds, got, h->nfds);
    if (r < 0) {  /* nothing of a broken reply is kept */
        for (uint32_t i = 0; i < got; i++) close(s->fds[i]);
        h->nfds = 0;
        return r;
    }
    *payload = s->rx + sizeof *h;
    return 0;
}

static void close_reply_fds(struct tstate *s, const struct bd_rep *h)
{
    for (uint32_t i = 0; i < h->nfds; i++) close(s->fds[i]);
}

static int connect_to(const char *path, int flags)
{
    int s = socket(AF_UNIX, SOCK_SEQPACKET | ((flags & O_CLOEXEC) ? SOCK_CLOEXEC : 0), 0);
    struct sockaddr_un a = {.sun_family = AF_UNIX};
    if (s < 0) return -errno;
    snprintf(a.sun_path, sizeof a.sun_path, "%s", path);
    if (connect(s, (struct sockaddr *)&a, sizeof a) != 0) { int e = errno; close(s); return -e; }
    return s;
}

/* the daemon socket and context of a binder path (after the path layer): /dev/<ctx> is this instance's, /dev/binderfs/<ctx>-<id> instance id's
 * when that instance runs a userspace binder; anything else (a Halium device's own /dev/binderfs/binder) stays the kernel's */
static bool route_of(const char *path, char *sock, size_t n, char *ctx, size_t cn)
{
    static const char *const names[] = {"binder", "hwbinder", "vndbinder", NULL};
    for (int i = 0; names[i]; i++) {
        size_t l = strlen(names[i]);
        if (!strncmp(path, "/dev/", 5) && !strcmp(path + 5, names[i])) {
            snprintf(sock, n, "%s/%s.sock", sock_dir, instance);
            snprintf(ctx, cn, "%s", names[i]);
            return true;
        }
        if (!strncmp(path, "/dev/binderfs/", 14) && !strncmp(path + 14, names[i], l) && path[14 + l] == '-') {
            snprintf(sock, n, "%s/%s.sock", sock_dir, path + 15 + l);
            snprintf(ctx, cn, "%s", names[i]);
            return !strcmp(path + 15 + l, instance) || access(sock, F_OK) == 0;
        }
    }
    return false;
}

/* /dev/binderfs/features/<name>: libbinder's driver feature checks */
static int feature(const char *name)
{
    const char *v = !strcmp(name, "oneway_spam_detection") || !strcmp(name, "extended_error") ? "1\n" : "0\n";
    int fd = memfd_create("binder-feature", MFD_CLOEXEC);
    if (fd < 0) return -1;
    if (write(fd, v, 2) != 2 || lseek(fd, 0, SEEK_SET) != 0) { close(fd); return -1; }
    return fd;
}

static int route_open(const char *path, int flags)
{
    char sock[sizeof conns[0].path], ctx[16];
    if (!path || strncmp(path, "/dev/", 5) || !on()) return -2;
    if (!strncmp(path, "/dev/binderfs/features/", 23)) return feature(path + 23);
    if (!route_of(path, sock, sizeof sock, ctx, sizeof ctx)) return -2;
    if (debug) fprintf(stderr, "[binder-route] %s: %s (%s)\n", path, sock, ctx);
    struct tstate *t = tstate();
    if (!t) { errno = ENOMEM; return -1; }
    int s = connect_to(sock, flags);
    if (s < 0) { errno = -s == ENOENT || -s == ECONNREFUSED ? ENOENT : -s; return -1; }  /* no daemon: as a missing device */
    struct bd_rep h;
    const uint8_t *payload;
    int r = request(s, BD_HELLO_PROC, BD_VERSION, 0, 0, 0, ctx, strlen(ctx), NULL, 0);
    if (!r) r = reply(t, s, &h, &payload);
    if (!r) { close_reply_fds(t, &h); r = h.ret; }
    if (r) { close(s); errno = -r; return -1; }
    pthread_mutex_lock(&conns_lock);
    struct conn *c = NULL;
    for (int i = 0; i < MAX_CONNS && !c; i++)
        if (conns[i].fd < 0) c = &conns[i];
    if (c) {
        c->gen++;
        c->cookie = h.write_consumed;
        c->nonblock = flags & O_NONBLOCK;
        snprintf(c->path, sizeof c->path, "%s", sock);
        c->ro = c->rw = NULL;
        c->size = 0;
        __atomic_store_n(&c->fd, s, __ATOMIC_RELEASE);  /* last: find() runs without the lock */
    }
    pthread_mutex_unlock(&conns_lock);
    if (!c) { close(s); errno = EMFILE; return -1; }
    return s;
}

/* this thread's slot for c: its socket and shared buffer, connected on first use; NULL with *err */
static struct tslot *thread_slot(struct tstate *t, struct conn *c, bool create, int *err)
{
    struct tslot *s = &t->slot[c - conns];
    if (s->sock >= 0 && s->gen != c->gen) drop_slot(s);
    if (s->sock >= 0 || !create) { *err = 0; return s->sock >= 0 ? s : NULL; }
    int tx = memfd_create("binder-tx", MFD_CLOEXEC | MFD_ALLOW_SEALING);
    if (tx < 0) { *err = -errno; return NULL; }
    void *map = MAP_FAILED;
    if (ftruncate(tx, BD_TX_SIZE) == 0 && fcntl(tx, F_ADD_SEALS, F_SEAL_SHRINK) == 0)  /* the daemon maps it: it must not shrink */
        map = mmap(NULL, BD_TX_SIZE, PROT_READ | PROT_WRITE, MAP_SHARED, tx, 0);
    int sock = map == MAP_FAILED ? -errno : connect_to(c->path, O_CLOEXEC), r = sock < 0 ? sock : 0;
    struct bd_rep h;
    const uint8_t *payload;
    if (!r) r = request(sock, BD_HELLO_THREAD, c->cookie, (uint64_t)syscall(SYS_gettid), 0, 0, NULL, 0, &tx, 1);
    if (!r) r = reply(t, sock, &h, &payload);
    if (!r) { close_reply_fds(t, &h); r = h.ret; }
    close(tx);
    if (r) {
        if (sock >= 0) close(sock);
        if (map != MAP_FAILED) munmap(map, BD_TX_SIZE);
        *err = r;
        return NULL;
    }
    s->sock = sock;
    s->tx = map;
    s->gen = c->gen;
    return s;
}

static void drain(struct conn *c, uint32_t n)
{
    char b;
    while (n-- && recv(c->fd, &b, 1, MSG_DONTWAIT) > 0) {}  /* one-byte messages */
}

static int route_write_read(struct conn *c, struct tstate *t, struct tslot *s, struct binder_write_read *bwr)
{
    struct wbuf *w = &t->w;
    wbuf_reset(w, s->tx, BD_TX_SIZE);
    int r = marshal(c, (const uint8_t *)(uintptr_t)bwr->write_buffer + bwr->write_consumed, bwr->write_size - bwr->write_consumed, w);
    if (r) return r;
    uint32_t flags = (c->nonblock ? BD_RD_NONBLOCK : 0) | (bwr->read_consumed == 0 ? BD_RD_FIRST : 0);
    uint64_t rsize = bwr->read_size > bwr->read_consumed ? bwr->read_size - bwr->read_consumed : 0;
    if (rsize > BD_MAX_READ) rsize = BD_MAX_READ;  /* a reply is one message; the rest stays queued for the next read */
    struct bd_rep h;
    const uint8_t *payload;
    r = request(s->sock, BD_IOCTL, BINDER_WRITE_READ, rsize, flags, w->len, NULL, 0, w->fds, w->nfds);
    if (!r) r = reply(t, s->sock, &h, &payload);
    if (r) return r;
    drain(c, h.ndrain);
    bwr->write_consumed += unmarshal_consumed(w, h.write_consumed);
    uint64_t n = h.read_consumed <= rsize ? h.read_consumed : rsize;
    memcpy((uint8_t *)(uintptr_t)bwr->read_buffer + bwr->read_consumed, payload, n);
    bwr->read_consumed += n;
    const struct bd_fix *fix = (const struct bd_fix *)(payload + h.read_consumed);
    for (uint32_t i = 0; i < h.nfix; i++) {
        if (fix[i].fd_index >= h.nfds || !c->rw || fix[i].offset + sizeof(uint32_t) > c->size) continue;
        uint32_t v = (uint32_t)t->fds[fix[i].fd_index];
        memcpy((uint8_t *)c->rw + fix[i].offset, &v, sizeof v);
        if (fix[i].fda) fda_add(c, fix[i].buffer, t->fds[fix[i].fd_index]);
    }
    return h.ret;
}

static int route_ioctl(int fd, unsigned int request_code, void *arg, int *result)
{
    struct conn *c = find(fd);
    if (!c) return 0;
    struct tstate *t = tstate();
    int r = t ? 0 : -ENOMEM;
    struct tslot *s = t ? thread_slot(t, c, request_code != BINDER_THREAD_EXIT, &r) : NULL;
    if (request_code == BINDER_THREAD_EXIT) {
        if (s) {  /* else the thread never talked to this binder: nothing to release */
            struct bd_rep h;
            const uint8_t *payload;
            if (!request(s->sock, BD_IOCTL, BINDER_THREAD_EXIT, 0, 0, 0, NULL, 0, NULL, 0) && !reply(t, s->sock, &h, &payload)) close_reply_fds(t, &h);
            drop_slot(s);
        }
        r = 0;
    } else if (s && request_code == BINDER_WRITE_READ) {
        r = route_write_read(c, t, s, arg);
    } else if (s) {
        uint32_t size = _IOC_SIZE(request_code), dir = _IOC_DIR(request_code);
        struct bd_rep h;
        const uint8_t *payload;
        r = request(s->sock, BD_IOCTL, request_code, 0, 0, 0, arg, (dir & _IOC_WRITE) && arg ? size : 0, NULL, 0);
        if (!r) r = reply(t, s->sock, &h, &payload);
        if (!r) {
            if ((dir & _IOC_READ) && arg) memcpy(arg, payload, h.read_consumed < size ? h.read_consumed : size);
            close_reply_fds(t, &h);
            r = h.ret;
        }
    }
    if (r < 0) { errno = -r; *result = -1; }
    else *result = r;
    return 1;
}

static int route_mmap(void *addr, size_t len, int prot, int flags, int fd, off_t off, void **result)
{
    (void)addr; (void)prot; (void)flags;
    struct conn *c = find(fd);
    if (!c) return 0;
    *result = MAP_FAILED;
    struct tstate *t = tstate();
    int r = t ? 0 : -ENOMEM;
    struct tslot *s = t ? thread_slot(t, c, true, &r) : NULL;
    struct bd_rep h = {0};
    const uint8_t *payload;
    if (s) r = request(s->sock, BD_MMAP, len, 0, 0, 0, NULL, 0, NULL, 0);
    if (s && !r) r = reply(t, s->sock, &h, &payload);
    if (s && !r) r = h.ret ? h.ret : h.nfds == 1 && off == 0 ? 0 : -EINVAL;
    if (s && !r) {
        int memfd = t->fds[0];
        void *ro = mmap(NULL, len, PROT_READ, MAP_SHARED, memfd, 0);
        void *rw = ro == MAP_FAILED ? MAP_FAILED : mmap(NULL, len, PROT_READ | PROT_WRITE, MAP_SHARED, memfd, 0);
        if (rw == MAP_FAILED) { r = -errno; if (ro != MAP_FAILED) munmap(ro, len); }
        close(memfd);
        if (!r) {
            c->ro = ro; c->rw = rw; c->size = len;
            r = request(s->sock, BD_MAPPED, (uint64_t)(uintptr_t)ro, 0, 0, 0, NULL, 0, NULL, 0);
            if (!r) r = reply(t, s->sock, &h, &payload);
            if (!r) { close_reply_fds(t, &h); r = h.ret; }
            if (!r) *result = ro;
        }
    } else if (s && h.nfds) {
        close_reply_fds(t, &h);
    }
    if (r) errno = -r;
    return 1;
}

static int route_close(int fd, int *result)
{
    struct conn *c = find(fd);
    if (!c) return 0;
    pthread_mutex_lock(&conns_lock);
    if (c->rw) munmap(c->rw, c->size);
    pthread_mutex_lock(&c->lock);
    for (size_t i = 0; i < c->nfdas; i++) close(c->fdas[i].fd);
    c->nfdas = 0;
    pthread_mutex_unlock(&c->lock);
    c->rw = c->ro = NULL;
    c->fd = -1;
    pthread_mutex_unlock(&conns_lock);
    *result = close(fd);  /* the daemon sees the proc's end: its threads' sockets go with it */
    return 1;
}

/* the path layer's glibc-level open wrappers (paths_preload.c: bionic code's fortified __open_2 reaches them on glibc) ask here too; and access()
 * of a routed node succeeds, as for a device */
int apklayer_binder_route_open(const char *path, int flags) { return route_open(path, flags); }
int apklayer_binder_route_exists(const char *path)
{
    char sock[sizeof conns[0].path], ctx[16];
    if (!path || strncmp(path, "/dev/", 5) || !on()) return 0;
    return !strncmp(path, "/dev/binderfs/features/", 23) || (route_of(path, sock, sizeof sock, ctx, sizeof ctx) && access(sock, F_OK) == 0);
}

/* what bionic_translation's libc looks up (patches/bionic_translation/0046) */
struct bionic_fd_hooks {
    int (*open)(const char *path, int flags);
    int (*ioctl)(int fd, unsigned int request, void *arg, int *result);
    int (*mmap)(void *addr, size_t len, int prot, int flags, int fd, off_t off, void **result);
    int (*close)(int fd, int *result);
};
__attribute__((visibility("default"))) const struct bionic_fd_hooks bionic_fd_hooks = {route_open, route_ioctl, route_mmap, route_close};
