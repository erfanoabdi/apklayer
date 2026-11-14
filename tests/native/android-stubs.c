/* Android's file existence in an app's view (paths.c desktop_root_path, android_stub_path): /usr, /home ... are ENOENT as exact directories (files below stay
 * for the host libraries), /data/local/tmp is an empty writable directory and /system/bin/app_process64 an executable ELF. */
#define _GNU_SOURCE
#include <assert.h>
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

int main(int argc, char **argv) {
    char tmpl[] = "/tmp/apklayer-stubs-XXXXXX";
    struct stat st;
    (void)argc;
    if (!getenv("APKLAYER_DATA")) {  /* the path layer reads it once */
        assert(mkdtemp(tmpl));
        setenv("APKLAYER_DATA", tmpl, 1);
        execv("/proc/self/exe", argv);
        return 1;
    }
    int usr = stat("/usr", &st) == 0;
    void (*map)(const char *, long, const char *, const char *) = dlsym(RTLD_DEFAULT, "apklayer_map_own_data");
    assert(map);
    map("p.stubs", 12345, "", getenv("APKLAYER_DATA"));
    if (usr) assert(stat("/usr", &st) && errno == ENOENT);
    assert(stat("/usr/bin", &st) == 0 || !usr);  /* below the node stays */
    assert(stat("/lib64", &st) != 0 && errno == ENOENT);
    assert(stat("/data/local/tmp", &st) == 0 && S_ISDIR(st.st_mode) && access("/data/local/tmp", W_OK) == 0);
    unsigned char magic[4];
    int fd = open("/system/bin/app_process64", O_RDONLY);
    assert(fd >= 0 && read(fd, magic, 4) == 4 && !memcmp(magic, "\177ELF", 4) && access("/system/bin/app_process64", X_OK) == 0);
    char cmd[600];
    snprintf(cmd, sizeof cmd, "rm -rf '%s'", getenv("APKLAYER_DATA"));
    system(cmd);
    puts("android-stubs ok");
    return 0;
}
