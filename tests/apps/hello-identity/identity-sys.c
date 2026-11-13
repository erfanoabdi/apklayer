/* Direct machine and libc identity (handoff item 6): uname, getauxval, personality, prctl, ids, CPU counts, system properties, well-known
 * file existence (root/emulator/host checks), environment variables, and the same uname/getuid/openat through raw syscall instructions
 * (svc #0 / syscall) that bypass libc and any preload. */
#include "identity.h"
#include <errno.h>
#include <fcntl.h>
#include <sched.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/auxv.h>
#include <sys/personality.h>
#include <sys/prctl.h>
#include <sys/stat.h>
#include <sys/system_properties.h>
#include <sys/utsname.h>
#include <unistd.h>

#if defined(__aarch64__)
static long raw6(long nr, long a, long b, long c, long d)
{
    register long x8 __asm__("x8") = nr, x0 __asm__("x0") = a, x1 __asm__("x1") = b, x2 __asm__("x2") = c, x3 __asm__("x3") = d;
    __asm__ volatile("svc #0" : "+r"(x0) : "r"(x8), "r"(x1), "r"(x2), "r"(x3) : "memory", "cc");
    return x0;
}
enum { NR_UNAME = 160, NR_GETUID = 174, NR_OPENAT = 56, NR_READ = 63, NR_CLOSE = 57, NR_GETPID = 172 };
#else
static long raw6(long nr, long a, long b, long c, long d)
{
    long ret;
    register long r10 __asm__("r10") = d;
    __asm__ volatile("syscall" : "=a"(ret) : "a"(nr), "D"(a), "S"(b), "d"(c), "r"(r10) : "rcx", "r11", "memory");
    return ret;
}
enum { NR_UNAME = 63, NR_GETUID = 102, NR_OPENAT = 257, NR_READ = 0, NR_CLOSE = 3, NR_GETPID = 39 };
#endif

static void raw_syscalls(struct buf *b)
{
    struct utsname u;
    long r = raw6(NR_UNAME, (long)&u, 0, 0, 0);
    if (r == 0) put(b, "raw.uname", "%s %s %s", u.sysname, u.release, u.machine);
    else put(b, "raw.uname", "ERR %ld", r);
    put(b, "raw.getuid", "%ld libc=%d", raw6(NR_GETUID, 0, 0, 0, 0), (int)getuid());
    put(b, "raw.getpid", "%s", raw6(NR_GETPID, 0, 0, 0, 0) == getpid() ? "same" : "differs");
    const char *files[] = {"/proc/self/maps", "/proc/self/status", "/proc/self/cmdline", "/system/build.prop", "/apex/com.android.art/lib64/libart.so", "/proc/1/cmdline"};
    for (size_t i = 0; i < sizeof files / sizeof *files; i++) {
        char key[96];
        snprintf(key, sizeof key, "raw.openat.%s", files[i]);
        long fd = raw6(NR_OPENAT, AT_FDCWD, (long)files[i], O_RDONLY | O_CLOEXEC, 0);
        if (fd < 0) { put(b, key, "ERR %s", strerror((int)-fd)); continue; }
        static char data[1 << 20];
        long total = 0, n;
        while (total < (long)sizeof data - 1 && (n = raw6(NR_READ, fd, (long)data + total, (long)sizeof data - 1 - total, 0)) > 0) total += n;
        raw6(NR_CLOSE, fd, 0, 0, 0);
        data[total] = 0;
        if (i == 0)
            put(b, key, "bytes=%ld libart=%d libjvm=%d", total, strstr(data, "libart.so") != NULL, strstr(data, "libjvm.so") != NULL);
        else if (i == 1) {
            char *tracer = strstr(data, "TracerPid:"), *uid = strstr(data, "Uid:");
            if (tracer) tracer[strcspn(tracer, "\n")] = 0;
            put(b, key, "bytes=%ld %s uidline=%.40s", total, tracer ? tracer : "no TracerPid", uid ? uid : "none");
        } else if (i == 2) {
            for (long j = 0; j + 1 < total; j++) if (!data[j]) data[j] = ' ';
            put(b, key, "%s", data);
        } else if (i == 4) put(b, key, "bytes=%ld elf=%d machine=%d", total, !memcmp(data, "\177ELF", 4), total > 20 ? *(unsigned short *)(data + 18) : -1);
        else put(b, key, "bytes=%ld", total);
    }
}

