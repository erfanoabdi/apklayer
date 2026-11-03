/* The path layer's LD_PRELOAD part (bridge/native/paths_preload.c): the functions glibc does not route through open/stat/mkdir map /storage too
 * (APKLAYER_STORAGE is a scratch directory here). Each call is made with Android's spelling and checked against the scratch directory. */
#define _GNU_SOURCE
#include <assert.h>
#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/inotify.h>
#include <sys/stat.h>
#include <sys/xattr.h>
#include <unistd.h>

static char real[512];
static const char *in(const char *rel) { snprintf(real, sizeof real, "%s/%s", getenv("APKLAYER_STORAGE"), rel); return real; }
static int any(const struct dirent *d) { return d->d_name[0] != '.'; }

int main(int argc, char **argv) {
    char tmpl[] = "/tmp/apklayer-path-layer-XXXXXX";
    if (!getenv("APKLAYER_STORAGE")) {  /* the path layer reads it once: the check runs again with it set */
        assert(mkdtemp(tmpl));
        setenv("APKLAYER_STORAGE", tmpl, 1);
        execv("/proc/self/exe", argv);
        return 1;
    }
    strcpy(tmpl, getenv("APKLAYER_STORAGE"));
    assert(mkdir("/storage/t", 0700) == 0 && access(in("t"), F_OK) == 0);
    int fd = creat("/storage/t/a", 0600);
    assert(fd >= 0 && access(in("t/a"), F_OK) == 0);
    close(fd);
    assert(renameat2(AT_FDCWD, "/storage/t/a", AT_FDCWD, "/storage/t/b", 0) == 0 && access(in("t/b"), F_OK) == 0);
    assert(linkat(AT_FDCWD, "/storage/t/b", AT_FDCWD, "/storage/t/c", 0) == 0 && access(in("t/c"), F_OK) == 0);
    assert(symlinkat("b", AT_FDCWD, "/storage/t/d") == 0);
    char buf[64] = {0};
    assert(readlinkat(AT_FDCWD, "/storage/t/d", buf, sizeof buf) == 1 && buf[0] == 'b');
    assert(fchownat(AT_FDCWD, "/storage/t/b", getuid(), getgid(), 0) == 0);
    char name[] = "/storage/t/eXXXXXX";
    fd = mkstemp(name);
    assert(fd >= 0 && strncmp(name, "/storage/t/e", 12) == 0 && strcmp(name + 12, "XXXXXX") != 0);
    close(fd);
    assert(access(in(name + strlen("/storage/")), F_OK) == 0);
    char dir[] = "/storage/t/fXXXXXX";
    assert(mkdtemp(dir) == dir && access(in(dir + strlen("/storage/")), F_OK) == 0);
    struct dirent **list;
    int entries = scandir("/storage/t", &list, any, alphasort);
    if (entries != 5) fprintf(stderr, "scandir: %d entries (%s)\n", entries, strerror(errno));
    assert(entries == 5);  /* b c d e... f... */
    FILE *f = fopen("/storage/t/b", "r");
    assert(f && freopen("/storage/t/c", "r", f));
    fclose(f);
    assert(mkfifo("/storage/t/g", 0600) == 0 && access(in("t/g"), F_OK) == 0);
    int watch = inotify_init1(0);
    assert(watch >= 0 && inotify_add_watch(watch, "/storage/t", IN_CREATE) >= 0);
    if (setxattr("/storage/t/b", "user.apklayer", "1", 1, 0) == 0)  /* tmpfs without user xattrs answers EOPNOTSUPP, not ENOENT */
        assert(getxattr("/storage/t/b", "user.apklayer", buf, sizeof buf) == 1 && removexattr("/storage/t/b", "user.apklayer") == 0);
    else
        assert(errno != ENOENT);
    char cmd[600];
    snprintf(cmd, sizeof cmd, "rm -rf '%s'", tmpl);
    return system(cmd);
}
