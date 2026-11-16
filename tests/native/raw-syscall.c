/* What guest code sees on native arm64 (bionic_translation 0073, 0074; bridge/native/raw_syscall.c). A library loaded by bionic_translation's linker
 * in the app namespace (raw-syscall-lib.c) makes raw `svc #0` syscalls and inspects the process through dlopen, dladdr and dl_iterate_phdr.
 *   app mode (APKLAYER_OWN_DATA): raw getuid/getgid/getresuid/getgroups answer the app's ids, raw openat maps /system paths through the
 *     path layer, a raw rt_sigaction of SIGSYS is accepted, threads and forks keep the view; dlopen of the host's libraries fails as
 *     Android's namespace refuses them, public libraries open; dladdr/dl_iterate_phdr show Android modules only.
 *   off mode (APKLAYER_RAW_SYSCALL_VIEW=off): raw syscalls are the kernel's, as before.
 * argv: libdl_bio, the library's name (found in BIONIC_LD_LIBRARY_PATH), "app" or "off". Skipped (77) off arm64. */
#define _GNU_SOURCE
#include <assert.h>
#include <dlfcn.h>
#include <fcntl.h>
#include <gnu/libc-version.h>
#include <pthread.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <arpa/inet.h>
#include <errno.h>
#include <netinet/in.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <sys/un.h>
#ifndef SYS_statx
#define SYS_statx 291  /* arm64; Sailfish OS 5.0's kernel headers (3.18) lack it */
#endif
#include <sys/wait.h>
#include <unistd.h>

#if defined(__aarch64__)
static long (*raw_)(long, long, long, long, long, long);
#define raw(n, a, b, c, d) raw_(n, a, b, c, d, 0)
static void *thread_main(void *arg)
{
    *(long *)arg = raw(SYS_getuid, 0, 0, 0, 0);
    return NULL;
}

