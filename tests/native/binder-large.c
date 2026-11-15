/* The userspace binder (bridge/native/binder_route.c + bridge/binderd) with a transaction larger than the route's shared buffer (BD_TX_SIZE):
 * as on the kernel, only that transaction fails (BR_FAILED_REPLY to the caller, BR_TRANSACTION_COMPLETE to a replier) and the ioctl succeeds.
 * Before, the route failed the whole BINDER_WRITE_READ with -E2BIG and libbinder aborted the process (Play services, exit 134, an 8 MB reply).
 * Run with LD_PRELOAD=libapklayer-preload.so; argv[1]: apklayer-binderd. Exit 0 pass, 1 fail. */
#define _GNU_SOURCE
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <linux/android/binder.h>
#include <signal.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/un.h>
#include <sys/wait.h>
#include <time.h>
#include <unistd.h>

struct fd_hooks { /* binder_route.c's bionic_fd_hooks */
    int (*open)(const char *path, int flags);
    int (*ioctl)(int fd, unsigned int request, void *arg, int *result);
    int (*mmap)(void *addr, size_t len, int prot, int flags, int fd, off_t off, void **result);
    int (*close)(int fd, int *result);
};
static const struct fd_hooks *hooks;
#define BIG (9u << 20)
#define AREA (1u << 20)

static int bopen(void)
{
    int fd = hooks->open("/dev/binder", O_RDWR | O_CLOEXEC);
    void *m;
    if (fd < 0 || !hooks->mmap(NULL, AREA, PROT_READ, MAP_PRIVATE, fd, 0, &m) || m == MAP_FAILED) return -1;
    return fd;
}

/* one BINDER_WRITE_READ; -errno of a failed ioctl, else the bytes read */
static long wr(int fd, const void *w, size_t wn, void *r, size_t rn)
{
    struct binder_write_read b = {.write_size = wn, .write_buffer = (uintptr_t)w, .read_size = rn, .read_buffer = (uintptr_t)r};
    int res;
    hooks->ioctl(fd, BINDER_WRITE_READ, &b, &res);
    return res < 0 ? -errno : (long)b.read_consumed;
}

static long cmd(int fd, uint32_t c, const void *p, size_t n)
{
    uint8_t w[4 + sizeof(struct binder_transaction_data_sg)];
    memcpy(w, &c, 4);
    memcpy(w + 4, p, n);
    return wr(fd, w, 4 + n, NULL, 0);
}

/* reads until a return of interest: BR_TRANSACTION (*tr filled), BR_REPLY, BR_FAILED_REPLY, BR_DEAD_REPLY, BR_TRANSACTION_COMPLETE */
static uint32_t next(int fd, struct binder_transaction_data *tr)
{
    static uint8_t buf[4096];
    static size_t len, pos;
    for (;;) {
        while (pos + 4 <= len) {
            uint32_t c;
            memcpy(&c, buf + pos, 4);
            pos += 4;
            size_t sz = _IOC_SIZE(c);
            if (c == BR_TRANSACTION || c == BR_REPLY) memcpy(tr, buf + pos, sizeof *tr);
            pos += sz;
            if (c == BR_TRANSACTION || c == BR_REPLY || c == BR_FAILED_REPLY || c == BR_DEAD_REPLY || c == BR_TRANSACTION_COMPLETE) return c;
        }
        long n = wr(fd, NULL, 0, buf, sizeof buf);
        if (n < 0) { fprintf(stderr, "read failed: %s\n", strerror((int)-n)); return 0; }
        len = (size_t)n, pos = 0;
    }
}

static const char *name(uint32_t c)
{
    return c == BR_REPLY ? "BR_REPLY" : c == BR_FAILED_REPLY ? "BR_FAILED_REPLY" : c == BR_TRANSACTION_COMPLETE ? "BR_TRANSACTION_COMPLETE"
         : c == BR_TRANSACTION ? "BR_TRANSACTION" : c == BR_DEAD_REPLY ? "BR_DEAD_REPLY" : "?";
}

static void freebuf(int fd, const struct binder_transaction_data *tr)
{
    binder_uintptr_t p = tr->data.ptr.buffer;
    cmd(fd, BC_FREE_BUFFER, &p, sizeof p);
}

static long transact(int fd, uint32_t c, uint32_t code, const void *data, size_t n)
{
    struct binder_transaction_data tr = {.code = code, .data_size = n, .data.ptr.buffer = (uintptr_t)data};
    return cmd(fd, c, &tr, sizeof tr);
}

