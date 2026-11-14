/* What guest code sees on native arm64 (bionic_translation 0071, 0072; bridge/native/raw_syscall.c). A library loaded by bionic_translation's linker
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
#include <sys/syscall.h>
#include <sys/wait.h>
#include <unistd.h>

#if defined(__aarch64__)
static long (*raw)(long, long, long, long, long);
static void *thread_main(void *arg)
{
    *(long *)arg = raw(SYS_getuid, 0, 0, 0, 0);
    return NULL;
}

int main(int argc, char **argv)
{
    assert(argc == 4);
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
    void *lib = b_dlopen(argv[2], RTLD_NOW);
    assert(lib);
    raw = b_dlsym(lib, "raw_sys");
    int (*guest_dlopen)(const char *, char *, size_t) = b_dlsym(lib, "guest_dlopen");
    int (*guest_dladdr)(const void *, char *, char *, size_t) = b_dlsym(lib, "guest_dladdr");
    void (*census)(int *) = b_dlsym(lib, "guest_census");
    assert(raw && guest_dlopen && guest_dladdr && census);

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
    assert(raw(SYS_getresuid, (long)&r[0], (long)&r[1], (long)&r[2]) == 0 && r[0] == 10001 && r[1] == 10001 && r[2] == 10001);
    assert(raw(SYS_getresgid, (long)&g[0], (long)&g[1], (long)&g[2]) == 0 && g[0] == 10001 && g[2] == 10001);
    gid_t groups[16];
    long count = raw(SYS_getgroups, 0, 0, 0, 0);
    assert(count == 5 && raw(SYS_getgroups, 16, (long)groups, 0, 0) == 5 && groups[0] == 3003);
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
        assert(!strcmp(err, want));
    }
    assert(guest_dlopen("libc.so", err, sizeof err) && guest_dlopen("libdl.so", err, sizeof err) && guest_dlopen("libm.so", err, sizeof err));

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
