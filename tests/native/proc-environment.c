#define _GNU_SOURCE
#include <assert.h>
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/syscall.h>
#include <sys/wait.h>
#include <unistd.h>

static long (*raw)(long, ...);
static char *contents(int fd, size_t *size)
{
    assert(fd >= 0);
    size_t cap = 4096; *size = 0;
    char *bytes = malloc(cap); assert(bytes);
    for (;;) {
        if (*size == cap) { cap *= 2; bytes = realloc(bytes, cap); assert(bytes); }
        ssize_t n = read(fd, bytes + *size, cap - *size);
        if (n < 0 && errno == EINTR) continue;
        assert(n >= 0);
        if (!n) break;
        *size += n;
    }
    close(fd);
    return bytes;
}
static void check(int fd, const char *expected, size_t expected_size)
{
    size_t size;
    char *actual = contents(fd, &size);
    assert(size == expected_size && !memcmp(actual, expected, size));
    free(actual);
}
static void aliases(const char *expected, size_t size)
{
    char pid[96], task[96];
    snprintf(pid, sizeof pid, "/proc/%d/environ", getpid());
    snprintf(task, sizeof task, "/proc/%d/task/%ld/environ", getpid(), syscall(SYS_gettid));
    const char *paths[] = {"/proc/self/environ", "/proc/thread-self/environ", pid, task};
    for (size_t i = 0; i < sizeof paths / sizeof *paths; i++) {
        check(open(paths[i], O_RDONLY), expected, size);
        check(syscall(SYS_openat, AT_FDCWD, paths[i], O_RDONLY, 0), expected, size);
        FILE *file = fopen(paths[i], "rb"); assert(file);
        char *actual = malloc(size + 1); assert(actual);
        assert(fread(actual, 1, size + 1, file) == size && !ferror(file));
        assert(!memcmp(actual, expected, size)); free(actual); fclose(file);
    }
    int dir = open("/proc/self", O_RDONLY | O_DIRECTORY); assert(dir >= 0);
    check(openat(dir, "environ", O_RDONLY), expected, size); close(dir);
    char link[] = "/tmp/apklayer-proc-environment-XXXXXX";
    int temp = mkstemp(link); assert(temp >= 0); close(temp); assert(!unlink(link));
    assert(!symlink("/proc/self/environ", link)); check(open(link, O_RDONLY), expected, size); unlink(link);
    int fd = open(paths[0], O_RDONLY | O_CLOEXEC); assert(fd >= 0);
    assert(fcntl(fd, F_GETFD) & FD_CLOEXEC);
    errno = 0; assert(write(fd, "x", 1) == -1 && errno == EBADF);
    assert(lseek(fd, 0, SEEK_SET) == 0); check(fd, expected, size);
    fd = open(paths[0], O_RDONLY); assert(fd >= 0 && !(fcntl(fd, F_GETFD) & FD_CLOEXEC)); close(fd);
}
int main(int argc, char **argv)
{
    assert(argc == 2);
    void *host = dlopen("libc.so.6", RTLD_NOW); assert(host);
    raw = dlsym(host, "syscall"); assert(raw);
    int app = getenv("APKLAYER_OWN_DATA") && !getenv("APKLAYER_PROC_VIEW");
    size_t host_size;
    char *host_bytes = contents(raw(SYS_openat, AT_FDCWD, "/proc/self/environ", O_RDONLY, 0), &host_size);
    if (app) {
        errno = 0; assert(open("/proc/self/environ", O_RDONLY) == -1 && errno == ENOSYS);
    }
    void *c = dlopen(argv[1], RTLD_NOW | RTLD_GLOBAL); assert(c);
    const char *(*initial)(size_t *) = dlsym(c, "libc_bio_initial_environment");
    int (*set)(const char *, const char *, int) = dlsym(c, "bionic_setenv");
    int (*clear)(void) = dlsym(c, "bionic_clearenv");
    int (*initialize)(char *const [], int) = dlsym(c, "libc_bio_init_guest_environment");
    assert(initial && set && clear && initialize);
    size_t size = 0;
    const char *expected = app ? initial(&size) : host_bytes;
    if (!app) size = host_size;
    assert(expected && (!size || expected[size - 1] == 0));
    aliases(expected, size);
    assert(!set("PROC_ENV_LIVE_ONLY", "changed", 1));
    if (app) assert(!clear());
    aliases(expected, size); /* /proc reports the initial block, never the live libc environment. */

    /* Original open failures and path-only descriptors must retain kernel semantics. */
    const char *bad[] = {"/proc/self/environ/missing", "/proc/self/no-such-environ"};
    for (size_t i = 0; i < 2; i++) {
        errno = 0; assert(raw(SYS_openat, AT_FDCWD, bad[i], O_RDONLY, 0) == -1); int original = errno;
        errno = 0; assert(open(bad[i], O_RDONLY) == -1 && errno == original);
    }
    errno = 0; assert(raw(SYS_openat, AT_FDCWD, "/proc/self/environ", O_RDONLY | O_DIRECTORY, 0) == -1);
    int original = errno; errno = 0; assert(open("/proc/self/environ", O_RDONLY | O_DIRECTORY) == -1 && errno == original);
    int fd = open("/proc/self/environ", O_PATH); assert(fd >= 0);
    char byte; errno = 0; assert(read(fd, &byte, 1) == -1 && errno == EBADF); close(fd);

    int pipefd[2]; assert(!pipe(pipefd)); pid_t child = fork(); assert(child >= 0);
    if (!child) { close(pipefd[1]); read(pipefd[0], &byte, 1); _exit(0); }
    close(pipefd[0]); char path[80]; snprintf(path, sizeof path, "/proc/%d/environ", child);
    size_t external_size; char *external = contents(raw(SYS_openat, AT_FDCWD, path, O_RDONLY, 0), &external_size);
    check(open(path, O_RDONLY), external, external_size); free(external);
    close(pipefd[1]); assert(waitpid(child, NULL, 0) == child);
    if (app) {
        char first[] = "FIRST=one", second[] = "SECOND=two";
        char *seed[] = {first, second, NULL};
        const char bytes[] = "FIRST=one\0SECOND=two\0";
        assert(!initialize(seed, 1)); aliases(bytes, sizeof bytes - 1);
        assert(!set("FIRST", "changed", 1)); aliases(bytes, sizeof bytes - 1);
        assert(!initialize(NULL, 1)); aliases("", 0);
    }
    free(host_bytes);
    puts("PASS proc environment: immutable NUL block, aliases, fd access, original errors, external PID and optouts");
}
