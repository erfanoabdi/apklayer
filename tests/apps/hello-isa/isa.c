/* ISA feature / id register / HWCAP probe. ACE (anti-emulation) runs instructions that must or must not exist on the claimed CPU, reads id registers and compares them with
 * AT_HWCAP / cpuinfo. One line per probe: "op <name> OK" or "op <name> SIG<n>/<si_code>"; "reg <name> <value>|SIGILL"; hwcap, cpuinfo; then "CONSIST" lines where a claimed HWCAP bit
 * does not execute or an executing feature is not in HWCAP (the profile must claim exactly what runs; real hardware SIGILLs on what the claimed CPU lacks).
 * App hello.isa (JNI run, "[isa]" lines) or static program: aarch64-linux-android26-clang -DISA_MAIN -static isa.c (compare.sh). */
#define _GNU_SOURCE
#include <setjmp.h>
#include <signal.h>
#include <stdarg.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/auxv.h>
#include <sys/mman.h>
#include <unistd.h>

static char out[65536]; static size_t on;
static void say(const char *fmt, ...) __attribute__((format(printf, 1, 2)));
static void say(const char *fmt, ...) { char l[400]; va_list ap; va_start(ap, fmt); vsnprintf(l, sizeof l, fmt, ap); va_end(ap); if (on < sizeof out - 400) on += snprintf(out + on, sizeof out - on, "%s\n", l); }

static sigjmp_buf jb; static volatile int armed, gsig, gcode; static void *gaddr;
static void on_sig(int s, siginfo_t *si, void *uc) { (void)uc; if (armed) { gsig = s; gcode = si->si_code; gaddr = si->si_addr; siglongjmp(jb, 1); } _exit(99); }

