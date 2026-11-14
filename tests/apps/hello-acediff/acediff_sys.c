/* acediff, part 2: what HWCAP advertises beyond the crypto/NEON core (fp16, lrcpc, dc/ic), raw svc syscalls, threads, mrs readability, timing and call costs. */
#include "acediff.h"

/* ---- the rest of what HWCAP advertises (fphp/asimdhp, lrcpc, dcpop) and raw svc syscalls, threads, timing ---- */
static uint64_t HH(uint64_t h, float16x8_t v) { return H(h, vreinterpretq_u8_f16(v)); }
static float16x8_t rh(void) { uint16_t a[8]; for (int i = 0; i < 8; i++) { a[i] = (uint16_t)next(); if ((a[i] & 0x7c00) == 0x7c00) a[i] ^= 0x4000; } return vld1q_f16((__fp16 *)a); }
uint64_t t_fp16(void)
{
    uint64_t h = 0;
    LOOP(300) { float16x8_t a = rh(), b = rh(), c = rh();
        F(HH, vaddq_f16(a, b)); F(HH, vmulq_f16(a, b)); F(HH, vdivq_f16(a, b)); F(HH, vfmaq_f16(c, a, b)); F(HH, vsqrtq_f16(a)); F(HH, vmaxq_f16(a, b)); F(HH, vabsq_f16(a)); F(HH, vrecpeq_f16(a)); F(HH, vrndnq_f16(a));
        F(H, vreinterpretq_u8_s16(vcvtq_s16_f16(a))); F(HH, vcvtq_f16_s16(vreinterpretq_s16_f16(b))); F(H, vreinterpretq_u8_u16(vceqq_f16(a, b)));
        F(HF, vcvt_f32_f16(vget_high_f16(a))); F(HH, vcombine_f16(vcvt_f16_f32(rf(1)), vcvt_f16_f32(rf(1))));
        F(HD, vcvt_f64_f32(vget_low_f32(vcvt_f32_f16(vget_low_f16(c))))); }
    return h;
}
uint64_t t_misc(void)
{
    static uint64_t mem[8] __attribute__((aligned(64))); uint64_t h = 0, v, w;
    LOOP(50) { mem[i & 7] = next();
        __asm__ volatile(".arch_extension rcpc\n ldapr %0, [%1]\n ldaprb %w0, [%1]\n" : "=&r"(v) : "r"(&mem[i & 7]) : "memory"); FL("ldapr/ldaprb", v);
        __asm__ volatile("dc cvac, %0\n dc cvau, %0\n dc civac, %0\n dsb ish\n dmb ishld\n dmb ishst\n isb\n prfm pldl1keep, [%0]\n prfm pstl3strm, [%0, #64]\n" :: "r"(&mem[i & 7]) : "memory"); FL("dc/dsb/dmb/isb/prfm done", 1);
        __asm__ volatile("ic ivau, %0\n dsb ish\n isb\n" :: "r"(&mem[i & 7]) : "memory"); FL("ic ivau done", 1);
        __asm__ volatile("mrs %0, nzcv\n mrs %1, tpidr_el0" : "=r"(v), "=r"(w)); FL("tpidr_el0 nonzero", w != 0); (void)v; }
    __asm__ volatile("dc cvap, %0\n" :: "r"(&mem[0]) : "memory"); FL("dc cvap done", 1);
    return h;
}

