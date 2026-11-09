/* Without the sandbox an app runs as the desktop user; Android code sees its app uid: the id calls, and the owner of the user's files
 * (bionic_translation 0055: Termux refuses a system uid). */
#define _GNU_SOURCE
#include <assert.h>
#include <dirent.h>
#include <dlfcn.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
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
    /* its supplementary groups are the app's, not the user's (0061) */
    void (*set_groups)(int, const gid_t *) = dlsym(lib, "libc_bio_set_app_groups");
    int (*groups)(int, gid_t *) = dlsym(lib, "bionic_getgroups");
    gid_t want[] = {3003, 9997, 22345, 52345}, got[8];
    assert(set_groups && groups);
    set_groups(4, want);
    assert(groups(0, NULL) == 4 && groups(2, got) == -1 && groups(8, got) == 4 && got[0] == 3003 && got[3] == 52345);
    /* readdir lists "." and ".." as bionic's does (0062) */
    struct { unsigned long ino; long off; unsigned short reclen; unsigned char type; char name[256]; } *(*rd)(DIR *) = dlsym(lib, "bionic_readdir");
    DIR *dir = opendir("/");
    int dots = 0;
    assert(rd && dir);
    for (typeof(rd(dir)) d; (d = rd(dir));) dots += !strcmp(d->name, ".") || !strcmp(d->name, "..");
    assert(dots == 2 && closedir(dir) == 0);
    /* an entry stays its stream's while another stream is read on the thread (Gecko's ScanStoreDir holds the outer one across its recursion) */
    DIR *outer = opendir("/"), *inner = opendir("/proc/self");
    typeof(rd(outer)) held = outer ? rd(outer) : NULL;
    char name[256];
    assert(held && inner && snprintf(name, sizeof name, "%s", held->name) > 0);
    while (rd(inner)) {}
    assert(!strcmp(held->name, name) && closedir(inner) == 0 && closedir(outer) == 0);
    close(fd);
    unlink(own);
    puts("PASS the app uid and groups for ids and the user's files; readdir lists . and ..");
    return 0;
}
