/* Android's fixed paths for the desktop (PLAN archive P7.1 step 0.3). Android libraries read /system, /vendor, /apex, /etc/task_profiles.json,
 * /dev/binder ... A desktop has none of them, and an instance (PLAN archive P9) must not share them. This overlays them from the
 * instance's Android root: a path under a known prefix is served from $APKLAYER_ANDROID_ROOT/<path> (else <native>/android-root)
 * when that file exists there, otherwise the host's own file is used, so nothing the host needs is hidden. Installed into
 * bionic_translation's libc (open, fopen, stat, lstat, fstatat) through its apply_path_overrides hook. */
#define _GNU_SOURCE
#include <dlfcn.h>
#include <errno.h>
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

/* The same immutable app groups handed to bionic; -1 means the kernel already owns this process's identity. */
int apklayer_app_groups(gid_t *out, size_t capacity)
{
    const struct own_data *o = __atomic_load_n(&own_data, __ATOMIC_ACQUIRE);
    if (!o || o->uid < 0 || (uid_t)o->uid == getuid()) return -1;
    if (!capacity) return o->ngroups;
    if (capacity < (size_t)o->ngroups) { errno = EINVAL; return -1; }
    memcpy(out, o->groups, o->ngroups * sizeof *out);
    return o->ngroups;
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

/* apklayer's own tooling that shares the app's scope: the `apklayer run` wrapper and the CLI it started live in the app's systemd scope next to the app's JVMs, and
 * their command lines name the host (`/usr/lib/apklayer/cli/apklayer --no-daemon --instance default run <package>`). Android shows an app its own processes only: the
 * app's JVMs (java) stay, the host's tools (an executable named apklayer*) do not. Raw syscall: the wrappers call this. */
static int host_tool_process(long pid)
{
    char path[48], exe[PATH_MAX];
    snprintf(path, sizeof path, "/proc/%ld/exe", pid);
    long n = syscall(SYS_readlinkat, AT_FDCWD, path, exe, sizeof exe - 1);
    if (n <= 0) return 0;
    exe[n] = 0;
    const char *base = strrchr(exe, '/');
    base = base ? base + 1 : exe;
    return !strncmp(base, "apklayer", 8);
}

/* whether /proc/<pid> (the digits at *name, ended by '/' or the end) is a process of another app or of the desktop */
int apklayer_proc_pid_hidden(const char *name)
{
    static int host_view = -1;
    if (host_view < 0) { const char *v = getenv("APKLAYER_PROC_VIEW"); host_view = v && !strcmp(v, "host"); }
    static __thread volatile int busy;  /* volatile: GCC drops the store of a non-escaping local around the call (systemd-run recursed to SIGSEGV); the cgroup read below is a path call of its own (the preload wraps syscall) */
    if (host_view || busy || !__atomic_load_n(&own_data, __ATOMIC_ACQUIRE)) return 0;
    char *end;
    long pid = strtol(name, &end, 10);
    if (end == name || (*end && *end != '/') || pid == getpid()) return 0;
    char mine[1024], theirs[1024];
    busy = 1;
    int hidden = !cgroup_read(getpid(), mine, sizeof mine) && strstr(mine, "/") && (cgroup_read(pid, theirs, sizeof theirs) || strcmp(mine, theirs));
    if (!hidden && pid != getpid()) hidden = host_tool_process(pid);  /* same scope: the app's own JVMs stay, the host's tools go */
    busy = 0;
    return hidden;
}

/* whether the directory `dirfd` (AT_FDCWD: the working directory) is /proc */
int apklayer_proc_is_dir(int dirfd)
{
    static __thread volatile int busy;  /* volatile: see apklayer_proc_pid_hidden */
    struct stat here, proc;
    if (busy) return 0;
    busy = 1;
    int is = syscall(SYS_newfstatat, dirfd, "", &here, AT_EMPTY_PATH) == 0 && syscall(SYS_newfstatat, AT_FDCWD, "/proc", &proc, 0) == 0
        && here.st_ino == proc.st_ino && here.st_dev == proc.st_dev;
    busy = 0;
    return is;
}

/* /proc/<pid>[/...] under any spelling: "//", "/./", "/../" collapsed, a prefix through /proc/self/root (or <own pid>, thread-self) stripped. `rel` is a path
 * under /proc already (no leading slash). Hidden only when its first component is a number of a process outside the app's view. */
static int proc_rel_hidden(const char *rel)
{
    while (*rel == '/') rel++;
    return *rel >= '0' && *rel <= '9' && apklayer_proc_pid_hidden(rel);
}

extern int apklayer_proc_map_hidden(int dirfd, const char *path) __attribute__((weak));

int apklayer_proc_hidden(const char *path)
{
    if (apklayer_proc_map_hidden && apklayer_proc_map_hidden(AT_FDCWD, path)) return 1;
    if (!path) return 0;
    if (path[0] != '/') {  /* relative to the working directory: /proc itself ("1/status"), or / ("proc/1/status") */
        const char *c = path;
        while (!strncmp(c, "./", 2)) c += 2;
        if (!strncmp(c, "proc/", 5) && c[5] >= '0' && c[5] <= '9') {
            struct stat root, here;
            static __thread volatile int busy_cwd;
            if (busy_cwd) return 0;
            busy_cwd = 1;
            int at_root = syscall(SYS_newfstatat, AT_FDCWD, ".", &here, 0) == 0 && syscall(SYS_newfstatat, AT_FDCWD, "/", &root, 0) == 0 && here.st_ino == root.st_ino && here.st_dev == root.st_dev;
            busy_cwd = 0;
            return at_root && proc_rel_hidden(c + 5);
        }
        return *c >= '0' && *c <= '9' && apklayer_proc_is_dir(AT_FDCWD) && proc_rel_hidden(c);
    }
    size_t len = strlen(path);
    if (len < 6 || len >= PATH_MAX || !strstr(path, "proc")) return 0;
    char norm[PATH_MAX], *w = norm;  /* the path with its dots and repeated slashes resolved, textually */
    for (const char *r = path; *r;) {
        if (*r == '/') { while (*r == '/') r++; if (r[0] == '.' && (r[1] == '/' || !r[1])) { r++; continue; } 
            if (r[0] == '.' && r[1] == '.' && (r[2] == '/' || !r[2])) { r += 2; while (w > norm && *--w != '/') ; continue; }
            *w++ = '/'; continue; }
        *w++ = *r++;
    }
    *w = 0;
    const char *p = norm;
    for (int hops = 0; hops < 4 && !strncmp(p, "/proc/", 6); hops++) {
        const char *rest = p + 6;
        if (*rest >= '0' && *rest <= '9' && !(hops == 0 && 0)) {
            char self[16];
            snprintf(self, sizeof self, "%ld/", (long)getpid());
            if (!strncmp(rest, self, strlen(self)) && !strncmp(rest + strlen(self), "root/", 5)) { p = rest + strlen(self) + 4; continue; }
            return apklayer_proc_pid_hidden(rest);
        }
        if (!strncmp(rest, "self/root/", 10)) { p = rest + 9; continue; }
        if (!strncmp(rest, "thread-self/root/", 17)) { p = rest + 16; continue; }
        return 0;
    }
    return 0;
}

/* an *at call's path: relative to a directory fd that is /proc (opendir + openat/fstatat "1/status"), or absolute/cwd-relative as above. A path relative to /proc
 * is judged as the absolute one it names ("self/../1" is /proc/1, "../proc/1" too), so the textual rules above apply to it as they do to /proc/<path>. */
int apklayer_proc_hidden_at(int dirfd, const char *path)
{
    if (apklayer_proc_map_hidden && apklayer_proc_map_hidden(dirfd, path)) return 1;
    if (!path || path[0] == '/' || dirfd == AT_FDCWD) return apklayer_proc_hidden(path);
    if (!*path) return 0;  /* AT_EMPTY_PATH: the open descriptor itself (glibc's fstat is fstatat(fd, "", AT_EMPTY_PATH)), no name to hide */
    char abs[PATH_MAX];
    if (strlen(path) + 7 >= sizeof abs || !apklayer_proc_is_dir(dirfd)) return 0;
    snprintf(abs, sizeof abs, "/proc/%s", path);
    return apklayer_proc_hidden(abs);
}

bool apklayer_apply_path_overrides(char **path);

/* Other packages' data is not an app's to see: Android's /data/data is not listable for an app and another package's directory is not reachable. The instance's
 * /data/data (data_env/data) holds a link for every installed package, so without this an app lists them all. Applies to app uids (>= 10000); a system uid
 * (the hub's system components) sees them. Filter: a name under the three spellings of the user's data that is not the app's own package. */
static const char *const data_roots[] = { "/data/data", "/data/user/0", "/data/user_de/0", NULL };

static int data_filter_active(void)
{
    const struct own_data *o = __atomic_load_n(&own_data, __ATOMIC_ACQUIRE);
    static int host_view = -1;
    if (host_view < 0) { const char *v = getenv("APKLAYER_PROC_VIEW"); host_view = v && !strcmp(v, "host"); }
    return o && !host_view && o->uid >= 10000;
}

/* a directory entry of one of those roots: false for the app's own package */
int apklayer_data_entry_hidden(const char *name)
{
    const struct own_data *o = __atomic_load_n(&own_data, __ATOMIC_ACQUIRE);
    return data_filter_active() && strcmp(name, ".") && strcmp(name, "..") && strcmp(name, o->pkg);
}

/* the path is one of the roots themselves (a trailing slash allowed) */
int apklayer_is_data_root(const char *path)
{
    if (!path || !data_filter_active()) return 0;
    for (int i = 0; data_roots[i]; i++) {
        size_t n = strlen(data_roots[i]);
        if (!strncmp(path, data_roots[i], n) && (!path[n] || (path[n] == '/' && !path[n + 1]))) return 1;
    }
    return 0;
}

/* the directory `fd` is one of the roots (what the translator's getdents64 sees): compared by device and inode with the mapped roots, once */
int apklayer_fd_is_data_root(int fd)
{
    static dev_t dev[3]; static ino_t ino[3]; static int known;
    struct stat st;
    if (!data_filter_active()) return 0;
    if (!__atomic_load_n(&known, __ATOMIC_ACQUIRE)) {
        for (int i = 0; i < 3; i++) {
            char *m = (char *)data_roots[i];
            bool alloc = apklayer_apply_path_overrides(&m);
            struct stat r;
            int ok = syscall(SYS_newfstatat, AT_FDCWD, m, &r, 0) == 0;
            if (alloc) free(m);
            dev[i] = ok ? r.st_dev : 0; ino[i] = ok ? r.st_ino : 0;
        }
        __atomic_store_n(&known, 1, __ATOMIC_RELEASE);
    }
    if (syscall(SYS_newfstatat, fd, "", &st, AT_EMPTY_PATH)) return 0;
    for (int i = 0; i < 3; i++) if (ino[i] && st.st_ino == ino[i] && st.st_dev == dev[i]) return 1;
    return 0;
}

/* /data/data/<other>[/...] (and the user spellings) */
static int other_package_path(const char *path)
{
    const struct own_data *o = __atomic_load_n(&own_data, __ATOMIC_ACQUIRE);
    if (!data_filter_active()) return 0;
    for (int i = 0; data_roots[i]; i++) {
        size_t n = strlen(data_roots[i]);
        if (strncmp(path, data_roots[i], n) || path[n] != '/') continue;
        const char *rest = path + n + 1;
        size_t k = strlen(o->pkg);
        if (!*rest) return 0;
        return !(!strncmp(rest, o->pkg, k) && (!rest[k] || rest[k] == '/'));
    }
    return 0;
}

/* The desktop's terminals: /dev/pts/<n> are the user's shells and terminal tabs. An app on Android has none of them in its /dev/pts; ACE (PGR) probes
 * /dev/pts/0..7, which on Android tells a debugger or `adb shell` session from a plain start. ENOENT for an app process (own data set) under any path that
 * names one by number; /dev/pts itself and /dev/pts/ptmx stay (Termux opens a pty of its own: its new terminals are numbered ones too, so APKLAYER_PTS_VIEW=host
 * turns the filter off). */
static int desktop_terminal_path(const char *path)
{
    if (strncmp(path, "/dev/pts/", 9)) return 0;
    const char *leaf = path + 9;
    static int host_view = -1;
    if (host_view < 0) { const char *v = getenv("APKLAYER_PTS_VIEW"); host_view = v && !strcmp(v, "host"); }
    return !host_view && *leaf >= '0' && *leaf <= '9' && __atomic_load_n(&own_data, __ATOMIC_ACQUIRE);
}

/* The desktop's own files in /etc that Android has no equivalent of: its distribution, hostname, machine and shell identity. An app that reads them learns it
 * runs on Ubuntu (Tencent ACE asks the path layer for /etc/passwd and /etc/localtime; hello-arm64's audit lists /etc/os-release as visible). ENOENT for an app
 * process (own data set). Not hidden: /etc/localtime (glibc's time zone: the JVM reads it), /etc/resolv.conf and /etc/hosts (the resolver; hosts is Android's own
 * file in the root), /etc/passwd and /etc/group (Android's empty files in the root), ld.so.cache and the TLS and fonts directories the host libraries need.
 * APKLAYER_ETC_VIEW=host turns the filter off. */
static int desktop_etc_path(const char *path)
{
    static const char *const names[] = {"/etc/os-release", "/etc/lsb-release", "/etc/issue", "/etc/issue.net", "/etc/hostname", "/etc/machine-id", "/etc/timezone",
                                        "/etc/debian_version", "/etc/fstab", "/etc/mtab", "/etc/shadow", "/etc/gshadow", "/etc/sudoers", "/etc/shells", "/etc/motd",
                                        "/etc/apt", "/etc/dpkg", "/etc/systemd", "/etc/netplan", "/etc/NetworkManager", "/etc/X11", "/etc/xdg", "/etc/alternatives",
                                        "/etc/default", "/etc/network", "/etc/cloud", "/etc/udev", "/etc/pam.d", "/etc/security", "/etc/sudoers.d", "/etc/profile.d",
                                        "/etc/profile", "/etc/environment", NULL};
    if (strncmp(path, "/etc/", 5) || !__atomic_load_n(&own_data, __ATOMIC_ACQUIRE)) return 0;
    static int host_view = -1;
    if (host_view < 0) { const char *v = getenv("APKLAYER_ETC_VIEW"); host_view = v && !strcmp(v, "host"); }
    if (host_view) return 0;
    for (int i = 0; names[i]; i++) {
        size_t n = strlen(names[i]);
        if (!strncmp(path, names[i], n) && (!path[n] || path[n] == '/')) return 1;
    }
    return 0;
}

static bool from_root(const char *r, char **path);

bool apklayer_apply_path_overrides(char **path)
{
    if (apklayer_proc_hidden(*path) || other_package_path(*path) || desktop_terminal_path(*path) || desktop_etc_path(*path)) {  /* any path call of the process (the wrappers and bionic_translation's libc): no such process */
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

/* The reverse of own_data_path. A link the kernel resolves (an fd's /proc/self/fd/N, map_files, realpath) names the app's data directory by its host
 * path (<root>/app, <root>/device); Android code reads /data/user/0/<pkg> and /data/user_de/0/<pkg> there, as getFilesDir returned them (Tencent ACE,
 * mmkv and crash reporters compare the two). Rewritten in place, never longer than the host path (the caller's buffer holds n bytes); the new
 * length is returned, n when the path is not below the app's data directories. */
static long own_data_spelling(char *buf, long n)
{
    const struct own_data *o = __atomic_load_n(&own_data, __ATOMIC_ACQUIRE);
    if (!o || !o->dir || !o->device || n <= 0) return n;
    const char *const hosts[2] = { o->dir, o->device };
    const char *const androids[2] = { "/data/user/0/", "/data/user_de/0/" };
    for (int i = 0; i < 2; i++) {
        long k = (long)strlen(hosts[i]);
        if (n < k || strncmp(buf, hosts[i], (size_t)k) || (n > k && buf[k] != '/')) continue;
        char spelled[PATH_MAX];
        int m = snprintf(spelled, sizeof spelled, "%s%s", androids[i], o->pkg);
        long rest = n - k;
        if (m < 0 || m + rest > (long)sizeof spelled || m > k) return n;  /* never longer than the host path: the caller's buffer has n bytes */
        memcpy(buf + m, buf + k, (size_t)rest);
        memcpy(buf, spelled, (size_t)m);
        return m + rest;
    }
    return n;
}

long apklayer_android_spelling_own_data(char *buf, long n) { return own_data_spelling(buf, n); }  /* paths_preload.c: realpath */

/* A file the overlay served (under the instance's or the shared Android root) spelled as Android's again, in place (readlink's buffer, n bytes,
 * no terminator): what /proc/self/fd/N links to. bionic's realpath (O_PATH, then readlink of the fd) in translated code reads it, and the guest
 * linker matches the result against its config's /system sections. Returns the new length. */
long apklayer_android_spelling(char *buf, long n)
{
    n = own_data_spelling(buf, n);
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