long sc(long n, long a, long b, long c, long d, long e, long f)
{
    register long x8 __asm__("x8") = n, x0 __asm__("x0") = a, x1 __asm__("x1") = b, x2 __asm__("x2") = c, x3 __asm__("x3") = d, x4 __asm__("x4") = e, x5 __asm__("x5") = f;
    __asm__ volatile("svc #0" : "+r"(x0) : "r"(x8), "r"(x1), "r"(x2), "r"(x3), "r"(x4), "r"(x5) : "memory", "cc");
    return x0;
}
struct ksa { void *handler; unsigned long flags; void *restorer; unsigned long mask; };
static volatile int usr1_hits; static volatile long usr1_code;
static void on_usr1(int s, siginfo_t *si, void *uc) { (void)s; (void)uc; usr1_hits++; usr1_code = si->si_code; }
uint64_t t_svc(void)
{
    uint64_t h = 0; long r;
    /* raw rt_sigaction with the kernel's struct layout (handler, flags, restorer, mask), as an app with its own syscall stubs makes it */
    struct ksa n = {.handler = (void *)on_usr1, .flags = SA_SIGINFO | SA_RESTART | SA_NODEFER, .restorer = 0, .mask = 1ul << (SIGUSR2 - 1)}, o;
    memset(&o, 0xaa, sizeof o);
    r = sc(134, SIGUSR1, (long)&n, (long)&o, 8, 0, 0); FL("rt_sigaction set", r);
    memset(&o, 0xaa, sizeof o); r = sc(134, SIGUSR1, 0, (long)&o, 8, 0, 0); FL("rt_sigaction get ret", r); FL("get handler", o.handler == (void *)on_usr1); FL("get flags", o.flags & 0xfffffff); FL("get mask", o.mask); FL("get restorer null", o.restorer == 0);
    r = sc(134, SIGUSR1, (long)&n, 0, 16, 0, 0); FL("rt_sigaction bad sigsetsize", r); r = sc(134, 0, (long)&n, 0, 8, 0, 0); FL("rt_sigaction signal 0", r); r = sc(134, SIGKILL, (long)&n, 0, 8, 0, 0); FL("rt_sigaction SIGKILL", r);
    r = sc(131, getpid(), sc(178, 0, 0, 0, 0, 0, 0), SIGUSR1, 0, 0, 0); FL("tgkill self", r); FL("raw handler ran", usr1_hits); FL("si_code SI_TKILL", usr1_code == -6);
    r = sc(129, getpid(), SIGUSR1, 0, 0, 0, 0); FL("kill self", r); FL("raw handler ran 2", usr1_hits);
    uint64_t set = 1ull << (SIGUSR1 - 1), old = 7; r = sc(135, 0 /*SIG_BLOCK*/, (long)&set, (long)&old, 8, 0, 0); FL("sigprocmask block", r); FL("old mask was clear of usr1", !(old & set));
    r = sc(129, getpid(), SIGUSR1, 0, 0, 0, 0); FL("blocked: no delivery", usr1_hits); uint64_t pend = 0; r = sc(136, (long)&pend, 8, 0, 0, 0, 0); FL("rt_sigpending ret", r); FL("pending has usr1", !!(pend & set));
    r = sc(135, 1 /*SIG_UNBLOCK*/, (long)&set, 0, 8, 0, 0); FL("unblock delivers", usr1_hits); usleep(100000); FL("unblock delivers by 100 ms", usr1_hits);
    { sigset_t m, om; sigemptyset(&m); sigaddset(&m, SIGUSR1); int b = usr1_hits; pthread_sigmask(SIG_BLOCK, &m, &om); raise(SIGUSR1); FL("libc: blocked raise not delivered", usr1_hits - b); sigset_t pd; sigpending(&pd); FL("libc: sigpending has usr1", sigismember(&pd, SIGUSR1)); pthread_sigmask(SIG_SETMASK, &om, NULL); FL("libc: delivered at unblock", usr1_hits - b); usleep(100000); FL("libc: delivered by 100 ms", usr1_hits - b); }
    stack_t ss = {.ss_sp = altstack, .ss_size = sizeof altstack}, os; r = sc(132, (long)&ss, (long)&os, 0, 0, 0, 0); FL("sigaltstack set", r); memset(&os, 0, sizeof os); r = sc(132, 0, (long)&os, 0, 0, 0, 0); FL("sigaltstack get", r); FL("altstack same", os.ss_sp == altstack && os.ss_size == sizeof altstack);
    ss.ss_size = 100; r = sc(132, (long)&ss, 0, 0, 0, 0, 0); FL("sigaltstack too small (-ENOMEM)", r); ss.ss_flags = SS_DISABLE; sc(132, (long)&ss, 0, 0, 0, 0, 0);
    r = sc(167 /*prctl*/, 3 /*PR_GET_DUMPABLE*/, 0, 0, 0, 0, 0); FL("PR_GET_DUMPABLE", r); r = sc(167, 21 /*PR_GET_SECCOMP*/, 0, 0, 0, 0, 0); FL("PR_GET_SECCOMP", r); r = sc(167, 39 /*PR_GET_NO_NEW_PRIVS*/, 0, 0, 0, 0, 0); FL("PR_GET_NO_NEW_PRIVS", r);
    char nm[16] = "acediff-thr"; r = sc(167, 15 /*PR_SET_NAME*/, (long)nm, 0, 0, 0, 0); FL("PR_SET_NAME", r); char g[16] = {0}; sc(167, 16, (long)g, 0, 0, 0, 0); FL("PR_GET_NAME", !strcmp(g, "acediff-thr"));
    struct { char f[6][65]; } u; memset(&u, 0, sizeof u); r = sc(160, (long)&u, 0, 0, 0, 0, 0); FL("uname ret", r); FL("uname machine == aarch64", !strcmp(u.f[4], "aarch64")); FL("uname sysname", !strcmp(u.f[0], "Linux"));
    say("info uname: %s %s %s", u.f[0], u.f[2], u.f[4]);
    r = sc(278 /*getrandom*/, (long)nm, 8, 0, 0, 0, 0); FL("getrandom", r); struct timespec tsx; r = sc(113, 1, (long)&tsx, 0, 0, 0, 0); FL("clock_gettime ret", r);
    r = sc(123, getpid(), 128, (long)g, 0, 0, 0); FL("sched_getaffinity > 0", r > 0);
    /* futex wait with a 20 ms timeout: wakes after at least that long, with ETIMEDOUT */
    { uint32_t f = 0; struct timespec t0, t1, to = {0, 20000000}; clock_gettime(CLOCK_MONOTONIC, &t0); r = sc(98, (long)&f, 0 /*FUTEX_WAIT*/ | 128, 0, (long)&to, 0, 0); clock_gettime(CLOCK_MONOTONIC, &t1);
      long ms = (t1.tv_sec - t0.tv_sec) * 1000 + (t1.tv_nsec - t0.tv_nsec) / 1000000; FL("futex timeout ret (-ETIMEDOUT)", r); FL("futex waited 20..80 ms", ms >= 20 && ms < 80); }
    /* mmap/mprotect/mincore/madvise by svc */
    { long a = sc(222, 0, 8192, PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0); FL("svc mmap ok", a > 0); unsigned char vec[2]; r = sc(232, a, 8192, (long)vec, 0, 0, 0); FL("mincore", r);
      r = sc(226, a, 4096, PROT_READ, 0, 0, 0); FL("mprotect", r); r = sc(233, a, 8192, 4 /*MADV_DONTNEED*/, 0, 0, 0); FL("madvise", r); r = sc(215, a, 8192, 0, 0, 0, 0); FL("munmap", r); }
    /* ptrace(PTRACE_TRACEME) in a child: first call 0, second -EPERM, as the usual anti-debug check expects */
    { pid_t p = fork(); if (p == 0) { long r1 = sc(117, 0, 0, 0, 0, 0, 0), r2 = sc(117, 0, 0, 0, 0, 0, 0); _exit((r1 == 0) * 1 + (r2 == -EPERM) * 2 + 16); }
      int st = 0; waitpid(p, &st, 0); FL("ptrace TRACEME twice child status", WIFEXITED(st) ? WEXITSTATUS(st) : 100 + WTERMSIG(st)); }
    /* /proc/self/status TracerPid and the cpuinfo Features line */
    { FILE *f = fopen("/proc/self/status", "r"); char line[256]; long tp = -1; while (f && fgets(line, sizeof line, f)) if (!strncmp(line, "TracerPid:", 10)) tp = atol(line + 10); if (f) fclose(f); FL("TracerPid", tp);
      f = fopen("/proc/cpuinfo", "r"); while (f && fgets(line, sizeof line, f)) if (!strncmp(line, "Features", 8) || !strncmp(line, "CPU part", 8) || !strncmp(line, "CPU implementer", 15) || !strncmp(line, "BogoMIPS", 8)) { line[strcspn(line, "\n")] = 0; say("info cpuinfo %.200s", line); } if (f) fclose(f); }
    sigaction(SIGUSR1, &(struct sigaction){.sa_handler = SIG_DFL}, NULL);
    return h;
}

