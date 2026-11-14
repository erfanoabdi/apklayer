/* What an app process sees of its own process beyond the maps (the preload's part of the proc view, proc_view.c reads these): thread names,
 * the executable and descriptor links, the cgroup line, the parent (zygote64 on Android) and the security state Android sets in an app
 * (not dumpable, no_new_privs, a seccomp filter). Only the views change: the real threads, descriptors, executable and process attributes stay
 * as the host runtime needs them (the JVM's threads keep their names for its tools, `apklayer stop` still reads the JVM's environ).
 * Android's values: AOSP (AndroidRuntime::setArgv0 for comm, cgroups.json uid_%d/pid_%d) and an Android 13 app (tests/identity/results/waydroid.txt). */
#define _GNU_SOURCE
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <limits.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/auxv.h>
#include <sys/prctl.h>
#include <sys/syscall.h>
#include <unistd.h>

const char *apklayer_own_data_env(void);
long apklayer_app_uid(void);
long apklayer_android_spelling(char *buf, long n, long size);
const char *apklayer_proc_jdk_root(void);  /* proc_view.c: the JDK's directory (libjvm's maps line), "" when unknown */

static long raw(long nr, long a, long b, long c, long d)
{
    static long (*real)(long, ...);
    if (!real) real = (long (*)(long, ...))dlsym(RTLD_NEXT, "syscall");
    return real(nr, a, b, c, d, 0L, 0L);
}

int apklayer_proc_app_view(void)
{
    const char *view = getenv("APKLAYER_PROC_VIEW");
    return apklayer_own_data_env() && (!view || strcmp(view, "host"));
}

static ssize_t read_small(const char *path, char *out, size_t size)
{
    int fd = (int)raw(SYS_openat, AT_FDCWD, (long)path, O_RDONLY | O_CLOEXEC, 0);
    if (fd < 0) return -1;
    ssize_t n = (ssize_t)raw(SYS_read, fd, (long)out, (long)size - 1, 0);
    raw(SYS_close, fd, 0, 0, 0);
    out[n > 0 ? n : 0] = 0;
    return n;
}

/* btload (an Android program the app started) exports the program's Android path: its exe, and its comm is already the program's. */
static const char *btload_program(void)
{
    const char *const *program = dlsym(RTLD_DEFAULT, "apklayer_btload_program");
    return program ? *program : NULL;
}

/* The JVM's own threads under ART's names (an Android 13 app: Signal Catcher, HeapTaskDaemon, ReferenceQueueD, FinalizerDaemon,
 * FinalizerWatchd, Jit thread pool, Profile Saver, Runtime worker, binder:<pid>_<n>); the app's own threads keep theirs. */
static const struct { const char *host; int exact; const char *android; } jvm_threads[] = {
    {"VM Thread", 1, "HeapTaskDaemon"}, {"Reference Handl", 1, "ReferenceQueueD"}, {"Finalizer", 1, "FinalizerDaemon"},
    {"Signal Dispatch", 1, "Signal Catcher"}, {"Common-Cleaner", 1, "FinalizerWatchd"}, {"Service Thread", 1, "Profile Saver"},
    {"C1 CompilerThre", 0, "Jit thread pool"}, {"C2 CompilerThre", 0, "Jit thread pool"}, {"java", 1, "Runtime worker"},
    {"VM Periodic Tas", 1, "Runtime worker"}, {"Monitor Deflati", 1, "Runtime worker"}, {"Native Heap Tri", 1, "Runtime worker"},
    {"Notification Th", 1, "Runtime worker"}, {"Cleaner-", 0, "Runtime worker"}, {"process reaper", 0, "Runtime worker"},
    {"Attach Listener", 1, "Runtime worker"}, {"GC Thread#", 0, "Runtime worker"}, {"G1 ", 0, "Runtime worker"}, {"VM Wait", 0, "Runtime worker"},
    {"Sweeper thread", 1, "Runtime worker"}, {"JFR ", 0, "Runtime worker"}, {"SDK ", 0, "Runtime worker"}, {"ForkJoinPool", 0, "Runtime worker"}, {"Thread-", 0, "Runtime worker"}, {"AWT-", 0, "Runtime worker"},
    {"Java2D", 0, "Runtime worker"},
};

