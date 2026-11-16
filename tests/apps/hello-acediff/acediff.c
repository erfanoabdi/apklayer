/* Differential test for the arm64 translator (PGR/ACE hunt): each line is one instruction family or one runtime-code / signal behaviour, deterministic
 * (fixed seed), so a real arm64 machine and Berberis on x86_64 must print identical lines. A family whose instruction the CPU or the translator lacks prints
 * SIG<n> instead of a hash. Lines starting "info" are CPU-specific on purpose (id registers, hwcaps) and are not compared.
 * Build native: gcc -O2 -march=armv8.2-a+crypto+sha3+sm4+lse+dotprod+crc acediff.c -o acediff ; as an app: tests/apps/build.sh hello-acediff (JNI run()). */
#define ACEDIFF_DEFS
#include "acediff.h"

const char *slot_name[96]; uint64_t slot_val[96]; int slots;
sigjmp_buf jb;
volatile sig_atomic_t armed;
char altstack[65536] __attribute__((aligned(16)));
/* a child that ends by a signal on purpose must not dump core: with core_pattern piped to systemd-coredump the kernel stalls it (and the parent's waitpid) for 100+ s per child */
#define NOCORE do { struct rlimit z = {0, 0}; setrlimit(RLIMIT_CORE, &z); prctl(PR_SET_DUMPABLE, 0); } while (0)
static void on_sig(int s) { if (armed) siglongjmp(jb, s); _exit(99); }
static void run(const char *name, uint64_t (*f)(void))
{
    rng = 0x9e3779b97f4a7c15ull; for (const char *c = name; *c; c++) rng = (rng ^ (uint8_t)*c) * 0x100000001b3ull;  /* a family that faults on one CPU must not shift the others */
    struct sigaction sa = {.sa_handler = on_sig}; sigemptyset(&sa.sa_mask);
    sigaction(SIGILL, &sa, NULL); sigaction(SIGSEGV, &sa, NULL); sigaction(SIGBUS, &sa, NULL); sigaction(SIGFPE, &sa, NULL);
    int s = sigsetjmp(jb, 1);
    slots = 0;
    if (s == 0) { armed = 1; uint64_t h = f(); armed = 0; for (int i = 0; i < slots; i++) say("  %s %016llx", slot_name[i], (unsigned long long)slot_val[i]); say("%-28s %016llx", name, (unsigned long long)h); }
    else { armed = 0; say("%-28s SIG%d", name, s); }
}
/* ---- crypto ---- */
static uint64_t t_aes(void)
{
    uint64_t h = 0;
    LOOP(300) { uint8x16_t a = rv(), k = rv();
        uint8x16_t e = vaeseq_u8(a, k), m = vaesmcq_u8(e), d = vaesdq_u8(a, k), im = vaesimcq_u8(d);
        h = H(H(H(H(h, e), m), d), im); h = H(h, vaesimcq_u8(vaesmcq_u8(a))); }
    return h;
}
static uint64_t t_sha1(void)
{
    uint64_t h = 0;
    LOOP(300) { uint32x4_t abcd = vreinterpretq_u32_u8(rv()), w = vreinterpretq_u32_u8(rv()), w2 = vreinterpretq_u32_u8(rv()), w3 = vreinterpretq_u32_u8(rv()); uint32_t e = (uint32_t)next();
        h = H4(h, vsha1cq_u32(abcd, e, w)); h = H4(h, vsha1pq_u32(abcd, e, w)); h = H4(h, vsha1mq_u32(abcd, e, w));
        h = fnv(h, vsha1h_u32(e)); h = H4(h, vsha1su0q_u32(abcd, w, w2)); h = H4(h, vsha1su1q_u32(abcd, w3)); }
    return h;
}
static uint64_t t_sha256(void)
{
    uint64_t h = 0;
    LOOP(300) { uint32x4_t a = vreinterpretq_u32_u8(rv()), b = vreinterpretq_u32_u8(rv()), w = vreinterpretq_u32_u8(rv()), w2 = vreinterpretq_u32_u8(rv());
        h = H4(h, vsha256hq_u32(a, b, w)); h = H4(h, vsha256h2q_u32(a, b, w)); h = H4(h, vsha256su0q_u32(a, b)); h = H4(h, vsha256su1q_u32(a, b, w2)); }
    return h;
}
static uint64_t t_sha512(void)
{
    uint64_t h = 0;
    LOOP(300) { uint64x2_t a = vreinterpretq_u64_u8(rv()), b = vreinterpretq_u64_u8(rv()), c = vreinterpretq_u64_u8(rv());
        F(H2, vsha512hq_u64(a, b, c)); F(H2, vsha512h2q_u64(a, b, c)); F(H2, vsha512su0q_u64(a, b)); F(H2, vsha512su1q_u64(a, b, c)); }
    return h;
}
static uint64_t t_sha3(void)
{
    uint64_t h = 0;
    LOOP(300) { uint8x16_t a = rv(), b = rv(), c = rv(); uint64x2_t x = vreinterpretq_u64_u8(a), y = vreinterpretq_u64_u8(b);
        h = H(h, veor3q_u8(a, b, c)); h = H2(h, vrax1q_u64(x, y)); h = H2(h, vxarq_u64(x, y, 23)); h = H(h, vbcaxq_u8(a, b, c)); }
    return h;
}
static uint64_t t_sm4(void)
{
    uint64_t h = 0;
    LOOP(100) { uint32x4_t a = vreinterpretq_u32_u8(rv()), b = vreinterpretq_u32_u8(rv()); h = H4(h, vsm4eq_u32(a, b)); h = H4(h, vsm4ekeyq_u32(a, b)); }
    return h;
}
static uint64_t t_pmull(void)
{
    uint64_t h = 0;
    LOOP(300) { uint64_t a = next(), b = next(); poly128_t r = vmull_p64(a, b); h = fnv(fnv(h, (uint64_t)r), (uint64_t)(r >> 64));
        uint64x2_t x = vreinterpretq_u64_u8(rv()), y = vreinterpretq_u64_u8(rv()); poly128_t q = vmull_high_p64(vreinterpretq_p64_u64(x), vreinterpretq_p64_u64(y));
        h = fnv(fnv(h, (uint64_t)q), (uint64_t)(q >> 64));
        uint8x16_t u = rv(), v = rv(); h = H(h, vreinterpretq_u8_p16(vmull_p8(vget_low_p8(vreinterpretq_p8_u8(u)), vget_low_p8(vreinterpretq_p8_u8(v)))));
        h = H(h, vreinterpretq_u8_p16(vmull_high_p8(vreinterpretq_p8_u8(u), vreinterpretq_p8_u8(v)))); h = fnv(h, vget_lane_u64(vreinterpret_u64_p8(vmul_p8(vget_low_p8(vreinterpretq_p8_u8(u)), vget_high_p8(vreinterpretq_p8_u8(v)))), 0)); }
    return h;
}
static uint64_t t_crc(void)
{
    uint64_t h = 0;
    LOOP(300) { uint32_t c = (uint32_t)next(); uint64_t b = next();
        c = __crc32b(c, (uint8_t)b); h = fnv(h, c); c = __crc32h(c, (uint16_t)(b >> 8)); h = fnv(h, c); c = __crc32w(c, (uint32_t)(b >> 24)); h = fnv(h, c); c = __crc32d(c, b); h = fnv(h, c);
        c = __crc32cb(c, (uint8_t)b); h = fnv(h, c); c = __crc32ch(c, (uint16_t)(b >> 7)); h = fnv(h, c); c = __crc32cw(c, (uint32_t)(b >> 9)); h = fnv(h, c); c = __crc32cd(c, b); h = fnv(h, c); }
    return h;
}

