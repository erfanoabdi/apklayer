/* An arm64-only JNI library (hello-arm64): on an x86_64 host it runs only through the native bridge (docs/history/PLAN-2026-11-10.md "apps built for another CPU"). */
#include <dlfcn.h>
#include <dirent.h>
#include <errno.h>
#include <stdarg.h>
#include <fcntl.h>
#include <jni.h>
#include <math.h>
#include <pthread.h>
#include <setjmp.h>
#include <signal.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <sys/auxv.h>
#include <sys/syscall.h>
#include <sys/stat.h>
#include <time.h>
#include <unistd.h>

static JavaVM *vm;

/* Which CPU this code was built for: proof that the translated library, not a host one, answered. */
JNIEXPORT jstring JNICALL Java_hello_arm64_Main_cpu(JNIEnv *env, jclass c)
{
    (void)c;
#if defined(__aarch64__)
    return (*env)->NewStringUTF(env, "aarch64");
#else
    return (*env)->NewStringUTF(env, "other");
#endif
}

/* Integer and floating point work (libm through the guest's libm). */
JNIEXPORT jdouble JNICALL Java_hello_arm64_Main_work(JNIEnv *env, jclass c, jint n)
{
    (void)env; (void)c;
    uint64_t acc = 1469598103934665603ull;
    double f = 0;
    for (jint i = 1; i <= n; i++) {
        acc = (acc ^ (uint64_t)i) * 1099511628211ull;
        f += sqrt((double)i) / (double)i;
    }
    return f + (double)(acc % 1000);
}

/* A call from native code into Java: Main.twice(int). */
JNIEXPORT jint JNICALL Java_hello_arm64_Main_callJava(JNIEnv *env, jclass c, jint v)
{
    jmethodID m = (*env)->GetStaticMethodID(env, c, "twice", "(I)I");
    return m ? (*env)->CallStaticIntMethod(env, c, m, v) : -1;
}

/* An overloaded getter/setter pair as JavaCPP generates them (field() and field(long)): the getter's long JNI name (__, no arguments) must bind
 * only field(), never the setter. The setter is registered by JNI_OnLoad, so a binding by name that ignores the empty argument list replaces it
 * whatever the order of the library's symbols. */
static jlong field_value = 7;
JNIEXPORT jlong JNICALL Java_hello_arm64_Main_field__(JNIEnv *env, jclass c)
{
    (void)env; (void)c;
    return field_value;
}

static jint field_set(JNIEnv *env, jclass c, jlong v)
{
    (void)env; (void)c;
    field_value = v;
    return 1;
}

/* A guest fault the guest itself handles: a null read caught by its SIGSEGV handler, which jumps back (1); the JVM must survive it. */
static sigjmp_buf fault_jump;
static void on_fault(int sig) { siglongjmp(fault_jump, sig); }
JNIEXPORT jint JNICALL Java_hello_arm64_Main_fault(JNIEnv *env, jclass c)
{
    (void)env; (void)c;
    struct sigaction sa = {.sa_handler = on_fault}, old;
    if (sigaction(SIGSEGV, &sa, &old) != 0) return -1;
    int r = sigsetjmp(fault_jump, 1);
    if (r == 0) r = *(volatile int *)(uintptr_t)16;  /* faults */
    sigaction(SIGSEGV, &old, NULL);
    return r == SIGSEGV;
}

/* errno of failing libc calls made from guest code: open of a missing file (a translated syscall) ENOENT, then clock_gettime of a bad clock
 * (a host call through the bridge's proxy libc) EINVAL, as open*100 + clock (222). */
JNIEXPORT jint JNICALL Java_hello_arm64_Main_errnos(JNIEnv *env, jclass c)
{
    (void)env; (void)c;
    struct timespec ts;
    errno = 0;
    int r = open("/nonexistent/apklayer-errno", O_RDONLY) == -1 ? errno * 100 : 0;
    errno = 0;
    return r + (clock_gettime((clockid_t)1000, &ts) == -1 ? errno : 0);
}

