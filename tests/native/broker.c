/* The sandbox's file broker (bridge/native/broker.c, cli/broker.cpp) for the calls a Java test cannot make: broker-test <dir> as the app's uid in
 * `apklayer sandbox-exec` with the path layer preloaded (tests/vm/sandbox.sh). <dir> is a 0700 folder of the user's granted home holding p.txt (0600): the app's
 * uid may not search it, so every call here goes through apklayerd. Prints "[broker] <name> ok" per call that worked, "[broker] <name> FAIL <why>" else. */
#define _GNU_SOURCE
#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/time.h>
#include <sys/xattr.h>
#include <unistd.h>

static void result(const char *name, int ok) { ok ? printf("[broker] %s ok\n", name) : printf("[broker] %s FAIL (%s)\n", name, strerror(errno)); }

static long mtime(const char *path) { struct stat st; return stat(path, &st) == 0 ? (long)st.st_mtime : -1; }

static int put(const char *path, const char *text)
{
    int fd = open(path, O_WRONLY | O_CREAT | O_TRUNC, 0600);
    if (fd < 0) return -1;
    int ok = write(fd, text, strlen(text)) == (ssize_t)strlen(text);
    close(fd);
    return ok ? 0 : -1;
}

static int has(const char *dir, const char *name)
{
    DIR *d = opendir(dir);
    int found = 0;
    for (struct dirent *e; d && (e = readdir(d));) found |= !strcmp(e->d_name, name);
    if (d) closedir(d);
    return found;
}

static int only_p(const struct dirent *e) { return !strcmp(e->d_name, "p.txt"); }

int main(int argc, char **argv)
{
    if (argc != 2) return 2;
    char p[4096], a[4096], b[4096], tmpl[4096], buf[64], list[256];
    snprintf(p, sizeof p, "%s/p.txt", argv[1]);
    snprintf(a, sizeof a, "%s/ra.txt", argv[1]);
    snprintf(b, sizeof b, "%s/rb.txt", argv[1]);

    /* item 1: times and extended attributes */
    errno = 0;
    int ok = utimes(p, (struct timeval[2]){{1000000000, 0}, {1000000000, 0}}) == 0 && mtime(p) == 1000000000;
    result("utimes", ok);
    ok = utimensat(AT_FDCWD, p, (struct timespec[2]){{1100000000, 0}, {1100000000, 0}}, 0) == 0 && mtime(p) == 1100000000;
    result("utimensat", ok);
    int fd = open(p, O_RDONLY);
    ok = fd >= 0 && futimens(fd, (struct timespec[2]){{1200000000, 0}, {1200000000, 0}}) == 0 && mtime(p) == 1200000000;
    result("futimens", ok);
    ok = setxattr(p, "user.apkl", "value1", 6, 0) == 0 && getxattr(p, "user.apkl", buf, sizeof buf) == 6 && !memcmp(buf, "value1", 6);
    result("xattr-path", ok);
    ssize_t n = listxattr(p, list, sizeof list);
    ok = n > 0 && memmem(list, n, "user.apkl", 10) && getxattr(p, "user.apkl", NULL, 0) == 6;
    result("xattr-list", ok);
    ok = fd >= 0 && fsetxattr(fd, "user.apkl2", "v2", 2, 0) == 0 && fgetxattr(fd, "user.apkl2", buf, sizeof buf) == 2 && removexattr(p, "user.apkl") == 0 &&
         fremovexattr(fd, "user.apkl2") == 0 && getxattr(p, "user.apkl", buf, sizeof buf) < 0 && errno == ENODATA;
    result("xattr-fd-remove", ok);
    if (fd >= 0) close(fd);

    /* item 2: renameat2's flags */
    ok = put(a, "A") == 0 && put(b, "B") == 0;
    result("renameat2-setup", ok);
    ok = renameat2(AT_FDCWD, a, AT_FDCWD, b, RENAME_NOREPLACE) < 0 && errno == EEXIST;
    result("renameat2-noreplace", ok);
    ok = renameat2(AT_FDCWD, a, AT_FDCWD, b, RENAME_EXCHANGE) == 0;
    char got[8] = {0};
    fd = open(a, O_RDONLY);
    ok = ok && fd >= 0 && read(fd, got, 7) == 1 && got[0] == 'B';
    if (fd >= 0) close(fd);
    result("renameat2-exchange", ok);
    snprintf(tmpl, sizeof tmpl, "%s/rc.txt", argv[1]);
    ok = renameat2(AT_FDCWD, a, AT_FDCWD, tmpl, RENAME_NOREPLACE) == 0 && access(a, F_OK) != 0;
    result("renameat2-new-name", ok);
    unlink(tmpl);
    unlink(b);
    unlink(a);

    /* item 3: the glibc calls that open inside glibc */
    snprintf(tmpl, sizeof tmpl, "%s/tmpXXXXXX", argv[1]);
    fd = mkstemp(tmpl);
    ok = fd >= 0 && strcmp(tmpl + strlen(tmpl) - 6, "XXXXXX") && write(fd, "t", 1) == 1 && access(tmpl, F_OK) == 0;
    result("mkstemp", ok);
    if (fd >= 0) { close(fd); unlink(tmpl); }
    snprintf(tmpl, sizeof tmpl, "%s/dirXXXXXX", argv[1]);
    ok = mkdtemp(tmpl) != NULL && strcmp(tmpl + strlen(tmpl) - 6, "XXXXXX") && rmdir(tmpl) == 0;
    result("mkdtemp", ok);
    struct dirent **names = NULL;
    int count = scandir(argv[1], &names, only_p, alphasort);
    ok = count == 1 && !strcmp(names[0]->d_name, "p.txt");
    for (int i = 0; i < count; i++) free(names[i]);
    free(names);
    result("scandir", ok);
    result("opendir-readdir", has(argv[1], "p.txt"));
    return 0;
}
