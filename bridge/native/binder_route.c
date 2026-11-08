/* The userspace binder's side in every Android process (docs/BINDER.md): with APKLAYER_BINDER=userspace, bionic_translation's libc hands
 * open/ioctl/mmap/close of binder fds to these hooks (its fd hooks, patches/bionic_translation/0046) and they talk to the instance's daemon
 * (bridge/binderd, protocol.h) instead of a kernel driver. Opening /dev/binder (as the path layer maps it) connects a proc socket; each thread
 * gets a socket of its own on first use, so blocking reads of several threads wait side by side. A BINDER_WRITE_READ's write stream is
 * marshalled: transaction data, offsets and scatter-gather buffers inline, fds as SCM_RIGHTS. The receive area is the daemon's memfd: mapped
 * read-only where libbinder reads it, and writable once more here for the fd fixups. In the LD_PRELOAD library: one copy per process. */
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
static __thread int tsock[MAX_CONNS]; /* fd + 1 (0: none) */
static __thread unsigned tgen[MAX_CONNS];
static pthread_key_t thread_key;
static pthread_once_t key_once = PTHREAD_ONCE_INIT;
static int enabled = -1;
static const char *sock_dir, *instance;

static void thread_gone(void *unused)
{
    (void)unused;
    for (int i = 0; i < MAX_CONNS; i++)
        if (tsock[i]) { close(tsock[i] - 1); tsock[i] = 0; }
}

static void make_key(void) { pthread_key_create(&thread_key, thread_gone); }

