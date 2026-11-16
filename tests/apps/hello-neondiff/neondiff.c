/* Instruction-form differential test. Every case (cases.inc, made by gen.py: one arm64 instruction form each) runs N times on deterministic pseudo-random inputs (edge values
 * mixed in) with the vector registers v0-v7, x0-x3, NZCV and a 512 byte buffer around x4 loaded from the input; v0-v7, x0-x3, NZCV, FPSR, the base register's move and the buffer are
 * hashed after every run. Output "<case> <hash>"; the same program under interpret-only, lite, two-gear (BERBERIS_GEARUP_POLICY=legacy BERBERIS_GEARUP_MIN_INSNS=0: every hot
 * region heavy) and on real arm64 must print the same hashes. Options (system property debug.neondiff on Android, argv natively): n=<runs> pre=<name prefix>[,..] v=<case>
 * (dump every input and output of that case). App hello.neondiff, or static: aarch64-linux-android26-clang -DNEONDIFF_MAIN -static neondiff.c kernels.c (compare.sh). */
#define _GNU_SOURCE
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <setjmp.h>
#include <time.h>
#include <signal.h>
#ifdef __ANDROID__
#include <sys/system_properties.h>
#endif

#pragma clang attribute push(__attribute__((target("+fp16,+dotprod,+aes,+sha2,+crc,+lse,+rcpc,+fp16fml,+jsconv,+rdm,+sha3,+sm4,+complxnum,+fptoint,+flagm,+rcpc-immo"))), apply_to = function)
struct io { uint8_t v[8][16]; uint64_t x[4]; uint64_t nzcv, fpsr, off, pad; uint8_t mem[512]; } __attribute__((aligned(64)));
_Static_assert(sizeof(struct io) == 704, "layout");

static uint64_t rnd(uint64_t *s) { uint64_t x = *s; x ^= x >> 12; x ^= x << 25; x ^= x >> 27; *s = x; return x * 0x2545F4914F6CDD1Dull; }
static const uint32_t I32[] = {0, 1, 2, 0x7f, 0x80, 0xff, 0x7fff, 0x8000, 0xffff, 0x7fffffff, 0x80000000, 0xffffffff, 0x55555555, 0xaaaaaaaa, 0x01010101, 0xfefefefe};
static const uint32_t F32[] = {0, 0x80000000, 0x3f800000, 0xbf800000, 0x7f800000, 0xff800000, 0x7fc00000, 0x7fa00000, 0xffc00001, 1, 0x007fffff, 0x7f7fffff, 0x3f000000, 0x40200000, 0xc0200000, 0x4b000000, 0x3effffff, 0x477fff00};
static const uint32_t F64H[] = {0x3ff00000, 0x7ff00000, 0x7ff80000, 0, 0x80000000, 0x40040000, 0x43e00000, 0xbff00000, 0x000fffff, 0x7fefffff, 0x7ff40000, 0xfff00000};
#define PICK(t, r) (t[(r) % (sizeof t / sizeof t[0])])

static uint64_t lane64(uint64_t *s) {
  uint64_t r = rnd(s), q = rnd(s);
  switch ((r >> 60) & 15) {
    case 4: return (uint64_t)PICK(I32, q) | (uint64_t)PICK(I32, q >> 20) << 32;
    case 5: return (uint64_t)PICK(F32, q) | (uint64_t)PICK(F32, q >> 20) << 32;
    case 6: return (uint64_t)PICK(F64H, q) << 32 | ((q & 1) ? 0 : (q >> 8) & 0xffffffff);
    case 7: return q & 0x0f0f0f0f0f0f0f0full;
    case 8: return (q & 0xff) * 0x0101010101010101ull;
    case 9: { float a = (float)((int32_t)q >> 10) / 1024.0f, b = (float)((int32_t)(q >> 32) >> 10) / 1024.0f; uint32_t x, y; memcpy(&x, &a, 4); memcpy(&y, &b, 4); return x | (uint64_t)y << 32; }
    case 10: { double d = (double)((int64_t)q >> 20) / 65536.0; uint64_t x; memcpy(&x, &d, 8); return x; }
    case 11: { uint64_t o = 0; for (int i = 0; i < 4; i++) o |= (uint64_t)((q >> (i * 16) & 0x83ff) | ((8 + ((q >> (i * 3 + 7)) & 15)) << 10)) << (i * 16); return o; }
    default: return q;
  }
}

