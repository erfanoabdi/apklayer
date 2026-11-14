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
#include <sys/syscall.h>
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
    /* a native bridge's kernel emulation runs the guest's symlinkat as the raw syscall: the link's path is argument 3 (a translated Gecko's profile
     * lock, "<profile>/lock" -> "ip:+pid", failed with ENOENT and Gecko's XRE_main ended early), the target text stays as the caller wrote it */
    assert(syscall(SYS_symlinkat, "127.0.0.1:+1", AT_FDCWD, "/storage/t/lock") == 0);
    assert(readlinkat(AT_FDCWD, in("t/lock"), buf, sizeof buf) == 12 && !memcmp(buf, "127.0.0.1:+1", 12));
    assert(syscall(SYS_truncate, "/storage/t/b", 0) == 0 && unlink("/storage/t/lock") == 0);
    assert(fchownat(AT_FDCWD, "/storage/t/b", getuid(), getgid(), 0) == 0);
    {   /* a descriptor-relative stat (glibc's fstat is fstatat(fd, "", AT_EMPTY_PATH)): the /proc filter asks whether fd is /proc with a raw
         * syscall that reaches the preload's syscall() again; it recursed until the stack ran out (systemd-run under the preload, 2026-11-13) */
        struct stat st;
        int dir = open(in("t"), O_RDONLY | O_DIRECTORY);
        assert(dir >= 0 && fstatat(dir, "", &st, AT_EMPTY_PATH) == 0 && S_ISDIR(st.st_mode) && fstat(dir, &st) == 0);
        assert(fstatat(dir, "b", &st, 0) == 0 && syscall(SYS_newfstatat, dir, "", &st, AT_EMPTY_PATH) == 0);
        close(dir);
    }
    /* glibc < 2.33's stat entry points, which programs built against it call (the Sailfish package's JDK) */
    int (*xstat64)(int, const char *, struct stat64 *) = (int (*)(int, const char *, struct stat64 *))dlsym(RTLD_DEFAULT, "__xstat64");
    int (*lxstat64)(int, const char *, struct stat64 *) = (int (*)(int, const char *, struct stat64 *))dlsym(RTLD_DEFAULT, "__lxstat64");
    struct stat64 xs;
    int ver = 0;  /* _STAT_VER: the kernel's struct stat */
#ifdef __x86_64__
    ver = 1;
#endif
    assert(xstat64 && xstat64(ver, "/storage/t/d", &xs) == 0 && S_ISREG(xs.st_mode));
    assert(lxstat64 && lxstat64(ver, "/storage/t/d", &xs) == 0 && S_ISLNK(xs.st_mode));
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
    /* /proc as an app sees it (paths.c apklayer_proc_pid_hidden): processes of another cgroup (init, the desktop) are gone from stat and readdir,
     * its own and /proc/self stay. Skipped where every process shares one cgroup (a container). */
    {
        char mine[256] = "", init[256] = "";
        FILE *c = fopen("/proc/self/cgroup", "r");
        if (c) { fgets(mine, sizeof mine, c); fclose(c); }
        if ((c = fopen("/proc/1/cgroup", "r"))) { fgets(init, sizeof init, c); fclose(c); }
        if (!getenv("APKLAYER_PROC_VIEW") && strcmp(mine, init)) {
            struct stat st;
            char self[32];
            snprintf(self, sizeof self, "/proc/%d", getpid());
            assert(stat("/proc/1", &st) != 0 && errno == ENOENT && stat("/proc/1/cmdline", &st) != 0);
            assert(stat(self, &st) == 0 && stat("/proc/self/status", &st) == 0 && stat("/proc/meminfo", &st) == 0);
            /* other spellings of the same pid, and fd-relative lookups on an open /proc */
            assert(stat("/proc//1/status", &st) != 0 && stat("/proc/./1", &st) != 0 && stat("/proc/self/../1", &st) != 0 && stat("/proc/self/root/proc/1", &st) != 0);
            int procfd = open("/proc", O_RDONLY | O_DIRECTORY);
            assert(procfd >= 0 && fstatat(procfd, "1", &st, 0) != 0 && openat(procfd, "1/status", O_RDONLY) < 0 && fstatat(procfd, "self", &st, 0) == 0);
            close(procfd);
            /* a link to a hidden process is not made, and the cwd spelling of /proc/1 is hidden */
            assert(symlink("/proc/1", in("lnk")) != 0 && errno == ENOENT);
            assert(symlinkat("/proc/1", AT_FDCWD, in("lnk2")) != 0);
            assert(fstatat(open("/proc", O_RDONLY | O_DIRECTORY), "self/../1", &st, 0) != 0);
            struct dirent **scanned;
            int scanned_n = scandir("/proc", &scanned, NULL, NULL);
            assert(scanned_n > 0);
            for (int i = 0; i < scanned_n; i++) { assert(strcmp(scanned[i]->d_name, "1") != 0); free(scanned[i]); }
            free(scanned);
            DIR *proc = opendir("/proc");
            int saw_self = 0;
            for (struct dirent *e; proc && (e = readdir(proc));) {
                assert(strcmp(e->d_name, "1") != 0);
                saw_self |= atoi(e->d_name) == getpid();
            }
            assert(proc && saw_self);
            closedir(proc);
        }
    }
    char cmd[600];
    snprintf(cmd, sizeof cmd, "rm -rf '%s'", tmpl);
    return system(cmd);
}