/* pthread_getattr_np(pthread_self()) of a guest thread covers its own stack (GCs such as il2cpp's Boehm scan up to that top): 1 if so.
 * The calling thread is a JVM one, as Unity's UnityMain. */
static int own_stack(void)
{
    pthread_attr_t a;
    void *base;
    size_t size;
    char here;
    if (pthread_getattr_np(pthread_self(), &a) != 0 || pthread_attr_getstack(&a, &base, &size) != 0) return 0;
    pthread_attr_destroy(&a);
    return (char *)base <= &here && &here < (char *)base + size;
}

JNIEXPORT jint JNICALL Java_hello_arm64_Main_ownStack(JNIEnv *env, jclass c)
{
    (void)env; (void)c;
    return own_stack();
}

/* /proc/self/auxv is the auxv getauxval reports (Go reads the file; the host's x86 AT_HWCAP read as arm64 bits made it run DIT: SIGILL):
 * 1 if its AT_HWCAP and AT_HWCAP2 (0 when absent) match. */
JNIEXPORT jint JNICALL Java_hello_arm64_Main_auxv(JNIEnv *env, jclass c)
{
    (void)env; (void)c;
    unsigned long v[128], hwcap = 0, hwcap2 = 0;
    int fd = open("/proc/self/auxv", O_RDONLY);
    if (fd < 0) return 0;
    ssize_t n = read(fd, v, sizeof(v));
    close(fd);
    for (ssize_t i = 0; i + 1 < n / (ssize_t)sizeof(v[0]); i += 2) {
        if (v[i] == AT_HWCAP) hwcap = v[i + 1];
        if (v[i] == AT_HWCAP2) hwcap2 = v[i + 1];
    }
    return n > 0 && hwcap == getauxval(AT_HWCAP) && hwcap2 == getauxval(AT_HWCAP2);
}

/* The app uid as translated code sees it (binary_translation 0015): bionic's getuid() is a raw syscall the translator answers with the app's
 * uid, as host-side code gets it (bionic_translation 0056); without apklayer's sandbox the kernel would say the desktop user (often 1000). */
JNIEXPORT jint JNICALL Java_hello_arm64_Main_uid(JNIEnv *env, jclass c)
{
    (void)env; (void)c;
    return (jint)getuid();
}

/* The owner of a file as translated code sees it: the app's own files are the app uid's (0015's stat conversion), -1 if stat fails. */
JNIEXPORT jint JNICALL Java_hello_arm64_Main_ownerUid(JNIEnv *env, jclass c, jstring path)
{
    (void)c;
    const char *p = (*env)->GetStringUTFChars(env, path, NULL);
    struct stat st;
    int r = p && stat(p, &st) == 0 ? (int)st.st_uid : -1;
    if (p) (*env)->ReleaseStringUTFChars(env, path, p);
    return r;
}

/* What arm64 code sees of the process table and its ids (binary_translation 0016, 0017; the path layer's /proc view): bit 0 /proc/self/status shows
 * the app's uid and gid in all four fields of Uid:/Gid: (as getuid/getgid answer), bit 1 /proc/1 is not there (stat), bit 2 a getdents64 listing of
 * /proc has no pid of another cgroup (init's 1), bit 3 /proc/self/status is there. 15 is all. */
