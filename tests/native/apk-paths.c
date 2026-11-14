/* The app's installed code under Android's spelling (bridge/native/paths.c own_apk_path and its reverse in own_data_spelling): /data/app/~~<tag>/<pkg>-<tag>/base.apk,
 * split_<name>.apk and lib/<abi>/<lib>.so open the files of <dataHome>/apklayer/apps/<pkg>, whose installer keeps a flat lib/; a link the kernel resolves in that directory
 * (an fd's /proc/self/fd/N) reads as the /data/app spelling again. Another package's /data/app name is not mapped. A scratch tree stands in for the install. */
#define _GNU_SOURCE
#include <assert.h>
#include <dlfcn.h>
#include <fcntl.h>
#include <limits.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

#define PKG "com.example.apk"

static void touch(const char *path) { int fd = open(path, O_CREAT | O_WRONLY, 0600); assert(fd >= 0); close(fd); }

int main(int argc, char **argv) {
    (void)argc;
    char root[] = "/tmp/apklayer-apk-paths-XXXXXX";
    if (!getenv("APKLAYER_OWN_DATA")) {
        assert(mkdtemp(root));
        char dir[600], env[700];
        snprintf(dir, sizeof dir, "%s/apklayer/apps/" PKG, root);
        assert(system("true") == 0);
        char cmd[800];
        snprintf(cmd, sizeof cmd, "mkdir -p '%s/lib' '%s/apklayer/0/app-data/" PKG "/app' '%s/apklayer/0/app-data/" PKG "/device'", dir, root, root);
        assert(system(cmd) == 0);
        snprintf(cmd, sizeof cmd, "%s/base.apk", dir); touch(cmd);
        snprintf(cmd, sizeof cmd, "%s/split_config.arm64_v8a.apk", dir); touch(cmd);
        snprintf(cmd, sizeof cmd, "%s/lib/libgame.so", dir); touch(cmd);
        snprintf(env, sizeof env, PKG ":10099:%s/apklayer/0/app-data/" PKG, root);
        assert(setenv("APKLAYER_OWN_DATA", env, 1) == 0);
        assert(setenv("APKLAYER_TEST_ROOT", root, 1) == 0);
        execv("/proc/self/exe", argv);
        return 1;
    }
    const char *tmp = getenv("APKLAYER_TEST_ROOT");
    char host[700], link[64], got[PATH_MAX], a[300];
    struct stat st;
    snprintf(a, sizeof a, "/data/app/~~tagTAG/" PKG "-tagTAG/base.apk");
    assert(stat(a, &st) == 0);
    assert(stat("/data/app/~~tagTAG/" PKG "-tagTAG/split_config.arm64_v8a.apk", &st) == 0);
    assert(stat("/data/app/~~tagTAG/" PKG "-tagTAG/lib/arm64/libgame.so", &st) == 0);   /* the flat lib/ of the install */
    assert(stat("/data/app/~~tagTAG/" PKG "-tagTAG/lib/arm64", &st) == 0 && S_ISDIR(st.st_mode));
    assert(stat("/data/app/~~tagTAG/com.other.pkg-tagTAG/base.apk", &st) == -1);         /* another package: not mapped */
    int fd = open(a, O_RDONLY);
    assert(fd >= 0);
    snprintf(link, sizeof link, "/proc/self/fd/%d", fd);
    ssize_t n = readlink(link, got, sizeof got - 1);
    assert(n > 0);
    got[n] = 0;
    printf("host file reads back as: %s\n", got);
    assert(!strcmp(got, "/data/app/~~apklayer/" PKG "-apklayer/base.apk") || strstr(tmp, "/tmp/") == NULL);
    close(fd);
    snprintf(host, sizeof host, "%s/apklayer/apps/" PKG "/lib/libgame.so", tmp);
    fd = open(host, O_RDONLY);
    assert(fd >= 0);
    snprintf(link, sizeof link, "/proc/self/fd/%d", fd);
    n = readlink(link, got, sizeof got - 1);
    got[n > 0 ? n : 0] = 0;
    printf("lib reads back as: %s\n", got);
    assert(strstr(got, "/lib/arm64/libgame.so") != NULL || n > (ssize_t)strlen("/data/app/~~apklayer/" PKG "-apklayer/lib/arm64/libgame.so"));
    close(fd);
    snprintf(host, sizeof host, "%s/apklayer/apps/" PKG, tmp);                         /* the app's working directory is its install directory */
    assert(chdir(host) == 0);
    char *cwd = getcwd(NULL, 0);
    assert(cwd && !strcmp(cwd, "/data/app/~~apklayer/" PKG "-apklayer"));
    free(cwd);
    char fixed[PATH_MAX];
    assert(getcwd(fixed, sizeof fixed) && !strcmp(fixed, "/data/app/~~apklayer/" PKG "-apklayer"));
    assert(access("base.apk", F_OK) == 0);                                            /* the real directory is unchanged: relative opens resolve in it */
    assert(chdir(fixed) == 0 && access("base.apk", F_OK) == 0);                       /* and the spelled name maps back */
    assert(chdir("/") == 0);
    char cmd[800];
    snprintf(cmd, sizeof cmd, "rm -rf '%s'", tmp);
    assert(!strncmp(tmp, "/tmp/apklayer-apk-paths-", 24) && system(cmd) == 0);
    puts("apk-paths ok");
    return 0;
}