/* The comm an app reads for thread `tid` of this process whose kernel comm is `real` (no newline); out holds 16 bytes. */
const char *apklayer_proc_thread_name(long tid, const char *real, char *out)
{
    long pid = getpid();
    if (tid == pid) {
        if (btload_program()) return real;  /* btload named itself after the program (btload.c) */
        /* the JVM's main thread: the process name ActivityThread set (cmdline, proc_title.c), its last 15 bytes as AndroidRuntime::setArgv0 */
        char cmdline[256];
        if (read_small("/proc/self/cmdline", cmdline, sizeof cmdline) <= 0 || cmdline[0] == '/' || !cmdline[0]) return real;
        size_t n = strlen(cmdline);
        snprintf(out, 16, "%s", n > 15 ? cmdline + n - 15 : cmdline);
        return out;
    }
    if (!strncmp(real, "apklayer-", 9)) { snprintf(out, 16, "binder:%ld_%ld", pid, ((tid - pid) & 0x7) + 1); return out; }
    for (size_t i = 0; i < sizeof jvm_threads / sizeof *jvm_threads; i++)
        if (jvm_threads[i].exact ? !strcmp(real, jvm_threads[i].host) : !strncmp(real, jvm_threads[i].host, strlen(jvm_threads[i].host)))
            return jvm_threads[i].android;
    return real;
}

/* /proc/self/cgroup of an Android 12+ app (cgroups.json: the v2 hierarchy uid_<uid>/pid_<pid>), in place of the desktop's systemd scope */
int apklayer_proc_cgroup(char *out, size_t size, long pid)
{
    long uid = apklayer_app_uid();
    return snprintf(out, size, "0::/uid_%ld/pid_%ld\n", uid >= 0 ? uid : (long)getuid(), pid);
}

/* The host runtime's own files as an app's descriptors show them: Android's boot class path jars where the JVM holds its jars, nothing of
 * the JDK, the instance log as /dev/null (an app's stdout and stderr). Only these names change: a file the app opened itself keeps its path
 * (bionic's realpath reads it back through /proc/self/fd). */
static const struct { const char *name; const char *android; } runtime_files[] = {
    {"Robolectric_all.jar", "/apex/com.android.art/javalib/core-libart.jar"},
    {"android-all-", "/system/framework/framework.jar"},
    {"host-classes.jar", "/system/framework/framework-graphics.jar"},
    {"sim-classes.jar", "/system/framework/telephony-common.jar"},
    {"native-runtime.jar", "/apex/com.android.art/javalib/okhttp.jar"},
    {"android-stub.jar", "/apex/com.android.art/javalib/bouncycastle.jar"},
    {"dex2jvm.jar", "/apex/com.android.art/javalib/apache-xml.jar"},
};

static const char *runtime_file(const char *target)
{
    const char *base = strrchr(target, '/');
    if (target[0] != '/' || !base) return NULL;
    base++;
    const char *jdk = apklayer_proc_jdk_root();
    size_t n = strlen(jdk);
    if (n && !strncmp(target, jdk, n) && target[n] == '/')
        return !strcmp(base, "modules") ? "/apex/com.android.art/javalib/core-oj.jar" : "/apex/com.android.art/javalib/core-icu4j.jar";
    for (size_t i = 0; i < sizeof runtime_files / sizeof *runtime_files; i++)
        if (!strncmp(base, runtime_files[i].name, strlen(runtime_files[i].name)) && strstr(base, ".jar")) return runtime_files[i].android;
    if (!strcmp(base, "cfg.d") && strstr(target, "/bionic_translation/")) return "/linkerconfig";
    if (!strncmp(base, "app-", 4) && strstr(base, ".log") && strstr(target, "/apklayer/")) return "/dev/null";
    return NULL;
}

/* Where Android has a staged platform library: /system/lib64, or its apex for the few that live there (the same table as bionic_translation 0078's apex_dir) */
static const char *apex_dir(const char *base)
{
    static const struct { const char *dir, *names; } apexes[] = {
        {"/apex/com.android.art/lib64/", " libandroidio.so libjavacore.so libnativehelper.so libnativebridge.so libnativeloader.so libsigchain.so libartbase.so libdexfile.so libartpalette.so libprofile.so libadbconnection.so libperfetto_hprof.so "},
        {"/apex/com.android.i18n/lib64/", " libandroidicu.so libicu.so libicui18n.so libicuuc.so libicu_jni.so "},
        {"/apex/com.android.os.statsd/lib64/", " libstatssocket.so "},
    };
    char key[128];
    snprintf(key, sizeof key, " %s ", base);
    for (size_t i = 0; i < sizeof apexes / sizeof *apexes; i++)
        if (strstr(apexes[i].names, key)) return apexes[i].dir;
    return "/system/lib64/";
}

