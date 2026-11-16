/* Raw syscalls of guest code on native arm64 (docs/PLAN.md "Raw syscalls of guest code", PATCHES: bionic_translation 0074).
 * An `svc #0` in a library the bionic linker loaded (an app's anti-tamper code, a Go runtime, a static libc) reaches the kernel past every
 * view the libc wrappers and the path layer give: it saw the desktop uid and the JVM in /proc/self/maps. The bionic linker places the
 * libraries it loads in one 4 GiB window (apklayer_guest_window_take, below); one seccomp-bpf filter turns the few syscalls that carry
 * identity into SIGSYS when the instruction pointer is in that window, and the handler answers them through the views the libc path has
 * (syscall() of paths_preload.c: the path layer, the /proc projections; apklayer_app_uid and groups: paths.c). Host code (the JVM, glibc, bionic's libc here)
 * is outside the window: the filter costs it one compare. mkdirat/unlinkat/bind/connect/inotify_add_watch (a Go runtime's, at /data/data/<pkg>) are answered
 * by a handler that uses no libc at all (fast_syscall below): the libc-based handler corrupts a Go thread's state ("systemstack called from unexpected
 * goroutine" after an openat of /proc/sys/net/core/somaxconn, so /proc/sys opens are fast too), and a thread that waits for a supervisor (seccomp user
 * notification was tried) makes Go's scheduler take its processor away ("stoplockedm: inconsistent locking"). Only the app process (APKLAYER_OWN_DATA) gets the window; x86_64 hosts translate
 * guest code, whose syscalls the translator already sees. APKLAYER_RAW_SYSCALL_VIEW=off turns it off. */
#define _GNU_SOURCE
#include <dlfcn.h>
#include <errno.h>
#include <pthread.h>
#include <signal.h>
#include <stdarg.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>
#include <sys/prctl.h>
#include <sys/syscall.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <sys/utsname.h>
#include <ucontext.h>
#include <unistd.h>

#if defined(__aarch64__)
#include <linux/audit.h>
#include <linux/filter.h>
#include <linux/seccomp.h>

#ifndef SYS_SECCOMP
#define SYS_SECCOMP 1  /* si_code of a seccomp trap (asm-generic/siginfo.h) */
#endif
#ifndef __NR_statx
#define __NR_statx 291  /* arm64's number; Sailfish OS 5.0's kernel headers (3.18) lack it */
#endif
#define WINDOW_SIZE (1UL << 32)
#define REAL(name) ({ static void *f_; if (!f_) f_ = dlsym(RTLD_NEXT, #name); f_; })

const char *apklayer_own_data_env(void);
long apklayer_app_uid(void);
int apklayer_app_groups(gid_t *out, size_t capacity);
__attribute__((weak)) int apklayer_uname_view(struct utsname *u);  /* the uname view of the process, when something provides one */
static void default_uname(struct utsname *u) { strcpy(u->nodename, "localhost"); }  /* Android's nodename; the desktop's leaked */

int apklayer_raw_syscall_active;  /* the filter is in place: SIGSYS is ours (bionic_translation's sigaction leaves it alone) */
static unsigned long window_base, window_next;
static pthread_mutex_t window_lock = PTHREAD_MUTEX_INITIALIZER;
static bool window_tried;

static bool debug(void) { static int d = -1; if (d < 0) d = getenv("APKLAYER_RAW_SYSCALL_DEBUG") != NULL; return d; }