JNIEXPORT jint JNICALL Java_hello_arm64_Main_procView(JNIEnv *env, jclass c)
{
    (void)env; (void)c;
    int r = 0;
    char line[256];
    FILE *f = fopen("/proc/self/status", "r");
    int ids = f != NULL, seen = 0;
    while (f && fgets(line, sizeof line, f)) {
        unsigned a, b, d, e;
        int uid = !strncmp(line, "Uid:", 4), gid = !strncmp(line, "Gid:", 4);
        if ((uid || gid) && sscanf(line + 4, "%u %u %u %u", &a, &b, &d, &e) == 4) {
            unsigned want = uid ? (unsigned)getuid() : (unsigned)getgid();
            ids = ids && a == want && b == want && d == want && e == want;
            seen++;
        }
    }
    if (f) fclose(f);
    if (ids && seen == 2) r |= 1;
    struct stat st;
    if (stat("/proc/1", &st) != 0) r |= 2;
    int fd = open("/proc", O_RDONLY | O_DIRECTORY);
    int listed_init = 0;
    char buf[8192];
    for (long n; fd >= 0 && (n = syscall(SYS_getdents64, fd, buf, sizeof buf)) > 0;)
        for (long pos = 0; pos < n; pos += *(unsigned short *)(buf + pos + 16))
            listed_init |= !strcmp(buf + pos + 19, "1");
    if (fd >= 0) close(fd);
    if (fd >= 0 && !listed_init) r |= 4;
    if (stat("/proc/self/status", &st) == 0) r |= 8;
    return r;
}

/* Filesystem layout as translated code sees it (the sandbox audit, docs/PLAN.md): one line per finding, "ok|LEAK <what>". Host paths are the desktop's
 * (/home, /usr, /run/user, /tmp, .local/share, /snap); Android code never meets them. */