static __attribute__((noinline)) void gen(struct io *in, uint64_t *s) {
  uint64_t *p = (uint64_t *)in->v;
  for (int i = 0; i < 16; i++) p[i] = lane64(s);
  for (int i = 0; i < 4; i++) in->x[i] = lane64(s);
  in->nzcv = rnd(s) & 0xf0000000u; in->fpsr = in->off = in->pad = 0;
  p = (uint64_t *)in->mem;
  for (int i = 0; i < 64; i++) p[i] = lane64(s);
}
static uint64_t fpsr_mask = 0x08000000;  /* QC only by default: the translators do not keep IOC/IDC/IXC/UFC/OFC (fm=<hex> keeps more) */
static __attribute__((noinline)) uint64_t hashio(uint64_t h, const struct io *o) {
  const uint64_t *p = (const uint64_t *)o;
  for (int i = 0; i < (int)(sizeof *o / 8); i++) { h = (h ^ (i == 21 ? p[i] & fpsr_mask : p[i])) * 0x100000001b3ull; h ^= h >> 29; }
  return h;
}
static void hex(const char *tag, const void *b, int n) { fprintf(stderr, " %s=", tag); for (int i = 0; i < n; i++) fprintf(stderr, "%02x", ((const uint8_t *)b)[i]); }
static __attribute__((noinline)) void dumpio(const char *name, int i, const struct io *in, const struct io *out) {
  fprintf(stderr, "[nd] V %s %d", name, i); hex("in", in, 176); hex("mem", in->mem, 512); hex("out", out, 176); hex("omem", out->mem, 512); fputc('\n', stderr); fflush(stderr);
}

/* NZCV and FPSR are read and written in stubs: the heavy tier does not translate MSR/MRS of them, a region holding one would never leave the lite tier. A call ends a region, so the
 * instruction under test sits in a region of its own (entered after nd_setf returns). */
