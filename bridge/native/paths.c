/* Android's fixed paths for the desktop (PLAN archive P7.1 step 0.3). Android libraries read /system, /vendor, /apex, /etc/task_profiles.json,
 * /dev/binder ... A desktop has none of them, and an instance (PLAN archive P9) must not share them. This overlays them from the
 * instance's Android root: a path under a known prefix is served from $APKLAYER_ANDROID_ROOT/<path> (else <native>/android-root)
 * when that file exists there, otherwise the host's own file is used, so nothing the host needs is hidden. Installed into
 * bionic_translation's libc (open, fopen, stat, lstat, fstatat) through its apply_path_overrides hook. */
#define _GNU_SOURCE
#include <dlfcn.h>
#include <fcntl.h>
#include <limits.h>
#include <sys/syscall.h>
#include <sys/stat.h>
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
    /* the kernel's spelling (readlink of /proc/self/fd/N): a root under a symlink (the deb's runtime/current) must match it, or
     * apklayer_android_spelling misses and the guest linker finds no ld.config section for /system/bin/arm64/app_process64 */
    char real[PATH_MAX];
    if (root_buf[0] && realpath(root_buf, real)) snprintf(root_buf, sizeof root_buf, "%s", real);
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

/* /storage/<uuid>[/...] of a removable drive: its link in the storage directory resolved here, so the volume's root is the drive's directory and
 * not a symlink (a scanner's walk from the root, which follows no links, stops at one). rest is "/<uuid>..." */
static bool drive_path(const char *storage, const char *rest, char **mapped)
{
    if (rest[0] != '/' || !rest[1] || !strncmp(rest, "/emulated", 9)) return false;
    const char *slash = strchr(rest + 1, '/');
    size_t k = slash ? (size_t)(slash - rest) : strlen(rest);
    char link[4096], target[4096];
    if (snprintf(link, sizeof link, "%s%.*s", storage, (int)k, rest) >= (int)sizeof link) return false;
    ssize_t t = readlink(link, target, sizeof target - 1);
    if (t <= 0 || target[0] != '/') return false;
    target[t] = 0;
    return asprintf(mapped, "%s%s", target, rest + k) >= 0;
}

/* Android's storage paths are the instance's storage directory ($APKLAYER_STORAGE, cli/layout): /storage/emulated/0 as on a device (MediaProvider
 * reads volume and relative path from that spelling), and its other names /sdcard and /data/media/0; a removable drive's /mnt/media_rw/<uuid> is its
 * /storage/<uuid> (a link to the desktop's mount, HostVolumes.Removable). Mapped whether or not the file exists yet. */