static void calls(struct buf *b)
{
    struct utsname u;
    uname(&u);
    put(b, "uname", "%s|%s|%s|%s|%s", u.sysname, u.nodename, u.release, u.version, u.machine);
    put(b, "getauxval.hwcap", "0x%lx", getauxval(AT_HWCAP));
    put(b, "getauxval.hwcap2", "0x%lx", getauxval(AT_HWCAP2));
    const char *platform = (const char *)getauxval(AT_PLATFORM);
    put(b, "getauxval.platform", "%s", platform ? platform : "null");
    put(b, "getauxval.pagesz", "%lu", getauxval(AT_PAGESZ));
    const char *execfn = (const char *)getauxval(AT_EXECFN);
    put(b, "getauxval.execfn", "%s", execfn ? execfn : "null");
    put(b, "getauxval.secure", "%lu", getauxval(AT_SECURE));
    put(b, "getauxval.sysinfo_ehdr", "%s", getauxval(AT_SYSINFO_EHDR) ? "set" : "0");
    char w[512];
    put(b, "getauxval.base", "%s", where((void *)getauxval(AT_BASE), w, sizeof w));
    put(b, "getauxval.entry", "%s", where((void *)getauxval(AT_ENTRY), w, sizeof w));
    put(b, "personality", "0x%x", personality(0xffffffff));
    char name[32] = "";
    prctl(PR_GET_NAME, name);
    put(b, "prctl.name", "%s", name);
    put(b, "prctl.dumpable", "%d", prctl(PR_GET_DUMPABLE));
    put(b, "prctl.no_new_privs", "%d", prctl(PR_GET_NO_NEW_PRIVS, 0, 0, 0, 0));
    put(b, "prctl.seccomp", "%d", prctl(PR_GET_SECCOMP));
    put(b, "prctl.subreaper", "%d", ({ int v = -1; prctl(PR_GET_CHILD_SUBREAPER, &v); v; }));
    put(b, "ids", "uid=%d euid=%d gid=%d egid=%d", getuid(), geteuid(), getgid(), getegid());
    gid_t groups[64];
    int ng = getgroups(64, groups);
    char gl[512] = "";
    for (int i = 0; i < ng; i++) { char g[16]; snprintf(g, sizeof g, "%d ", groups[i]); strcat(gl, g); }
    put(b, "groups", "%s", gl);
    put(b, "ppid", "%d", getppid());
    put(b, "pidIsTid", "%d", getpid() == gettid());
    cpu_set_t set;
    CPU_ZERO(&set);
    sched_getaffinity(0, sizeof set, &set);
    put(b, "cpus", "conf=%ld onln=%ld affinity=%d", sysconf(_SC_NPROCESSORS_CONF), sysconf(_SC_NPROCESSORS_ONLN), CPU_COUNT(&set));
    put(b, "pagesize", "%d", getpagesize());
    char *cpuinfo = slurp("/proc/cpuinfo", NULL);
    if (cpuinfo) {
        const char *keys[] = {"Hardware", "model name", "CPU implementer", "CPU part", "Features", "flags", "vendor_id"};
        for (size_t i = 0; i < sizeof keys / sizeof *keys; i++) {
            char *p = strstr(cpuinfo, keys[i]);
            char key[64];
            snprintf(key, sizeof key, "cpuinfo.%s", keys[i]);
            if (!p) { put(b, key, "absent"); continue; }
            char *colon = strchr(p, ':');
            size_t n = colon ? strcspn(colon + 1, "\n") : 0;
            put(b, key, "%.*s", (int)(n > 160 ? 160 : n), colon ? colon + 1 : "");
        }
        free(cpuinfo);
    }
}

static void count_property(const prop_info *pi, void *count) { (void)pi; (*(int *)count)++; }