/* a syscall of guest code, as the kernel's registers hold it (x8 the number, x0..x5 the arguments) */
static long guest_syscall(long nr, const long a[6])
{
    long r;
    switch (nr) {
    case __NR_getuid: case __NR_geteuid: case __NR_getgid: case __NR_getegid: {
        long uid = apklayer_app_uid();
        return uid >= 0 ? uid : syscall(nr);
    }
    case __NR_getresuid: case __NR_getresgid: {
        long uid = apklayer_app_uid();
        if (uid < 0) break;
        unsigned *out[3] = { (unsigned *)a[0], (unsigned *)a[1], (unsigned *)a[2] };
        for (int i = 0; i < 3; i++) if (!out[i]) return -EFAULT;
        for (int i = 0; i < 3; i++) *out[i] = (unsigned)uid;
        return 0;
    }
    case __NR_getgroups: {
        int n = (int)a[0];
        gid_t *list = (gid_t *)a[1];
        if (n < 0) return -EINVAL;
        int have = apklayer_app_groups(NULL, 0);
        if (have < 0) break;
        if (!n) return have;
        if (n < have) return -EINVAL;
        return apklayer_app_groups(list, (size_t)n) < 0 ? -errno : have;
    }
    case __NR_uname:
        r = syscall(nr, a[0]);
        if (r == 0) { if (apklayer_uname_view) apklayer_uname_view((struct utsname *)a[0]); else default_uname((struct utsname *)a[0]); }
        return r < 0 ? -errno : r;
    case __NR_newfstatat: case __NR_statx: {  /* the owner ids of the real result are the desktop's: show the app's (files the app owns read as the desktop user) */
        r = syscall(nr, a[0], a[1], a[2], a[3], a[4], a[5]);
        long uid = apklayer_app_uid();
        if (r == 0 && uid >= 0) {
            long host = syscall(__NR_getuid);
            if (nr == __NR_statx) {  /* struct statx: stx_uid @20, stx_gid @24 */
                unsigned *o = (unsigned *)((char *)a[4] + 20);
                if (o[0] == host) o[0] = (unsigned)uid;
                if (o[1] == host) o[1] = (unsigned)uid;
            } else {  /* arm64 struct stat: st_uid @24, st_gid @28 */
                unsigned *o = (unsigned *)((char *)a[2] + 24);
                if (o[0] == host) o[0] = (unsigned)uid;
                if (o[1] == host) o[1] = (unsigned)uid;
            }
        }
        return r < 0 ? -errno : r;
    }
    case __NR_rt_sigaction:  /* SIGSYS only (the filter): the handler is ours, the guest's is never installed (a Go runtime installs its own through the kernel) */
        if (a[2]) memset((void *)a[2], 0, 3 * sizeof(unsigned long));  /* the old action (the kernel's arm64 struct: handler, flags, mask) reads as the default */
        return 0;
    default:
        break;
    }
    /* openat, readlinkat: syscall() of the path layer's preload: the path mapping, the /proc projections */
    r = syscall(nr, a[0], a[1], a[2], a[3], a[4], a[5]);
    return r == -1 ? -errno : r;
}


/* The libc-free answers (see the header): the app's own data path spellings mapped into a stack buffer, the call made again with a bare `svc`. Anything else
 * of these numbers is made as it is (the kernel's view). */
static char fast_pkg[256], fast_dir[1024], fast_device[1024];
static size_t fast_pkg_len;
static int fast_debug;
bool apklayer_own_data_paths(char *pkg, size_t pn, char *dir, size_t dn, char *device, size_t vn);  /* paths.c */

static void fast_paths_init(void)
{
    fast_debug = getenv("APKLAYER_RAW_SYSCALL_TRACE") != NULL;
    if (apklayer_own_data_paths(fast_pkg, sizeof fast_pkg, fast_dir, sizeof fast_dir, fast_device, sizeof fast_device)) fast_pkg_len = strlen(fast_pkg);
}

#if defined(__has_attribute)
#if __has_attribute(no_stack_protector)
#define NO_CANARY __attribute__((no_stack_protector))
#endif
#endif
#ifndef NO_CANARY
#define NO_CANARY
#endif

static NO_CANARY long bare_svc(long nr, long a0, long a1, long a2, long a3, long a4)
{
    register long x8 __asm__("x8") = nr, x0 __asm__("x0") = a0, x1 __asm__("x1") = a1, x2 __asm__("x2") = a2, x3 __asm__("x3") = a3, x4 __asm__("x4") = a4;
    __asm__ volatile("svc #0" : "+r"(x0) : "r"(x8), "r"(x1), "r"(x2), "r"(x3), "r"(x4) : "memory");
    return x0;
}

static NO_CANARY bool starts(const char *s, const char *prefix)
{
    for (; *prefix; s++, prefix++) if (*s != *prefix) return false;
    return true;
}

