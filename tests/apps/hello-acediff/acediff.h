/* Shared by acediff.c and acediff_sys.c. Differential test for the arm64 translator (PGR/ACE hunt): each line is one instruction family or one runtime-code / signal behaviour, deterministic
 * (fixed seed), so a real arm64 machine and Berberis on x86_64 must print identical lines. A family whose instruction the CPU or the translator lacks prints
 * SIG<n> instead of a hash. Lines starting "info" are CPU-specific on purpose (id registers, hwcaps) and are not compared.
 * Build native: gcc -O2 -march=armv8.2-a+crypto+sha3+sm4+lse+dotprod+crc acediff.c -o acediff ; as an app: tests/apps/build.sh hello-acediff (JNI run()). */
#pragma once
#define _GNU_SOURCE
#ifdef __clang__  /* the NDK builds for plain armv8-a: switch the extensions on per function (gcc is given -march instead) */
#pragma clang attribute push(__attribute__((target("v8.2a,fullfp16,rcpc,aes,sha2,sha3,sm4,crc,lse,rdm,dotprod,fp16fml"))), apply_to = function)
#endif
#include <arm_acle.h>
#include <arm_neon.h>
#include <errno.h>
#include <fcntl.h>
#include <math.h>
#include <setjmp.h>
#include <signal.h>
#include <stdarg.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/auxv.h>
#include <sys/mman.h>
#include <sys/syscall.h>
#include <pthread.h>
#include <sys/wait.h>
#include <ucontext.h>
#include <unistd.h>

extern char out[65536];
extern size_t on;
void say(const char *fmt, ...) __attribute__((format(printf, 1, 2)));
#ifdef ACEDIFF_DEFS
char out[65536];
size_t on;
void say(const char *fmt, ...)
{
    char line[256]; va_list ap; va_start(ap, fmt); vsnprintf(line, sizeof line, fmt, ap); va_end(ap);
    if (on < sizeof out - 300) on += snprintf(out + on, sizeof out - on, "%s\n", line);
}
uint64_t rng = 0x9e3779b97f4a7c15ull;
#else
extern uint64_t rng;
#endif
static uint64_t next(void) { rng ^= rng << 13; rng ^= rng >> 7; rng ^= rng << 17; return rng; }
static uint64_t fnv(uint64_t h, uint64_t v) { return (h ^ v) * 1099511628211ull; }
static uint64_t H(uint64_t h, uint8x16_t v) { return fnv(fnv(h, vgetq_lane_u64(vreinterpretq_u64_u8(v), 0)), vgetq_lane_u64(vreinterpretq_u64_u8(v), 1)); }
static uint64_t H4(uint64_t h, uint32x4_t v) { return H(h, vreinterpretq_u8_u32(v)); }
static uint64_t H2(uint64_t h, uint64x2_t v) { return H(h, vreinterpretq_u8_u64(v)); }
static uint8x16_t rv(void) { uint64_t a[2] = {next(), next()}; return vld1q_u8((uint8_t *)a); }
/* random floats: finite only (fin=1) or any bit pattern incl. NaN/inf/denormal */
static float32x4_t rf(int fin) { uint32_t a[4]; for (int i = 0; i < 4; i++) { a[i] = (uint32_t)next(); if (fin && (a[i] & 0x7f800000) == 0x7f800000) a[i] &= ~0x40000000u; if (fin && (a[i] & 0x7f800000) == 0x7f800000) a[i] ^= 0x10000000u; } return vld1q_f32((float *)a); }
static float64x2_t rd(int fin) { uint64_t a[2]; for (int i = 0; i < 2; i++) { a[i] = next(); if (fin && (a[i] & 0x7ff0000000000000ull) == 0x7ff0000000000000ull) a[i] ^= 0x4000000000000000ull; } return vld1q_f64((double *)a); }
static uint64_t HF(uint64_t h, float32x4_t v) { return H(h, vreinterpretq_u8_f32(v)); }
static uint64_t HD(uint64_t h, float64x2_t v) { return H(h, vreinterpretq_u8_f64(v)); }

static uint32_t BC32(float f) { uint32_t u; memcpy(&u, &f, 4); return u; }
static uint64_t BC64(double f) { uint64_t u; memcpy(&u, &f, 8); return u; }
static float BCF(uint32_t u) { float f; memcpy(&f, &u, 4); return f; }
/* per-operation hashes (F macro): a mismatch then names the instruction */
extern const char *slot_name[96]; extern uint64_t slot_val[96]; extern int slots;
static uint64_t *slot(const char *nm) { for (int i = 0; i < slots; i++) if (!strcmp(slot_name[i], nm)) return &slot_val[i]; slot_name[slots] = nm; slot_val[slots] = 0; return &slot_val[slots++]; }
#define FL(label, v) do { uint64_t *p_ = slot(label); *p_ = fnv(*p_, (v)); } while (0)
#define F(fn, expr) do { uint64_t *p_ = slot(#expr); *p_ = fn(*p_, expr); } while (0)
extern sigjmp_buf jb;
extern volatile sig_atomic_t armed;
extern char altstack[65536];
long sc(long n, long a, long b, long c, long d, long e, long f);

#define LOOP(n) for (int i = 0; i < (n); i++)

#define LOOP(n) for (int i = 0; i < (n); i++)