static bool on(void)
{
    if (enabled < 0) {
        const char *b = getenv("APKLAYER_BINDER");
        sock_dir = getenv("APKLAYER_BINDER_DIR");
        instance = getenv("APKLAYER_INSTANCE");
        if (!instance || !instance[0]) instance = "default";
        enabled = b && !strcmp(b, "userspace") && sock_dir && sock_dir[0];
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

static int request(int sock, uint32_t op, uint64_t a0, uint64_t a1, uint64_t a2, const void *payload, uint32_t plen, const int *fds, uint32_t nfds)
{
    struct bd_req h = {.len = sizeof h + plen, .op = op, .nfds = nfds, .a0 = a0, .a1 = a1, .a2 = a2};
    return bd_send(sock, &h, sizeof h, payload, plen, fds, nfds);
}

/* a reply: header, payload into *payload (malloc'ed, caller frees), fds into *fds (malloc'ed) */
static int reply(int sock, struct bd_rep *h, uint8_t **payload, int **fds)
{
    int first[BD_MAX_FDS];
    uint32_t got;
    *payload = NULL;
    *fds = NULL;
    int r = bd_recv_hdr(sock, h, sizeof *h, first, &got);
    if (r) return r;
    if (h->len < sizeof *h) return -EPROTO;
    *payload = malloc(h->len - sizeof *h + 1);
    *fds = malloc(sizeof(int) * (h->nfds + 1));
    if (!*payload || !*fds) return -ENOMEM;
    memcpy(*fds, first, sizeof(int) * got);
    r = bd_recv_all(sock, *payload, h->len - sizeof *h);
    if (!r) r = bd_recv_more_fds(sock, *fds, got, h->nfds);
    return r;
}

static int connect_to(const char *path, int flags)
{
    int s = socket(AF_UNIX, SOCK_STREAM | ((flags & O_CLOEXEC) ? SOCK_CLOEXEC : 0), 0);
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
    if (!path || !on()) return -2;
    if (!strncmp(path, "/dev/binderfs/features/", 23)) return feature(path + 23);
    if (!route_of(path, sock, sizeof sock, ctx, sizeof ctx)) return -2;
    int s = connect_to(sock, flags);
    if (s < 0) { errno = -s == ENOENT || -s == ECONNREFUSED ? ENOENT : -s; return -1; }  /* no daemon: as a missing device */
    struct bd_rep h;
    uint8_t *payload = NULL;
    int *fds = NULL;
    int r = request(s, BD_HELLO_PROC, BD_VERSION, 0, 0, ctx, strlen(ctx), NULL, 0);
    if (!r) r = reply(s, &h, &payload, &fds);
    if (!r) r = h.ret;
    free(payload);
    free(fds);
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

/* this thread's socket for c (connected on first use) */
static int thread_sock(struct conn *c, bool create)
{
    int i = c - conns;
    if (tsock[i] && tgen[i] != c->gen) { close(tsock[i] - 1); tsock[i] = 0; }
    if (tsock[i] || !create) return tsock[i] - 1;
    int s = connect_to(c->path, O_CLOEXEC);
    if (s < 0) return s;
    struct bd_rep h;
    uint8_t *payload = NULL;
    int *fds = NULL;
    int r = request(s, BD_HELLO_THREAD, c->cookie, (uint64_t)syscall(SYS_gettid), 0, NULL, 0, NULL, 0);
    if (!r) r = reply(s, &h, &payload, &fds);
    if (!r) r = h.ret;
    free(payload);
    free(fds);
    if (r) { close(s); return r; }
    pthread_once(&key_once, make_key);
    pthread_setspecific(thread_key, (void *)1);
    tsock[i] = s + 1;
    tgen[i] = c->gen;
    return s;
}

static void drain(struct conn *c, uint32_t n)
{
    char buf[64];
    while (n) {
        ssize_t r = recv(c->fd, buf, n < sizeof buf ? n : sizeof buf, MSG_DONTWAIT);
        if (r <= 0) break;
        n -= r;
    }
}

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

static int route_write_read(struct conn *c, int s, struct binder_write_read *bwr)
{
    struct wbuf w = {0};
    int r = marshal(c, (const uint8_t *)(uintptr_t)bwr->write_buffer + bwr->write_consumed, bwr->write_size - bwr->write_consumed, &w);
    if (r) { wbuf_free(&w); return r; }
    uint32_t flags = (c->nonblock ? BD_RD_NONBLOCK : 0) | (bwr->read_consumed == 0 ? BD_RD_FIRST : 0);
    uint64_t rsize = bwr->read_size > bwr->read_consumed ? bwr->read_size - bwr->read_consumed : 0;
    r = request(s, BD_IOCTL, BINDER_WRITE_READ, rsize, flags, w.data, w.len, w.fds, w.nfds);
    struct bd_rep h;
    uint8_t *payload = NULL;
    int *fds = NULL;
    if (!r) r = reply(s, &h, &payload, &fds);
    if (!r) {
        drain(c, h.ndrain);
        bwr->write_consumed += unmarshal_consumed(&w, h.write_consumed);
        uint64_t n = h.read_consumed <= rsize ? h.read_consumed : rsize;
        memcpy((uint8_t *)(uintptr_t)bwr->read_buffer + bwr->read_consumed, payload, n);
        bwr->read_consumed += n;
        const struct bd_fix *fix = (const struct bd_fix *)(payload + h.read_consumed);
        for (uint32_t i = 0; i < h.nfix; i++) {
            if (fix[i].fd_index >= h.nfds || !c->rw || fix[i].offset + sizeof(uint32_t) > c->size) continue;
            uint32_t v = (uint32_t)fds[fix[i].fd_index];
            memcpy((uint8_t *)c->rw + fix[i].offset, &v, sizeof v);
            if (fix[i].fda) fda_add(c, fix[i].buffer, fds[fix[i].fd_index]);
        }
        r = h.ret;
    }
    free(payload);
    free(fds);
    wbuf_free(&w);
    return r;
}

static int route_ioctl(int fd, unsigned int request_code, void *arg, int *result)
{
    struct conn *c = find(fd);
    if (!c) return 0;
    int r;
    if (request_code == BINDER_THREAD_EXIT) {
        int s = thread_sock(c, false);
        r = 0;
        if (s >= 0) {
            struct bd_rep h;
            uint8_t *payload = NULL;
            int *fds = NULL;
            r = request(s, BD_IOCTL, BINDER_THREAD_EXIT, 0, 0, NULL, 0, NULL, 0);
            if (!r) r = reply(s, &h, &payload, &fds);
            if (!r) { free(payload); free(fds); }
            close(s);
            tsock[c - conns] = 0;
        }
    } else {
        int s = thread_sock(c, true);
        if (s < 0) r = s;
        else if (request_code == BINDER_WRITE_READ) r = route_write_read(c, s, arg);
        else {
            uint32_t size = _IOC_SIZE(request_code), dir = _IOC_DIR(request_code);
            struct bd_rep h;
            uint8_t *payload = NULL;
            int *fds = NULL;
            r = request(s, BD_IOCTL, request_code, 0, 0, arg, (dir & _IOC_WRITE) && arg ? size : 0, NULL, 0);
            if (!r) r = reply(s, &h, &payload, &fds);
            if (!r) {
                if ((dir & _IOC_READ) && arg) memcpy(arg, payload, h.read_consumed < size ? h.read_consumed : size);
                for (uint32_t i = 0; i < h.nfds; i++) close(fds[i]);
                r = h.ret;
            }
            free(payload);
            free(fds);
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
    int s = thread_sock(c, true), r = s < 0 ? s : 0;
    struct bd_rep h;
    uint8_t *payload = NULL;
    int *fds = NULL;
    if (!r) r = request(s, BD_MMAP, len, 0, 0, NULL, 0, NULL, 0);
    if (!r) r = reply(s, &h, &payload, &fds);
    if (!r) r = h.ret;
    if (!r && h.nfds == 1 && off == 0) {
        void *ro = mmap(NULL, len, PROT_READ, MAP_SHARED, fds[0], 0);
        void *rw = ro == MAP_FAILED ? MAP_FAILED : mmap(NULL, len, PROT_READ | PROT_WRITE, MAP_SHARED, fds[0], 0);
        close(fds[0]);
        free(payload);
        free(fds);
        payload = NULL;
        fds = NULL;
        if (rw == MAP_FAILED) { r = -errno; if (ro != MAP_FAILED) munmap(ro, len); }
        else {
            c->ro = ro; c->rw = rw; c->size = len;
            r = request(s, BD_MAPPED, (uint64_t)(uintptr_t)ro, 0, 0, NULL, 0, NULL, 0);
            if (!r) r = reply(s, &h, &payload, &fds);
            if (!r) r = h.ret;
            if (!r) *result = ro;
        }
    } else if (!r) {
        for (uint32_t i = 0; i < h.nfds; i++) close(fds[i]);
        r = -EINVAL;
    }
    free(payload);
    free(fds);
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

/* what bionic_translation's libc looks up (patches/bionic_translation/0046) */
struct bionic_fd_hooks {
    int (*open)(const char *path, int flags);
    int (*ioctl)(int fd, unsigned int request, void *arg, int *result);
    int (*mmap)(void *addr, size_t len, int prot, int flags, int fd, off_t off, void **result);
    int (*close)(int fd, int *result);
};
__attribute__((visibility("default"))) const struct bionic_fd_hooks bionic_fd_hooks = {route_open, route_ioctl, route_mmap, route_close};