/* in under /data/data/<pkg>, /data/user/0/<pkg> or /data/user_de/0/<pkg> of the app: out is the host path (the same rule as paths.c own_data_path) */
static NO_CANARY bool fast_map(const char *in, size_t len, char *out, size_t cap)
{
    static const char *const spellings[] = { "/data/data/", "/data/user/0/", "/data/user_de/0/" };
    if (!fast_pkg_len || len >= cap) return false;
    for (int i = 0; i < 3; i++) {
        size_t n = 0;
        while (spellings[i][n]) n++;
        if (!starts(in, spellings[i])) continue;
        const char *rest = in + n;
        if (!starts(rest, fast_pkg) || (rest[fast_pkg_len] && rest[fast_pkg_len] != '/')) return false;
        const char *base = i == 2 ? fast_device : fast_dir, *tail = rest + fast_pkg_len;
        size_t o = 0;
        for (; base[o]; o++) { if (o + 1 >= cap) return false; out[o] = base[o]; }
        for (; *tail; tail++, o++) { if (o + 1 >= cap) return false; out[o] = *tail; }
        out[o] = 0;
        return true;
    }
    return false;
}

static NO_CANARY size_t cstr_len(const char *s, size_t limit)
{
    size_t n = 0;
    while (n < limit && s[n]) n++;
    return n;
}

static NO_CANARY bool fast_syscall(long nr, const long a[6], long *r)
{
    char buf[1100];
    switch (nr) {
    case __NR_mkdirat: case __NR_unlinkat: case __NR_inotify_add_watch: {
        const char *path = (const char *)a[1];
        if (!path) return false;
        size_t n = cstr_len(path, sizeof buf);
        const char *use = fast_map(path, n, buf, sizeof buf) ? buf : path;
        *r = bare_svc(nr, a[0], (long)use, a[2], a[3], a[4]);
        return true;
    }
    case __NR_openat:  /* /proc/sys: the kernel's own file (Go's net package reads somaxconn before it listens), nothing in it to project */
        if (!starts((const char *)a[1], "/proc/sys/")) return false;
        *r = bare_svc(nr, a[0], a[1], a[2], a[3], 0);
        return true;
    case __NR_bind: case __NR_connect: {
        const struct sockaddr_un *in = (const struct sockaddr_un *)a[1];
        size_t head = __builtin_offsetof(struct sockaddr_un, sun_path), len = (size_t)a[2];
        struct sockaddr_un out;
        if (in && len > head + 1 && len <= sizeof out && in->sun_family == AF_UNIX && in->sun_path[0] == '/') {
            size_t n = cstr_len(in->sun_path, len - head);
            char path[sizeof out.sun_path + 1];
            if (n < sizeof out.sun_path && n < sizeof path) {
                for (size_t i = 0; i < n; i++) path[i] = in->sun_path[i];
                path[n] = 0;
                if (fast_map(path, n, buf, sizeof buf)) {
                    size_t m = cstr_len(buf, sizeof buf);
                    if (m < sizeof out.sun_path) {
                        for (size_t i = 0; i < sizeof out; i++) ((char *)&out)[i] = 0;
                        out.sun_family = AF_UNIX;
                        for (size_t i = 0; i <= m; i++) out.sun_path[i] = buf[i];
                        *r = bare_svc(nr, a[0], (long)&out, (long)(head + m + 1), 0, 0);
                        return true;
                    }
                }
            }
        }
        *r = bare_svc(nr, a[0], a[1], a[2], 0, 0);
        return true;
    }
    default:
        return false;
    }
}

