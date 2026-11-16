/* Unity-style compute kernels written with NEON intrinsics and plain C, compiled -O2 (loops the heavy tier captures): matrix/quaternion/skinning math, half-float and byte swizzle,
 * saturating narrowing, SAD, compare/select, reciprocal estimates, 4 register loads/stores, scalar integer and float code. One "[nd] k_<name> <hash>" line each. */
#include <arm_neon.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <math.h>
#include <stdlib.h>
#include <time.h>

#pragma clang attribute push(__attribute__((target("+fp16,+dotprod,+aes,+sha2,+crc,+lse,+rcpc,+fp16fml,+jsconv,+rdm,+sha3,+sm4,+complxnum,+fptoint,+flagm,+rcpc-immo"))), apply_to = function)
static uint64_t S;
static uint32_t r32(void) { S ^= S >> 12; S ^= S << 25; S ^= S >> 27; return (uint32_t)((S * 0x2545F4914F6CDD1Dull) >> 32); }
static float rf(void) { return (float)((int32_t)r32() >> 8) / 65536.0f; }
static uint64_t H;
static void mix(uint64_t w) { H = (H ^ w) * 0x100000001b3ull; H ^= H >> 29; }
static void mixf(float32x4_t v) { uint32_t b[4]; memcpy(b, &v, 16); for (int i = 0; i < 4; i++) mix(b[i]); }
static void mixu(uint8x16_t v) { uint64_t b[2]; memcpy(b, &v, 16); mix(b[0]); mix(b[1]); }