#ifdef __clang__
#pragma clang attribute push(__attribute__((target("+lse,+rcpc,+rcpc-immo,+sve2,+sme,+sme2,+bf16,+i8mm,+sb,+ssbs,+dit,+pauth,+mte,+rand,+flagm,+altnzcv,+fptoint,+jsconv,+complxnum,+fullfp16,+fp16fml,+dotprod,+rdm,+crc,+aes,+sha2,+sha3,+sm4,+predres,+wfxt,+mops,+hbc,+cssc,+rcpc3,+lse128,+tme,+ls64,+bti,+ccpp,+ccdp,+dpb,+dpb2,+ras,+f32mm,+f64mm,+specrestrict,+lor,+pan-rwv,+uaops,+vh"))), apply_to = function)
#endif
/* X(name, hwcap word (1 = AT_HWCAP, 2 = AT_HWCAP2, 0 = behaviour only), bit, asm). x8 = x1 = x4 = scratch buffer, x2 = 1, x3 = 3 (set by S); asm of a probe must not need more. */
#define S "mov x8, %0\n mov x1, x8\n mov x4, x8\n mov x2, #1\n mov x3, #3\n"
#define PROBES \
 X(lse_cas,1,8, S "mov x2,#0\n casal x2, x3, [x8]\n") X(lse_add,1,8, S "ldaddal x2, x3, [x8]\n") X(lse_swp,1,8, S "swp x2, x3, [x8]\n") X(casp,1,8, S "mov x2,#0\n mov x3,#0\n mov x4,#0\n mov x5,#0\n casp x2, x3, x4, x5, [x8]\n") \
 X(lse128,2,-1, S "swpp x2, x3, [x8]\n") X(lrcpc_ldapr,1,15, S "ldapr x2, [x8]\n") X(lrcpc2_ldapur,1,26, S "ldapur x2, [x8, #1]\n") X(lrcpc3_ldiapp,2,-1, S "ldiapp x2, x3, [x8]\n") \
 X(crc32,1,7, S "crc32w w2, w2, w3\n crc32cx w2, w2, x3\n") X(aes,1,3, S "aese v0.16b, v1.16b\n") X(pmull,1,4, S "pmull v0.1q, v1.1d, v2.1d\n") X(pmull8,0,0, S "pmull v0.8h, v1.8b, v2.8b\n") \
 X(sha1,1,5, S "sha1c q0, s1, v2.4s\n") X(sha2,1,6, S "sha256h q0, q1, v2.4s\n") X(sha512,1,21, S "sha512h q0, q1, v2.2d\n") X(sha3,1,17, S "eor3 v0.16b, v1.16b, v2.16b, v3.16b\n") \
 X(sm3,1,18, S "sm3ss1 v0.4s, v1.4s, v2.4s, v3.4s\n") X(sm4,1,19, S "sm4e v0.4s, v1.4s\n") X(dotprod,1,20, S "sdot v0.4s, v1.16b, v2.16b\n") \
 X(fp16_scalar,1,9, S "fadd h0, h1, h2\n") X(fp16_vector,1,10, S "fadd v0.8h, v1.8h, v2.8h\n") X(fhm,1,23, S "fmlal v0.4s, v1.4h, v2.4h\n") X(rdm,1,12, S "sqrdmlah v0.4s, v1.4s, v2.4s\n") \
 X(fcma,1,14, S "fcmla v0.4s, v1.4s, v2.4s, #90\n") X(jscvt,1,13, S "fjcvtzs w2, d0\n") X(flagm_cfinv,1,27, S "cfinv\n") X(flagm_setf8,1,27, S "setf8 w2\n") \
 X(flagm2_axflag,2,7, S "axflag\n") X(flagm2_xaflag,2,7, S "xaflag\n") X(frint32z,2,8, S "frint32z s0, s1\n") X(bf16_dot,2,14, S "bfdot v0.4s, v1.8h, v2.8h\n") X(bf16_cvt,2,14, S "bfcvt h0, s1\n") \
 X(i8mm,2,13, S "smmla v0.4s, v1.16b, v2.16b\n") X(sb,1,29, S "sb\n") X(ssbs_msr,1,28, S "msr ssbs, #1\n") X(dit_msr,1,24, S "msr dit, #1\n") X(dgh,2,15, S "hint #6\n") \
 X(pauth_pacia,1,30, S "pacia x2, x3\n") X(pauth_autia,1,30, S "autia x2, x3\n") X(pauth_xpaci,1,30, S "xpaci x2\n") X(pauth_pacga,1,31, S "pacga x2, x3, x4\n") X(pauth_ldraa,1,30, S "ldraa x2, [x8]\n") \
 X(mte_irg,2,18, S "irg x2, x3\n") X(mte_ldg,2,18, S "ldg x2, [x8]\n") X(mte_dc_gva,2,18, S "dc gva, x8\n") X(rng_rndr,2,16, S "mrs x2, s3_3_c2_c4_0\n") X(rng_rndrrs,2,16, S "mrs x2, s3_3_c2_c4_1\n") \
 X(sve_ptrue,1,22, S "ptrue p0.b\n") X(sve_rdvl,1,22, S "rdvl x2, #1\n") X(sve2,2,1, S "ptrue p0.b\n sqrdmlah z0.b, z1.b, z2.b\n") X(sme_rdsvl,2,23, S "rdsvl x2, #1\n") X(sme_smstart,2,23, S "smstart\n smstop\n") \
 X(cssc_abs,2,34, S "abs x2, x3\n") X(cssc_cnt,2,34, S "cnt x2, x3\n") X(wfet,2,31, S "mrs x2, cntvct_el0\n wfet x2\n") X(mops_setp,2,43, S "mov x2, x8\n mov x3, #4\n setp [x2]!, x3!, xzr\n") \
 X(mops_cpyfp,2,43, S "cpyfp [x4]!, [x8]!, x3!\n") X(hbc,2,44, S "bc.eq 1f\n 1:\n") X(dc_cvap,1,16, S "dc cvap, x8\n") X(dc_cvadp,2,0, S "dc cvadp, x8\n") X(st64b,0,0, S "st64b x4, [x8]\n") X(tme,0,0, S "tstart x2\n") \
 X(dc_civac,0,0, S "dc civac, x8\n") X(dc_cvau,0,0, S "dc cvau, x8\n") X(dc_cvac,0,0, S "dc cvac, x8\n") X(dc_zva,0,0, S "dc zva, x8\n") X(dc_ivac,0,0, S "dc ivac, x8\n") X(dc_isw,0,0, S "dc isw, x8\n") \
 X(ic_ivau,0,0, S "ic ivau, x8\n") X(ic_iallu,0,0, S "ic iallu\n") X(ic_ialluis,0,0, S "ic ialluis\n") X(isb,0,0, S "isb\n") X(dsb_ssbb,0,0, S "dsb #0\n") X(dsb_pssbb,0,0, S "dsb #4\n") \
 X(barriers,0,0, S "dsb nsh\n dsb ishst\n dmb ish\n dmb ld\n dmb oshst\n dmb #0\n") X(hints,0,0, S "hint #16\n hint #17\n hint #18\n hint #20\n hint #34\n hint #127\n hint #64\n bti jc\n paciasp\n autiasp\n") \
 X(yield_wfe_sev,0,0, S "yield\n sev\n sevl\n wfe\n") X(wfi,0,0, S "wfi\n") X(prfm,0,0, S "prfm pldl1keep, [x8]\n prfm pstl3strm, [x8, #8]\n prfm #31, [x8]\n prfum pldl2keep, [x8, #1]\n prfm pldl1keep, 1f\n 1:\n") \
 X(ldp_unaligned,0,0, S "add x5, x8, #1\n ldp x2, x3, [x5]\n") X(stp_unaligned,0,0, S "add x5, x8, #1\n stp x2, x3, [x5]\n") X(ldxr_unaligned,0,0, S "add x5, x8, #1\n ldxr x2, [x5]\n") X(ldar_unaligned,0,0, S "add x5, x8, #1\n ldar x2, [x5]\n") \
 X(ldadd_unaligned,0,0, S "add x5, x8, #1\n ldadd x2, x3, [x5]\n") X(ldaxp_stlxp,0,0, S "ldaxp x2, x3, [x8]\n stlxp w5, x2, x3, [x8]\n") X(ldxr_stxr,0,0, S "ldxr x2, [x8]\n stxr w5, x2, [x8]\n") \
 X(clrex,0,0, S "clrex\n clrex #5\n") X(ldxr_sizes,0,0, S "ldxr w2, [x8]\n ldxrh w2, [x8]\n ldxrb w2, [x8]\n") \
 X(msr_nzcv,0,0, S "mrs x2, nzcv\n msr nzcv, x2\n") X(msr_fpcr,0,0, S "mrs x2, fpcr\n msr fpcr, x2\n") X(msr_fpsr,0,0, S "mrs x2, fpsr\n msr fpsr, x2\n") X(msr_tpidr,0,0, S "mrs x2, tpidr_el0\n msr tpidr_el0, x2\n") \
 X(mrs_tpidrro,0,0, S "mrs x2, tpidrro_el0\n") X(msr_tpidrro,0,0, S "msr s3_3_c13_c0_3, x2\n") X(msr_daifset,0,0, S "msr daifset, #2\n") X(mrs_daif,0,0, S "mrs x2, daif\n") X(msr_spsel,0,0, S "msr spsel, #1\n") \
 X(mrs_currentel,0,0, S "mrs x2, currentel\n") X(msr_pan,0,0, S "msr pan, #1\n") X(msr_uao,0,0, S "msr uao, #1\n") X(mrs_ssbs,0,0, S "mrs x2, ssbs\n") X(mrs_dit,0,0, S "mrs x2, dit\n") X(mrs_tco,0,0, S "mrs x2, tco\n") \
 X(mrs_cntvct,0,0, S "mrs x2, cntvct_el0\n") X(mrs_cntpct,0,0, S "mrs x2, cntpct_el0\n") X(mrs_cntfrq,0,0, S "mrs x2, cntfrq_el0\n") X(mrs_cntkctl,0,0, S "mrs x2, cntkctl_el1\n") X(mrs_cntv_ctl,0,0, S "mrs x2, cntv_ctl_el0\n") \
 X(mrs_pmccntr,0,0, S "mrs x2, pmccntr_el0\n") X(mrs_pmcr,0,0, S "mrs x2, pmcr_el0\n") X(mrs_pmuserenr,0,0, S "mrs x2, pmuserenr_el0\n") X(mrs_sctlr,0,0, S "mrs x2, sctlr_el1\n") X(mrs_vbar,0,0, S "mrs x2, vbar_el1\n") \
 X(mrs_ttbr0,0,0, S "mrs x2, ttbr0_el1\n") X(mrs_esr,0,0, S "mrs x2, esr_el1\n") X(mrs_sp_el0,0,0, S "mrs x2, sp_el0\n") X(mrs_actlr,0,0, S "mrs x2, actlr_el1\n") X(mrs_cpacr,0,0, S "mrs x2, cpacr_el1\n") \
 X(mrs_mdscr,0,0, S "mrs x2, mdscr_el1\n") X(mrs_oslsr,0,0, S "mrs x2, oslsr_el1\n") X(mrs_lorid,0,0, S "mrs x2, lorid_el1\n") X(mrs_gmid,0,0, S "mrs x2, gmid_el1\n") X(mrs_zcr,0,0, S "mrs x2, s3_0_c1_c2_0\n") \
 X(mrs_ctr,0,0, S "mrs x2, ctr_el0\n") X(mrs_dczid,0,0, S "mrs x2, dczid_el0\n") X(mrs_midr,0,0, S "mrs x2, midr_el1\n") X(mrs_mpidr,0,0, S "mrs x2, mpidr_el1\n") X(mrs_revidr,0,0, S "mrs x2, revidr_el1\n") \
 X(mrs_aidr,0,0, S "mrs x2, aidr_el1\n") X(mrs_ccsidr,0,0, S "mrs x2, ccsidr_el1\n") X(mrs_clidr,0,0, S "mrs x2, clidr_el1\n") X(mrs_csselr,0,0, S "mrs x2, csselr_el1\n") \
 X(brk,0,0, S "brk #0x123\n") X(hlt,0,0, S "hlt #0x123\n") X(udf,0,0, S "udf #0x123\n") X(svc_gettid,0,0, "mov x8, #178\n svc #0\n") X(hvc,0,0, S "hvc #0\n") X(smc,0,0, S "smc #0\n") X(eret,0,0, S "eret\n") \
 X(und_zero,0,0, ".inst 0x00000000\n") X(und_ffff,0,0, ".inst 0xffffffff\n") X(und_movz_hw2,0,0, ".inst 0x52c00000\n") X(und_sys_c15,0,0, S "sys #7, c15, c15, #7, x2\n") X(und_sys_dc_bad,0,0, S "sys #3, c7, c11, #7, x8\n") \
 X(unp_ldp_same,0,0, S ".inst 0xa9400902\n") X(unp_ldr_wb_same,0,0, S "mov x2, x8\n .inst 0xf8408442\n") X(unp_stxr_same,0,0, S ".inst 0xc8027d02\n") X(unp_ldxp_same,0,0, S ".inst 0xc87f0902\n") \
 X(div_zero,0,0, S "mov x3, #0\n sdiv x2, x2, x3\n udiv x2, x2, x3\n") X(fcvt_zero,0,0, S "fmov d0, xzr\n fcvtzs x2, d0\n fcvtzu w2, s0\n") X(fp16_conv,0,0, S "fcvt h0, d1\n fcvtn v0.4h, v1.4s\n fcvtl v0.4s, v1.4h\n fmov h0, #1.0\n") \
 X(misc_int,0,0, S "ccmp x2, x3, #4, eq\n rbit x2, x3\n clz x2, x3\n cls x2, x3\n rev x2, x3\n rev16 x2, x3\n rev32 x2, x3\n smaddl x2, w2, w3, x4\n umulh x2, x3, x4\n extr x2, x3, x4, #7\n")
