/* Without the sandbox an app runs as the desktop user; Android code sees its app uid: the id calls, and the owner of the user's files
 * (bionic_translation 0055: Termux refuses a system uid). */
#define _GNU_SOURCE
#include <assert.h>
#include <dlfcn.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/stat.h>
#include <unistd.h>
int main(int argc, char **argv)
{
    assert(argc == 2);
    void *lib = dlopen(argv[1], RTLD_NOW);
    if (!lib) { fprintf(stderr, "%s\n", dlerror()); return 1; }
    void (*set)(long) = dlsym(lib, "libc_bio_set_app_uid");
    uid_t (*uid)(void) = dlsym(lib, "bionic_getuid"), (*euid)(void) = dlsym(lib, "bionic_geteuid");
    gid_t (*gid)(void) = dlsym(lib, "bionic_getgid");
    int (*resuid)(uid_t *, uid_t *, uid_t *) = dlsym(lib, "bionic_getresuid");
    int (*st)(const char *, struct stat *) = dlsym(lib, "bionic_stat"), (*fst)(int, struct stat *) = dlsym(lib, "bionic_fstat");
    int (*sx)(int, const char *, int, unsigned, struct statx *) = dlsym(lib, "bionic_statx");
    assert(set && uid && euid && gid && resuid && st && fst && sx);
    char own[] = "/tmp/app-uid-XXXXXX";
    int fd = mkstemp(own);
    assert(fd >= 0);
    struct stat s;
    assert(uid() == getuid() && st(own, &s) == 0 && s.st_uid == getuid());  /* not set: the real ids */
    set(12345);
    uid_t r, e, sv;
    assert(uid() == 12345 && euid() == 12345 && gid() == 12345 && resuid(&r, &e, &sv) == 0 && r == 12345 && e == 12345 && sv == 12345);
    assert(st(own, &s) == 0 && s.st_uid == 12345 && s.st_gid == 12345 && fst(fd, &s) == 0 && s.st_uid == 12345);
    struct statx x;
    assert(sx(AT_FDCWD, own, 0, STATX_UID | STATX_GID, &x) == 0 && x.stx_uid == 12345);
    if (getuid() != 0) assert(st("/", &s) == 0 && s.st_uid == 0);  /* root's files stay root's */
    close(fd);
    unlink(own);
    puts("PASS the app uid for ids and the user's files");
    return 0;
}