static bool storage_path(char **path)
{
    static const char *const names[][2] = { { "/storage", "" }, { "/sdcard", "/emulated/0" }, { "/data/media/0", "/emulated/0" }, { "/mnt/media_rw", "" } };
    const char *storage = storage_env;
    if (!storage[0]) return false;
    for (size_t i = 0; i < sizeof names / sizeof names[0]; i++) {
        size_t n = strlen(names[i][0]);
        if (strncmp(*path, names[i][0], n) || ((*path)[n] && (*path)[n] != '/')) continue;
        char *mapped;
        if (!names[i][1][0] && drive_path(storage, *path + n, &mapped)) { *path = mapped; return true; }
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
 * inherited by the programs it runs through APKLAYER_OWN_DATA=<pkg>:<uid>[,<gid>...]:<root> (exec_preload.c adds it to every exec). <root> is the app's
 * data tree (cli/layout appData): app/ is its data dir, device/ its device-protected one. Other packages' stay in the instance's /data (data_path), where
 * they do not exist. <uid> is its Android uid: without the sandbox the process runs as the user, and Android code is told the app uid (its ids,
 * the owner of the user's files: bionic_translation 0056) and the supplementary <gid>s zygote gives an app (getgroups: 0061); under the sandbox
 * the real ids are the app's and nothing changes. */
struct own_data { char *env, *pkg, *dir, *device; long uid; int ngroups; gid_t groups[16]; };
static struct own_data *own_data;

/* the app uid bionic code sees, or -1 (none known, or the real uid is the app's: the sandbox) */
long apklayer_app_uid(void)
{
    const struct own_data *o = __atomic_load_n(&own_data, __ATOMIC_ACQUIRE);
    return o && o->uid >= 0 && (uid_t)o->uid != getuid() ? o->uid : -1;
}

/* into bionic_translation's libc once it is loaded (also from apklayer_install_path_layer) */
static void push_app_uid(void)
{
    void (*set)(long) = dlsym(RTLD_DEFAULT, "libc_bio_set_app_uid");
    void (*set_groups)(int, const gid_t *) = dlsym(RTLD_DEFAULT, "libc_bio_set_app_groups");
    const struct own_data *o = __atomic_load_n(&own_data, __ATOMIC_ACQUIRE);
    long uid = apklayer_app_uid();
    if (set && uid >= 0) set(uid);
    if (set_groups && uid >= 0) set_groups(o->ngroups, o->groups);
}

/* groups: the app's supplementary gids, comma separated ("" or NULL: none) */
void apklayer_map_own_data(const char *pkg, long uid, const char *groups, const char *root)
{
    struct own_data *o = calloc(1, sizeof *o);
    if (!groups) groups = "";
    if (!o || strchr(pkg, ':') || strchr(groups, ':') || asprintf(&o->env, "APKLAYER_OWN_DATA=%s:%ld%s%s:%s", pkg, uid, *groups ? "," : "", groups, root) < 0
        || !(o->pkg = strdup(pkg)) || asprintf(&o->dir, "%s/app", root) < 0 || asprintf(&o->device, "%s/device", root) < 0) return;
    o->uid = uid;
    for (char *end; *groups && o->ngroups < (int)(sizeof o->groups / sizeof *o->groups); groups = end + (*end == ',')) {
        o->groups[o->ngroups] = (gid_t)strtoul(groups, &end, 10);
        if (end == groups || (*end && *end != ',')) break;
        o->ngroups++;
    }
    __atomic_store_n(&own_data, o, __ATOMIC_RELEASE);  /* every thread's file calls read it: published whole, an earlier one stays allocated (a reader may hold it) */
    push_app_uid();
    /* and in the instance's /data as directories, as on Android: a walk that reaches them component by component (mkdir -p: /data/data, then
     * "com.termux" relative to it) lands in the app's tree, not in a new one (errors ignored: the sandbox's view has its own) */
    const char *links[][3] = { {"data", "", o->dir}, {"user", "/0", o->dir}, {"user_de", "/0", o->device} };
    for (int i = 0; data_env[0] && i < 3; i++) {
        char *top = NULL, *dir = NULL, *link = NULL;
        if (asprintf(&top, "%s/%s", data_env, links[i][0]) >= 0) mkdir(top, 0771);
        if (asprintf(&dir, "%s/%s%s", data_env, links[i][0], links[i][1]) >= 0) mkdir(dir, 0771);
        if (asprintf(&link, "%s/%s%s/%s", data_env, links[i][0], links[i][1], pkg) >= 0) symlink(links[i][2], link);
        free(top);
        free(dir);
        free(link);
    }
}

/* "APKLAYER_OWN_DATA=<pkg>:<uid>[,<gid>...]:<root>" for a child's environment, or NULL */
const char *apklayer_own_data_env(void)
{
    const struct own_data *o = __atomic_load_n(&own_data, __ATOMIC_ACQUIRE);
    return o ? o->env : NULL;
}

static void own_data_from_env(void)
{
    char *v = getenv("APKLAYER_OWN_DATA"), *colon = v ? strchr(v, ':') : NULL, *end;
    if (!colon || colon == v) return;
    long uid = strtol(colon + 1, &end, 10);
    char *groups = *end == ',' ? end + 1 : end, *root = strchr(groups, ':');  /* <uid>[,<gid>...]:<root> */
    if (end == colon + 1 || !root || (*end != ',' && *end != ':') || !root[1]) return;
    char *pkg = strndup(v, (size_t)(colon - v)), *gids = strndup(groups, (size_t)(root - groups));
    if (pkg && gids) apklayer_map_own_data(pkg, uid, gids, root + 1);
    free(pkg);
    free(gids);
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

/* /system/bin/linker64 is the file of Android code's linker, bionic_translation's libdl (its getauxval(AT_BASE), 0054): hook libraries
 * (bytehook) read the linker's .symtab from it */
static bool linker_path(char **path)
{
    Dl_info linker;
    void *entry;
    char *mapped;
    if (strcmp(*path, sizeof(void *) == 8 ? "/system/bin/linker64" : "/system/bin/linker")) return false;
    if (!(entry = dlsym(RTLD_DEFAULT, "bionic_getauxval")) || !dladdr(entry, &linker) || !linker.dli_fname) return false;
    if (!(mapped = strdup(linker.dli_fname))) return false;
    *path = mapped;
    return true;
}

/* What Android shows an app of /proc: hidepid=invisible, only the processes of its own uid. Here every process of the desktop is in /proc, and
 * Tencent ACE (PGR, Endfield) stats, lstats and reads the cmdline of Firefox, the hub and others. An app process (own_data set) sees as its own
 * the processes in its cgroup (the app's systemd scope: its JVM, translator threads, children); another pid's /proc/<pid>[/...] is ENOENT. Not hidden:
 * /proc, /proc/self, /proc/thread-self and everything not numeric. The kernel's own answer is read with raw syscalls (the wrappers call this).
 * APKLAYER_PROC_VIEW=host turns it off. Without a scope for the app (no systemd) nothing is hidden: every process shares the cgroup. */
static int cgroup_read(long pid, char *out, size_t n)
{
    char p[48];
    snprintf(p, sizeof p, "/proc/%ld/cgroup", pid);
    int fd = (int)syscall(SYS_openat, AT_FDCWD, p, O_RDONLY | O_CLOEXEC);
    if (fd < 0) return -1;
    ssize_t r = read(fd, out, n - 1);
    close(fd);
    if (r < 0) return -1;
    out[r] = 0;
    return 0;
}

/* whether /proc/<pid> (the digits at *name, ended by '/' or the end) is a process of another app or of the desktop */
int apklayer_proc_pid_hidden(const char *name)
{
    static int host_view = -1;
    if (host_view < 0) { const char *v = getenv("APKLAYER_PROC_VIEW"); host_view = v && !strcmp(v, "host"); }
    static __thread int busy;  /* the cgroup read below is a path call of its own (the preload wraps syscall) */
    if (host_view || busy || !__atomic_load_n(&own_data, __ATOMIC_ACQUIRE)) return 0;
    char *end;
    long pid = strtol(name, &end, 10);
    if (end == name || (*end && *end != '/') || pid == getpid()) return 0;
    char mine[1024], theirs[1024];
    busy = 1;
    int hidden = !cgroup_read(getpid(), mine, sizeof mine) && strstr(mine, "/") && (cgroup_read(pid, theirs, sizeof theirs) || strcmp(mine, theirs));
    busy = 0;
    return hidden;
}

/* an absolute path: /proc/<pid>[/...] of a process this app does not see */
int apklayer_proc_hidden(const char *path)
{
    return path && !strncmp(path, "/proc/", 6) && path[6] >= '0' && path[6] <= '9' && apklayer_proc_pid_hidden(path + 6);
}

static bool from_root(const char *r, char **path);

bool apklayer_apply_path_overrides(char **path)
{
    if (apklayer_proc_hidden(*path)) {  /* any path call of the process (the wrappers and bionic_translation's libc): no such process */
        char *gone = strdup("/proc/.apklayer-hidden");
        if (gone) { *path = gone; return true; }
    }
    if (debug_env) fprintf(stderr, "[paths] %s (root %s)\n", *path, root());
    if (binder_device(path) || storage_path(path) || own_data_path(path) || data_path(path) || linker_path(path)) return true;
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
    push_app_uid();
    if (getenv("APKLAYER_PATHS_DEBUG")) fprintf(stderr, "[paths] path layer %s\n", set ? "installed" : "NOT installed (libc_bio not loaded)");
}
