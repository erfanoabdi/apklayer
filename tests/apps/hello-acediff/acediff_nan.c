/* acediff, part 3: the NaN a floating-point instruction returns (positive default NaN, first signalling then first quiet operand quieted, FMLS/FNMADD operand negation, FMAXNM's
 * quiet-NaN rule) and FPCR.DN, for scalar and vector single/double arithmetic. Each op is its own hash line over 200 NaN-rich operand sets, long enough for the translators to take over from
 * the interpreter. Inline asm: the compiler must not swap the operands of a commutative instruction. */
#include "acediff.h"

/* mode 3 ("nn "): mode 0 with every NaN operand as drawn. mode 0: NaN operands in every position (no infinities: no invalid operation, so no default NaN is made); 1 ("inv "): infinities and negative square roots too, the
 * default NaN of an invalid operation; 2 ("dn "): mode 0 with FPCR.DN set */
static int mode;
#define LB(l) (mode == 1 ? "inv " l : mode == 2 ? "dn " l : mode == 3 ? "nn " l : l)
static uint32_t nv32(void)
{
    static const uint32_t sp[] = {0, 0x80000000, 0x3f800000, 0xbf800000, 0x40000000, 0x40400000, 0x7f800000, 0xff800000, 0x7fc00000, 0xffc00000, 0x7fa00000, 0xffa00000, 0x7f800001, 0x00000001, 0x80400000};
    uint64_t r = next(); uint32_t v = sp[(r >> 8) % 15];
    if (v != 0x7f800000 && v != 0xff800000 && (v & 0x7f800000) == 0x7f800000) v = (v & 0xffc00000) | ((r >> 40) & 0x3ff) | 1;  /* a NaN: random payload */
    if (mode != 1 && (v & 0x7fffffff) == 0x7f800000) v = 0x40400000;
    return (r & 7) == 0 ? (uint32_t)(r >> 32) : v;
}
static uint64_t nv64(void)
{
    static const uint64_t sp[] = {0, 1ull << 63, 0x3ff0000000000000ull, 0xbff0000000000000ull, 0x4000000000000000ull, 0x4008000000000000ull, 0x7ff0000000000000ull, 0xfff0000000000000ull,
        0x7ff8000000000000ull, 0xfff8000000000000ull, 0x7ff4000000000000ull, 0xfff4000000000000ull, 0x7ff0000000000001ull, 1, 0x8008000000000000ull};
    uint64_t r = next(), v = sp[(r >> 8) % 15];
    if (v != 0x7ff0000000000000ull && v != 0xfff0000000000000ull && (v & 0x7ff0000000000000ull) == 0x7ff0000000000000ull) v = (v & 0xfff8000000000000ull) | ((r >> 40) & 0xfffff) | 1;
    if (mode != 1 && (v & 0x7fffffffffffffffull) == 0x7ff0000000000000ull) v = 0x4008000000000000ull;
    return (r & 7) == 0 ? next() : v;
}
static float32x4_t nf4(void) { uint32_t a[4]; for (int i = 0; i < 4; i++) a[i] = nv32(); return vld1q_f32((float *)a); }
static float64x2_t nd2(void) { uint64_t a[2] = {nv64(), nv64()}; return vld1q_f64((double *)a); }

