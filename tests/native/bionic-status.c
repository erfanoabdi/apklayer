/* bionic's open of /proc/self/status (bionic_translation 0071): a descriptor that reads, the app's projection (ids, security state) through it, on the
 * same view as the glibc open. Upstream returned fileno() of an fmemopen stream, -1, so every read failed with EBADF (an app's native code, and a toybox
 * child's `cat /proc/self/status`). argv[1]: libc_bio. */
#define _GNU_SOURCE
#include <assert.h>
#include <dlfcn.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

int main(int argc, char **argv)
{
    assert(argc == 2);
    void *c = dlopen(argv[1], RTLD_NOW | RTLD_GLOBAL);
    assert(c);
    int (*bopen)(const char *, int, ...) = dlsym(c, "bionic_open");
    int (*bopenat)(int, const char *, int, ...) = dlsym(c, "bionic_openat");
    assert(bopen && bopenat);
    int app = getenv("APKLAYER_OWN_DATA") && !getenv("APKLAYER_PROC_VIEW");
    for (int variant = 0; variant < 2; variant++) {
        int fd = variant ? bopenat(AT_FDCWD, "/proc/self/status", O_RDONLY) : bopen("/proc/self/status", O_RDONLY);
        assert(fd >= 0);
        char text[8192];
        ssize_t n = read(fd, text, sizeof text - 1);
        assert(n > 0);
        text[n] = 0;
        close(fd);
        char *uid = strstr(text, "\nUid:");
        assert(uid && strstr(text, "\nTracerPid:\t0"));
        unsigned u;
        assert(sscanf(uid + 5, "%u", &u) == 1);
        printf("bionic %s: read %zd bytes, Uid %u, NoNewPrivs %s\n", variant ? "openat" : "open", n, u, strstr(text, "NoNewPrivs:\t1") ? "1" : "0");
        if (app) assert(strstr(text, "NoNewPrivs:\t1") && strstr(text, "Seccomp:\t2"));
    }
    puts("bionic-status ok");
    return 0;
}