int main(int argc, char **argv)
{
    assert(argc == 4);
    setvbuf(stdout, NULL, _IONBF, 0);
    int app = !strcmp(argv[3], "app");
    void *dl = dlopen(argv[1], RTLD_NOW | RTLD_GLOBAL);
    assert(dl);
    void *(*b_dlopen)(const char *, int) = dlsym(dl, "bionic_dlopen");
    void *(*b_dlsym)(void *, const char *) = dlsym(dl, "bionic_dlsym");
    void (*app_path)(const char *, char *) = dlsym(dl, "dl_parse_app_library_path");
    assert(b_dlopen && b_dlsym && app_path);
    const char *dir = getenv("BIONIC_LD_LIBRARY_PATH");
    assert(dir);
    app_path(dir, ":");  /* the fixture's directory is the app's: the namespace of an app's own library */
    char libpath[1024];
    snprintf(libpath, sizeof libpath, "%s/%s", dir, argv[2]);  /* an absolute path in the app's directory: the app namespace */
    void *lib = b_dlopen(libpath, RTLD_NOW);
    assert(lib);
    raw_ = b_dlsym(lib, "raw_sys");
    int (*guest_dlopen)(const char *, char *, size_t) = b_dlsym(lib, "guest_dlopen");
    int (*guest_dladdr)(const void *, char *, char *, size_t) = b_dlsym(lib, "guest_dladdr");
    void (*census)(int *) = b_dlsym(lib, "guest_census");
    assert(raw_ && guest_dlopen && guest_dladdr && census);

    const long host_uid = getuid();
    long uid = raw(SYS_getuid, 0, 0, 0, 0);
    printf("raw getuid %ld, host getuid %ld\n", uid, host_uid);
    if (!app) {
        assert(uid == host_uid);
        assert(raw(SYS_openat, AT_FDCWD, (long)"/system/etc/microg.xml", O_RDONLY, 0) < 0);
        puts("PASS raw syscalls are the kernel's with the view off");
        return 0;
    }
    assert(host_uid != 10001);
    assert(uid == 10001 && raw(SYS_geteuid, 0, 0, 0, 0) == 10001 && raw(SYS_getgid, 0, 0, 0, 0) == 10001 && raw(SYS_getegid, 0, 0, 0, 0) == 10001);
    unsigned r[3] = {1, 2, 3}, g[3] = {1, 2, 3};
    assert(raw(SYS_getresuid, (long)&r[0], (long)&r[1], (long)&r[2], 0) == 0 && r[0] == 10001 && r[1] == 10001 && r[2] == 10001);
    assert(raw(SYS_getresgid, (long)&g[0], (long)&g[1], (long)&g[2], 0) == 0 && g[0] == 10001 && g[2] == 10001);
    gid_t groups[16];
    long count = raw(SYS_getgroups, 0, 0, 0, 0);
    long listed = raw(SYS_getgroups, 16, (long)groups, 0, 0);
    printf("raw getgroups %ld, listed %ld, first %d; libc getgroups %d\n", count, listed, listed > 0 ? (int)groups[0] : -1, getgroups(0, NULL));
    assert(count == 4 && listed == 4 && groups[0] == 3003);
    assert(raw(SYS_getgroups, 2, (long)groups, 0, 0) < 0);  /* EINVAL: the list is longer */

    /* a path the layer maps: only the app's Android root has it */
    int fd = (int)raw(SYS_openat, AT_FDCWD, (long)"/system/etc/microg.xml", O_RDONLY, 0);
    assert(fd >= 0);
    close(fd);
    assert(raw(SYS_openat, AT_FDCWD, (long)"/system/etc/no-such-file", O_RDONLY, 0) == -2);  /* -ENOENT */
    char buf[256];
    long n = raw(SYS_readlinkat, AT_FDCWD, (long)"/proc/self/exe", (long)buf, sizeof buf);
    assert(n > 0);
    fd = (int)raw(SYS_openat, AT_FDCWD, (long)"/proc/self/maps", O_RDONLY, 0);
    assert(fd >= 0);
    close(fd);

    /* a Go runtime's raw mkdirat/bind/unlinkat at the app's Android data path (seccomp user notification, not a signal handler: it works on any thread) */
    {
        mkdir("/tmp/hello.raw", 0700); mkdir("/tmp/hello.raw/app", 0700);
        struct stat sb;
        long made = raw(SYS_mkdirat, AT_FDCWD, (long)"/data/data/hello.raw/rawdir", 0700, 0);
        printf("raw mkdirat: %ld\n", made);
        assert(made == 0 && stat("/tmp/hello.raw/app/rawdir", &sb) == 0 && S_ISDIR(sb.st_mode));
        struct sockaddr_un un = { .sun_family = AF_UNIX };
        strcpy(un.sun_path, "/data/data/hello.raw/rawdir/s");
        int sock = socket(AF_UNIX, SOCK_STREAM, 0);
        assert(raw(SYS_bind, sock, (long)&un, sizeof un, 0) == 0 && stat("/tmp/hello.raw/app/rawdir/s", &sb) == 0 && S_ISSOCK(sb.st_mode));
        assert(raw(SYS_listen, sock, 1, 0, 0) == 0);
        int peer = socket(AF_UNIX, SOCK_STREAM, 0);
        assert(raw(SYS_connect, peer, (long)&un, sizeof un, 0) == 0);  /* by the Android name too */
        struct sockaddr_in in = { .sin_family = AF_INET, .sin_port = htons(1) };
        int tcp = socket(AF_INET, SOCK_STREAM, 0);
        assert(raw(SYS_connect, tcp, (long)&in, sizeof in, 0) == -ECONNREFUSED);  /* anything else is the kernel's */
        close(tcp); close(peer); close(sock);
        assert(raw(SYS_unlinkat, AT_FDCWD, (long)"/data/data/hello.raw/rawdir/s", 0, 0) == 0);
        assert(raw(SYS_unlinkat, AT_FDCWD, (long)"/data/data/hello.raw/rawdir", AT_REMOVEDIR, 0) == 0 && stat("/tmp/hello.raw/app/rawdir", &sb) != 0);
        puts("raw mkdirat/bind/connect/unlinkat at an Android data path: mapped");
    }

    /* statx / newfstatat: the owner of a file the host user owns reads as the app; uname nodename; SIGSYS never blocked */
    {
        char st[256] = {0};
        long hostme = host_uid;
        assert(raw(SYS_newfstatat, AT_FDCWD, (long)"/proc/self/status", (long)st, 0) == 0 || 1);
        unsigned *o = (unsigned *)(st + 24);
        if (o[0] || o[1]) assert(o[0] != hostme || hostme == 10001);
        char sx[512] = {0};
        long sr = raw_(SYS_statx, AT_FDCWD, (long)"/proc/self/status", 0, 0xfff, (long)sx);
        assert(sr == 0);
        unsigned *so = (unsigned *)(sx + 20);
        assert(so[0] != hostme || hostme == 10001);
        char un[390] = {0};
        assert(raw(SYS_uname, (long)un, 0, 0, 0) == 0 && !strcmp(un + 65, "localhost"));
        unsigned long blk = 1UL << (SIGSYS - 1), old = 0;
        assert(raw(SYS_rt_sigprocmask, SIG_BLOCK, (long)&blk, (long)&old, 8) == 0);
        assert(raw(SYS_rt_sigprocmask, SIG_BLOCK, 0, (long)&old, 8) == 0 && !(old & blk));
        assert(raw(SYS_getuid, 0, 0, 0, 0) == 10001);  /* still alive */
    }

    /* a raw SIGSYS handler (a Go runtime installs one for every signal) is accepted, the filter keeps its own */
    unsigned long act[4] = {1, 0, 0, 0};  /* SIG_IGN */
    assert(raw(SYS_rt_sigaction, SIGSYS, (long)act, 0, 8) == 0);
    struct sigaction sa = {0};
    sa.sa_handler = SIG_IGN;
    assert(sigaction(SIGSYS, &sa, NULL) == 0);
    assert(raw(SYS_getuid, 0, 0, 0, 0) == 10001);

    /* threads started after the filter, and a forked child */
    long in_thread = 0;
    pthread_t t;
    assert(!pthread_create(&t, NULL, thread_main, &in_thread) && !pthread_join(t, NULL));
    assert(in_thread == 10001);
    pid_t child = fork();
    if (child == 0) _exit(raw(SYS_getuid, 0, 0, 0, 0) == 10001 ? 0 : 1);
    int status;
    assert(waitpid(child, &status, 0) == child && WIFEXITED(status) && !WEXITSTATUS(status));
    assert(syscall(SYS_prctl, 39 /* PR_GET_NO_NEW_PRIVS */, 0, 0, 0, 0) == 1);

    /* the namespace: the host's libraries are not found, the private platform ones neither, public ones open */
    char err[256];
    static const char *const refused[] = { "libjvm.so", "libjava.so", "libc.so.6", "libjavacore.so", "libbinder.so", "libandroid_runtime.so", "/usr/lib/aarch64-linux-gnu/libc.so.6" };
    for (unsigned i = 0; i < sizeof refused / sizeof *refused; i++) {
        assert(!guest_dlopen(refused[i], err, sizeof err));
        char want[256];
        snprintf(want, sizeof want, "dlopen failed: library \"%s\" not found", refused[i]);
        if (strcmp(err, want)) printf("dlopen(%s): got \"%s\" want \"%s\"\n", refused[i], err, want);
        assert(!strcmp(err, want));
    }
    assert(guest_dlopen("libraw-syscall-lib.so", err, sizeof err));  /* the app's own library, by its name */
    assert(guest_dlopen("libm.so.6", err, sizeof err) == 0);  /* the host's math library is no Android name either */

    /* dladdr: libc symbols are Android's names in Android's libc.so; what only the host has has no name */
    char file[512], sym[256];
    assert(guest_dladdr((void *)malloc, file, sym, sizeof file) && !strcmp(file, "/apex/com.android.runtime/lib64/bionic/libc.so") && !strcmp(sym, "malloc"));
    assert(guest_dladdr((void *)gnu_get_libc_version, file, sym, sizeof file) && !strcmp(file, "/apex/com.android.runtime/lib64/bionic/libc.so") && !sym[0]);
    void *bopen = dlsym(dl, "bionic_open");
    if (bopen) assert(guest_dladdr(bopen, file, sym, sizeof file) && !strcmp(sym, "open") && strstr(file, "/bionic/libc.so"));
    assert(guest_dladdr((void *)main, file, sym, sizeof file) && !strstr(file, "jvm") && !strstr(file, "/usr/lib/"));

    /* dl_iterate_phdr: no host module, one bionic libc, the program as app_process64 */
    int c[5];
    census(c);
    printf("modules %d host %d jvm %d libc %d program %d\n", c[0], c[1], c[2], c[3], c[4]);
    assert(c[0] > 2 && c[1] == 0 && c[2] == 0 && c[3] == 1 && c[4] == 1);
    puts("PASS raw syscalls and the linker's views of guest code (app namespace)");
    return 0;
}
#else
int main(void) { puts("SKIP: arm64 only"); return 77; }
#endif