static void on_sigsys(int sig, siginfo_t *info, void *context)
{
    (void)sig;
    ucontext_t *uc = context;
    if (info->si_code == SYS_SECCOMP) {  /* before errno or any libc: the thread may be a Go runtime's */
        long fast_a[6], fast_r;
        for (int i = 0; i < 6; i++) fast_a[i] = (long)uc->uc_mcontext.regs[i];
        if (fast_syscall((long)uc->uc_mcontext.regs[8], fast_a, &fast_r)) { uc->uc_mcontext.regs[0] = (unsigned long)fast_r; return; }
    }
    if (fast_debug) {  /* APKLAYER_RAW_SYSCALL_TRACE: the number and path of what the libc handler is about to answer, written without libc */
        char msg[32] = "[sigsys nr ";
        long v = (long)uc->uc_mcontext.regs[8];
        int n = 11;
        char d[8]; int k = 0;
        do { d[k++] = (char)('0' + v % 10); v /= 10; } while (v);
        while (k) msg[n++] = d[--k];
        msg[n++] = ']'; msg[n++] = '\n';
        bare_svc(__NR_write, 2, (long)msg, n, 0, 0);
        long pv = (long)uc->uc_mcontext.regs[8];
        if (pv == __NR_openat || pv == __NR_newfstatat || pv == __NR_readlinkat) {
            const char *pp = (const char *)uc->uc_mcontext.regs[1];
            size_t pl = cstr_len(pp, 200);
            bare_svc(__NR_write, 2, (long)pp, (long)pl, 0, 0);
            bare_svc(__NR_write, 2, (long)"\n", 1, 0, 0);
        }
    }
    int saved = errno;
    if (info->si_code != SYS_SECCOMP) {  /* not our trap (kill(SIGSYS) or a program's own): the default */
        errno = saved;
        signal(SIGSYS, SIG_DFL);
        raise(SIGSYS);
        return;
    }
    long a[6];
    for (int i = 0; i < 6; i++) a[i] = (long)uc->uc_mcontext.regs[i];
    long nr = (long)uc->uc_mcontext.regs[8];
    long r;
    if (nr == __NR_rt_sigprocmask && a[3] == 8) {  /* the handler's own mask is restored at sigreturn: the guest's mask is the saved one, SIGSYS never in it */
        unsigned long *m = (unsigned long *)&uc->uc_sigmask, was = *m & ~(1UL << (SIGSYS - 1));
        r = 0;
        if (a[2]) *(unsigned long *)a[2] = was;
        if (a[1]) {
            unsigned long set = *(unsigned long *)a[1];
            if (a[0] == SIG_BLOCK) was |= set; else if (a[0] == SIG_UNBLOCK) was &= ~set; else if (a[0] == SIG_SETMASK) was = set; else r = -EINVAL;
            if (!r) *m = was & ~(1UL << (SIGSYS - 1));
        }
    } else r = guest_syscall(nr, a);
    uc->uc_mcontext.regs[0] = (unsigned long)r;
    errno = saved;
}

/* the filter: in the window and one of the numbers: trap; everything else runs */
static int install_filter(unsigned long base)
{
    static const int numbers[] = { __NR_getuid, __NR_geteuid, __NR_getgid, __NR_getegid, __NR_getresuid, __NR_getresgid, __NR_getgroups,
        __NR_openat, __NR_readlinkat, __NR_uname, __NR_newfstatat, __NR_statx, __NR_rt_sigprocmask,
        __NR_mkdirat, __NR_unlinkat, __NR_bind, __NR_connect, __NR_inotify_add_watch };
    enum { N = sizeof numbers / sizeof *numbers };
    struct sock_filter f[8 + N + 6];  /* rt_sigprocmask passes the SIGSYS test below only for numbers listed above, so it is trapped for every call */
    int n = 0;
#define OFF_NR 0
#define OFF_ARCH 4
#define OFF_IP_HI 12  /* instruction_pointer (8 bytes at 8), its upper word on a little-endian machine */
#define OFF_ARG0 16
    f[n++] = (struct sock_filter)BPF_STMT(BPF_LD | BPF_W | BPF_ABS, OFF_ARCH);
    f[n++] = (struct sock_filter)BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, AUDIT_ARCH_AARCH64, 1, 0);
    f[n++] = (struct sock_filter)BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_ALLOW);
    f[n++] = (struct sock_filter)BPF_STMT(BPF_LD | BPF_W | BPF_ABS, OFF_IP_HI);
    f[n++] = (struct sock_filter)BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, (unsigned)(base >> 32), 1, 0);
    f[n++] = (struct sock_filter)BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_ALLOW);
    f[n++] = (struct sock_filter)BPF_STMT(BPF_LD | BPF_W | BPF_ABS, OFF_NR);
    for (int i = 0; i < N; i++)  /* a match jumps to the trap, the filter's last instruction (N - i + 4 instructions on) */
        f[n++] = (struct sock_filter)BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, (unsigned)numbers[i], (unsigned char)(N - i + 4), 0);
    /* rt_sigaction of SIGSYS: its first argument is 31 */
    f[n++] = (struct sock_filter)BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, __NR_rt_sigaction, 1, 0);
    f[n++] = (struct sock_filter)BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_ALLOW);
    f[n++] = (struct sock_filter)BPF_STMT(BPF_LD | BPF_W | BPF_ABS, OFF_ARG0);
    f[n++] = (struct sock_filter)BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, SIGSYS, 1, 0);
    f[n++] = (struct sock_filter)BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_ALLOW);
    f[n++] = (struct sock_filter)BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_TRAP);
    struct sock_fprog prog = { .len = (unsigned short)n, .filter = f };
    long (*raw)(long, ...) = REAL(syscall);
    if (!raw) raw = syscall;
    if (raw(SYS_prctl, PR_SET_NO_NEW_PRIVS, 1L, 0L, 0L, 0L)) return -1;
    if (raw(SYS_seccomp, SECCOMP_SET_MODE_FILTER, SECCOMP_FILTER_FLAG_TSYNC, &prog) == 0) return 0;
    return raw(SYS_seccomp, SECCOMP_SET_MODE_FILTER, 0, &prog);  /* a thread with a filter of its own (TSYNC refuses): this thread and its children */
}

