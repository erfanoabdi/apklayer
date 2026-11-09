/* bionic_translation's libc wrappers (patches/bionic_translation 0024) take bionic code's mkdir, rename, unlink, unlinkat and rmdir through the
 * path layer (bridge/native/paths.c, installed as the shim does): Android's storage spellings (/storage/emulated/0, /sdcard, /data/media/0) land
 * in the instance's storage (APKLAYER_STORAGE, a scratch directory here). argv[1]: libc_bio. */
#define _GNU_SOURCE
#include <assert.h>
#include <dlfcn.h>
#include <fcntl.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

bool apklayer_apply_path_overrides(char **path);
void apklayer_map_own_data(const char *pkg, long uid, const char *groups, const char *root);

static char real[512];
static const char *in(const char *rel) { snprintf(real, sizeof real, "%s/emulated/0/%s", getenv("APKLAYER_STORAGE"), rel); return real; }
static bool there(const char *rel) { return access(in(rel), F_OK) == 0; }
static void create(const char *rel) { int fd = open(in(rel), O_CREAT | O_WRONLY, 0600); assert(fd >= 0); close(fd); }

int main(int argc, char **argv) {
    assert(argc == 2);
    char tmpl[] = "/tmp/apklayer-bionic-paths-XXXXXX";
    if (!getenv("APKLAYER_STORAGE")) {  /* the path layer reads it once, when it loads: the check runs again with it set */
        assert(mkdtemp(tmpl));
        setenv("APKLAYER_STORAGE", tmpl, 1);
        execv("/proc/self/exe", argv);
        return 1;
    }
    snprintf(tmpl, sizeof tmpl, "%s", getenv("APKLAYER_STORAGE"));
    snprintf(real, sizeof real, "%s/emulated", tmpl);
    assert(mkdir(real, 0700) == 0 && mkdir(in(""), 0700) == 0);
    void *libc = dlopen(argv[1], RTLD_NOW | RTLD_LOCAL);
    assert(libc);
    void (*set)(bool (*)(char **)) = dlsym(libc, "libc_bio_set_apply_path_overrides_func");
    int (*b_mkdir)(const char *, mode_t) = dlsym(libc, "bionic_mkdir"), (*b_rmdir)(const char *) = dlsym(libc, "bionic_rmdir");
    int (*b_rename)(const char *, const char *) = dlsym(libc, "bionic_rename"), (*b_unlink)(const char *) = dlsym(libc, "bionic_unlink");
    int (*b_unlinkat)(int, const char *, int) = dlsym(libc, "bionic_unlinkat");
    assert(set && b_mkdir && b_rmdir && b_rename && b_unlink && b_unlinkat);
    set(apklayer_apply_path_overrides);
    assert(b_mkdir("/sdcard/d", 0700) == 0 && there("d"));
    create("d/a");
    assert(b_rename("/sdcard/d/a", "/storage/emulated/0/d/b") == 0 && !there("d/a") && there("d/b"));
    assert(b_unlinkat(AT_FDCWD, "/storage/emulated/0/d/b", 0) == 0 && !there("d/b"));
    create("d/c");
    assert(b_unlink("/data/media/0/d/c") == 0 && !there("d/c"));
    assert(b_rmdir("/sdcard/d") == 0 && !there("d"));
    /* the app's own /data/data/<pkg> (apklayer_map_own_data): its data tree is a scratch directory below the storage one here */
    assert(mkdir(in("own"), 0700) == 0 && mkdir(in("own/app"), 0700) == 0);
    char own[512];
    snprintf(own, sizeof own, "%s", in("own"));
    apklayer_map_own_data("p.kg", -1, NULL, own);
    assert(b_mkdir("/data/data/p.kg/d", 0700) == 0 && there("own/app/d"));
    assert(b_rename("/data/user/0/p.kg/d", "/data/data/p.kg/e") == 0 && there("own/app/e"));
    assert(b_rmdir("/data/data/p.kg/e") == 0 && !there("own/app/e"));
    assert(rmdir(in("own/app")) == 0 && rmdir(in("own")) == 0);
    dlclose(libc);
    char cmd[600];
    snprintf(cmd, sizeof cmd, "rm -rf '%s'", tmpl);
    assert(system(cmd) == 0);
    puts("PASS bionic mkdir, rename, unlinkat, unlink, rmdir of /storage, /sdcard and /data/media/0 in the instance's storage, and of the app's own /data/data/<pkg>");
    return 0;
}
