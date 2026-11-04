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

/* The environment is read once, when this library loads: the path layer runs inside every stat/open, also while another thread's setenv
 * (GeckoView's mozglue sets variables from its own threads) reallocates environ, and a getenv walking the freed array crashed the JVM. */
static char root_buf[PATH_MAX];
static const char *inst_env, *own_env, *storage_env = "", *data_env = "", *host_binder_env, *host_hwbinder_env;
static int debug_env;

static const char *copy_env(const char *name, const char *fallback) { const char *v = getenv(name); return v ? strdup(v) : fallback; }

static void own_data_from_env(void);

__attribute__((constructor)) static void read_env(void)
{
    const char *e = getenv("APKLAYER_ANDROID_ROOT"), *n = getenv("APKLAYER_NATIVE");
    if (e) snprintf(root_buf, sizeof root_buf, "%s", e);
    else if (n) snprintf(root_buf, sizeof root_buf, "%s/android-root", n);
    inst_env = copy_env("APKLAYER_INSTANCE", NULL);
    host_binder_env = copy_env("APKLAYER_HOST_BINDER", NULL);
    host_hwbinder_env = copy_env("APKLAYER_HOST_HWBINDER", NULL);
    own_env = copy_env("APKLAYER_INSTANCE_ROOT", NULL);
    storage_env = copy_env("APKLAYER_STORAGE", "");
    data_env = copy_env("APKLAYER_DATA", "");
    debug_env = getenv("APKLAYER_PATHS_DEBUG") != NULL;
    own_data_from_env();
}

static const char *root(void) { return root_buf; }

/* An instance other than "default" has its own binder contexts: /dev/<name> is /dev/binderfs/<name>-<instance>
 * (created by tools/host-setup/binder.sh <instance>), so two instances never share a servicemanager.
 * /dev/host_binder and /dev/host_hwbinder are the host contexts of libbinder and libhwbinder (docs/HALIUM.md): the device's binder and
 * hwbinder named by $APKLAYER_HOST_BINDER and $APKLAYER_HOST_HWBINDER. */