/* fnv's multiply never carries a difference in bit 63 downwards: an even number of wrong sign bits would hash alike. Rotating by one puts the sign at the bottom. */
#define ROT(v) ((((uint64_t)(v)) << 1) | (((uint64_t)(v)) >> 63))
static uint64_t HRV(uint64_t h, uint8x16_t v) { return fnv(fnv(h, ROT(vgetq_lane_u64(vreinterpretq_u64_u8(v), 0))), ROT(vgetq_lane_u64(vreinterpretq_u64_u8(v), 1))); }
static uint64_t HRF(uint64_t h, float32x4_t v) { return HRV(h, vreinterpretq_u8_f32(v)); }
static uint64_t HRD(uint64_t h, float64x2_t v) { return HRV(h, vreinterpretq_u8_f64(v)); }
#define S1(l, T, R, I, a) do { T r_; __asm__ volatile(I " %" R "0, %" R "1" : "=w"(r_) : "w"(a)); FL(LB(l), ROT(_Generic(r_, float: BC32, double: BC64)(r_))); } while (0)
#define S2(l, T, R, I, a, b) do { T r_; __asm__ volatile(I " %" R "0, %" R "1, %" R "2" : "=w"(r_) : "w"(a), "w"(b)); FL(LB(l), ROT(_Generic(r_, float: BC32, double: BC64)(r_))); } while (0)
#define S3(l, T, R, I, a, b, c) do { T r_; __asm__ volatile(I " %" R "0, %" R "1, %" R "2, %" R "3" : "=w"(r_) : "w"(a), "w"(b), "w"(c)); FL(LB(l), ROT(_Generic(r_, float: BC32, double: BC64)(r_))); } while (0)
#define V2(l, T, HF_, A, I, a, b) do { T r_; __asm__ volatile(I " %0." A ", %1." A ", %2." A : "=w"(r_) : "w"(a), "w"(b)); *slot(LB(l)) = HF_(*slot(LB(l)), r_); } while (0)
#define V1(l, T, HF_, A, I, a) do { T r_; __asm__ volatile(I " %0." A ", %1." A : "=w"(r_) : "w"(a)); *slot(LB(l)) = HF_(*slot(LB(l)), r_); } while (0)
#define VA(l, T, HF_, A, I, a, b, c) do { T r_ = c; __asm__ volatile(I " %0." A ", %1." A ", %2." A : "+w"(r_) : "w"(a), "w"(b)); *slot(LB(l)) = HF_(*slot(LB(l)), r_); } while (0)
#define VX(l, T, HF_, A, I, a, b, E) do { T r_ = c4; __asm__ volatile(I " %0." A ", %1." A ", %2." E "[1]" : "+w"(r_) : "w"(a), "w"(b)); *slot(LB(l)) = HF_(*slot(LB(l)), r_); } while (0)

/* Two NaN operands of different kinds: Arm returns the first signalling one, x86's translated FADD/FSUB/FMUL/FDIV/FMA return the first operand. The instructions the translators
 * handle exactly (FMAX/FMIN/FMAXNM/FMINNM, and FMLS/FRECPS/FRSQRTS in the interpreter) get every operand as drawn; the others (the second group below) get at most one NaN
 * among their operands, except in mode 3 ("nn "), which gives them all and is a known difference. FADDP and the by-element FMLA/FMLS pair operands of different lanes, and the
 * translated scalar FMSUB/FNMADD/FNMSUB keep a NaN operand's sign where Arm negates it first: those run only in the modes whose lines are known differences. */
static int isn32(uint32_t u) { return (u & 0x7fffffff) > 0x7f800000; }
static int isn64(uint64_t u) { return (u & 0x7fffffffffffffffull) > 0x7ff0000000000000ull; }
static void one32(uint32_t *a, uint32_t *b, uint32_t *c)
{
    if (isn32(*a)) { if (isn32(*b)) *b = 0x3fc00000; if (isn32(*c)) *c = 0x3fc00000; } else if (isn32(*b) && isn32(*c)) *c = 0x3fc00000;
}
static void one64(uint64_t *a, uint64_t *b, uint64_t *c)
{
    if (isn64(*a)) { if (isn64(*b)) *b = 0x3ff8000000000000ull; if (isn64(*c)) *c = 0x3ff8000000000000ull; } else if (isn64(*b) && isn64(*c)) *c = 0x3ff8000000000000ull;
}