static int host_path(const char *s)
{
    static const char *const p[] = {"/home/", "/usr/", "/run/", "/tmp/", "/snap/", "/nix/", "/opt/", "/var/", "/lib/", "/lib64/", "/etc/", "/mnt/", "/media/", "/root", NULL};
    for (int i = 0; p[i]; i++) if (!strncmp(s, p[i], strlen(p[i]))) return 1;
    return strstr(s, ".local/share") || strstr(s, "apklayer") || strstr(s, "x86_64-linux-gnu") || strstr(s, "libberberis") || strstr(s, "/jdk");
}
static void add(char *out, size_t n, const char *fmt, ...)
{
    va_list ap; va_start(ap, fmt);
    size_t k = strlen(out);
    if (k + 1 < n) vsnprintf(out + k, n - k, fmt, ap);
    va_end(ap);
}
static void file_head(char *out, size_t n, const char *tag, const char *path, int lines)
{
    FILE *f = fopen(path, "r"); char l[300];
    if (!f) { add(out, n, "%s %s: open failed (%s)\n", "info", path, strerror(errno)); return; }
    for (int i = 0; i < lines && fgets(l, sizeof l, f); i++) { l[strcspn(l, "\n")] = 0; add(out, n, "%s %s: %s\n", tag, path, l); }
    fclose(f);
}
JNIEXPORT jstring JNICALL Java_hello_arm64_Main_layout(JNIEnv *env, jclass c, jstring pkg)
{
    (void)c;
    static char out[24000];
    out[0] = 0;
    const char *p = (*env)->GetStringUTFChars(env, pkg, NULL);
    char path[512], l[1024];
    struct stat st;
    const char *must[] = {"/system", "/system/bin", "/system/lib64", "/system/etc", "/system/build.prop", "/system/framework", "/vendor", "/data", "/data/local/tmp", "/storage", "/sdcard", "/proc/self", "/dev/null", "/cache", NULL};
    for (int i = 0; must[i]; i++) add(out, sizeof out, "%s exists %s%s\n", stat(must[i], &st) == 0 ? "ok" : "info", must[i], stat(must[i], &st) == 0 ? "" : " (missing)");
    snprintf(path, sizeof path, "/data/data/%s", p);
    add(out, sizeof out, "%s /data/data/<pkg> %s uid %u mode %o\n", stat(path, &st) == 0 ? "ok" : "LEAK", stat(path, &st) == 0 ? "there" : "missing", st.st_uid, st.st_mode & 07777);
    snprintf(path, sizeof path, "/data/user/0/%s", p);
    add(out, sizeof out, "%s /data/user/0/<pkg> %s\n", stat(path, &st) == 0 ? "ok" : "info", stat(path, &st) == 0 ? "there" : "missing");
    /* what an app must not see: other packages' data and the host's roots */
    DIR *d = opendir("/data/data"); int others = 0; struct dirent *e;
    while (d && (e = readdir(d))) if (e->d_name[0] != '.' && strcmp(e->d_name, p)) { others++; add(out, sizeof out, "info /data/data has %s\n", e->d_name); }
    if (d) closedir(d);
    add(out, sizeof out, "%s /data/data lists %d other packages\n", others ? "LEAK" : "ok", others);
    d = opendir("/data/app"); int apps = 0;
    while (d && (e = readdir(d))) if (e->d_name[0] != '.') { apps++; add(out, sizeof out, "info /data/app has %s\n", e->d_name); }
    if (d) closedir(d);
    add(out, sizeof out, "info /data/app lists %d entries%s\n", apps, d ? "" : " (cannot open)");
    const char *host[] = {"/home", "/usr", "/etc/passwd", "/etc/os-release", "/run/user", "/tmp", "/proc/self/exe", NULL};
    for (int i = 0; host[i]; i++) add(out, sizeof out, "%s visible %s\n", stat(host[i], &st) == 0 ? "LEAK" : "ok", host[i]);
    ssize_t n = readlink("/proc/self/exe", l, sizeof l - 1); if (n > 0) { l[n] = 0; add(out, sizeof out, "%s /proc/self/exe -> %s\n", host_path(l) ? "LEAK" : "ok", l); }
    n = readlink("/proc/self/cwd", l, sizeof l - 1); if (n > 0) { l[n] = 0; add(out, sizeof out, "%s cwd -> %s\n", host_path(l) ? "LEAK" : "ok", l); }
    char *cwd = getcwd(l, sizeof l); if (cwd) add(out, sizeof out, "%s getcwd %s\n", host_path(l) ? "LEAK" : "ok", l);
    /* maps: every named mapping */
    FILE *f = fopen("/proc/self/maps", "r"); int maps = 0, leaks = 0;
    while (f && fgets(l, sizeof l, f)) {
        char *name = strchr(l, '/'); if (!name) continue; name[strcspn(name, "\n")] = 0; maps++;
        if (host_path(name)) { leaks++; if (leaks <= 8) add(out, sizeof out, "LEAK maps %s\n", name); }
    }
    if (f) fclose(f);
    add(out, sizeof out, "%s maps: %d named mappings, %d host paths\n", leaks ? "LEAK" : "ok", maps, leaks);
    /* mounts and kernel strings */
    f = fopen("/proc/self/mountinfo", "r"); int mounts = 0, mleaks = 0;
    while (f && fgets(l, sizeof l, f)) { mounts++; if (strstr(l, "/home/") || strstr(l, "apklayer") || strstr(l, "/run/user")) { mleaks++; if (mleaks <= 4) { l[strcspn(l, "\n")] = 0; add(out, sizeof out, "LEAK mountinfo %s\n", l); } } }
    if (f) fclose(f);
    add(out, sizeof out, "info mountinfo: %d lines, %d name host paths\n", mounts, mleaks);
    file_head(out, sizeof out, "info", "/proc/version", 1);
    file_head(out, sizeof out, "info", "/proc/self/cgroup", 3);
    file_head(out, sizeof out, "info", "/proc/self/cmdline", 1);
    f = fopen("/proc/self/environ", "r");
    if (f) { int k = 0; char buf[8192]; size_t r = fread(buf, 1, sizeof buf - 1, f); fclose(f); buf[r] = 0;
        for (char *v = buf; v < buf + r; v += strlen(v) + 1) if (host_path(v) || !strncmp(v, "HOME=", 5) || !strncmp(v, "XDG_", 4) || !strncmp(v, "DISPLAY", 7) || !strncmp(v, "WAYLAND", 7) || !strncmp(v, "USER=", 5) || !strncmp(v, "LD_PRELOAD", 10) || !strncmp(v, "DBUS", 4)) { if (k++ < 12) add(out, sizeof out, "LEAK environ %.120s\n", v); }
        add(out, sizeof out, "%s environ: %d host-looking variables\n", k ? "LEAK" : "ok", k); }
    const char *envs[] = {"HOME", "USER", "PATH", "TMPDIR", "ANDROID_ROOT", "ANDROID_DATA", "EXTERNAL_STORAGE", "LD_LIBRARY_PATH", "LD_PRELOAD", NULL};
    for (int i = 0; envs[i]; i++) { const char *v = getenv(envs[i]); add(out, sizeof out, "%s getenv %s=%.100s\n", v && host_path(v) ? "LEAK" : "info", envs[i], v ? v : "(unset)"); }
    snprintf(path, sizeof path, "/data/data/%s/files", p);
    char *rp = realpath(path, l); add(out, sizeof out, "%s realpath(%s) = %s\n", rp && host_path(l) ? "LEAK" : "ok", path, rp ? l : "(fails)");
    if (p) (*env)->ReleaseStringUTFChars(env, pkg, p);
    return (*env)->NewStringUTF(env, out);
}