static void reserve_and_install(void)
{
    window_tried = true;
    const char *off = getenv("APKLAYER_RAW_SYSCALL_VIEW");
    if (off && !strcmp(off, "off")) return;
    /* a 4 GiB window no desktop mapping uses (the kernel's mmap area, the program and the JVM's heap are elsewhere), the upper word of the address is one compare */
    for (unsigned long hi = 0x40; hi <= 0x70; hi += 0x10) {
        void *p = mmap((void *)(hi << 32), WINDOW_SIZE, PROT_NONE, MAP_PRIVATE | MAP_ANONYMOUS | MAP_NORESERVE | MAP_FIXED_NOREPLACE, -1, 0);
        if (p != MAP_FAILED && (unsigned long)p == hi << 32) { window_base = window_next = (unsigned long)p; break; }
        if (p != MAP_FAILED) munmap(p, WINDOW_SIZE);
    }
    if (!window_base) { if (debug()) fprintf(stderr, "[raw-syscall] no free window: raw syscalls are not viewed\n"); return; }
    struct sigaction sa = {0};
    sa.sa_sigaction = on_sigsys;
    sa.sa_flags = SA_SIGINFO | SA_ONSTACK | SA_RESTART;
    sigemptyset(&sa.sa_mask);
    int (*real_sigaction)(int, const struct sigaction *, struct sigaction *) = REAL(sigaction);
    if (!real_sigaction || real_sigaction(SIGSYS, &sa, NULL) || install_filter(window_base)) {
        if (debug()) fprintf(stderr, "[raw-syscall] filter not installed (%s): raw syscalls are not viewed\n", strerror(errno));
        munmap((void *)window_base, WINDOW_SIZE);
        window_base = 0;
        return;
    }
    fast_paths_init();
    apklayer_raw_syscall_active = 1;
    if (debug()) fprintf(stderr, "[raw-syscall] guest libraries in %#lx..%#lx, raw syscalls viewed\n", window_base, window_base + WINDOW_SIZE);
}

/* The bionic linker's allocation of a library's address range (bionic_translation 0074): a page-aligned address in the window, 0 for
 * the kernel's choice (not an app process, no window, window full). */
void *apklayer_guest_window_take(size_t size)
{
    if (!apklayer_own_data_env()) return NULL;
    void *r = NULL;
    pthread_mutex_lock(&window_lock);
    if (!window_tried) reserve_and_install();
    size = (size + 0xffffUL) & ~0xffffUL;
    if (window_base && window_next + size <= window_base + WINDOW_SIZE) {
        r = (void *)window_next;
        window_next += size;
    }
    pthread_mutex_unlock(&window_lock);
    return r;
}

/* SIGSYS belongs to the filter: a program's handler (Go installs one for every signal) is accepted and never installed. */
int sigaction(int sig, const struct sigaction *act, struct sigaction *oact)
{
    int (*real)(int, const struct sigaction *, struct sigaction *) = REAL(sigaction);
    if (sig == SIGSYS && apklayer_raw_syscall_active && act) {
        if (oact) memset(oact, 0, sizeof *oact);
        return 0;
    }
    return real(sig, act, oact);
}
#else
void *apklayer_guest_window_take(size_t size) { (void)size; return NULL; }
int apklayer_raw_syscall_active;
#endif
