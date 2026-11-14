/* Raw syscalls of guest code on native arm64 (docs/PLAN.md "Raw syscalls of guest code", PATCHES: bionic_translation 0072).
 * An `svc #0` in a library the bionic linker loaded (an app's anti-tamper code, a Go runtime, a static libc) reaches the kernel past every
 * view the libc wrappers and the path layer give: it saw the desktop uid and the JVM in /proc/self/maps. The bionic linker places the
 * libraries it loads in one 4 GiB window (apklayer_guest_window_take, below); one seccomp-bpf filter turns the few syscalls that carry
 * identity into SIGSYS when the instruction pointer is in that window, and the handler answers them through the views the libc path has
 * (syscall() of paths_preload.c: the path layer, the /proc projections; apklayer_app_uid and groups: paths.c). Host code (the JVM, glibc, bionic's libc here)
 * is outside the window: the filter costs it one compare. Only the app process (APKLAYER_OWN_DATA) gets the window; x86_64 hosts translate
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
#define WINDOW_SIZE (1UL << 32)
#define REAL(name) ({ static void *f_; if (!f_) f_ = dlsym(RTLD_NEXT, #name); f_; })

const char *apklayer_own_data_env(void);
long apklayer_app_uid(void);
int apklayer_app_groups(gid_t *out, size_t capacity);
__attribute__((weak)) int apklayer_uname_view(struct utsname *u);  /* the uname view of the process, when something provides one */

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
        if (r == 0 && apklayer_uname_view) apklayer_uname_view((struct utsname *)a[0]);
        return r < 0 ? -errno : r;
    case __NR_rt_sigaction:  /* SIGSYS only (the filter): the handler is ours, the guest's is never installed (a Go runtime installs its own through the kernel) */
        if (a[3] && a[2]) memset((void *)a[2], 0, (size_t)a[3] < 32 ? (size_t)a[3] : 32);  /* the old action reads as the default */
        return 0;
    default:
        break;
    }
    /* openat, readlinkat: syscall() of the path layer's preload: the path mapping, the /proc projections */
    r = syscall(nr, a[0], a[1], a[2], a[3], a[4], a[5]);
    return r == -1 ? -errno : r;
}

static void on_sigsys(int sig, siginfo_t *info, void *context)
{
    (void)sig;
    ucontext_t *uc = context;
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
    long r = guest_syscall(nr, a);
    uc->uc_mcontext.regs[0] = (unsigned long)r;
    errno = saved;
}

/* the filter: in the window and one of the numbers: trap; everything else runs */
static int install_filter(unsigned long base)
{
    static const int numbers[] = { __NR_getuid, __NR_geteuid, __NR_getgid, __NR_getegid, __NR_getresuid, __NR_getresgid, __NR_getgroups,
        __NR_openat, __NR_readlinkat, __NR_uname };
    enum { N = sizeof numbers / sizeof *numbers };
    struct sock_filter f[8 + N + 6];
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
    apklayer_raw_syscall_active = 1;
    if (debug()) fprintf(stderr, "[raw-syscall] guest libraries in %#lx..%#lx, raw syscalls viewed\n", window_base, window_base + WINDOW_SIZE);
}

/* The bionic linker's allocation of a library's address range (bionic_translation 0072): a page-aligned address in the window, 0 for
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