static int server(int ready, uint8_t *big)
{
    int fd = bopen(), res, zero = 0, fails = 0;
    if (fd < 0 || (hooks->ioctl(fd, BINDER_SET_CONTEXT_MGR, &zero, &res), res) < 0) { perror("server: open/context manager"); return 1; }
    cmd(fd, BC_ENTER_LOOPER, NULL, 0);
    if (write(ready, "r", 1) != 1) return 1;
    for (;;) {
        struct binder_transaction_data tr;
        uint32_t r = next(fd, &tr);
        if (r != BR_TRANSACTION) { fprintf(stderr, "server: %s\n", name(r)); return 1; }
        uint32_t code = tr.code;
        freebuf(fd, &tr);
        long w = code == 1 ? transact(fd, BC_REPLY, 0, big, BIG) : transact(fd, BC_REPLY, 0, "ok!", 4);
        if (w < 0) { fprintf(stderr, "FAIL server: reply to code %u: ioctl %s\n", code, strerror((int)-w)); return 1; }
        r = next(fd, &tr);
        printf("server: reply to code %u: ioctl ok, %s\n", code, name(r));
        if (r != BR_TRANSACTION_COMPLETE) fails++;
        if (code == 2) return fails != 0;
    }
}

static int client(uint8_t *big)
{
    int fd = bopen(), fails = 0;
    struct binder_transaction_data tr;
    if (fd < 0) { perror("client: open"); return 1; }
    struct { uint32_t code; const void *data; size_t n; uint32_t want; const char *what; } steps[] = {
        {1, "x", 1, BR_FAILED_REPLY, "small call, 9 MB reply"},
        {3, big, BIG, BR_FAILED_REPLY, "9 MB call"},
        {2, "y", 1, BR_REPLY, "small call, small reply"},
    };
    for (size_t i = 0; i < sizeof steps / sizeof *steps; i++) {
        long w = transact(fd, BC_TRANSACTION, steps[i].code, steps[i].data, steps[i].n);
        if (w < 0) { printf("FAIL client: %s: ioctl %s\n", steps[i].what, strerror((int)-w)); return 1; }
        uint32_t r = next(fd, &tr);
        if (r == BR_TRANSACTION_COMPLETE) r = next(fd, &tr);
        printf("%s client: %s: %s\n", r == steps[i].want ? "ok  " : "FAIL", steps[i].what, name(r));
        if (r != steps[i].want) fails++;
        if (r == BR_REPLY) {
            if (tr.data_size != 4 || memcmp((void *)(uintptr_t)tr.data.ptr.buffer, "ok!", 4)) { printf("FAIL client: reply data\n"); fails++; }
            freebuf(fd, &tr);
        }
    }
    return fails != 0;
}

int main(int argc, char **argv)
{
    if (argc < 2) { fprintf(stderr, "usage: binder-large-test apklayer-binderd\n"); return 2; }
    alarm(10);  /* a hang fails, it does not wait */
    setvbuf(stdout, NULL, _IONBF, 0);
    hooks = dlsym(RTLD_DEFAULT, "bionic_fd_hooks");
    if (!hooks) { fprintf(stderr, "no bionic_fd_hooks: run with LD_PRELOAD=libapklayer-preload.so\n"); return 1; }
    char dir[] = "/tmp/binder-large-XXXXXX", sock[sizeof dir + 16];
    if (!mkdtemp(dir)) return 1;
    snprintf(sock, sizeof sock, "%s/test.sock", dir);
    setenv("APKLAYER_BINDER", "userspace", 1);
    setenv("APKLAYER_BINDER_DIR", dir, 1);
    setenv("APKLAYER_INSTANCE", "test", 1);
    pid_t d = fork();
    if (d == 0) {
        unsetenv("LD_PRELOAD");
        execl(argv[1], argv[1], "--socket", sock, (char *)NULL);
        _exit(127);
    }
    struct timespec ts = {0, 10 * 1000 * 1000};
    for (int i = 0; i < 300 && access(sock, F_OK) != 0; i++) nanosleep(&ts, NULL);
    uint8_t *big = malloc(BIG);
    memset(big, 0x5a, BIG);
    int p[2];
    if (pipe(p) != 0) return 1;
    pid_t s = fork();
    if (s == 0) { alarm(10); close(p[0]); _exit(server(p[1], big)); }
    close(p[1]);
    char c;
    int rc = read(p[0], &c, 1) == 1 ? client(big) : 1, st = 0;
    if (waitpid(s, &st, 0) != s || !WIFEXITED(st) || WEXITSTATUS(st)) rc = 1;
    kill(d, SIGTERM);
    waitpid(d, NULL, 0);
    unlink(sock);
    rmdir(dir);
    printf("%s\n", rc ? "FAIL" : "PASS");
    return rc;
}