static void properties(struct buf *b)
{
    const char *names[] = {"ro.build.fingerprint", "ro.build.version.sdk", "ro.build.type", "ro.build.tags", "ro.debuggable", "ro.secure",
        "ro.kernel.qemu", "ro.boot.qemu", "ro.hardware", "ro.product.cpu.abi", "ro.product.cpu.abilist", "ro.product.model", "ro.boot.verifiedbootstate",
        "ro.boot.flash.locked", "ro.zygote", "ro.dalvik.vm.native.bridge", "ro.enable.native.bridge.exec", "persist.sys.dalvik.vm.lib.2",
        "dalvik.vm.isa.arm64.variant", "dalvik.vm.heapsize", "dalvik.vm.heapgrowthlimit", "dalvik.vm.usejit", "ro.vndk.version", "ro.treble.enabled",
        "ro.build.selinux", "ro.boot.selinux", "init.svc.adbd", "sys.usb.state", "service.adb.root", "ro.adb.secure", "ro.serialno",
        "gsm.version.baseband", "ro.waydroid.cpu", "ro.apklayer.version", "ro.product.board", "ro.board.platform"};
    for (size_t i = 0; i < sizeof names / sizeof *names; i++) {
        char v[PROP_VALUE_MAX] = "", key[96];
        int n = __system_property_get(names[i], v);
        snprintf(key, sizeof key, "prop.%s", names[i]);
        put(b, key, "%s", n > 0 ? v : "(unset)");
    }
    int count = 0;
    __system_property_foreach(count_property, &count);
    put(b, "prop.count", "%d", count);
}

static void files(struct buf *b)
{
    const char *paths[] = {"/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su", "/data/local/tmp", "/data/local/xbin/su",
        "/system/app/Superuser.apk", "/data/adb/magisk", "/system/bin/app_process64", "/system/bin/app_process32", "/system/bin/linker64",
        "/apex/com.android.runtime/bin/linker64", "/apex/com.android.art/lib64/libart.so", "/apex/com.android.art/javalib/core-oj.jar",
        "/system/framework/framework.jar", "/system/framework/arm64/boot.oat", "/system/framework/x86_64/boot.oat",
        "/apex/com.android.art/javalib/arm64/boot.oat", "/data/dalvik-cache", "/dev/binder", "/dev/binderfs", "/dev/ashmem", "/dev/socket/zygote",
        "/dev/__properties__", "/dev/qemu_pipe", "/dev/goldfish_pipe", "/system/lib64/libhoudini.so", "/system/lib64/libndk_translation.so",
        "/system/bin/sh", "/system/bin/toybox", "/vendor/build.prop", "/system/build.prop", "/sys/fs/selinux/enforce", "/sys/class/net",
        "/proc/net/tcp", "/etc/os-release", "/usr/lib", "/home", "/run/user", "/tmp", "/lib/x86_64-linux-gnu", "/lib/aarch64-linux-gnu",
        "/data/data", "/storage/emulated/0", "/sdcard", "/mnt/user/0", "/odm", "/product", "/system_ext", "/vendor/lib64"};
    char exist[4096] = "", missing[4096] = "";
    for (size_t i = 0; i < sizeof paths / sizeof *paths; i++) {
        struct stat st;
        int ok = stat(paths[i], &st) == 0;
        char *dst = ok ? exist : missing;
        strncat(dst, paths[i], 4000 - strlen(dst));
        if (!ok && errno != ENOENT) { char e[32]; snprintf(e, sizeof e, "(%s)", errno == EACCES ? "EACCES" : "err"); strcat(dst, e); }
        strcat(dst, " ");
    }
    put(b, "files.exist", "%s", exist);
    put(b, "files.missing", "%s", missing);
    const char *vars[] = {"ANDROID_ROOT", "ANDROID_DATA", "ANDROID_ART_ROOT", "ANDROID_I18N_ROOT", "ANDROID_TZDATA_ROOT", "ANDROID_STORAGE",
        "EXTERNAL_STORAGE", "ASEC_MOUNTPOINT", "PATH", "LD_LIBRARY_PATH", "LD_PRELOAD", "HOME", "USER", "SHELL", "TMPDIR", "JAVA_HOME", "CLASSPATH",
        "ANDROID_SOCKET_zygote", "DISPLAY", "WAYLAND_DISPLAY", "XDG_RUNTIME_DIR"};
    for (size_t i = 0; i < sizeof vars / sizeof *vars; i++) {
        char key[96];
        snprintf(key, sizeof key, "getenv.%s", vars[i]);
        const char *v = getenv(vars[i]);
        put(b, key, "%s", v ? v : "(unset)");
    }
    const char *bcp = getenv("BOOTCLASSPATH");
    put(b, "getenv.BOOTCLASSPATH", "%s", bcp ? (strlen(bcp) > 200 ? "(long, starts /apex)" : bcp) : "(unset)");
}

JNIEXPORT jstring JNICALL Java_hello_identity_Main_sys(JNIEnv *env, jclass cls)
{
    (void)cls;
    struct buf b = {0};
    calls(&b);
    raw_syscalls(&b);
    properties(&b);
    files(&b);
    return finish(env, &b);
}
