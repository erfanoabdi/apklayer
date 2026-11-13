/* Android's /etc files in an app's view (bridge/native/android-root/system/etc): /etc/passwd and /etc/group are the empty files Android ships, /etc/hosts its two
 * lines, instead of the desktop's user list and hosts. The path layer maps /etc/<name> to <android-root>/system/etc/<name> when that file exists (paths.c from_root).
 * Run against the source tree's root (APKLAYER_ANDROID_ROOT), as the shim sets it. argv[1]: that root. */
#define _GNU_SOURCE
#include <assert.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

bool apklayer_apply_path_overrides(char **path);

static void check(const char *path, long size, const char *must_contain) {
    char *p = strdup(path);
    assert(apklayer_apply_path_overrides(&p));
    assert(strstr(p, "/system/etc/") != NULL);
    struct stat st;
    assert(stat(p, &st) == 0 && st.st_size == size);
    if (must_contain) {
        FILE *f = fopen(p, "r"); char buf[256] = ""; size_t n = f ? fread(buf, 1, sizeof buf - 1, f) : 0; buf[n] = 0; if (f) fclose(f);
        assert(strstr(buf, must_contain));
    }
}

int main(int argc, char **argv) {
    assert(argc == 2);
    if (!getenv("APKLAYER_ANDROID_ROOT")) { setenv("APKLAYER_ANDROID_ROOT", argv[1], 1); execv("/proc/self/exe", argv); return 1; }
    check("/etc/passwd", 0, NULL);
    check("/etc/group", 0, NULL);
    { struct stat st; char *p = strdup("/etc/hosts"); assert(apklayer_apply_path_overrides(&p) && stat(p, &st) == 0 && st.st_size > 0); check("/etc/hosts", st.st_size, "localhost"); }
    puts("etc-files ok");
    return 0;
}