/* four threads: LSE and exclusive counters add up; a release/acquire flag hands a payload over */
static volatile uint64_t cnt_lse, cnt_llsc, payload, flag_ready, ack, bad_order;
static void *thr(void *a)
{
    uint64_t k = (uint64_t)a;
    for (int i = 0; i < 20000; i++) { uint64_t one = 1, o, t; uint32_t w;
        __asm__ volatile(".arch_extension lse\n ldaddal %1, %0, [%2]" : "=&r"(o) : "r"(one), "r"(&cnt_lse) : "memory");
        do { __asm__ volatile("ldaxr %0, [%2]\n add %0, %0, #1\n stlxr %w1, %0, [%2]" : "=&r"(t), "=&r"(w) : "r"(&cnt_llsc) : "memory"); } while (w); }
    if (k == 0) for (uint64_t i = 1; i <= 20000; i++) { payload = i; __atomic_store_n(&flag_ready, i, __ATOMIC_RELEASE); while (__atomic_load_n(&ack, __ATOMIC_ACQUIRE) != i) __asm__ volatile("yield"); }
    else if (k == 1) for (uint64_t i = 1; i <= 20000; i++) { while (__atomic_load_n(&flag_ready, __ATOMIC_ACQUIRE) != i) __asm__ volatile("yield"); if (payload != i) bad_order++; __atomic_store_n(&ack, i, __ATOMIC_RELEASE); }
    return NULL;
}
uint64_t t_threads(void)
{
    pthread_t t[4]; uint64_t h = 0;
    cnt_lse = cnt_llsc = payload = flag_ready = ack = bad_order = 0;
    for (long i = 0; i < 4; i++) pthread_create(&t[i], NULL, thr, (void *)i);
    for (int i = 0; i < 4; i++) pthread_join(t[i], NULL);
    FL("ldaddal total (80000)", cnt_lse); FL("ldaxr/stlxr total (80000)", cnt_llsc); FL("release/acquire order violations", bad_order);
    return h;
}
static double now_ns(void) { struct timespec t; clock_gettime(CLOCK_MONOTONIC, &t); return t.tv_sec * 1e9 + t.tv_nsec; }
static volatile uint64_t sink; static volatile int raised;
static void on_raise(int s) { (void)s; raised++; }
static void *nop_thread(void *a) { return a; }
#define COST(label, n, ...) do { double t0_ = now_ns(); for (int i_ = 0; i_ < (n); i_++) { __VA_ARGS__; } double dt_ = (now_ns() - t0_) / (n); say("info cost %-34s %9.1f ns", label, dt_); } while (0)
void costs(void)
{
    uint64_t v; static uint8_t pg[8192] __attribute__((aligned(4096))); signal(SIGUSR2, on_raise);
    COST("mrs cntvct (isb before)", 20000, { __asm__ volatile("isb\n mrs %0, cntvct_el0" : "=r"(v)); sink = v; });
    COST("mrs cntvct (no isb)", 20000, { __asm__ volatile("mrs %0, cntvct_el0" : "=r"(v)); sink = v; });
    COST("mrs midr_el1", 5000, { __asm__ volatile("mrs %0, midr_el1" : "=r"(v)); sink = v; });
    COST("libc clock_gettime", 20000, { sink = (uint64_t)now_ns(); });
    COST("svc getpid", 20000, { sink = sc(172, 0, 0, 0, 0, 0, 0); });
    COST("1000 dependent add/eor", 2000, { uint64_t a = sink; for (int k = 0; k < 1000; k++) __asm__ volatile("add %0, %0, #3\n eor %0, %0, %0, lsl #1" : "+r"(a)); sink = a; });
    COST("aese+aesmc x100", 2000, { uint8x16_t x = vdupq_n_u8((uint8_t)sink), k = vdupq_n_u8(7); for (int j = 0; j < 100; j++) x = vaesmcq_u8(vaeseq_u8(x, k)); sink = vgetq_lane_u8(x, 0); });
    COST("mprotect RX<->RW page", 2000, { mprotect(pg, 4096, PROT_READ | PROT_EXEC); mprotect(pg, 4096, PROT_READ | PROT_WRITE); });
    COST("mmap+munmap 1 page", 2000, { void *m = mmap(NULL, 4096, PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0); munmap(m, 4096); });
    COST("raise(SIGUSR2) + handler", 2000, { raise(SIGUSR2); });
    COST("pthread_create+join", 200, { pthread_t t; pthread_create(&t, NULL, nop_thread, NULL); pthread_join(t, NULL); });
    COST("open+read+close /proc/self/stat", 500, { int fd = open("/proc/self/stat", 0); char b[512]; sink = read(fd, b, sizeof b); close(fd); });
    COST("fork+wait (exit 0)", 50, { pid_t p = fork(); if (p == 0) _exit(0); waitpid(p, NULL, 0); });
}
void timing(void)
{
    uint64_t f, c0, c1; struct timespec t0, t1;
    __asm__ volatile("mrs %0, cntfrq_el0" : "=r"(f)); clock_gettime(CLOCK_MONOTONIC, &t0); __asm__ volatile("isb\n mrs %0, cntvct_el0" : "=r"(c0));
    do { clock_gettime(CLOCK_MONOTONIC, &t1); } while ((t1.tv_sec - t0.tv_sec) * 1000000000L + (t1.tv_nsec - t0.tv_nsec) < 100000000L);
    __asm__ volatile("isb\n mrs %0, cntvct_el0" : "=r"(c1));
    double ns = (t1.tv_sec - t0.tv_sec) * 1e9 + (t1.tv_nsec - t0.tv_nsec);
    say("info counter: cntfrq %llu Hz, measured %.0f Hz over %.1f ms (ratio %.3f)", (unsigned long long)f, (double)(c1 - c0) * 1e9 / ns, ns / 1e6, (double)(c1 - c0) * 1e9 / ns / (double)f);
    uint64_t a0, a1; uint8x16_t x = rv(), k = rv(); __asm__ volatile("isb\n mrs %0, cntvct_el0" : "=r"(a0)); for (int i = 0; i < 100000; i++) { x = vaesmcq_u8(vaeseq_u8(x, k)); } __asm__ volatile("isb\n mrs %0, cntvct_el0" : "=r"(a1));
    say("info timing: 100000 aese+aesmc took %llu ticks (%.1f us), result %llx", (unsigned long long)(a1 - a0), (double)(a1 - a0) * 1e6 / (double)f, (unsigned long long)vgetq_lane_u64(vreinterpretq_u64_u8(x), 0) & 0xf);
    armed = 1; uint64_t p; if (sigsetjmp(jb, 1) == 0) { __asm__ volatile("mrs %0, cntpct_el0" : "=r"(p)); say("info cntpct_el0 readable %d", p != 0); } else say("info cntpct_el0 SIGILL"); armed = 0;
}