/* The CPU as translated code reads it (a device profile, binary_translation 0020): the cores in /proc/cpuinfo ("N cores, <implementer> <part> ...") and MIDR_EL1
 * read directly with mrs (the translator emulates the register). */
JNIEXPORT jstring JNICALL Java_hello_arm64_Main_cpuProfile(JNIEnv *env, jclass c)
{
    (void)c;
    char out[256], l[256], first[96] = "", last[96] = "";
    int cores = 0;
    FILE *f = fopen("/proc/cpuinfo", "r");
    while (f && fgets(l, sizeof l, f)) {
        if (!strncmp(l, "processor", 9)) cores++;
        if (!strncmp(l, "CPU part", 8)) { snprintf(last, sizeof last, "%s", strchr(l, ':') + 2); last[strcspn(last, "\n")] = 0; if (!first[0]) snprintf(first, sizeof first, "%s", last); }
    }
    if (f) fclose(f);
    unsigned long midr = 0;
    __asm__ volatile("mrs %0, midr_el1" : "=r"(midr));
    snprintf(out, sizeof out, "%d cores, part first %s last %s, MIDR_EL1 0x%lx", cores, first, last, midr);
    return (*env)->NewStringUTF(env, out);
}

/* exit-deadline.py native: the library ignores SIGTERM (signal) and then catches it (sigaction), as an app library may; 1 if both calls report
 * success. The process must still end on SIGTERM (bionic_translation 0064). */
static void on_term(int sig) { (void)sig; }
JNIEXPORT jint JNICALL Java_hello_arm64_Main_ignoreTerm(JNIEnv *env, jclass c)
{
    (void)env; (void)c;
    struct sigaction sa = {.sa_handler = on_term};
    return signal(SIGTERM, SIG_IGN) != SIG_ERR && sigaction(SIGTERM, &sa, NULL) == 0;
}

static jclass main_class;

static void *thread_main(void *arg)
{
    JNIEnv *env;
    if ((*vm)->AttachCurrentThread(vm, &env, NULL) != JNI_OK) return (void *)(intptr_t)-1;
    jmethodID m = (*env)->GetStaticMethodID(env, main_class, "twice", "(I)I");
    intptr_t r = m ? (*env)->CallStaticIntMethod(env, main_class, m, (jint)(intptr_t)arg) : -2;
    (*vm)->DetachCurrentThread(vm);
    return (void *)r;
}