static uint64_t nan_body(int m)
{
    mode = m;
    uint64_t fpcr0; __asm__ volatile("mrs %0, fpcr" : "=r"(fpcr0));
    if (mode == 2) { uint64_t v = fpcr0 | (1ull << 25); __asm__ volatile("msr fpcr, %0" :: "r"(v)); }
    const int single = mode == 0 || mode == 2;  /* the second group: at most one NaN among the operands */
    LOOP(200) {
        uint32_t ua = nv32(), ub = nv32(), uc = nv32(), pa = ua, pb = ub, pc = uc; uint64_t da = nv64(), db = nv64(), dc = nv64(), qa = da, qb = db, qc = dc;
        if (single) { one32(&pa, &pb, &pc); one64(&qa, &qb, &qc); }
        float a = BCF(ua), b = BCF(ub), a1 = BCF(pa), b1 = BCF(pb), c1 = BCF(pc); (void)uc;
        double x, y, x1, y1, z1; memcpy(&x, &da, 8); memcpy(&y, &db, 8); memcpy(&x1, &qa, 8); memcpy(&y1, &qb, 8); memcpy(&z1, &qc, 8); (void)dc;
        /* the exact group */
        S2("s fmax", float, "s", "fmax", a, b); S2("s fmin", float, "s", "fmin", a, b); S2("s fmaxnm", float, "s", "fmaxnm", a, b); S2("s fminnm", float, "s", "fminnm", a, b);
        S2("s frecps", float, "s", "frecps", a, b); S2("s frsqrts", float, "s", "frsqrts", a, b);
        S2("d fmax", double, "d", "fmax", x, y); S2("d fmin", double, "d", "fmin", x, y); S2("d fmaxnm", double, "d", "fmaxnm", x, y); S2("d fminnm", double, "d", "fminnm", x, y);
        S2("d frecps", double, "d", "frecps", x, y); S2("d frsqrts", double, "d", "frsqrts", x, y);
        /* the second group */
        S2("s fadd", float, "s", "fadd", a1, b1); S2("s fsub", float, "s", "fsub", a1, b1); S2("s fmul", float, "s", "fmul", a1, b1); S2("s fdiv", float, "s", "fdiv", a1, (mode == 1 || b1 != 0) ? b1 : 1.5f);
        S2("s fnmul", float, "s", "fnmul", a1, b1); S2("s fabd", float, "s", "fabd", a1, b1); S2("s fmulx", float, "s", "fmulx", a1, b1); S1("s fsqrt", float, "s", "fsqrt", mode == 1 ? a1 : __builtin_fabsf(a1));
        S3("s fmadd", float, "s", "fmadd", a1, b1, c1); if (!single) { S3("s fmsub", float, "s", "fmsub", a1, b1, c1); S3("s fnmadd", float, "s", "fnmadd", a1, b1, c1); S3("s fnmsub", float, "s", "fnmsub", a1, b1, c1); }
        S2("d fadd", double, "d", "fadd", x1, y1); S2("d fsub", double, "d", "fsub", x1, y1); S2("d fmul", double, "d", "fmul", x1, y1); S2("d fdiv", double, "d", "fdiv", x1, (mode == 1 || y1 != 0) ? y1 : 1.5);
        S2("d fnmul", double, "d", "fnmul", x1, y1); S1("d fsqrt", double, "d", "fsqrt", mode == 1 ? x1 : __builtin_fabs(x1));
        S3("d fmadd", double, "d", "fmadd", x1, y1, z1); if (!single) { S3("d fmsub", double, "d", "fmsub", x1, y1, z1); S3("d fnmadd", double, "d", "fnmadd", x1, y1, z1); S3("d fnmsub", double, "d", "fnmsub", x1, y1, z1); }
        uint32_t la[4], lb[4], lc[4], ma[4], mb[4], mc[4]; uint64_t ea[2], eb[2], ec[2], na[2], nb[2], nc[2];
        for (int i = 0; i < 4; i++) { la[i] = ma[i] = nv32(); lb[i] = mb[i] = nv32(); lc[i] = mc[i] = nv32(); if (single) one32(&ma[i], &mb[i], &mc[i]); }
        for (int i = 0; i < 2; i++) { ea[i] = na[i] = nv64(); eb[i] = nb[i] = nv64(); ec[i] = nc[i] = nv64(); if (single) one64(&na[i], &nb[i], &nc[i]); }
        float32x4_t fa = vld1q_f32((float *)la), fb = vld1q_f32((float *)lb), fc = vld1q_f32((float *)lc), ga = vld1q_f32((float *)ma), gb = vld1q_f32((float *)mb), gc = vld1q_f32((float *)mc);
        float64x2_t xa = vld1q_f64((double *)ea), xb = vld1q_f64((double *)eb), xc = vld1q_f64((double *)ec), ya = vld1q_f64((double *)na), yb = vld1q_f64((double *)nb), yc = vld1q_f64((double *)nc);
        float32x4_t c4 = gc;
        V2("4s fmax", float32x4_t, HRF, "4s", "fmax", fa, fb); V2("4s fmin", float32x4_t, HRF, "4s", "fmin", fa, fb); V2("4s fmaxnm", float32x4_t, HRF, "4s", "fmaxnm", fa, fb); V2("4s fminnm", float32x4_t, HRF, "4s", "fminnm", fa, fb);
        V2("4s frecps", float32x4_t, HRF, "4s", "frecps", fa, fb); V2("4s frsqrts", float32x4_t, HRF, "4s", "frsqrts", fa, fb); VA("4s fmls", float32x4_t, HRF, "4s", "fmls", fa, fb, fc);
        V2("4s fmaxp", float32x4_t, HRF, "4s", "fmaxp", fa, fb); V2("4s fmaxnmp", float32x4_t, HRF, "4s", "fmaxnmp", fa, fb);
        V2("2d fmax", float64x2_t, HRD, "2d", "fmax", xa, xb); V2("2d fmin", float64x2_t, HRD, "2d", "fmin", xa, xb); V2("2d fmaxnm", float64x2_t, HRD, "2d", "fmaxnm", xa, xb); V2("2d fminnm", float64x2_t, HRD, "2d", "fminnm", xa, xb);
        V2("2d frecps", float64x2_t, HRD, "2d", "frecps", xa, xb); V2("2d frsqrts", float64x2_t, HRD, "2d", "frsqrts", xa, xb); VA("2d fmls", float64x2_t, HRD, "2d", "fmls", xa, xb, xc);
        V2("4s fadd", float32x4_t, HRF, "4s", "fadd", ga, gb); V2("4s fsub", float32x4_t, HRF, "4s", "fsub", ga, gb); V2("4s fmul", float32x4_t, HRF, "4s", "fmul", ga, gb); V2("4s fdiv", float32x4_t, HRF, "4s", "fdiv", ga, mode == 1 ? gb : vbslq_f32(vceqzq_f32(gb), vdupq_n_f32(1.5f), gb));
        V2("4s fabd", float32x4_t, HRF, "4s", "fabd", ga, gb); V2("4s fmulx", float32x4_t, HRF, "4s", "fmulx", ga, gb); if (!single) V2("4s faddp", float32x4_t, HRF, "4s", "faddp", ga, gb);
        V1("4s fsqrt", float32x4_t, HRF, "4s", "fsqrt", mode == 1 ? ga : vabsq_f32(ga));
        VA("4s fmla", float32x4_t, HRF, "4s", "fmla", ga, gb, gc); if (!single) { VX("4s fmla idx", float32x4_t, HRF, "4s", "fmla", ga, gb, "s"); VX("4s fmls idx", float32x4_t, HRF, "4s", "fmls", ga, gb, "s"); }
        V2("2d fadd", float64x2_t, HRD, "2d", "fadd", ya, yb); V2("2d fmul", float64x2_t, HRD, "2d", "fmul", ya, yb); V2("2d fdiv", float64x2_t, HRD, "2d", "fdiv", ya, mode == 1 ? yb : vbslq_f64(vceqzq_f64(yb), vdupq_n_f64(1.5), yb));
        V1("2d fsqrt", float64x2_t, HRD, "2d", "fsqrt", mode == 1 ? ya : vabsq_f64(ya));
        VA("2d fmla", float64x2_t, HRD, "2d", "fmla", ya, yb, yc);
    }
    __asm__ volatile("msr fpcr, %0" :: "r"(fpcr0));
    return 0;
}
uint64_t t_nan(void) { return nan_body(0); }
uint64_t t_nan_inv(void) { return nan_body(1); }
uint64_t t_nan_dn(void) { return nan_body(2); }
uint64_t t_nan_nn(void) { return nan_body(3); }
#ifdef __clang__
#pragma clang attribute pop
#endif