/* ---- NEON ---- */
static uint64_t t_tbl(void)
{
    uint64_t h = 0;
    LOOP(300) { uint8x16_t t0 = rv(), t1 = rv(), t2 = rv(), t3 = rv(), idx = rv(), d = rv(); uint8x16x2_t t2x = {{t0, t1}}; uint8x16x3_t t3x = {{t0, t1, t2}}; uint8x16x4_t t4x = {{t0, t1, t2, t3}};
        if (i & 1) idx = vandq_u8(idx, vdupq_n_u8(0x3f));  /* half the rounds in range of 4 tables */
        h = H(h, vqtbl1q_u8(t0, idx)); h = H(h, vqtbl2q_u8(t2x, idx)); h = H(h, vqtbl3q_u8(t3x, idx)); h = H(h, vqtbl4q_u8(t4x, idx));
        h = H(h, vqtbx1q_u8(d, t0, idx)); h = H(h, vqtbx2q_u8(d, t2x, idx)); h = H(h, vqtbx3q_u8(d, t3x, idx)); h = H(h, vqtbx4q_u8(d, t4x, idx));
        uint8x8_t i8 = vget_low_u8(idx); h = fnv(h, vget_lane_u64(vreinterpret_u64_u8(vtbl1_u8(vget_low_u8(t0), i8)), 0));
        uint8x8x2_t a2 = {{vget_low_u8(t0), vget_high_u8(t0)}}; h = fnv(h, vget_lane_u64(vreinterpret_u64_u8(vtbl2_u8(a2, i8)), 0)); h = fnv(h, vget_lane_u64(vreinterpret_u64_u8(vtbx2_u8(vget_low_u8(d), a2, i8)), 0)); }
    return h;
}
static uint64_t t_shuffle(void)
{
    uint64_t h = 0;
    LOOP(200) { uint8x16_t a = rv(), b = rv();
        h = H(h, vextq_u8(a, b, 1)); h = H(h, vextq_u8(a, b, 7)); h = H(h, vextq_u8(a, b, 15));
        h = H(h, vzip1q_u8(a, b)); h = H(h, vzip2q_u8(a, b)); h = H(h, vuzp1q_u8(a, b)); h = H(h, vuzp2q_u8(a, b)); h = H(h, vtrn1q_u8(a, b)); h = H(h, vtrn2q_u8(a, b));
        h = H(h, vrev64q_u8(a)); h = H(h, vrev32q_u8(a)); h = H(h, vrev16q_u8(a)); h = H(h, vreinterpretq_u8_u16(vrev64q_u16(vreinterpretq_u16_u8(b)))); h = H(h, vreinterpretq_u8_u32(vzip2q_u32(vreinterpretq_u32_u8(a), vreinterpretq_u32_u8(b))));
        h = H(h, vcntq_u8(a)); h = H(h, vrbitq_u8(b)); h = H(h, vclzq_u8(a)); h = H(h, vreinterpretq_u8_u16(vclzq_u16(vreinterpretq_u16_u8(b)))); h = H(h, vreinterpretq_u8_s8(vclsq_s8(vreinterpretq_s8_u8(a))));
        h = H(h, vdupq_laneq_u8(a, 5)); h = H(h, vreinterpretq_u8_u32(vdupq_laneq_u32(vreinterpretq_u32_u8(b), 2))); h = H(h, vsetq_lane_u8(0x5a, a, 9)); h = H(h, vcombine_u8(vget_high_u8(a), vget_low_u8(b))); }
    return h;
}
static uint64_t t_intarith(void)
{
    uint64_t h = 0;
    LOOP(300) { uint8x16_t a = rv(), b = rv(), c = rv(); int8x16_t sa = vreinterpretq_s8_u8(a), sb = vreinterpretq_s8_u8(b); int16x8_t wa = vreinterpretq_s16_u8(a), wb = vreinterpretq_s16_u8(b); int32x4_t da = vreinterpretq_s32_u8(a), db = vreinterpretq_s32_u8(b);
        h = H(h, vreinterpretq_u8_u16(vmull_u8(vget_low_u8(a), vget_low_u8(b)))); h = H(h, vreinterpretq_u8_u16(vmlal_high_u8(vreinterpretq_u16_u8(c), a, b))); h = H(h, vabdq_u8(a, b)); h = H(h, vabaq_u8(c, a, b));
        h = H(h, vreinterpretq_u8_s8(vqaddq_s8(sa, sb))); h = H(h, vqsubq_u8(a, b)); h = H(h, vreinterpretq_u8_s8(vqshlq_s8(sa, sb))); h = H(h, vreinterpretq_u8_s8(vrshlq_s8(sa, sb))); h = H(h, vreinterpretq_u8_s8(vshlq_s8(sa, sb)));
        h = H(h, vreinterpretq_u8_s16(vqrdmulhq_s16(wa, wb))); h = H(h, vreinterpretq_u8_s16(vqdmulhq_s16(wa, wb))); h = H(h, vreinterpretq_u8_s32(vqdmulhq_s32(da, db))); h = H(h, vreinterpretq_u8_s32(vqrdmlahq_s32(da, db, vreinterpretq_s32_u8(c))));
        h = H(h, vsraq_n_u8(a, b, 3)); h = H(h, vsliq_n_u8(a, b, 3)); h = H(h, vsriq_n_u8(a, b, 5)); h = H(h, vshlq_n_u8(a, 5)); h = H(h, vreinterpretq_u8_s8(vrshrq_n_s8(sa, 3))); h = H(h, vreinterpretq_u8_s8(vqshlq_n_s8(sa, 2)));
        h = H(h, vmaxq_u8(a, b)); h = H(h, vminq_u8(a, b)); h = H(h, vpmaxq_u8(a, b)); h = H(h, vpaddq_u8(a, b)); h = H(h, vreinterpretq_u8_u16(vpadalq_u8(vreinterpretq_u16_u8(c), a)));
        h = fnv(h, vaddlvq_u8(a)); h = fnv(h, (uint64_t)vaddlvq_s16(wa)); h = fnv(h, vmaxvq_u8(b)); h = fnv(h, vaddvq_u32(vreinterpretq_u32_u8(a)));
        h = H(h, vreinterpretq_u8_s8(vabsq_s8(sa))); h = H(h, vreinterpretq_u8_s8(vqabsq_s8(sa))); h = H(h, vreinterpretq_u8_s8(vqnegq_s8(sb)));
        h = H(h, vcombine_u8(vmovn_u16(vreinterpretq_u16_u8(a)), vqmovn_u16(vreinterpretq_u16_u8(b)))); h = H(h, vcombine_u8(vqmovun_s16(wa), vreinterpret_u8_s8(vqmovn_s16(wb))));
        h = H(h, vreinterpretq_u8_s32(vdotq_s32(da, sa, sb))); h = H(h, vreinterpretq_u8_u32(vdotq_u32(vreinterpretq_u32_u8(c), a, b)));
        h = H(h, vreinterpretq_u8_u16(vmulq_laneq_u16(vreinterpretq_u16_u8(a), vreinterpretq_u16_u8(b), 3))); h = H(h, vreinterpretq_u8_u32(vmlaq_u32(vreinterpretq_u32_u8(a), vreinterpretq_u32_u8(b), vreinterpretq_u32_u8(c))));
        h = H(h, vbslq_u8(a, b, c)); h = H(h, vorrq_u8(vandq_u8(b, c), vbicq_u8(a, c))); h = H(h, vornq_u8(a, b)); h = H(h, vceqzq_u8(a)); h = H(h, vcgtq_u8(a, b)); h = H(h, vcgeq_s8(sa, sb)); h = H(h, vtstq_u8(a, b)); }
    return h;
}
static uint64_t t_ldst(void)
{
    static uint8_t m[512] __attribute__((aligned(16))); uint64_t h = 0;
    for (unsigned i = 0; i < sizeof m; i++) m[i] = (uint8_t)(next() >> 20);
    LOOP(60) { uint8x16x2_t a = vld2q_u8(m + i); uint8x16x3_t b = vld3q_u8(m + i + 1); uint8x16x4_t c = vld4q_u8(m + i + 3); uint16x8x2_t d = vld2q_u16((uint16_t *)(m + 2 * (i % 32)));
        h = H(H(h, a.val[0]), a.val[1]); h = H(H(H(h, b.val[0]), b.val[1]), b.val[2]); h = H(H(H(H(h, c.val[0]), c.val[1]), c.val[2]), c.val[3]); h = H(h, vreinterpretq_u8_u16(d.val[1]));
        h = H(h, vld1q_dup_u8(m + i)); h = H(h, vreinterpretq_u8_u32(vld1q_dup_u32((uint32_t *)(m + 4 * (i % 20))))); h = H(h, vld1q_lane_u8(m + 7 + i, rv(), 11));
        uint8_t t[64]; vst2q_u8(t, a); vst3q_u8(t + 3, b); h = fnv(h, *(uint64_t *)(t + 3)); h = fnv(h, *(uint64_t *)(t + 40)); vst4q_u8(t, c); h = fnv(h, *(uint64_t *)(t + 8)); }
    return h;
}