typedef struct { float32x4_t c[4]; } m4;
static m4 mmul(m4 a, m4 b) { m4 r; for (int j = 0; j < 4; j++) { float32x4_t x = vmulq_laneq_f32(a.c[0], b.c[j], 0); x = vfmaq_laneq_f32(x, a.c[1], b.c[j], 1); x = vfmaq_laneq_f32(x, a.c[2], b.c[j], 2); r.c[j] = vfmaq_laneq_f32(x, a.c[3], b.c[j], 3); } return r; }
static m4 rm(void) { m4 m; for (int j = 0; j < 4; j++) { float t[4] = {rf(), rf(), rf(), rf()}; m.c[j] = vld1q_f32(t); } return m; }
static void k_mat4(int n) { for (int i = 0; i < n; i++) { m4 a = rm(), b = rm(), c = mmul(a, b); for (int j = 0; j < 4; j++) mixf(c.c[j]); } }
static void k_quat(int n) {
  for (int i = 0; i < n; i++) {
    float32x4_t a = {rf(), rf(), rf(), rf()}, b = {rf(), rf(), rf(), rf()};
    float32x4_t r = vmulq_laneq_f32(vextq_f32(b, b, 3), a, 0); r = vfmsq_laneq_f32(r, b, a, 3); r = vfmaq_laneq_f32(r, vrev64q_f32(b), a, 1); r = vfmaq_laneq_f32(r, vcombine_f32(vget_high_f32(b), vget_low_f32(b)), a, 2);
    float32x4_t sq = vmulq_f32(r, r); float s = vaddvq_f32(sq); float32x4_t e = vrsqrteq_f32(vdupq_n_f32(s)); e = vmulq_f32(e, vrsqrtsq_f32(vmulq_f32(vdupq_n_f32(s), e), e));
    mixf(vmulq_f32(r, e)); mixf(vdivq_f32(r, vdupq_n_f32(s + 1.0f))); mixf(vsqrtq_f32(vabsq_f32(r)));
  }
}
static void k_skin(int n) {  /* 4 bone matrices, 64 vertices, ld1 x4 / st1 x4 */
  static float vtx[64 * 4], out[64 * 4];
  for (int i = 0; i < n; i++) {
    m4 b[4]; for (int k = 0; k < 4; k++) b[k] = rm();
    for (int v = 0; v < 64 * 4; v++) vtx[v] = rf();
    for (int v = 0; v < 64; v++) {
      float32x4_t p = vld1q_f32(vtx + v * 4), acc = vdupq_n_f32(0); float w[4] = {0.4f, 0.3f, 0.2f, 0.1f};
      for (int k = 0; k < 4; k++) { float32x4_t t = vmulq_laneq_f32(b[k].c[0], p, 0); t = vfmaq_laneq_f32(t, b[k].c[1], p, 1); t = vfmaq_laneq_f32(t, b[k].c[2], p, 2); t = vaddq_f32(t, b[k].c[3]); acc = vfmaq_n_f32(acc, t, w[k]); }
      vst1q_f32(out + v * 4, acc);
    }
    float32x4x4_t q = vld4q_f32(out); mixf(q.val[0]); mixf(q.val[1]); mixf(q.val[2]); mixf(q.val[3]); vst4q_f32(vtx, q); mixf(vld1q_f32(vtx + 4));
    for (int v = 0; v < 256; v += 16) { float32x4x4_t z = vld1q_f32_x4(out + v); vst1q_f32_x4(vtx + v, z); mixf(z.val[3]); }
  }
}
static void k_half(int n) {
  for (int i = 0; i < n; i++) {
    float32x4_t a = {rf(), rf() * 1e4f, rf() * 1e-6f, rf()}; float16x4_t h = vcvt_f16_f32(a); float32x4_t b = vcvt_f32_f16(h); mixf(b);
    uint16_t hb[4]; memcpy(hb, &h, 8); mix(hb[0] | (uint64_t)hb[1] << 16 | (uint64_t)hb[2] << 32 | (uint64_t)hb[3] << 48);
    float16x8_t h8 = vcombine_f16(h, vcvt_f16_f32(vmulq_n_f32(a, 3.3f))); mixf(vcvt_high_f32_f16(h8)); mixf(vcvt_f32_f16(vget_low_f16(h8)));
    mixf(vcvt_f32_f16(vcvt_f16_f32(vdupq_n_f32(65519.0f + (float)(r32() & 31)))));
  }
}
static void k_swizzle(int n) {  /* RGBA8 <-> float, byte shuffles, saturating narrow */
  static const uint8_t idx[16] = {2, 1, 0, 3, 6, 5, 4, 7, 10, 9, 8, 11, 14, 13, 12, 15}, idx2[16] = {0, 4, 8, 12, 1, 5, 9, 13, 2, 6, 10, 14, 3, 7, 11, 15};
  for (int i = 0; i < n; i++) {
    uint8_t d[16]; for (int k = 0; k < 16; k++) d[k] = (uint8_t)r32();
    uint8x16_t v = vld1q_u8(d), s = vqtbl1q_u8(v, vld1q_u8(idx)); mixu(s); mixu(vqtbl1q_u8(s, vld1q_u8(idx2))); mixu(vzip1q_u8(v, s)); mixu(vzip2q_u8(v, s)); mixu(vuzp1q_u8(v, s)); mixu(vuzp2q_u8(v, s)); mixu(vtrn1q_u8(v, s)); mixu(vextq_u8(v, s, 5));
    uint16x8_t w = vmovl_u8(vget_low_u8(v)); uint32x4_t u = vmovl_u16(vget_low_u16(w)); float32x4_t f = vmulq_n_f32(vcvtq_f32_u32(u), 1.0f / 255.0f); mixf(f);
    uint32x4_t back = vcvtq_u32_f32(vmulq_n_f32(vminq_f32(vmaxq_f32(vaddq_f32(f, vdupq_n_f32(rf() * 0.01f)), vdupq_n_f32(0)), vdupq_n_f32(1)), 255.0f + 0.5f));
    mixu(vcombine_u8(vqmovn_u16(vcombine_u16(vqmovn_u32(back), vqmovn_u32(vshrq_n_u32(u, 3)))), vqmovun_s16(vreinterpretq_s16_u16(w)))); mixu(vreinterpretq_u8_u32(vcvtnq_u32_f32(f))); mixu(vreinterpretq_u8_s32(vcvtq_s32_f32(vmulq_n_f32(f, -300.f))));
    mixu(vreinterpretq_u8_u16(vshll_n_u8(vget_high_u8(v), 3))); mixu(vcombine_u8(vshrn_n_u16(w, 4), vrshrn_n_u16(w, 5))); mixu(vcombine_u8(vqshrn_n_u16(w, 2), vqrshrun_n_s16(vreinterpretq_s16_u16(w), 3)));
    mixu(vbslq_u8(vcgtq_u8(v, s), v, s)); mixu(vcltq_u8(v, s)); mixu(vbicq_u8(v, s)); mixu(veorq_u8(vrev32q_u8(v), vclzq_u8(s))); mixu(vcntq_u8(v)); mixu(vrbitq_u8(v));
  }
}
static void k_int(int n) {
  for (int i = 0; i < n; i++) {
    int16_t a[8], b[8]; for (int k = 0; k < 8; k++) { a[k] = (int16_t)r32(); b[k] = (int16_t)r32(); }
    int16x8_t x = vld1q_s16(a), y = vld1q_s16(b); int32x4_t acc = vdupq_n_s32((int32_t)r32());
    acc = vmlal_s16(acc, vget_low_s16(x), vget_low_s16(y)); acc = vmlal_high_s16(acc, x, y); acc = vqdmlal_s16(acc, vget_high_s16(x), vget_low_s16(y)); acc = vmlsl_s16(acc, vget_low_s16(y), vget_high_s16(x));
    mixu(vreinterpretq_u8_s32(acc)); mixu(vreinterpretq_u8_s16(vqdmulhq_s16(x, y))); mixu(vreinterpretq_u8_s16(vqrdmulhq_s16(x, y))); mixu(vreinterpretq_u8_s16(vqaddq_s16(x, y))); mixu(vreinterpretq_u8_s16(vqsubq_s16(x, y)));
    mixu(vreinterpretq_u8_s16(vqabsq_s16(x))); mixu(vreinterpretq_u8_s16(vqnegq_s16(y))); mixu(vreinterpretq_u8_s16(vhaddq_s16(x, y))); mixu(vreinterpretq_u8_s16(vrhaddq_s16(x, y))); mixu(vreinterpretq_u8_s16(vabdq_s16(x, y)));
    mixu(vreinterpretq_u8_s16(vqshlq_s16(x, vshrq_n_s16(y, 12)))); mixu(vreinterpretq_u8_s16(vrshlq_s16(x, vshrq_n_s16(y, 12)))); mixu(vreinterpretq_u8_s32(vpaddlq_s16(x))); mixu(vreinterpretq_u8_s32(vpadalq_s16(acc, y)));
    mix((uint64_t)vaddvq_s16(x)); mix((uint64_t)vaddlvq_s16(y)); mix(vmaxvq_s16(x) | (uint64_t)vminvq_s16(y) << 16); mix(vaddlvq_u8(vreinterpretq_u8_s16(x)));
    uint8x16_t u = vreinterpretq_u8_s16(x), w = vreinterpretq_u8_s16(y); uint16x8_t sad = vabdl_u8(vget_low_u8(u), vget_low_u8(w)); sad = vabal_high_u8(sad, u, w); mixu(vreinterpretq_u8_u16(sad)); mix(vaddvq_u16(sad));
    mixu(vreinterpretq_u8_p16(vmull_p8(vreinterpret_p8_u8(vget_low_u8(u)), vreinterpret_p8_u8(vget_low_u8(w))))); mixu(vreinterpretq_u8_u32(vmull_u16(vget_low_u16(vreinterpretq_u16_u8(u)), vget_high_u16(vreinterpretq_u16_u8(w)))));
  }
}
static void k_fp(int n) {
  for (int i = 0; i < n; i++) {
    float32x4_t a = {rf(), rf(), rf(), rf()}, b = {rf(), rf(), rf(), rf()}, c = {rf(), rf(), rf(), rf()};
    uint32x4_t m = vcgtq_f32(a, b); mixf(vbslq_f32(m, a, b)); mixf(vfmaq_f32(c, a, b)); mixf(vfmsq_f32(c, a, b)); mixf(vmlaq_f32(c, a, b)); mixf(vminq_f32(a, b)); mixf(vmaxnmq_f32(a, c)); mixf(vabdq_f32(a, c));
    float32x4_t r = vrecpeq_f32(a); r = vmulq_f32(r, vrecpsq_f32(a, r)); mixf(r); mixf(vrndnq_f32(vmulq_n_f32(a, 3.5f))); mixf(vrndmq_f32(b)); mixf(vrndpq_f32(b)); mixf(vrndq_f32(c)); mixf(vrndaq_f32(vmulq_n_f32(c, 7.5f)));
    mixf(vcvtq_f32_s32(vcvtq_s32_f32(vmulq_n_f32(a, 1000.f)))); mixf(vcvtq_n_f32_s32(vcvtq_n_s32_f32(b, 8), 8)); mixf(vpaddq_f32(a, b)); mixf(vpmaxq_f32(a, c)); mixf(vnegq_f32(vabsq_f32(a)));
    mixf(vreinterpretq_f32_u32(vcgeq_f32(a, vdupq_n_f32(0)))); mixf(vreinterpretq_f32_u32(vcaltq_f32(a, b))); mixf(vreinterpretq_f32_u32(vceqzq_f32(vsubq_f32(a, a))));
    float64x2_t d = {(double)rf(), (double)rf()}, e = {(double)rf(), (double)rf()}; uint64_t t[2]; float64x2_t q = vfmaq_f64(d, d, e); memcpy(t, &q, 16); mix(t[0]); mix(t[1]);
    q = vdivq_f64(d, vaddq_f64(vabsq_f64(e), vdupq_n_f64(1.0))); memcpy(t, &q, 16); mix(t[0]); mix(t[1]); q = vcvt_f64_f32(vget_low_f32(a)); memcpy(t, &q, 16); mix(t[0]); mix(t[1]);
    mixf(vcombine_f32(vcvt_f32_f64(d), vcvt_f32_f64(e))); mix((uint64_t)vgetq_lane_f32(a, 2) == 0); mix((uint64_t)(vaddvq_f64(d) > vaddvq_f64(e)));
  }
}
static __attribute__((noinline)) int cmpf(const void *a, const void *b) { float x = *(const float *)a, y = *(const float *)b; return (x > y) - (x < y); }
static void k_scalar(int n) {
  for (int i = 0; i < n; i++) {
    float v[16]; for (int k = 0; k < 16; k++) v[k] = rf();
    float s = 0; for (int k = 0; k < 16; k++) { s += v[k] * v[(k + 3) & 15]; if (v[k] > 0.5f) s -= sqrtf(fabsf(v[k])); else if (v[k] < -0.5f) s += floorf(v[k] * 3.f); }
    float m[4][4]; for (int r = 0; r < 4; r++) for (int c = 0; c < 4; c++) m[r][c] = v[r * 4 + c] + (r == c ? 4.f : 0.f);
    for (int p = 0; p < 4; p++) { float inv = 1.0f / m[p][p]; for (int r = p + 1; r < 4; r++) { float f = m[r][p] * inv; for (int c = p; c < 4; c++) m[r][c] -= f * m[p][c]; } }
    mix((uint64_t)(int64_t)(s * 1000)); mix((uint64_t)(int64_t)(m[3][3] * 1e5)); qsort(v, 16, 4, cmpf); mix((uint64_t)(int64_t)(v[7] * 1e4f));
    uint64_t x = r32() | (uint64_t)r32() << 32; uint32_t cnt = 0; for (int k = 0; k < 64; k++) { if ((x >> k) & 1) cnt += (uint32_t)(x >> (k & 31)); else cnt ^= (uint32_t)(x << (k & 15)); }
    mix(cnt ^ ((x * 0x9e3779b97f4a7c15ull) >> 17) ^ (uint64_t)(int64_t)((int32_t)x / (int32_t)(r32() | 1)) ^ __builtin_clzll(x | 1) ^ __builtin_bswap64(x) ^ (x % ((x >> 40) | 1)));
    double dd = (double)(int32_t)r32() / 77.0; mix((uint64_t)(int64_t)(sin(dd) * 1e9) ^ (uint64_t)(int64_t)(exp(fmod(dd, 5.0)) * 1e6) ^ (uint64_t)lrint(dd * 3.7) ^ (uint64_t)(int32_t)(float)dd);
  }
}
static void k_copy(int n) {
  static uint8_t a[1024 + 64], b[1024 + 64];
  for (int i = 0; i < n; i++) {
    for (int k = 0; k < 1088; k++) a[k] = (uint8_t)r32();
    unsigned off = r32() & 15, len = 200 + (r32() & 511); memcpy(b + off, a + (r32() & 31), len); memmove(b + 7, b + 3, len / 2); memset(b + 600, (int)r32(), 77);
    for (unsigned k = 0; k < 1088; k += 8) { uint64_t w; memcpy(&w, b + k, 8); mix(w); } mix(memcmp(a, b, len) > 0); mix((uint64_t)(uintptr_t)strlen((const char *)a) & 0xffff);
  }
}
int kernels_run(int n, const char *pre, const char *ver) {
  static const struct { const char *name; void (*f)(int); } K[] = {{"k_mat4", k_mat4}, {"k_quat", k_quat}, {"k_skin", k_skin}, {"k_half", k_half}, {"k_swizzle", k_swizzle}, {"k_int", k_int}, {"k_fp", k_fp}, {"k_scalar", k_scalar}, {"k_copy", k_copy}};
  for (unsigned i = 0; i < sizeof K / sizeof K[0]; i++) {
    int ok = !pre; if (pre) for (const char *p = pre; *p;) { const char *e = strchr(p, ','); size_t l = e ? (size_t)(e - p) : strlen(p); if (!strncmp(K[i].name, p, l)) ok = 1; p += l; if (*p == ',') p++; }
    if (!ok || (ver && strcmp(ver, K[i].name))) continue;
    S = 0x1234567 + i; H = 0xcbf29ce484222325ull;
    struct timespec t0, t1; clock_gettime(CLOCK_MONOTONIC, &t0);
    K[i].f(n);  /* n kernel rounds */
    clock_gettime(CLOCK_MONOTONIC, &t1); fprintf(stderr, "[nt] %s %ld ms\n", K[i].name, (long)((t1.tv_sec - t0.tv_sec) * 1000 + (t1.tv_nsec - t0.tv_nsec) / 1000000));
    fprintf(stderr, "[nd] %s %016llx\n", K[i].name, (unsigned long long)H); fflush(stderr);
  }
  return 0;
}
#pragma clang attribute pop
