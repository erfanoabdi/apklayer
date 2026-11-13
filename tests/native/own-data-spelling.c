/* The path layer's reverse mapping of the app's own data directory (bridge/native/paths.c own_data_spelling): the host path the kernel names for a file
 * below <root>/app or <root>/device reads as /data/user/0/<pkg> or /data/user_de/0/<pkg> through readlink of /proc/self/fd/N and realpath, and the forward
 * mapping stays (the Android spelling opens the same file). APKLAYER_OWN_DATA is the process's own data mapping (cli sets it; a scratch root here). */
#define _GNU_SOURCE
#include <assert.h>
#include <fcntl.h>
#include <limits.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

#define PKG "com.example.own"

int main(int argc, char **argv) {
    char root[] = "/tmp/apklayer-own-data-XXXXXX";
    if (!getenv("APKLAYER_OWN_DATA")) {  /* the path layer reads it once, when it loads: run again with it set */
        assert(mkdtemp(root));
        char env[600], dir[600];
        snprintf(env, sizeof env, PKG ":10099:%s", root);
        assert(setenv("APKLAYER_OWN_DATA", env, 1) == 0);
        snprintf(dir, sizeof dir, "%s/app", root);
        assert(mkdir(dir, 0700) == 0);
        snprintf(dir, sizeof dir, "%s/device", root);
        assert(mkdir(dir, 0700) == 0);
        execv("/proc/self/exe", argv);
        return 1;
    }
    const char *v = getenv("APKLAYER_OWN_DATA");
    const char *root_in_env = strrchr(v, ':') + 1;
    char file[PATH_MAX], link[64], got[PATH_MAX];
    snprintf(file, sizeof file, "%s/app/note", root_in_env);
    int fd = open(file, O_CREAT | O_WRONLY, 0600);
    assert(fd >= 0);
    snprintf(link, sizeof link, "/proc/self/fd/%d", fd);
    ssize_t n = readlink(link, got, sizeof got - 1);
    assert(n > 0);
    got[n] = 0;
    assert(!strcmp(got, "/data/user/0/" PKG "/note"));
    close(fd);

    snprintf(file, sizeof file, "%s/device/dnote", root_in_env);
    fd = open(file, O_CREAT | O_WRONLY, 0600);
    assert(fd >= 0);
    snprintf(link, sizeof link, "/proc/self/fd/%d", fd);
    n = readlink(link, got, sizeof got - 1);
    assert(n > 0);
    got[n] = 0;
    assert(!strcmp(got, "/data/user_de/0/" PKG "/dnote"));
    close(fd);

    assert(realpath("/data/user/0/" PKG "/note", got) && !strcmp(got, "/data/user/0/" PKG "/note"));  /* realpath of the Android spelling: the same spelling back */
    assert(access("/data/data/" PKG "/note", F_OK) == 0);  /* the forward mapping is unchanged */

    snprintf(file, sizeof file, "%s/other", root_in_env);  /* a path outside the data directories keeps its host spelling */
    fd = open(file, O_CREAT | O_WRONLY, 0600);
    assert(fd >= 0);
    snprintf(link, sizeof link, "/proc/self/fd/%d", fd);
    n = readlink(link, got, sizeof got - 1);
    assert(n > 0);
    got[n] = 0;
    assert(!strcmp(got, file));
    close(fd);
    char cmd[PATH_MAX + 16];
    snprintf(cmd, sizeof cmd, "rm -rf '%s'", root_in_env);
    if (!strncmp(root_in_env, "/tmp/apklayer-own-data-", 23)) assert(system(cmd) == 0);
    puts("own-data-spelling ok");
    return 0;
}