/* ---- floating point ---- */
static uint64_t t_float(int fin)
{
    uint64_t h = 0;
    LOOP(400) { float32x4_t a = rf(fin), b = rf(fin), c = rf(fin); float64x2_t x = rd(fin), y = rd(fin), z = rd(fin);
        F(HF, vaddq_f32(a, b)); F(HF, vsubq_f32(a, b)); F(HF, vmulq_f32(a, b)); F(HF, vdivq_f32(a, b)); F(HF, vsqrtq_f32(a)); F(HF, vfmaq_f32(c, a, b)); F(HF, vfmsq_f32(c, a, b));
        F(HF, vmaxq_f32(a, b)); F(HF, vminq_f32(a, b)); F(HF, vmaxnmq_f32(a, b)); F(HF, vminnmq_f32(a, b)); F(HF, vabdq_f32(a, b)); F(HF, vabsq_f32(a)); F(HF, vnegq_f32(a));
        F(HD, vaddq_f64(x, y)); F(HD, vmulq_f64(x, y)); F(HD, vdivq_f64(x, y)); F(HD, vsqrtq_f64(x)); F(HD, vfmaq_f64(z, x, y)); F(HD, vmaxq_f64(x, y)); F(HD, vminnmq_f64(x, y));
        F(HF, vrecpeq_f32(a)); F(HF, vrsqrteq_f32(a)); F(HF, vrecpsq_f32(a, b)); F(HF, vrsqrtsq_f32(a, b)); F(HD, vrecpeq_f64(x)); F(HD, vrsqrteq_f64(x)); F(HD, vrecpsq_f64(x, y));
        F(H4, vcvtq_u32_f32(a)); F(H4, vreinterpretq_u32_s32(vcvtq_s32_f32(a))); F(H4, vreinterpretq_u32_s32(vcvtnq_s32_f32(a))); F(H4, vreinterpretq_u32_s32(vcvtaq_s32_f32(a))); F(H4, vreinterpretq_u32_s32(vcvtmq_s32_f32(a))); F(H4, vreinterpretq_u32_s32(vcvtpq_s32_f32(a)));
        F(H2, vreinterpretq_u64_s64(vcvtq_s64_f64(x))); F(H2, vcvtq_u64_f64(x)); F(H2, vreinterpretq_u64_s64(vcvtnq_s64_f64(x)));
        F(HF, vcvtq_f32_s32(vreinterpretq_s32_f32(a))); F(HF, vcvtq_f32_u32(vreinterpretq_u32_f32(b))); F(HD, vcvtq_f64_s64(vreinterpretq_s64_f64(x))); F(HD, vcvtq_f64_u64(vreinterpretq_u64_f64(y)));
        F(HF, vrndq_f32(a)); F(HF, vrndnq_f32(a)); F(HF, vrndmq_f32(a)); F(HF, vrndpq_f32(a)); F(HF, vrndaq_f32(a)); F(HF, vrndxq_f32(a)); F(HF, vrndiq_f32(a)); F(HD, vrndnq_f64(x)); F(HD, vrndaq_f64(y));
        F(H4, vceqq_f32(a, b)); F(H4, vcgeq_f32(a, b)); F(H4, vcgtq_f32(a, b)); F(H4, vcageq_f32(a, b)); F(H4, vcgtzq_f32(a));
        F(HD, vcvt_f64_f32(vget_low_f32(a))); F(HF, vcombine_f32(vcvt_f32_f64(x), vcvt_f32_f64(y)));
        F(HF, vcvt_f32_f16(vreinterpret_f16_u16(vget_low_u16(vreinterpretq_u16_f32(a))))); h = fnv(h, vget_lane_u64(vreinterpret_u64_f16(vcvt_f16_f32(a)), 0));
        float s = vgetq_lane_f32(a, 0), t = vgetq_lane_f32(b, 1); double u = vgetq_lane_f64(x, 0);
        h = fnv(h, (uint32_t)(s < t) | (uint32_t)(s == t) << 1 | (uint32_t)(s > t) << 2 | (uint32_t)(s != s || t != t) << 3 | (uint32_t)(__builtin_isless(s, t)) << 4 | (uint32_t)__builtin_isunordered(s, t) << 5);
        h = fnv(h, (uint64_t)(int64_t)(fin ? (u > -9e18 && u < 9e18 ? u : 0) : 0)); h = fnv(h, BC32(__builtin_fmaf(s, t, vgetq_lane_f32(c, 2)))); }
    return h;
}
static uint64_t t_float_fin(void) { return t_float(1); }
static uint64_t t_float_any(void) { return t_float(0); }
static uint64_t t_fpcr(void)
{
    uint64_t h = 0, fpcr0, r; static const char *L[4][4] = {{"rmode nearest add", "rmode nearest mul", "rmode nearest div", "rmode nearest lrintf"}, {"rmode +inf add", "rmode +inf mul", "rmode +inf div", "rmode +inf lrintf"},
        {"rmode -inf add", "rmode -inf mul", "rmode -inf div", "rmode -inf lrintf"}, {"rmode zero add", "rmode zero mul", "rmode zero div", "rmode zero lrintf"}};
    __asm__ volatile("mrs %0, fpcr" : "=r"(fpcr0));
    for (unsigned mode = 0; mode < 4; mode++) {
        uint64_t v = (fpcr0 & ~(3ull << 22)) | (uint64_t)mode << 22; __asm__ volatile("msr fpcr, %0" :: "r"(v)); __asm__ volatile("mrs %0, fpcr" : "=r"(r)); FL("fpcr read-back", r);
        LOOP(60) { volatile float a = (float)(int)(next() % 1000) / 7.0f + 1e-3f, b = (float)(int)(next() % 1000) / 13.0f + 3e-4f; volatile double d = a * 1e-7; FL(L[mode][0], BC32(a + b)); FL(L[mode][1], BC32(a * b)); FL(L[mode][2], BC64(d / b)); FL(L[mode][3], (uint64_t)(int)lrintf(a)); }
    }
    uint64_t fz = fpcr0 | (1ull << 24); __asm__ volatile("msr fpcr, %0" :: "r"(fz));  /* flush-to-zero: a denormal result becomes 0 */
    { volatile float a = 1e-39f, b = 1.0f; FL("flush-to-zero mul", BC32(a * b)); FL("flush-to-zero add", BC32(a + a)); }
    uint64_t dn = fpcr0 | (1ull << 25); __asm__ volatile("msr fpcr, %0" :: "r"(dn));  /* default NaN */
    { volatile uint32_t nb = 0x7fc12345u; volatile float n = BCF(nb); FL("default-NaN add", BC32(n + 1.0f)); }
    __asm__ volatile("msr fpcr, %0" :: "r"(fpcr0));
    __asm__ volatile("msr fpsr, xzr");
    { volatile float a = 1.0f, b = 3.0f; volatile float q = a / b; (void)q; uint64_t sr; __asm__ volatile("mrs %0, fpsr" : "=r"(sr)); FL("fpsr after inexact", sr & 0x9f); }
    { volatile float z = 0.0f, one = 1.0f; volatile float q = one / z; (void)q; uint64_t sr; __asm__ volatile("mrs %0, fpsr" : "=r"(sr)); FL("fpsr after div by zero", sr & 0x9f); }
    { int8x16_t a = vdupq_n_s8(127), b = vdupq_n_s8(100); int8x16_t s = vqaddq_s8(a, b); uint64_t sr; __asm__ volatile("mrs %0, fpsr" : "=r"(sr)); FL("fpsr QC after sat add", (sr >> 27) & 1); FL("sat add result", vgetq_lane_s8(s, 0)); }
    return h;
}