/* A native thread that attaches to the VM and calls Java. */
static jint thread_call(JNIEnv *env, jclass c, jint v)
{
    (void)env; (void)c;
    pthread_t t;
    void *r;
    if (pthread_create(&t, NULL, thread_main, (void *)(intptr_t)v) != 0) return -3;
    pthread_join(t, &r);
    return (jint)(intptr_t)r;
}

/* GLES through the bridge's EGL/GLES proxies: a pbuffer cleared to a colour and read back (0xAABBGGRR), 0 on failure. The libraries are
 * opened here, not linked, so the other checks do not depend on the graphics proxies. */
JNIEXPORT jint JNICALL Java_hello_arm64_Main_gl(JNIEnv *env, jclass c)
{
    (void)env; (void)c;
    void *egl = dlopen("libEGL.so", RTLD_NOW), *gles = dlopen("libGLESv2.so", RTLD_NOW);
    if (!egl || !gles) return 0;
#define F(lib, ret, name, ...) ret (*name)(__VA_ARGS__) = (ret (*)(__VA_ARGS__))dlsym(lib, #name)
    F(egl, void *, eglGetDisplay, void *);
    F(egl, unsigned, eglInitialize, void *, int *, int *);
    F(egl, unsigned, eglChooseConfig, void *, const int *, void **, int, int *);
    F(egl, void *, eglCreatePbufferSurface, void *, void *, const int *);
    F(egl, void *, eglCreateContext, void *, void *, void *, const int *);
    F(egl, unsigned, eglMakeCurrent, void *, void *, void *, void *);
    F(gles, void, glClearColor, float, float, float, float);
    F(gles, void, glClear, unsigned);
    F(gles, void, glReadPixels, int, int, int, int, unsigned, unsigned, void *);
#undef F
    if (!eglGetDisplay || !glReadPixels) return 0;
    void *dpy = eglGetDisplay(0), *cfg = 0;
    int n = 0;
    const int cfg_attr[] = {0x3033 /* SURFACE_TYPE */, 0x0001 /* PBUFFER_BIT */, 0x3040 /* RENDERABLE_TYPE */, 0x0004 /* ES2 */, 0x3038};
    const int pb_attr[] = {0x3057 /* WIDTH */, 4, 0x3056 /* HEIGHT */, 4, 0x3038};
    const int ctx_attr[] = {0x3098 /* CONTEXT_CLIENT_VERSION */, 2, 0x3038};
    if (!eglInitialize(dpy, 0, 0) || !eglChooseConfig(dpy, cfg_attr, &cfg, 1, &n) || n < 1) return 0;
    void *surf = eglCreatePbufferSurface(dpy, cfg, pb_attr), *ctx = eglCreateContext(dpy, cfg, 0, ctx_attr);
    if (!surf || !ctx || !eglMakeCurrent(dpy, surf, surf, ctx)) return 0;
    glClearColor(1.0f, 0.5f, 0.0f, 1.0f);
    glClear(0x4000 /* COLOR_BUFFER_BIT */);
    uint32_t px = 0;
    glReadPixels(0, 0, 1, 1, 0x1908 /* RGBA */, 0x1401 /* UNSIGNED_BYTE */, &px);
    return (jint)px;
}

/* JNI_OnLoad registers threadCall itself (RegisterNatives through the bridge); the others are bound by name. */
JNIEXPORT jint JNI_OnLoad(JavaVM *jvm, void *reserved)
{
    (void)reserved;
    vm = jvm;
    JNIEnv *env;
    if ((*vm)->GetEnv(vm, (void **)&env, JNI_VERSION_1_6) != JNI_OK) return -1;
    jclass c = (*env)->FindClass(env, "hello/arm64/Main");
    if (!c) return -1;
    main_class = (*env)->NewGlobalRef(env, c);
    static const JNINativeMethod methods[] = {{"threadCall", "(I)I", (void *)thread_call}, {"field", "(J)I", (void *)field_set}};
    if ((*env)->RegisterNatives(env, c, methods, 2) != 0) return -1;
    return JNI_VERSION_1_6;
}