/* The modules of the process that Android has too, by the file they are mapped from: bionic's libc (bionic_translation's libc_bio, which holds libm's and
 * libpthread's share too), its libdl (the linker's own code here), libm (glibc's), the dynamic loader (Android: linker64), the JDK's launcher (the app's
 * executable, app_process64), and the process's own data: the property area and the binder mappings (memfds). The Android root has files at these
 * names (bridge/native/meson.build); dl_iterate_phdr names the same modules alike (bionic_translation 0078). */
static const char *bionic_name(const char *host, const char *base)
{
    if (!base) return NULL;
    base++;
    if (strstr(host, "/bionic_translation/")) {
        if (!strncmp(base, "libc_bio.so", 11)) return "/apex/com.android.runtime/lib64/bionic/libc.so";
        if (!strncmp(base, "libdl_bio.so", 12)) return "/apex/com.android.runtime/lib64/bionic/libdl.so";
        if (!strncmp(base, "libstdc++_bio.so", 16)) return "/system/lib64/libstdc++.so";
        return NULL;
    }
    if (!strncmp(base, "ld-linux", 8)) return "/apex/com.android.runtime/bin/linker64";
    if (!strcmp(base, "libm.so.6")) return "/apex/com.android.runtime/lib64/bionic/libm.so";
    if (!strcmp(base, "java") && strstr(host, "/jdk/bin/")) return "/system/bin/app_process64";
    if (!strcmp(base, "properties") && strstr(host, "/apklayer/")) return "/dev/__properties__/u:object_r:default_prop:s0";
    if (!strncmp(host, "/memfd:binder", 13)) return "/dev/binder";
    if (!strcmp(base, "libandroid_runtime.so") && strstr(host, "/robolectric-nativeruntime")) return "/system/lib64/libandroid_runtime.so";
    return NULL;
}

/* A mapped file as Android names it: the instance's Android root files at their /system, /apex, /vendor paths (paths.c spelling), the staged Android
 * libraries at /system/lib64, the runtime's jars as above; NULL when the name stays (the app's own files). */
const char *apklayer_proc_map_name(const char *host, char *out, size_t size)
{
    int saved = errno;
    const char *result = NULL;
    snprintf(out, size, "%s", host);
    long n = apklayer_android_spelling(out, (long)strlen(out), (long)size - 1);
    out[n] = 0;
    if (strcmp(out, host)) result = out;
    else {
        const char *libs = strstr(host, "/android-libs/"), *base = strrchr(host, '/');
        /* the translator and its proxy libraries (libberberis_*.so) exist only here: no Android device has a file of that name. The mapping is the memory of the
         * native bridge, which Android's own bridge (libhoudini, libndk_translation) also shows; it keeps its place in the view without a name that exists nowhere */
        if (libs && base && (!strncmp(base + 1, "libberberis_", 12) || !strncmp(base + 1, "libapklayer_", 12))) result = "";  /* libapklayer_*.so: apklayer's own modules */
        else if (libs && base && strstr(base, ".so")) { snprintf(out, size, "%s%s", apex_dir(base + 1), base + 1); result = out; }
        else if (strstr(host, "/robolectric-nativeruntime-assets/") && base && strstr(host, "/fonts/")) { snprintf(out, size, "/system/fonts/%s", base + 1); result = out; }
        else if ((libs = runtime_file(host))) { snprintf(out, size, "%s", libs); result = out; }
        else if ((libs = bionic_name(host, base))) { snprintf(out, size, "%s", libs); result = out; }
        else {  /* the desktop's libraries (glibc, Mesa, X/Wayland, ICU...) and apklayer's own host libraries: no Android name exists, so no name at all */
            static const char *const desktop[] = {"/usr/", "/lib/", "/lib64/", "/opt/", "/snap/", "/nix/", NULL};
            int anonymous = strstr(host, "/lib/apklayer/") || strstr(host, "/apklayer/native/") || !strncmp(host, "/tmp/", 5) || !strncmp(host, "/home/", 6);  /* the rest of the host's own files */
            for (int i = 0; desktop[i]; i++) anonymous |= !strncmp(host, desktop[i], strlen(desktop[i]));
            if (anonymous) result = "";
        }
    }
    errno = saved;
    return result;
}

