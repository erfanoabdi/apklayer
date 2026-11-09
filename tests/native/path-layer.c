/* The path layer's LD_PRELOAD part (bridge/native/paths_preload.c): the functions glibc does not route through open/stat/mkdir map /storage too
 * (APKLAYER_STORAGE is a scratch directory here). Each call is made with Android's spelling and checked against the scratch directory. */
#define _GNU_SOURCE
#include <assert.h>
#include <dirent.h>
#include <dlfcn.h>
#include <sys/wait.h>
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
        char data[600];
        snprintf(data, sizeof data, "%s/data", tmpl);
        assert(mkdir(data, 0700) == 0 && setenv("APKLAYER_DATA", data, 1) == 0);  /* the instance's /data */
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
    /* the app's own /data/data/<pkg>, /data/user/0/<pkg> and /data/user_de/0/<pkg> (paths.c apklayer_map_own_data), not other packages' */
    void (*map_own)(const char *, long, const char *, const char *) = (void (*)(const char *, long, const char *, const char *))dlsym(RTLD_DEFAULT, "apklayer_map_own_data");
    assert(map_own);
    assert(mkdir(in("own"), 0700) == 0 && mkdir(in("own/app"), 0700) == 0 && mkdir(in("own/app/files"), 0700) == 0 && mkdir(in("own/device"), 0700) == 0);
    char own[512];
    snprintf(own, sizeof own, "%s/own", tmpl);
    map_own("p.kg", 12345, "3003,9997", own);
    fd = open("/data/data/p.kg/files/x", O_CREAT | O_WRONLY, 0600);
    assert(fd >= 0 && access(in("own/app/files/x"), F_OK) == 0);
    close(fd);
    assert(access("/data/user/0/p.kg/files/x", F_OK) == 0);
    /* the instance's /data/data/p.kg is the app's tree too: a walk component by component (Termux's mkdir -p) lands there */
    int walk = open("/data/data", O_DIRECTORY | O_RDONLY);
    assert(walk >= 0 && mkdirat(walk, "p.kg/files/w", 0700) == 0 && access(in("own/app/files/w"), F_OK) == 0);
    close(walk);
    assert(mkdir("/data/user_de/0/p.kg/d", 0700) == 0 && access(in("own/device/d"), F_OK) == 0);
    assert(access("/data/data/p.kgx/files/x", F_OK) != 0 && access("/data/data/other/files/x", F_OK) != 0 && access("/data/user/0/other/files/x", F_OK) != 0);
    /* a program the app runs from its data dir maps them too (exec_preload.c: the path, and APKLAYER_OWN_DATA in its environment) */
    assert(symlink("/bin/sh", "/data/data/p.kg/files/sh") == 0);
    pid_t child = fork();
    if (child == 0) {
        unsetenv("APKLAYER_OWN_DATA");
        execv("/data/data/p.kg/files/sh", (char *[]){"sh", "-c", "case $APKLAYER_OWN_DATA in p.kg:12345,3003,9997:/*) test -e /data/user/0/p.kg/files/x;; *) exit 1;; esac", NULL});
        _exit(127);
    }
    int status;
    assert(waitpid(child, &status, 0) == child && WIFEXITED(status) && WEXITSTATUS(status) == 0);
    /* and a script there whose interpreter is there too (Termux's #!/data/data/com.termux/files/usr/bin/sh) */
    FILE *script = fopen("/data/data/p.kg/files/s", "w");
    assert(script && fputs("#!/data/data/p.kg/files/sh -e\ntest -e /data/user/0/p.kg/files/x\n", script) >= 0 && fclose(script) == 0);
    assert(chmod("/data/data/p.kg/files/s", 0700) == 0);
    if ((child = fork()) == 0) {
        execvp("/data/data/p.kg/files/s", (char *[]){"s", NULL});
        _exit(127);
    }
    assert(waitpid(child, &status, 0) == child && WIFEXITED(status) && WEXITSTATUS(status) == 0);
    char cmd[600];
    snprintf(cmd, sizeof cmd, "rm -rf '%s'", tmpl);
    return system(cmd);
}