/* ---- scalar ---- */
static uint64_t t_scalar(void)
{
    uint64_t h = 0;
    LOOP(500) { uint64_t a = next(), b = next(), c = next(), n, z, r; unsigned s = (unsigned)(next() & 127);
        int64_t sa = a, sb = b;
        h = fnv(h, __rbitll(a)); h = fnv(h, __builtin_clzll(a >> (s & 63))); h = fnv(h, __clsll(a)); h = fnv(h, __cls((uint32_t)a)); h = fnv(h, __builtin_bswap64(a)); h = fnv(h, __rbit((uint32_t)b));
        __asm__ volatile("lsl %0, %1, %2" : "=r"(r) : "r"(a), "r"((uint64_t)s)); h = fnv(h, r); __asm__ volatile("asr %0, %1, %2" : "=r"(r) : "r"(a), "r"((uint64_t)s)); h = fnv(h, r);
        __asm__ volatile("ror %w0, %w1, %w2" : "=r"(r) : "r"(a), "r"((uint64_t)s)); h = fnv(h, r); __asm__ volatile("extr %0, %1, %2, #13" : "=r"(r) : "r"(a), "r"(b)); h = fnv(h, r);
        __asm__ volatile("udiv %0, %1, %2" : "=r"(r) : "r"(a), "r"(b >> (s & 63))); h = fnv(h, r); __asm__ volatile("sdiv %0, %1, %2" : "=r"(r) : "r"(a), "r"((uint64_t)(sb >> (s & 63)))); h = fnv(h, r);
        __asm__ volatile("sdiv %w0, %w1, %w2" : "=r"(r) : "r"(0x80000000u), "r"(0xffffffffu)); h = fnv(h, r); __asm__ volatile("sdiv %0, %1, %2" : "=r"(r) : "r"(0x8000000000000000ull), "r"(~0ull)); h = fnv(h, r);
        __asm__ volatile("udiv %w0, %w1, %w2" : "=r"(r) : "r"(a), "r"(0)); h = fnv(h, r);
        __asm__ volatile("adds %0, %2, %3\n cset %1, vs\n mrs %1, nzcv" : "=r"(r), "=r"(n) : "r"(a), "r"(b) : "cc"); h = fnv(fnv(h, r), n);
        __asm__ volatile("subs %0, %2, %3\n mrs %1, nzcv" : "=r"(r), "=r"(n) : "r"(a), "r"(b) : "cc"); h = fnv(fnv(h, r), n);
        __asm__ volatile("cmn %w2, %w3\n mrs %1, nzcv\n adc %0, %2, %3" : "=r"(r), "=r"(n) : "r"(a), "r"(b) : "cc"); h = fnv(fnv(h, r), n);
        __asm__ volatile("cmp %2, %3\n sbc %0, %2, %3\n mrs %1, nzcv" : "=r"(r), "=r"(n) : "r"(a), "r"(b) : "cc"); h = fnv(fnv(h, r), n);
        __asm__ volatile("cmp %2, %3\n ccmp %3, #5, #9, hi\n mrs %1, nzcv\n csinv %0, %2, %3, ge" : "=r"(r), "=r"(n) : "r"(a), "r"(b) : "cc"); h = fnv(fnv(h, r), n);
        __asm__ volatile("tst %2, %3\n mrs %1, nzcv\n cneg %0, %2, ne" : "=r"(r), "=r"(n) : "r"(a), "r"(b >> (s & 63)) : "cc"); h = fnv(fnv(h, r), n);
        __asm__ volatile("smulh %0, %1, %2" : "=r"(r) : "r"(a), "r"(b)); h = fnv(h, r); __asm__ volatile("umulh %0, %1, %2" : "=r"(r) : "r"(a), "r"(b)); h = fnv(h, r);
        __asm__ volatile("smaddl %0, %w1, %w2, %3" : "=r"(r) : "r"(a), "r"(b), "r"(c)); h = fnv(h, r); __asm__ volatile("umsubl %0, %w1, %w2, %3" : "=r"(r) : "r"(a), "r"(b), "r"(c)); h = fnv(h, r);
        __asm__ volatile("sbfx %0, %1, #11, #29" : "=r"(r) : "r"(a)); h = fnv(h, r); __asm__ volatile("ubfiz %0, %1, #7, #40" : "=r"(r) : "r"(a)); h = fnv(h, r);
        r = b; __asm__ volatile("bfxil %0, %1, #9, #22" : "+r"(r) : "r"(a)); h = fnv(h, r); r = b; __asm__ volatile("bfi %w0, %w1, #5, #12" : "+r"(r) : "r"(a)); h = fnv(h, r);
        __asm__ volatile("sxtw %0, %w1\n add %0, %0, %w1, uxth #2" : "=r"(r) : "r"(a)); h = fnv(h, r);
        __asm__ volatile("orn %0, %1, %2, lsr #7\n bic %0, %0, %2, ror #3\n eon %0, %0, %1, asr #11" : "=&r"(r) : "r"(a), "r"(b)); h = fnv(h, r);
        __asm__ volatile("movk %0, #0x1234, lsl #32" : "+r"(c)); h = fnv(h, c); }
    return h;
}
#define ATOM(...) __asm__ volatile(".arch_extension lse\n" __VA_ARGS__)
static uint64_t t_lse(void)
{
    static volatile uint64_t cell[4] __attribute__((aligned(16))); uint64_t h = 0, o, v;
    LOOP(200) { cell[0] = next(); cell[1] = next(); v = next();
        ATOM("ldadd %x1, %x0, [%2]" : "=&r"(o) : "r"(v), "r"(&cell[0]), "m"(cell[0])); h = fnv(fnv(h, o), cell[0]);
        ATOM("ldclral %x1, %x0, [%2]" : "=&r"(o) : "r"(v), "r"(&cell[0]), "m"(cell[0])); h = fnv(fnv(h, o), cell[0]);
        ATOM("ldeorl %w1, %w0, [%2]" : "=&r"(o) : "r"(v), "r"(&cell[1]), "m"(cell[1])); h = fnv(fnv(h, o), cell[1]);
        ATOM("ldset %x1, %x0, [%2]" : "=&r"(o) : "r"(v), "r"(&cell[1]), "m"(cell[1])); h = fnv(fnv(h, o), cell[1]);
        ATOM("ldsmax %x1, %x0, [%2]" : "=&r"(o) : "r"(v), "r"(&cell[0]), "m"(cell[0])); h = fnv(fnv(h, o), cell[0]);
        ATOM("ldumin %w1, %w0, [%2]" : "=&r"(o) : "r"(v), "r"(&cell[1]), "m"(cell[1])); h = fnv(fnv(h, o), cell[1]);
        ATOM("ldsmin %x1, %x0, [%2]" : "=&r"(o) : "r"(v), "r"(&cell[0]), "m"(cell[0])); h = fnv(fnv(h, o), cell[0]);
        ATOM("ldaddab %w1, %w0, [%2]" : "=&r"(o) : "r"(v), "r"(&cell[1]), "m"(cell[1])); h = fnv(fnv(h, o), cell[1]);
        ATOM("ldaddlh %w1, %w0, [%2]" : "=&r"(o) : "r"(v), "r"(&cell[1]), "m"(cell[1])); h = fnv(fnv(h, o), cell[1]);
        ATOM("swp %x1, %x0, [%2]" : "=&r"(o) : "r"(v), "r"(&cell[0]), "m"(cell[0])); h = fnv(fnv(h, o), cell[0]);
        ATOM("swpalb %w1, %w0, [%2]" : "=&r"(o) : "r"(v), "r"(&cell[0]), "m"(cell[0])); h = fnv(fnv(h, o), cell[0]);
        o = cell[2] = next(); uint64_t e = (i & 1) ? o : o ^ 1;  /* cas succeeds on every other round */
        ATOM("casal %x0, %x1, [%2]" : "+r"(e) : "r"(v), "r"(&cell[2]), "m"(cell[2])); h = fnv(fnv(h, e), cell[2]);
        o = cell[2] = next(); e = o; ATOM("casah %w0, %w1, [%2]" : "+r"(e) : "r"(v), "r"(&cell[2]), "m"(cell[2])); h = fnv(fnv(h, e), cell[2]);
        o = cell[2] = next(); e = (i & 1) ? o : 0; ATOM("casb %w0, %w1, [%2]" : "+r"(e) : "r"(v), "r"(&cell[2]), "m"(cell[2])); h = fnv(fnv(h, e), cell[2]);
        cell[2] = next(); cell[3] = next(); { register uint64_t x0 __asm__("x0") = cell[2], x1 __asm__("x1") = cell[3], x2 __asm__("x2") = v, x3 __asm__("x3") = ~v;
            ATOM("casp x0, x1, x2, x3, [%4]" : "+r"(x0), "+r"(x1) : "r"(x2), "r"(x3), "r"(&cell[2]), "m"(cell[2]), "m"(cell[3])); h = fnv(fnv(fnv(fnv(h, x0), x1), cell[2]), cell[3]); }
        ATOM("stadd %x0, [%1]" : : "r"(v), "r"(&cell[0]), "m"(cell[0])); h = fnv(h, cell[0]); ATOM("stsetl %x0, [%1]" : : "r"(v), "r"(&cell[1]), "m"(cell[1])); h = fnv(h, cell[1]); }
    return h;
}
static uint64_t t_excl(void)
{
    static uint64_t cell[2], cell2[2]; uint64_t h = 0, r; uint32_t w;
    LOOP(100) { cell[0] = next();
        do { __asm__ volatile("ldaxr %0, [%2]\n eor %0, %0, %3\n stlxr %w1, %0, [%2]" : "=&r"(r), "=&r"(w) : "r"(&cell[0]), "r"((uint64_t)i * 0x9e37) : "memory"); } while (w); FL("ldaxr/stlxr value", r); FL("ldaxr/stlxr cell", cell[0]);
        do { __asm__ volatile("ldxrb %w0, [%2]\n add %w0, %w0, #7\n stxrb %w1, %w0, [%2]" : "=&r"(r), "=&r"(w) : "r"(&cell[0]), "r"(0) : "memory"); } while (w); FL("ldxrb/stxrb value", r); FL("ldxrb/stxrb cell", cell[0]);
        uint64_t a, b; do { __asm__ volatile("ldxp %0, %1, [%3]\n add %0, %0, %1\n stxp %w2, %0, %1, [%3]" : "=&r"(a), "=&r"(b), "=&r"(w) : "r"(&cell[0]) : "memory"); } while (w); FL("ldxp/stxp value", a); FL("ldxp/stxp cell", cell[0]);
        __asm__ volatile("stxr %w0, %1, [%2]" : "=&r"(w) : "r"((uint64_t)5), "r"(&cell2[1]) : "memory"); FL("stxr without monitor (1)", w);
        __asm__ volatile("ldxr x9, [%1]\n clrex\n stxr %w0, %2, [%1]" : "=&r"(w) : "r"(&cell2[0]), "r"((uint64_t)6) : "x9", "memory"); FL("stxr after clrex (1)", w); }
    return h;
}
static uint64_t t_unalign(void)
{
    static uint8_t m[256] __attribute__((aligned(16))); uint64_t h = 0, v;
    LOOP(256) m[i] = (uint8_t)(next() >> 17);
    LOOP(120) { uint8_t *p = m + 1 + (i % 100);
        __asm__ volatile("ldr %0, [%1]" : "=r"(v) : "r"(p)); h = fnv(h, v); __asm__ volatile("ldrsw %0, [%1, #1]" : "=r"(v) : "r"(p)); h = fnv(h, v); __asm__ volatile("ldrsh %w0, [%1, #3]" : "=r"(v) : "r"(p)); h = fnv(h, v);
        __asm__ volatile("ldp %0, %1, [%2]" : "=&r"(v), "=&r"(h) : "r"(p)); h = fnv(v, h);  /* h clobbered on purpose: also the stored data */
        uint64_t q = next(); __asm__ volatile("str %0, [%1, #2]" :: "r"(q), "r"(p) : "memory"); h = fnv(h, *(uint64_t *)(p + 2)); __asm__ volatile("stp %0, %0, [%1, #8]!" :: "r"(q), "r"(p) : "memory"); h = fnv(h, *(uint64_t *)(p + 8));
        uint8x16_t t = vld1q_u8(p); vst1q_u8(m + 100 + (i & 7), t); h = H(h, vld1q_u8(m + 101)); }
    return h;
}

