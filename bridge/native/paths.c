/* Android's fixed paths for the desktop (PLAN P7.1 step 0.3). Android libraries read /system, /vendor, /apex, /etc/task_profiles.json,
 * /dev/binder ... A desktop has none of them, and an instance (PLAN P9) must not share them. This overlays them from the
 * instance's Android root: a path under a known prefix is served from $APKLAYER_ANDROID_ROOT/<path> (else <native>/android-root)
 * when that file exists there, otherwise the host's own file is used, so nothing the host needs is hidden. Installed into
 * bionic_translation's libc (open, fopen, stat, lstat, fstatat) through its apply_path_overrides hook. */
#define _GNU_SOURCE
#include <dlfcn.h>
#include <limits.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

static const char *const prefixes[] = {
    "/system/", "/vendor/", "/product/", "/system_ext/", "/odm/", "/apex/", "/data/", "/etc/", "/linkerconfig/", NULL,
};

static const char *root(void)
{
    static const char *r;
    static char buf[PATH_MAX];
    if (r) return r;
    const char *e = getenv("APKLAYER_ANDROID_ROOT"), *n = getenv("APKLAYER_NATIVE");
    if (e) snprintf(buf, sizeof buf, "%s", e);
    else if (n) snprintf(buf, sizeof buf, "%s/android-root", n);
    else buf[0] = 0;
    return r = buf;
}

bool apklayer_apply_path_overrides(char **path)
{
    const char *p = *path, *r = root();
    if (!r[0] || p[0] != '/') return false;
    for (int i = 0; prefixes[i]; i++) {
        if (strncmp(p, prefixes[i], strlen(prefixes[i]))) continue;
        char *mapped;
        if (asprintf(&mapped, "%s%s", r, p) < 0) return false;
        if (access(mapped, F_OK) != 0) { free(mapped); return false; } /* not in the instance's root: the host's file */
        *path = mapped;
        return true;
    }
    return false;
}

/* Hands the override function to bionic_translation's libc (loaded as a glibc library once Android code is). */
void apklayer_install_path_layer(void)
{
    void (*set)(bool (*)(char **)) = dlsym(RTLD_DEFAULT, "libc_bio_set_apply_path_overrides_func");
    if (set) set(apklayer_apply_path_overrides);
}