static bool binder_device(char **path)
{
    static const char *const names[] = { "binder", "hwbinder", "vndbinder", NULL };
    const char *inst = inst_env;
    if (!strcmp(*path, "/dev/host_binder") && host_binder_env && host_binder_env[0]) return (*path = strdup(host_binder_env)) != NULL;
    if (!strcmp(*path, "/dev/host_hwbinder") && host_hwbinder_env && host_hwbinder_env[0]) return (*path = strdup(host_hwbinder_env)) != NULL;
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

/* Android's storage paths are the instance's storage directory ($APKLAYER_STORAGE, cli/layout): /storage/emulated/0 as on a device (MediaProvider
 * reads volume and relative path from that spelling), and its other names /sdcard and /data/media/0. Mapped whether or not the file exists yet. */
static bool storage_path(char **path)
{
    static const char *const names[][2] = { { "/storage", "" }, { "/sdcard", "/emulated/0" }, { "/data/media/0", "/emulated/0" } };
    const char *storage = storage_env;
    if (!storage[0]) return false;
    for (size_t i = 0; i < sizeof names / sizeof names[0]; i++) {
        size_t n = strlen(names[i][0]);
        if (strncmp(*path, names[i][0], n) || ((*path)[n] && (*path)[n] != '/')) continue;
        char *mapped;
        if (asprintf(&mapped, "%s%s%s", storage, names[i][1], *path + n) < 0) return false;
        *path = mapped;
        return true;
    }
    return false;
}

/* The rest of /data is the instance's ($APKLAYER_DATA, cli/layout: seeded from the Android root's data/): keystore2's database and other state
 * never land in the native directory that all instances share. */
static bool data_path(char **path)
{
    const char *data = data_env;
    if (!data[0] || strncmp(*path, "/data", 5) || ((*path)[5] && (*path)[5] != '/')) return false;
    char *mapped;
    if (asprintf(&mapped, "%s%s", data, *path + 5) < 0) return false;
    *path = mapped;
    return true;
}

/* The app's own data directories under Android's spellings (/data/data/<pkg>, /data/user/0/<pkg>, /data/user_de/0/<pkg>): apps and their native code
 * hard-code them. Set once the process knows its package (apklayer_map_own_data: AppEnvironment through the shim; a spare learns it late), and
 * inherited by the programs it runs through APKLAYER_OWN_DATA=<pkg>:<root> (exec_preload.c adds it to every exec). <root> is the app's data tree
 * (cli/layout appData): app/ is its data dir, device/ its device-protected one. Other packages' stay in the instance's /data (data_path), where
 * they do not exist. */
struct own_data { char *env, *pkg, *dir, *device; };
static struct own_data *own_data;

void apklayer_map_own_data(const char *pkg, const char *root)
{
    struct own_data *o = calloc(1, sizeof *o);
    if (!o || strchr(pkg, ':') || asprintf(&o->env, "APKLAYER_OWN_DATA=%s:%s", pkg, root) < 0 || !(o->pkg = strdup(pkg))
        || asprintf(&o->dir, "%s/app", root) < 0 || asprintf(&o->device, "%s/device", root) < 0) return;
    __atomic_store_n(&own_data, o, __ATOMIC_RELEASE);  /* every thread's file calls read it: published whole, an earlier one stays allocated (a reader may hold it) */
}

/* "APKLAYER_OWN_DATA=<pkg>:<root>" for a child's environment, or NULL */
const char *apklayer_own_data_env(void)
{
    const struct own_data *o = __atomic_load_n(&own_data, __ATOMIC_ACQUIRE);
    return o ? o->env : NULL;
}

static void own_data_from_env(void)
{
    char *v = getenv("APKLAYER_OWN_DATA"), *colon = v ? strchr(v, ':') : NULL;
    if (!colon || colon == v || !colon[1]) return;
    char *pkg = strndup(v, (size_t)(colon - v));
    if (pkg) apklayer_map_own_data(pkg, colon + 1);
    free(pkg);
}

static bool own_data_path(char **path)
{
    static const char *const spellings[] = { "/data/data/", "/data/user/0/", "/data/user_de/0/" };
    const struct own_data *o = __atomic_load_n(&own_data, __ATOMIC_ACQUIRE);
    if (!o) return false;
    for (int i = 0; i < 3; i++) {
        size_t n = strlen(spellings[i]), k = strlen(o->pkg);
        if (strncmp(*path, spellings[i], n)) continue;
        const char *rest = *path + n;
        if (strncmp(rest, o->pkg, k) || (rest[k] && rest[k] != '/')) return false;
        char *mapped;
        if (asprintf(&mapped, "%s%s", i == 2 ? o->device : o->dir, rest + k) < 0) return false;
        *path = mapped;
        return true;
    }
    return false;
}

static bool from_root(const char *r, char **path);

bool apklayer_apply_path_overrides(char **path)
{
    if (debug_env) fprintf(stderr, "[paths] %s (root %s)\n", *path, root());
    if (binder_device(path) || storage_path(path) || own_data_path(path) || data_path(path)) return true;
    /* what one instance's stack stages for itself (the vintf fragments of the HALs it runs, cli/stack.cpp) comes before the shared root */
    const char *own = own_env;
    return (own && own[0] && from_root(own, path)) || from_root(root(), path);
}

static bool from_root(const char *r, char **path)
{
    const char *p = *path;
    if (!r[0] || p[0] != '/') return false;
    for (int i = 0; prefixes[i]; i++) {
        size_t n = strlen(prefixes[i]);  /* "/system/..." and the directory itself, "/system" (the guest linker's config sections test it); not the host's /etc */
        if (strncmp(p, prefixes[i], n) && (strncmp(p, prefixes[i], n - 1) || p[n - 1] || !strcmp(prefixes[i], "/etc/"))) continue;
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

/* A file the overlay served (under the instance's or the shared Android root) spelled as Android's again, in place (readlink's buffer, n bytes,
 * no terminator): what /proc/self/fd/N links to. bionic's realpath (O_PATH, then readlink of the fd) in translated code reads it, and the guest
 * linker matches the result against its config's /system sections. Returns the new length. */
long apklayer_android_spelling(char *buf, long n)
{
    const char *roots[2] = { own_env, root() };
    for (int i = 0; i < 2; i++) {
        long k = roots[i] ? (long)strlen(roots[i]) : 0;
        if (!k || n <= k || strncmp(buf, roots[i], (size_t)k) || buf[k] != '/') continue;
        for (int j = 0; prefixes[j]; j++) {
            long p = (long)strlen(prefixes[j]);  /* the directory itself too ("/system": n - k == p - 1) */
            if (n - k >= p - 1 && !strncmp(buf + k, prefixes[j], (size_t)(n - k >= p ? p : p - 1))) { memmove(buf, buf + k, (size_t)(n - k)); return n - k; }
        }
    }
    return n;
}

/* Hands the override function to bionic_translation's libc (loaded as a glibc library once Android code is). */
void apklayer_install_path_layer(void)
{
    void (*set)(bool (*)(char **)) = dlsym(RTLD_DEFAULT, "libc_bio_set_apply_path_overrides_func");
    if (set) set(apklayer_apply_path_overrides);
    if (getenv("APKLAYER_PATHS_DEBUG")) fprintf(stderr, "[paths] path layer %s\n", set ? "installed" : "NOT installed (libc_bio not loaded)");
}