/* "/proc/<pid>/<what>[/...]" (self, thread-self and task/<tid> spellings resolved by the caller's text): the pid, and *rest at <what> */
static long proc_pid_of(const char *path, const char **rest)
{
    long pid;
    char *end;
    if (strncmp(path, "/proc/", 6)) return -1;
    path += 6;
    if (!strncmp(path, "self/", 5)) { pid = getpid(); path += 5; }
    else if (!strncmp(path, "thread-self/", 12)) { pid = getpid(); path += 12; }
    else {
        pid = strtol(path, &end, 10);
        if (end == path || *end != '/') return -1;
        path = end + 1;
    }
    if (!strncmp(path, "task/", 5)) {
        long tid = strtol(path + 5, &end, 10);
        if (end == path + 5 || *end != '/') return -1;
        (void)tid;
        path = end + 1;
    }
    *rest = path;
    return pid;
}

static int is_own_pid(long pid)
{
    char task[48];
    if (pid == getpid()) return 1;
    snprintf(task, sizeof task, "/proc/self/task/%ld", pid);
    return raw(SYS_faccessat, AT_FDCWD, (long)task, F_OK, 0) == 0;
}

/* exe of a JVM (the app process, the CLI that started it: zygote64's) is app_process64, as an Android app's */
static const char *exe_view(long pid, const char *real)
{
    const char *base = strrchr(real, '/');
    base = base ? base + 1 : real;
    if (is_own_pid(pid) && btload_program()) return btload_program();
    if (!strcmp(base, "btload")) {  /* another Android program of the app: the program in its command line (btload.c rewrote it) */
        static __thread char program[PATH_MAX];
        char path[48];
        snprintf(path, sizeof path, "/proc/%ld/cmdline", pid);
        if (read_small(path, program, sizeof program) > 0 && program[0] == '/') return program;
        return NULL;
    }
    if (pid == getppid() && !strncmp(base, "apklayer", 8)) return "/system/bin/app_process64";  /* the CLI that started the JVM: zygote64's */
#if defined(__aarch64__)  /* translated (x86_64) code gets the translator's /system/bin/arm64/app_process64 */
    if (!strcmp(base, "java")) return "/system/bin/app_process64";
#endif
    return NULL;
}

/* AT_EXECFN is a string on the initial stack, the executable path as execve got it: the JDK's java path, in place by the shorter Android one
 * (the string's room is the longer path, the NUL after it stays; auxv carries the pointer, so /proc/self/auxv and getauxval agree). */
void apklayer_proc_rewrite_execfn(void)
{
    static const char android[] = "/system/bin/app_process64";
    char *execfn = (char *)getauxval(AT_EXECFN);
    if (!execfn || !strstr(execfn, "/jdk/bin/java") || strlen(execfn) < sizeof android) return;
    memcpy(execfn, android, sizeof android);
}

/* A readlink of `path` returned `n` bytes of `buf` (size `size`): the app's view of /proc/<pid>/exe and /proc/<pid>/fd/<n>; returns the length */
long apklayer_proc_link_view(const char *path, char *buf, long n, size_t size)
{
    if (n <= 0 || !path || !apklayer_proc_app_view()) return n;
    const char *rest;
    long pid = proc_pid_of(path, &rest);
    if (pid < 0) return n;
    int saved = errno;
    char real[PATH_MAX];
    size_t k = (size_t)n < sizeof real ? (size_t)n : sizeof real - 1;
    memcpy(real, buf, k); real[k] = 0;
    const char *view = NULL;
    if (!strcmp(rest, "exe")) view = exe_view(pid, real);
    else if (!strncmp(rest, "fd/", 3) && is_own_pid(pid)) view = runtime_file(real);
    errno = saved;
    if (!view) return n;
    size_t len = strlen(view);
    if (len > size) len = size;  /* readlink truncates silently */
    memcpy(buf, view, len);
    return (long)len;
}

/* The parent as Android shows an app its zygote: the CLI that started the JVM reads as zygote64 (cmdline and comm). */
int apklayer_proc_parent_is_host_tool(long pid)
{
    if (pid != getppid() || btload_program()) return 0;
    char path[48], exe[PATH_MAX];
    snprintf(path, sizeof path, "/proc/%ld/exe", pid);
    long n = raw(SYS_readlinkat, AT_FDCWD, (long)path, (long)exe, sizeof exe - 1);
    if (n <= 0) return 0;
    exe[n] = 0;
    const char *base = strrchr(exe, '/');
    base = base ? base + 1 : exe;
    return !strncmp(base, "apklayer", 8) || !strcmp(base, "java");
}