uint64_t t_sysregs(void)
{
    uint64_t v = 0;
#define SR(name) do { if (sigsetjmp(jb, 1) == 0) { armed = 1; __asm__ volatile("mrs %0, " name : "=r"(v)); armed = 0; say("  mrs %-16s readable", name); } else { armed = 0; say("  mrs %-16s SIGILL", name); } } while (0)
    SR("cntpct_el0");
    SR("cntvct_el0");
    SR("cntfrq_el0");
    SR("midr_el1");
    SR("mpidr_el1");
    SR("revidr_el1");
    SR("id_aa64pfr0_el1");
    SR("id_aa64pfr1_el1");
    SR("id_aa64isar0_el1");
    SR("id_aa64isar1_el1");
    SR("id_aa64isar2_el1");
    SR("id_aa64mmfr0_el1");
    SR("id_aa64mmfr1_el1");
    SR("id_aa64mmfr2_el1");
    SR("id_aa64dfr0_el1");
    SR("id_aa64dfr1_el1");
    SR("id_aa64afr0_el1");
    SR("s3_0_c0_c4_4");
    SR("id_pfr0_el1");
    SR("id_isar0_el1");
    SR("ctr_el0");
    SR("dczid_el0");
    SR("currentel");
    SR("daif");
    SR("sctlr_el1");
    SR("tpidr_el0");
    SR("tpidrro_el0");
    SR("tpidr_el1");
    SR("pmccntr_el0");
    SR("pmcr_el0");
    SR("pmuserenr_el0");
    SR("pmselr_el0");
    SR("cntkctl_el1");
    SR("vbar_el1");
    SR("ttbr0_el1");
    SR("tcr_el1");
    SR("elr_el1");
    SR("spsr_el1");
    SR("sp_el0");
    SR("spsel");
    SR("nzcv");
    SR("fpcr");
    SR("fpsr");
    SR("s3_3_c4_c2_5");
    SR("s3_3_c4_c2_6");
    SR("s3_3_c4_c2_7");
    SR("pan");
    SR("uao");
    SR("mdscr_el1");
    SR("oslsr_el1");
    SR("dbgdtr_el0");
    SR("cntp_ctl_el0");
    SR("cntv_ctl_el0");
    SR("s3_3_c2_c4_0");
    SR("s3_3_c2_c4_1");
    return v & 0;
}
#ifdef __clang__
#pragma clang attribute pop
#endif