static uint64_t nd_fpcr __attribute__((used));  /* fpcr=<hex>: FPCR (FZ 1<<24, DN 1<<25, RMode 22..23) during every case */
__asm__(".text\n.balign 4\n.globl nd_setf\n.type nd_setf,%function\nnd_setf:\n msr nzcv, x5\n msr fpsr, xzr\n adrp x6, nd_fpcr\n ldr x6, [x6, :lo12:nd_fpcr]\n tbz x6, #63, 1f\n ubfx x6, x5, #28, #1\n lsl x6, x6, #24\n lsr x5, x5, #29\n bfi x6, x5, #25, #1\n lsr x5, x5, #1\n bfi x6, x5, #22, #2\n1: msr fpcr, x6\n ret\n.globl nd_getf\n.type nd_getf,%function\nnd_getf:\n mrs x5, nzcv\n mrs x6, fpsr\n msr fpcr, xzr\n ret\n");
#define PRO "ldp q0, q1, [%0, #0]\n ldp q2, q3, [%0, #32]\n ldp q4, q5, [%0, #64]\n ldp q6, q7, [%0, #96]\n ldp x0, x1, [%0, #128]\n ldp x2, x3, [%0, #144]\n ldr x5, [%0, #160]\n bl nd_setf\n"
#define EPI "bl nd_getf\n stp q0, q1, [%1, #0]\n stp q2, q3, [%1, #32]\n stp q4, q5, [%1, #64]\n stp q6, q7, [%1, #96]\n stp x0, x1, [%1, #128]\n stp x2, x3, [%1, #144]\n stp x5, x6, [%1, #160]\n sub x4, x4, %1\n sub x4, x4, #448\n str x4, [%1, #176]\n"
#define C(NAME, MEM, PRE, INS) \
  static __attribute__((noinline)) uint64_t f_##NAME(uint64_t seed, int n, int vb) { \
    struct io in, out; uint64_t h = 0xcbf29ce484222325ull; \
    for (int i = 0; i < n; i++) { \
      gen(&in, &seed); memcpy(out.mem, in.mem, sizeof out.mem); out.pad = 0; \
      __asm__ volatile(PRO ".pushsection nd_marks,\"aw\"\n.balign 8\n.quad 1f\n.asciz \"" #NAME "\"\n.balign 8\n.popsection\n1:\n add x4, %1, #448\n" PRE INS "\n" EPI :: "r"(&in), "r"(&out) : "x0", "x1", "x2", "x3", "x4", "x5", "x6", "x30", "v0", "v1", "v2", "v3", "v4", "v5", "v6", "v7", "memory", "cc"); \
      h = hashio(h, &out); if (vb) dumpio(#NAME, i, &in, &out); } \
    return h; }
#include "cases.inc"
#undef C

/* Wide cases (cases_wide.inc): 40-150 forms over v0-v31 and x0-x3, x7-x15, x19-x28 in one function of their own (x4 = buffer base, x5/x6 left to the stubs, x0/x1 = in/out on entry). */
struct iow { uint8_t v[32][16]; uint64_t x[32]; uint64_t nzcv, fpsr, off, pad; uint8_t mem[512]; } __attribute__((aligned(64)));
_Static_assert(__builtin_offsetof(struct iow, mem) == 800, "layout");
#define WPRO1 ".balign 16\n stp x29, x30, [sp, #-176]!\n stp x19, x20, [sp, #16]\n stp x21, x22, [sp, #32]\n stp x23, x24, [sp, #48]\n stp x25, x26, [sp, #64]\n stp x27, x28, [sp, #80]\n stp d8, d9, [sp, #96]\n stp d10, d11, [sp, #112]\n stp d12, d13, [sp, #128]\n stp d14, d15, [sp, #144]\n stp x0, x1, [sp, #160]\n mov x7, x0\n ldr x5, [x7, #768]\n bl nd_setf\n"
#define WPRO2 " add x4, x1, #1056\n  ldp q0, q1, [x7, #0]\n ldp q2, q3, [x7, #32]\n ldp q4, q5, [x7, #64]\n ldp q6, q7, [x7, #96]\n ldp q8, q9, [x7, #128]\n ldp q10, q11, [x7, #160]\n ldp q12, q13, [x7, #192]\n ldp q14, q15, [x7, #224]\n ldp q16, q17, [x7, #256]\n ldp q18, q19, [x7, #288]\n ldp q20, q21, [x7, #320]\n ldp q22, q23, [x7, #352]\n ldp q24, q25, [x7, #384]\n ldp q26, q27, [x7, #416]\n ldp q28, q29, [x7, #448]\n ldp q30, q31, [x7, #480]\n  ldr x0, [x7, #512]\n ldr x1, [x7, #520]\n ldr x2, [x7, #528]\n ldr x3, [x7, #536]\n ldr x8, [x7, #576]\n ldr x9, [x7, #584]\n ldr x10, [x7, #592]\n ldr x11, [x7, #600]\n ldr x12, [x7, #608]\n ldr x13, [x7, #616]\n ldr x14, [x7, #624]\n ldr x15, [x7, #632]\n ldr x19, [x7, #664]\n ldr x20, [x7, #672]\n ldr x21, [x7, #680]\n ldr x22, [x7, #688]\n ldr x23, [x7, #696]\n ldr x24, [x7, #704]\n ldr x25, [x7, #712]\n ldr x26, [x7, #720]\n ldr x27, [x7, #728]\n ldr x28, [x7, #736]\n ldr x7, [x7, #568]\n"
#define WEPI " ldr x5, [sp, #168]\n  stp q0, q1, [x5, #0]\n stp q2, q3, [x5, #32]\n stp q4, q5, [x5, #64]\n stp q6, q7, [x5, #96]\n stp q8, q9, [x5, #128]\n stp q10, q11, [x5, #160]\n stp q12, q13, [x5, #192]\n stp q14, q15, [x5, #224]\n stp q16, q17, [x5, #256]\n stp q18, q19, [x5, #288]\n stp q20, q21, [x5, #320]\n stp q22, q23, [x5, #352]\n stp q24, q25, [x5, #384]\n stp q26, q27, [x5, #416]\n stp q28, q29, [x5, #448]\n stp q30, q31, [x5, #480]\n  str x0, [x5, #512]\n str x1, [x5, #520]\n str x2, [x5, #528]\n str x3, [x5, #536]\n str x7, [x5, #568]\n str x8, [x5, #576]\n str x9, [x5, #584]\n str x10, [x5, #592]\n str x11, [x5, #600]\n str x12, [x5, #608]\n str x13, [x5, #616]\n str x14, [x5, #624]\n str x15, [x5, #632]\n str x19, [x5, #664]\n str x20, [x5, #672]\n str x21, [x5, #680]\n str x22, [x5, #688]\n str x23, [x5, #696]\n str x24, [x5, #704]\n str x25, [x5, #712]\n str x26, [x5, #720]\n str x27, [x5, #728]\n str x28, [x5, #736]\n sub x4, x4, x5\n sub x4, x4, #1056\n str x4, [x5, #784]\n bl nd_getf\n ldr x7, [sp, #168]\n add x7, x7, #768\n stp x5, x6, [x7]\n ldp x19, x20, [sp, #16]\n ldp x21, x22, [sp, #32]\n ldp x23, x24, [sp, #48]\n ldp x25, x26, [sp, #64]\n ldp x27, x28, [sp, #80]\n ldp d8, d9, [sp, #96]\n ldp d10, d11, [sp, #112]\n ldp d12, d13, [sp, #128]\n ldp d14, d15, [sp, #144]\n ldp x29, x30, [sp], #176\n ret\n"

static __attribute__((noinline)) void genw(struct iow *in, uint64_t *s) {
  uint64_t *p = (uint64_t *)in->v;
  for (int i = 0; i < 64 + 32; i++) p[i] = lane64(s);
  in->nzcv = rnd(s) & 0xf0000000u; in->fpsr = in->off = in->pad = 0;
  p = (uint64_t *)in->mem;
  for (int i = 0; i < 64; i++) p[i] = lane64(s);
}
static __attribute__((noinline)) uint64_t hashw(uint64_t h, const struct iow *o) {
  const uint64_t *p = (const uint64_t *)o;
  for (int i = 0; i < (int)(sizeof *o / 8); i++) { h = (h ^ (i == 97 ? p[i] & fpsr_mask : p[i])) * 0x100000001b3ull; h ^= h >> 29; }
  return h;
}
#define W(NAME, INS) \
  extern void w_##NAME(struct iow *, struct iow *); \
  __asm__(".arch armv8.5-a+fp16+fp16fml+aes+sha2+sha3+sm4+dotprod+crc+lse+rcpc\n.text\n.globl w_" #NAME "\n.type w_" #NAME ",%function\nw_" #NAME ":\n" WPRO1 ".pushsection nd_marks,\"aw\"\n.balign 8\n.quad 1f\n.asciz \"" #NAME "\"\n.balign 8\n.popsection\n1:\n" WPRO2 INS "\n" WEPI); \
  static __attribute__((noinline)) uint64_t fw_##NAME(uint64_t seed, int n, int vb) { \
    struct iow in, out; uint64_t h = 0xcbf29ce484222325ull; (void)vb; \
    for (int i = 0; i < n; i++) { genw(&in, &seed); memset(&out, 0, sizeof out); memcpy(out.mem, in.mem, sizeof out.mem); w_##NAME(&in, &out); h = hashw(h, &out); } \
    return h; }
#include "cases_wide.inc"
#undef W
#define C(NAME, MEM, PRE, INS) {#NAME, f_##NAME, (const void *)f_##NAME},
#define W(NAME, INS) {#NAME, fw_##NAME, (const void *)w_##NAME},
static const struct { const char *name; uint64_t (*f)(uint64_t, int, int); const void *code; } T[] = {
#include "cases.inc"
#include "cases_wide.inc"
};
extern int kernels_run(int n, const char *pre, const char *verbose);

extern char __start_nd_marks[], __stop_nd_marks[];
static char cfg[256];
static const char *opt(const char *key, char *buf, size_t sz) {  /* value of key= in cfg, or NULL */
  size_t k = strlen(key);
  for (const char *p = cfg; *p;) {
    while (*p == ' ') p++;
    const char *e = strchr(p, ' '); size_t l = e ? (size_t)(e - p) : strlen(p);
    if (l > k && !strncmp(p, key, k) && p[k] == '=') { snprintf(buf, sz, "%.*s", (int)(l - k - 1), p + k + 1); return buf; }
    p += l;
  }
  return NULL;
}
static int wanted(const char *name, const char *pre) {  /* pre: comma separated prefixes */
  if (!pre) return 1;
  for (const char *p = pre; *p;) { const char *e = strchrnul(p, ','); if (!strncmp(name, p, e - p)) return 1; p = *e ? e + 1 : e; }
  return 0;
}
static sigjmp_buf jb; static volatile int armed;
static void on_sig(int s) { if (armed) siglongjmp(jb, s); signal(s, SIG_DFL); raise(s); }
static volatile int idcode;
static void on_id(int s, siginfo_t *si, void *u) { (void)s; (void)u; idcode = si->si_code; siglongjmp(jb, 1); }
int neondiff_run(void) {
  char nb[16], pb[200], vb[120]; const char *ns = opt("n", nb, sizeof nb), *pre = opt("pre", pb, sizeof pb), *ver = opt("v", vb, sizeof vb);
  int n = ns ? atoi(ns) : 1000; { char fb[20]; const char *fm = opt("fm", fb, sizeof fb); if (fm) fpsr_mask = strtoull(fm, 0, 16); fm = opt("fpcr", fb, sizeof fb); if (fm) nd_fpcr = !strcmp(fm, "mix") ? 1ull << 63 : strtoull(fm, 0, 16);  /* mix: FZ, DN and RMode of every run from the random NZCV bits */ }
  { int sigs[] = {SIGILL, SIGSEGV, SIGBUS, SIGFPE, SIGTRAP}; for (unsigned k = 0; k < 5; k++) signal(sigs[k], on_sig); }
  if (strstr(cfg, "addr"))  /* "[nm] <case> <guest address of the region after nd_setf>": with the perf map (BERBERIS_PROFILING) tells whether the instruction's own region went heavy */
    for (char *p = __start_nd_marks; p < __stop_nd_marks;) { fprintf(stderr, "[nm] %s %p\n", p + 8, *(void **)p); p += 8 + ((strlen(p + 8) + 1 + 7) & ~7ul); }
  for (size_t i = 0; i < sizeof T / sizeof T[0]; i++) {
    if (!wanted(T[i].name, pre)) continue;
    uint64_t seed = 0x9e3779b97f4a7c15ull; for (const char *c = T[i].name; *c; c++) seed = (seed ^ (uint8_t)*c) * 0x100000001b3ull;
    int v = ver && !strcmp(ver, T[i].name);
    if (ver && !v) continue;
    int sg = sigsetjmp(jb, 1); uint64_t h = 0;  /* a signal ends the case: its line says which (illegal forms differ on real hardware too) */
    struct timespec t0, t1; clock_gettime(CLOCK_MONOTONIC, &t0);
    if (!sg) { armed = 1; h = T[i].f(seed | 1, n, v); } armed = 0;
    clock_gettime(CLOCK_MONOTONIC, &t1); if (ver || (cfg[0] && strstr(cfg, "time"))) fprintf(stderr, "[nt] %s %ld ms\n", T[i].name, (long)((t1.tv_sec - t0.tv_sec) * 1000 + (t1.tv_nsec - t0.tv_nsec) / 1000000));
    if (sg) fprintf(stderr, "[nd] %s SIG%d\n", T[i].name, sg); else fprintf(stderr, "[nd] %s %016llx\n", T[i].name, (unsigned long long)h); fflush(stderr);
  }
  kernels_run(n, pre, ver);
  /* which translator build ran: an undefined instruction's si_code is ILL_ILLOPC (1) with patch 0060 (Nov 14+), SI_TKILL (-6) before; last, a guest SIGILL may keep later regions from gearing up */
  struct sigaction sa; memset(&sa, 0, sizeof sa); sa.sa_sigaction = on_id; sa.sa_flags = SA_SIGINFO | SA_NODEFER; sigaction(SIGILL, &sa, 0);
  if (!sigsetjmp(jb, 1)) __asm__ volatile(".inst 0x00000000");
  fprintf(stderr, "[id] sigill si_code=%d\n", idcode); fflush(stderr);
  return 0;
}
#if defined(__ANDROID__) && !defined(NEONDIFF_MAIN)
#include <jni.h>
JNIEXPORT void JNICALL Java_hello_neondiff_Main_run(JNIEnv *env, jclass c) {
  (void)env; (void)c;
  size_t l = __system_property_get("debug.neondiff", cfg);  /* a property holds 91 characters: debug.neondiff2 continues it */
  cfg[l++] = ' '; __system_property_get("debug.neondiff2", cfg + l);
  neondiff_run();
}
#else
int main(int argc, char **argv) { size_t o = 0; for (int i = 1; i < argc; i++) o += snprintf(cfg + o, sizeof cfg - o, "%s ", argv[i]); neondiff_run(); fputs("[nd] DONE\n", stderr); return 0; }
#endif
#pragma clang attribute pop