/* ---- id registers / hwcaps (info only) ---- */
static void info_guarded(void)
{
    struct sigaction sa = {.sa_handler = on_sig}; sigemptyset(&sa.sa_mask); sigaction(SIGILL, &sa, NULL);
    say("info hwcap %016lx hwcap2 %016lx", getauxval(AT_HWCAP), getauxval(AT_HWCAP2));
#define REG(name) do { uint64_t v = 0; if (sigsetjmp(jb, 1) == 0) { armed = 1; __asm__ volatile("mrs %0, " name : "=r"(v)); armed = 0; say("info %-18s %016llx", name, (unsigned long long)v); } else { armed = 0; say("info %-18s SIGILL", name); } } while (0)
    REG("midr_el1"); REG("id_aa64isar0_el1"); REG("id_aa64isar1_el1"); REG("id_aa64pfr0_el1"); REG("id_aa64mmfr0_el1"); REG("dczid_el0"); REG("ctr_el0"); REG("cntfrq_el0"); REG("id_aa64dfr0_el1");
}

/* ---- runtime-built code ---- */
static void clear(void *p, size_t n) { __builtin___clear_cache((char *)p, (char *)p + n); }
static int call_ret(void *p) { return ((int (*)(void))p)(); }
static void put_ret_n(uint32_t *c, int n) { c[0] = 0x52800000u | ((uint32_t)n << 5); c[1] = 0xd65f03c0u; }  /* movz w0, #n ; ret */
static uint64_t t_smc(void)
{
    uint64_t h = 0; size_t pg = 4096;
    uint32_t *c = mmap(NULL, 2 * pg, PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    if (c == MAP_FAILED) return 1;
    /* (a) RW -> RX, write, run; RX -> RW, patch, RX, run; repeated, with and without the cache flush (the flush is the architectural way; a translator must see the new bytes) */
    LOOP(200) { mprotect(c, pg, PROT_READ | PROT_WRITE); put_ret_n(c, i * 3 + 1); clear(c, 8); mprotect(c, pg, PROT_READ | PROT_EXEC); h = fnv(h, call_ret(c)); }
    say("smc a-mprotect-flip checked");
    /* (b) RWX, patch in place, flush, run; the function is hot (many calls) before each patch */
    mprotect(c, pg, PROT_READ | PROT_WRITE | PROT_EXEC);
    LOOP(60) { put_ret_n(c, 1000 + i); clear(c, 8); h = fnv(h, call_ret(c)); for (int k = 0; k < 3000; k++) h = fnv(h, call_ret(c) == 1000 + i); }
    /* (c) a function with a loop is patched while a copy of it ran: change its constant */
    { uint32_t *f = c + 64; f[0] = 0x52800000u; /* movz w0,#0 */ f[1] = 0x52800001u | (5u << 5); /* movz w1,#5 */ f[2] = 0x0b010000u; /* add w0,w0,w1 */ f[3] = 0x71000421u; /* subs w1,w1,#1 */ f[4] = 0x54ffffc1u; /* b.ne -8 */ f[5] = 0xd65f03c0u;
        clear(f, 24); for (int k = 0; k < 500; k++) call_ret(f); h = fnv(h, call_ret(f));
        f[1] = 0x52800001u | (9u << 5); clear(f, 24); h = fnv(h, call_ret(f)); }
    /* (d) the same page written through a second, writable alias (memfd dual mapping), executed through the RX alias */
    { int fd = syscall(SYS_memfd_create, "acediff", 0); if (fd >= 0 && ftruncate(fd, pg) == 0) {
        uint32_t *w = mmap(NULL, pg, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0), *x = mmap(NULL, pg, PROT_READ | PROT_EXEC, MAP_SHARED, fd, 0);
        if (w != MAP_FAILED && x != MAP_FAILED) { LOOP(100) { put_ret_n(w, 7000 + i); clear(w, 8); h = fnv(h, call_ret(x)); } munmap(w, pg); munmap(x, pg); }
        close(fd); } }
    /* (e) code written, run, the page made non-exec, then exec again: ACE's make-executable / run / protect cycle; also a partial page range inside a bigger mapping */
    LOOP(50) { mprotect(c + 1024, pg, PROT_READ | PROT_WRITE); put_ret_n(c + 1024, 31337 - i); clear(c + 1024, 8); mprotect(c + 1024, pg, PROT_READ | PROT_EXEC); h = fnv(h, call_ret(c + 1024)); mprotect(c + 1024, pg, PROT_NONE); mprotect(c + 1024, pg, PROT_READ | PROT_EXEC); h = fnv(h, call_ret(c + 1024)); }
    /* (f) unmap and map something else at the same address: the new code, not the old translation */
    { void *a0 = mmap(NULL, pg, PROT_READ | PROT_WRITE | PROT_EXEC, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0); put_ret_n(a0, 111); clear(a0, 8); h = fnv(h, call_ret(a0)); munmap(a0, pg);
      void *a1 = mmap(a0, pg, PROT_READ | PROT_WRITE | PROT_EXEC, MAP_PRIVATE | MAP_ANONYMOUS | MAP_FIXED, -1, 0); put_ret_n(a1, 222); clear(a1, 8); h = fnv(h, call_ret(a1) + (a1 == a0)); munmap(a1, pg); }
    /* (g) mremap'd code */
    { void *a0 = mmap(NULL, pg, PROT_READ | PROT_WRITE | PROT_EXEC, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0); put_ret_n(a0, 444); clear(a0, 8); h = fnv(h, call_ret(a0));
      void *a1 = mremap(a0, pg, 2 * pg, MREMAP_MAYMOVE); if (a1 != MAP_FAILED) { h = fnv(h, call_ret(a1)); put_ret_n(a1, 555); clear(a1, 8); h = fnv(h, call_ret(a1)); munmap(a1, 2 * pg); } }
    munmap(c, 2 * pg);
    return h;
}
static __attribute__((noinline)) uint64_t t_smc_noflush(void)  /* without the flush: result may legitimately differ on hardware; info only */
{
    uint32_t *c = mmap(NULL, 4096, PROT_READ | PROT_WRITE | PROT_EXEC, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0); uint64_t h = 0;
    LOOP(100) { put_ret_n(c, 900 + i); h = fnv(h, call_ret(c)); }
    munmap(c, 4096); return h;
}

/* ---- signals ---- */
static ucontext_t *seen_uc; static siginfo_t seen_si; static unsigned long seen_sp, seen_pc, seen_alt_lo, seen_alt_hi; static volatile int in_handler;
static void on_wild(int s, siginfo_t *si, void *ucv)
{
    ucontext_t *uc = ucv; char here; seen_si = *si; seen_sp = uc->uc_mcontext.sp; seen_pc = uc->uc_mcontext.pc; in_handler = 1; (void)s;
    seen_alt_lo = (unsigned long)altstack; seen_alt_hi = (unsigned long)altstack + sizeof altstack; seen_uc = uc;
    if ((unsigned long)&here >= seen_alt_lo && (unsigned long)&here < seen_alt_hi) in_handler = 2;  /* ran on the alternate stack */
    siglongjmp(jb, 1);
}
static void child_dead(int s) { _exit(40 + s); }
static void child_bus(int s, siginfo_t *si, void *u) { (void)s; (void)u; _exit(100 + si->si_code); }  /* SIGBUS handler: the exit status names the si_code */
static volatile uintptr_t brk_pc;
/* SIGTRAP handler of a BRK: 50 = si_code TRAP_BRKPT and si_addr = the BRK (hardware), 51 = the code only, 60 = another code (translation before 0045: SI_TKILL) */
static void child_brk(int s, siginfo_t *si, void *u) { (void)s; (void)u; _exit(si->si_code == TRAP_BRKPT ? (si->si_addr == (void *)brk_pc ? 50 : 51) : 60); }
static uint64_t t_signals(void)
{
    uint64_t h = 0; stack_t ss = {.ss_sp = altstack, .ss_size = sizeof altstack}, old_ss;
    sigaltstack(&ss, &old_ss);
    struct sigaction sa = {.sa_sigaction = on_wild, .sa_flags = SA_SIGINFO | SA_ONSTACK}; sigemptyset(&sa.sa_mask); sigaction(SIGSEGV, &sa, NULL); sigaction(SIGBUS, &sa, NULL);
    /* the wild jump of ACE: sp and pc both a small number */
    for (int round = 0; round < 3; round++) {
        in_handler = 0;
        if (sigsetjmp(jb, 1) == 0) {
            uint64_t target = round == 0 ? 0x1342 : round == 1 ? 0 : 0x1000;
            if (round == 2) { __asm__ volatile("mov x9, %0\n mov sp, x9\n ldr x0, [sp]\n" :: "r"(target) : "x0", "x9", "memory"); }  /* load through a bad sp (sp restored by siglongjmp) */
            else __asm__ volatile("mov x9, %0\n mov sp, x9\n br x9\n" :: "r"(target) : "x9", "memory");
        }
        say("signal wild-jump round %d: in_handler=%d signo=%d code=%d addr=%#lx pc=%#lx sp=%#lx", round, in_handler, seen_si.si_signo, seen_si.si_code, (unsigned long)seen_si.si_addr, round < 2 ? seen_pc : 0, seen_sp);
        h = fnv(h, in_handler);
    }
    /* sigaction round trip */
    struct sigaction a2 = {.sa_sigaction = on_wild, .sa_flags = SA_SIGINFO | SA_ONSTACK | SA_RESTART | SA_NODEFER}, a3; sigemptyset(&a2.sa_mask); sigaddset(&a2.sa_mask, SIGUSR2); sigaddset(&a2.sa_mask, SIGTERM);
    sigaction(SIGUSR1, &a2, NULL); memset(&a3, 0, sizeof a3); sigaction(SIGUSR1, NULL, &a3);
    say("signal sigaction roundtrip flags=%#x handler=%d mask-usr2=%d mask-term=%d mask-int=%d", a3.sa_flags & ~0x04000000, a3.sa_sigaction == on_wild, sigismember(&a3.sa_mask, SIGUSR2), sigismember(&a3.sa_mask, SIGTERM), sigismember(&a3.sa_mask, SIGINT));
    stack_t cur; sigaltstack(NULL, &cur); say("signal altstack roundtrip size=%zu flags=%d same=%d", cur.ss_size, cur.ss_flags, cur.ss_sp == altstack);
    /* SIGSEGV without SA_ONSTACK, sp bad, in a child: the kernel cannot build the frame and kills the thread with SIGSEGV */
    pid_t pid = fork();
    if (pid == 0) { NOCORE; struct sigaction s2 = {.sa_handler = child_dead}; sigemptyset(&s2.sa_mask); sigaction(SIGSEGV, &s2, NULL); sigaltstack(&(stack_t){.ss_flags = SS_DISABLE}, NULL);
        __asm__ volatile("mov x9, #0x1342\n mov sp, x9\n br x9\n" ::: "x9", "memory"); _exit(7); }
    if (pid > 0) { int st = 0; waitpid(pid, &st, 0); say("signal bad-sp no-altstack child: %s %d", WIFSIGNALED(st) ? "killed by signal" : "exited", WIFSIGNALED(st) ? WTERMSIG(st) : WEXITSTATUS(st)); }
    /* a handler address that is not 4-aligned (ACE's handler 0x...63ba): the PC alignment fault is a SIGBUS (BUS_ADRALN) the child's SIGBUS handler sees (its default action is the JVM's crash report under Berberis, not compared) */
    pid = fork();
    if (pid == 0) { NOCORE; struct sigaction sb = {.sa_sigaction = child_bus, .sa_flags = SA_SIGINFO}; sigemptyset(&sb.sa_mask); sigaction(SIGBUS, &sb, NULL); struct sigaction s3 = {.sa_flags = 0}; s3.sa_handler = (void (*)(int))((uintptr_t)on_wild | 2); sigemptyset(&s3.sa_mask); sigaction(SIGUSR1, &s3, NULL); raise(SIGUSR1); _exit(7); }
    if (pid > 0) { int st = 0; waitpid(pid, &st, 0); say("signal misaligned-handler child: %s %d", WIFSIGNALED(st) ? "killed by signal" : "exited", WIFSIGNALED(st) ? WTERMSIG(st) : WEXITSTATUS(st)); }
    /* BRK #imm: SIGTRAP with the architectural siginfo (apklayer 0045) */
    pid = fork();
    if (pid == 0) { NOCORE; struct sigaction sk = {.sa_sigaction = child_brk, .sa_flags = SA_SIGINFO}; sigemptyset(&sk.sa_mask); sigaction(SIGTRAP, &sk, NULL);
        __asm__ volatile("adr x9, 1f\n str x9, [%0]\n 1: brk #0x123\n" :: "r"(&brk_pc) : "x9", "memory"); _exit(7); }
    if (pid > 0) { int st = 0; waitpid(pid, &st, 0); say("signal brk-siginfo child: %s %d", WIFSIGNALED(st) ? "killed by signal" : "exited", WIFSIGNALED(st) ? WTERMSIG(st) : WEXITSTATUS(st)); }
    /* a signal the process sends itself, with the default disposition (reset with signal(), the handler run() left is gone): the process ends with that signal
     * (WIFSIGNALED, the same number), not in HotSpot's crash handler (SIGABRT) */
    static const int dsigs[] = {SIGSEGV, SIGBUS, SIGILL, SIGFPE, SIGABRT}; static const char *dvia[] = {"kill", "raise", "tgkill", "abort"};
    for (int si = 0; si < 5; si++) for (int via = 0; via < 4; via++) {
        if (via == 3 && dsigs[si] != SIGABRT) continue;
        pid = fork();
        if (pid == 0) { NOCORE; signal(dsigs[si], SIG_DFL);
            if (via == 0) kill(getpid(), dsigs[si]); else if (via == 1) raise(dsigs[si]); else if (via == 2) syscall(SYS_tgkill, getpid(), gettid(), dsigs[si]); else abort();
            _exit(7); }
        if (pid > 0) { int st = 0; waitpid(pid, &st, 0); say("signal default-action %s via %s: %s %d", strsignal(dsigs[si]), dvia[via], WIFSIGNALED(st) ? "killed by signal" : "exited", WIFSIGNALED(st) ? WTERMSIG(st) : WEXITSTATUS(st)); }
    }
    /* a pc fault at a misaligned address: br, blr and ret to it (hardware: SIGBUS BUS_ADRALN, si_addr = the target); a mapped non-executable page is SEGV_ACCERR */
    { uintptr_t t = (uintptr_t)t_signals | 2; static const char *K[3] = {"br", "blr", "ret"};
      for (int k = 0; k < 3; k++) {
          in_handler = 0; memset(&seen_si, 0, sizeof seen_si);
          if (sigsetjmp(jb, 1) == 0) {
              if (k == 0) __asm__ volatile("mov x9, %0\n br x9\n" :: "r"(t) : "x9");
              else if (k == 1) __asm__ volatile("mov x9, %0\n blr x9\n" :: "r"(t) : "x9", "x30");
              else __asm__ volatile("mov x30, %0\n ret\n" :: "r"(t) : "x30");
          }
          say("signal %s-misaligned: in_handler=%d signo=%d code=%d addr==target %d", K[k], in_handler, seen_si.si_signo, seen_si.si_code, (uintptr_t)seen_si.si_addr == t);
      }
      void *np = mmap(NULL, 4096, PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
      in_handler = 0; memset(&seen_si, 0, sizeof seen_si);
      if (sigsetjmp(jb, 1) == 0) { __asm__ volatile("mov x9, %0\n br x9\n" :: "r"(np) : "x9"); }
      say("signal br-noexec-page: in_handler=%d signo=%d code=%d addr==target %d", in_handler, seen_si.si_signo, seen_si.si_code, seen_si.si_addr == np);
      munmap(np, 4096); }
    sigaltstack(&old_ss, NULL);
    return h;
}

/* acediff_nan.c, acediff_sys.c */
uint64_t t_nan(void), t_nan_inv(void), t_nan_dn(void), t_nan_nn(void);
uint64_t t_fp16(void), t_misc(void), t_svc(void), t_sysregs(void), t_threads(void), t_selftext(void), t_munmap_views(void);
void timing(void), costs(void);

const char *acediff_run(void)
{
    on = 0; out[0] = 0;
    info_guarded(); timing(); costs();
    run("aes", t_aes); run("sha1", t_sha1); run("sha256", t_sha256); run("sha512", t_sha512); run("sha3 eor3/rax1/xar/bcax", t_sha3); run("sm4", t_sm4); run("pmull", t_pmull); run("crc32", t_crc);
    run("tbl/tbx", t_tbl); run("shuffles/ext/rev/cnt", t_shuffle); run("neon int arith", t_intarith); run("ld1-4/st1-4", t_ldst);
    run("float finite", t_float_fin); run("float any bits", t_float_any); run("fpcr/fpsr", t_fpcr); run("nan rules", t_nan); run("nan rules invalid operations", t_nan_inv); run("nan rules FPCR.DN", t_nan_dn); run("nan rules two NaN operands", t_nan_nn); run("scalar int/flags", t_scalar); run("lse atomics", t_lse); run("exclusives", t_excl); run("unaligned", t_unalign);
    run("fp16", t_fp16); run("lrcpc/dc/ic/barriers", t_misc); run("raw svc syscalls", t_svc); run("mrs readability", t_sysregs); run("threads", t_threads); run("text remapped from its file", t_selftext); run("views after a munmap of an own block", t_munmap_views);
    run("smc (flush)", t_smc); run("info smc (no flush)", t_smc_noflush); run("signals", t_signals);
    return out;
}

#if defined(__ANDROID__) && !defined(ACEDIFF_MAIN)  /* -DACEDIFF_MAIN -static: the same bionic build as a plain program (compare.sh) */
#include <jni.h>
JNIEXPORT jstring JNICALL Java_hello_acediff_Main_run(JNIEnv *env, jclass c) { (void)c; return (*env)->NewStringUTF(env, acediff_run()); }
#else
int main(void) { fputs(acediff_run(), stdout); return 0; }
#endif
#ifdef __clang__
#pragma clang attribute pop
#endif
