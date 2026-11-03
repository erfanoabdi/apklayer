/* Android's fixed paths for the desktop (PLAN P7.1 step 0.3). Android libraries read /system, /vendor, /apex, /etc/task_profiles.json,
 * /dev/binder ... A desktop has none of them, and an instance (PLAN P9) must not share them. This overlays them from the
 * instance's Android root: a path under a known prefix is served from $APKLAYER_ANDROID_ROOT/<path> (else <native>/android-root)
 * when that file exists there, otherwise the host's own file is used, so nothing the host needs is hidden. What one instance stages for
 * itself (the vintf fragments of the HALs its stack runs) is in $APKLAYER_INSTANCE_ROOT, looked at first. Installed into
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

/* An instance other than "default" has its own binder contexts: /dev/<name> is /dev/binderfs/<name>-<instance>
 * (created by tools/host-setup/binder.sh <instance>), so two instances never share a servicemanager. */
static bool binder_device(char **path)
{
    static const char *const names[] = { "binder", "hwbinder", "vndbinder", NULL };
    const char *inst = getenv("APKLAYER_INSTANCE");
    if (!inst || !inst[0] || !strcmp(inst, "default") || strncmp(*path, "/dev/", 5)) return false;
    for (int i = 0; names[i]; i++) {
        if (strcmp(*path + 5, names[i])) continue;
        char *mapped;
        if (asprintf(&mapped, "/dev/binderfs/%s-%s", names[i], inst) < 0) return false;
        *path = mapped;
        return true;
    }
    return false;
}

static bool from_root(const char *r, char **path);

bool apklayer_apply_path_overrides(char **path)
{
    static int debug = -1;
    if (debug < 0) debug = getenv("APKLAYER_PATHS_DEBUG") != NULL;
    if (debug) fprintf(stderr, "[paths] %s (root %s)\n", *path, root());
    if (binder_device(path)) return true;
    const char *own = getenv("APKLAYER_INSTANCE_ROOT");
    return (own && own[0] && from_root(own, path)) || from_root(root(), path);
}

static bool from_root(const char *r, char **path)
{
    const char *p = *path;
    if (!r[0] || p[0] != '/') return false;
    for (int i = 0; prefixes[i]; i++) {
        if (strncmp(p, prefixes[i], strlen(prefixes[i]))) continue;
        /* /etc is a symlink to /system/etc on Android */
        const char *tries[2] = { "", !strcmp(prefixes[i], "/etc/") ? "/system" : NULL };
        for (int t = 0; t < 2 && tries[t]; t++) {
            char *mapped;
            if (asprintf(&mapped, "%s%s%s", r, tries[t], p) < 0) return false;
            if (access(mapped, F_OK) == 0) { *path = mapped; return true; }
            if (!strcmp(prefixes[i], "/data/")) {  /* a file that is yet to be created (keystore2's database and its journal): served when its directory is in the root */
                char *dir = strdup(mapped), *slash = strrchr(dir, '/');
                if (slash) *slash = 0;
                int there = slash && access(dir, F_OK) == 0;
                free(dir);
                if (there) { *path = mapped; return true; }
            }
            free(mapped); /* not in the instance's root: try the next spelling, then the host's own file */
        }
        return false;
    }
    return false;
}

/* Hands the override function to bionic_translation's libc (loaded as a glibc library once Android code is). */
void apklayer_install_path_layer(void)
{
    void (*set)(bool (*)(char **)) = dlsym(RTLD_DEFAULT, "libc_bio_set_apply_path_overrides_func");
    if (set) set(apklayer_apply_path_overrides);
    if (getenv("APKLAYER_PATHS_DEBUG")) fprintf(stderr, "[paths] path layer %s\n", set ? "installed" : "NOT installed (libc_bio not loaded)");
}