/* The security state of an Android app process (zygote: not dumpable, PR_SET_NO_NEW_PRIVS, the app seccomp filter), for prctl's queries.
 * Returns 1 and *result when `option` is one of them. The real process stays dumpable (the host reads its environ to find an instance's
 * processes) and unfiltered. */
int apklayer_proc_prctl_view(int option, long *result)
{
    if (option != PR_GET_DUMPABLE && option != PR_GET_NO_NEW_PRIVS && option != PR_GET_SECCOMP) return 0;
    if (!apklayer_proc_app_view()) return 0;
    *result = option == PR_GET_DUMPABLE ? 0 : option == PR_GET_NO_NEW_PRIVS ? 1 : 2;
    return 1;
}

/* The status lines that state the same: NoNewPrivs 1, Seccomp 2 (filter), one filter, no bounding capabilities (an app uid's CapBnd is 0). */
const char *apklayer_proc_status_line(const char *line)
{
    if (!strncmp(line, "NoNewPrivs:", 11)) return "NoNewPrivs:\t1\n";
    if (!strncmp(line, "Seccomp:", 8)) return "Seccomp:\t2\n";
    if (!strncmp(line, "Seccomp_filters:", 16)) return "Seccomp_filters:\t1\n";
    if (!strncmp(line, "Umask:", 6)) return "Umask:\t0077\n";  /* zygote's */
    if (!strncmp(line, "CapBnd:", 7)) return "CapBnd:\t0000000000000000\n";
    return NULL;
}

/* The exec environment of another Android process of this app for its /proc/<pid>/environ: each one publishes the initial guest environment
 * (libc_bio_initial_environment) in a sealed memfd named "environ"; found through /proc/<pid>/fd (same uid). Returns an fd to read or -1. */
int apklayer_proc_publish_environment(void)
{
    static int published = -1;
    if (published >= 0 || !apklayer_proc_app_view()) return published;
    const char *(*initial)(size_t *) = dlsym(RTLD_DEFAULT, "libc_bio_initial_environment");
    size_t size = 0;
    const char *text = initial ? initial(&size) : NULL;
    if (!text) return -1;
    int fd = (int)raw(SYS_memfd_create, (long)"environ", 1 /* MFD_CLOEXEC */ | 2 /* MFD_ALLOW_SEALING */, 0, 0);
    if (fd < 0) return -1;
    for (size_t at = 0; at < size;) {
        long w = raw(SYS_write, fd, (long)(text + at), (long)(size - at), 0);
        if (w < 0 && errno == EINTR) continue;
        if (w <= 0) { raw(SYS_close, fd, 0, 0, 0); return -1; }
        at += (size_t)w;
    }
    fcntl(fd, F_ADD_SEALS, 0x0001 | 0x0002 | 0x0004 | 0x0008);  /* seal, shrink, grow, write */
    published = fd;
    return fd;
}

int apklayer_proc_foreign_environment(long pid)
{
    char dir[48], link[96], target[64];
    snprintf(dir, sizeof dir, "/proc/%ld/fd", pid);
    int d = (int)raw(SYS_openat, AT_FDCWD, (long)dir, O_RDONLY | O_DIRECTORY | O_CLOEXEC, 0);
    if (d < 0) return -1;
    char entries[4096];
    int found = -1;
    long n;
    while (found < 0 && (n = raw(SYS_getdents64, d, (long)entries, sizeof entries, 0)) > 0) {
        for (long at = 0; at + 19 < n && found < 0;) {
            unsigned short reclen;
            memcpy(&reclen, entries + at + 16, sizeof reclen);
            const char *name = entries + at + 19;
            at += reclen;
            if (!reclen) break;
            if (name[0] < '0' || name[0] > '9') continue;
            snprintf(link, sizeof link, "%s/%s", dir, name);
            long k = raw(SYS_readlinkat, AT_FDCWD, (long)link, (long)target, sizeof target - 1);
            if (k <= 0) continue;
            target[k] = 0;
            if (!strcmp(target, "/memfd:environ (deleted)")) found = (int)raw(SYS_openat, AT_FDCWD, (long)link, O_RDONLY | O_CLOEXEC, 0);
        }
    }
    raw(SYS_close, d, 0, 0, 0);
    return found;
}