#define X(n, w, b, a) static __attribute__((noinline)) void p_##n(void *buf) { __asm__ volatile(a ::"r"(buf) : "memory", "cc", "x1", "x2", "x3", "x4", "x5", "x8", "v0", "v1", "v2", "v3", "v4", "v5"); }
PROBES
#undef X
#ifdef __clang__
#pragma clang attribute pop
#endif

static const char *H1[32] = {"fp","asimd","evtstrm","aes","pmull","sha1","sha2","crc32","atomics","fphp","asimdhp","cpuid","asimdrdm","jscvt","fcma","lrcpc","dcpop","sha3","sm3","sm4","asimddp","sha512","sve","asimdfhm","dit","uscat","ilrcpc","flagm","ssbs","sb","paca","pacg"};
static const char *H2[45] = {"dcpodp","sve2","sveaes","svepmull","svebitperm","svesha3","svesm4","flagm2","frint","svei8mm","svef32mm","svef64mm","svebf16","i8mm","bf16","dgh","rng","bti","mte","ecv","afp","rpres","mte3","sme","sme_i16i64","sme_f64f64","sme_i8i32","sme_f16f32","sme_b16f32","sme_f32f32","sme_fa64","wfxt","ebf16","sve_ebf16","cssc","rprfm","sve2p1","sme2","sme2p1","sme_i16i32","sme_bi32i32","sme_b16b16","sme_f16f16","mops","hbc"};
struct probe { const char *name; void (*f)(void *); int word, bit; };
static const struct probe probes[] = {
#define X(n, w, b, a) {#n, p_##n, w, b},
PROBES
#undef X
};
static char *scratch, *scratch_ro;
/* signal frames: what a handler sees for a fault the CPU raised (the real kernel's frame: siginfo, pc, fault_address, pstate, the __reserved chain with its esr_context) */
#include <ucontext.h>
static volatile int f_sig, f_code; static void *f_addr; static unsigned long f_pc, f_fault, f_pstate, f_sp; static unsigned f_esr; static char f_chain[160];
static void on_frame(int s, siginfo_t *si, void *ucv)
{
    ucontext_t *uc = ucv; f_sig = s; f_code = si->si_code; f_addr = si->si_addr; f_pc = uc->uc_mcontext.pc; f_fault = uc->uc_mcontext.fault_address; f_pstate = uc->uc_mcontext.pstate; f_sp = uc->uc_mcontext.sp; f_esr = 0; f_chain[0] = 0;
    unsigned char *p = (unsigned char *)uc->uc_mcontext.__reserved; size_t n = 0;
    for (int i = 0; i < 6; i++) { uint32_t magic = *(uint32_t *)p, size = *(uint32_t *)(p + 4); n += snprintf(f_chain + n, sizeof f_chain - n, "%x:%u ", magic, size); if (magic == 0x45535201) f_esr = *(uint32_t *)(p + 8); if (!magic || !size) break; p += size; }
    siglongjmp(jb, 1);
}
static __attribute__((noinline)) void fr_udf(void) { __asm__ volatile("cmp xzr, xzr\n .inst 0x00000000\n" ::: "cc"); }
static __attribute__((noinline)) void fr_hlt(void) { __asm__ volatile("cmp xzr, xzr\n hlt #1\n" ::: "cc"); }
static __attribute__((noinline)) void fr_brk(void) { __asm__ volatile("cmp xzr, xzr\n brk #1\n" ::: "cc"); }
static __attribute__((noinline)) void fr_mrs(void) { unsigned long v; __asm__ volatile("cmp xzr, xzr\n mrs %0, sctlr_el1\n" : "=r"(v)::"cc"); }
static __attribute__((noinline)) void fr_load(void) { volatile long *p = (long *)0x10; __asm__ volatile("cmp xzr, xzr\n ldr x2, [%0]\n" ::"r"(p) : "x2", "cc"); }
static __attribute__((noinline)) void fr_store(void) { volatile long *p = (long *)scratch_ro; __asm__ volatile("cmp xzr, xzr\n str x2, [%0]\n" ::"r"(p) : "cc", "memory"); }
static __attribute__((noinline)) void fr_unalign_excl(void) { char *p = scratch + 1; __asm__ volatile("cmp xzr, xzr\n ldaxr x2, [%0]\n" ::"r"(p) : "x2", "cc", "memory"); }
static __attribute__((noinline)) void fr_unalign_atomic(void) { char *p = scratch + 1; __asm__ volatile("cmp xzr, xzr\n mov x8, %0\n .inst 0xf8220103\n" ::"r"(p) : "x2", "x3", "x8", "cc", "memory"); }  /* ldadd x2, x3, [x8] */
static __attribute__((noinline)) void fr_exec(void) { void (*f)(void) = (void (*)(void))0x1000; f(); }
static __attribute__((noinline)) void fr_exec_nx(void) { void (*f)(void) = (void (*)(void))scratch; f(); }
static void frames(void)
{
    struct sigaction sa = {.sa_sigaction = on_frame, .sa_flags = SA_SIGINFO | SA_NODEFER}; sigemptyset(&sa.sa_mask);
    int sg[] = {SIGILL, SIGSEGV, SIGBUS, SIGFPE, SIGTRAP}; for (int i = 0; i < 5; i++) sigaction(sg[i], &sa, NULL);
    scratch_ro = mmap(NULL, 4096, PROT_READ, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    struct { const char *n; void (*f)(void); } t[] = {{"udf", fr_udf}, {"hlt", fr_hlt}, {"brk", fr_brk}, {"mrs-sctlr", fr_mrs}, {"load-0x10", fr_load}, {"store-ro", fr_store}, {"ldaxr-unaligned", fr_unalign_excl}, {"ldadd-unaligned", fr_unalign_atomic}, {"exec-0x1000", fr_exec}, {"exec-nx", fr_exec_nx}};
    for (size_t i = 0; i < sizeof t / sizeof *t; i++) {
        f_sig = 0; memset(scratch, 0, 64);
        if (sigsetjmp(jb, 1) == 0) { armed = 0; t[i].f(); say("frame %-16s no signal", t[i].n); continue; }
        unsigned long fn = (unsigned long)t[i].f;
#define WHERE(a) ((a) - fn < 64 ? "fn+" : (a) == 0 ? "0" : (a) < 0x100000 ? "low" : "other")
        say("frame %-16s sig %d code %d addr %s pc %s fault_address %s pstate %08lx chain %s esr %08x", t[i].n, f_sig, f_code, WHERE((unsigned long)f_addr), WHERE(f_pc), WHERE(f_fault), f_pstate, f_chain, f_esr);
    }
}
#define REG(name) do { uint64_t v = 0; if (sigsetjmp(jb, 1) == 0) { armed = 1; __asm__ volatile("mrs %0, " name : "=r"(v)); armed = 0; say("reg %-20s %016llx", name, (unsigned long long)v); } else { armed = 0; say("reg %-20s SIGILL", name); } } while (0)

const char *isa_run(void)
{
    on = 0; out[0] = 0;
    struct sigaction sa = {.sa_sigaction = on_sig, .sa_flags = SA_SIGINFO}; sigemptyset(&sa.sa_mask);
    int sg[] = {SIGILL, SIGSEGV, SIGBUS, SIGFPE, SIGTRAP}; for (int i = 0; i < 5; i++) sigaction(sg[i], &sa, NULL);
    scratch = mmap(NULL, 8192, PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    unsigned long h1 = getauxval(AT_HWCAP), h2 = getauxval(AT_HWCAP2);
    say("hwcap %016lx hwcap2 %016lx", h1, h2);
    { char l[800] = "hwcap names:"; for (int i = 0; i < 32; i++) if ((h1 >> i) & 1) { strcat(l, " "); strcat(l, H1[i]); } say("%s", l);
      strcpy(l, "hwcap2 names:"); for (int i = 0; i < 45; i++) if ((h2 >> i) & 1) { strcat(l, " "); strcat(l, H2[i]); } say("%s", l); }
    say("platform %s", getauxval(AT_PLATFORM) ? (const char *)getauxval(AT_PLATFORM) : "(null)");
    { FILE *f = fopen("/proc/cpuinfo", "r"); char line[512]; int n = 0; while (f && fgets(line, sizeof line, f)) if (!strncmp(line, "Features", 8) || !strncmp(line, "CPU ", 4) || !strncmp(line, "BogoMIPS", 8)) { line[strcspn(line, "\n")] = 0; if (n++ < 12) say("cpuinfo %.300s", line); } if (f) fclose(f); }
    REG("midr_el1"); REG("mpidr_el1"); REG("revidr_el1"); REG("ctr_el0"); REG("dczid_el0"); REG("cntfrq_el0");
    REG("id_aa64isar0_el1"); REG("id_aa64isar1_el1"); REG("s3_0_c0_c6_2"); REG("id_aa64pfr0_el1"); REG("id_aa64pfr1_el1"); REG("id_aa64mmfr0_el1"); REG("id_aa64mmfr1_el1"); REG("id_aa64mmfr2_el1");
    REG("id_aa64dfr0_el1"); REG("s3_0_c0_c5_1"); REG("s3_0_c0_c5_4"); REG("s3_0_c0_c4_4"); REG("s3_0_c0_c4_5"); REG("s3_0_c0_c4_2"); REG("id_pfr0_el1"); REG("id_dfr0_el1"); REG("id_isar0_el1"); REG("id_mmfr0_el1"); REG("mvfr0_el1");
    int n = sizeof probes / sizeof *probes; int ran[n];
    for (int i = 0; i < n; i++) {
        memset(scratch, 0, 256); ran[i] = 0;
        if (sigsetjmp(jb, 1) == 0) { armed = 1; probes[i].f(scratch); armed = 0; ran[i] = 1; say("op %-18s OK", probes[i].name); }
        else { armed = 0; say("op %-18s SIG%d/%d%s", probes[i].name, gsig, gcode, (char *)gaddr >= (char *)probes[i].f && (char *)gaddr < (char *)probes[i].f + 512 ? "" : gsig == SIGSEGV || gsig == SIGBUS ? "" : " addr?"); }
    }
    for (int i = 0; i < n; i++) {  /* claimed vs executing (bit -1: no HWCAP bit exists, never claimed) */
        const struct probe *p = &probes[i]; if (!p->word || (p->word == 1 && p->bit == 0 && p->name[0] != 'f')) continue;
        if (p->word == 1 && p->bit == 0) continue;
        int claimed = p->bit < 0 ? 0 : (int)(((p->word == 1 ? h1 : h2) >> p->bit) & 1);
        const char *nm = p->bit < 0 ? "none" : p->word == 1 ? H1[p->bit] : H2[p->bit];
        if (claimed && !ran[i]) say("CONSIST %-18s HWCAP%d %s claimed but the instruction does not execute", p->name, p->word, nm);
        if (!claimed && ran[i]) say("CONSIST %-18s executes but HWCAP%d %s is not claimed", p->name, p->word, nm);
    }
    frames();
    say("DONE");
    return out;
}
#if defined(__ANDROID__) && !defined(ISA_MAIN)
#include <jni.h>
JNIEXPORT jstring JNICALL Java_hello_isa_Main_run(JNIEnv *env, jclass c) { (void)c; return (*env)->NewStringUTF(env, isa_run()); }
#else
int main(void) { fputs(isa_run(), stdout); return 0; }
#endif
