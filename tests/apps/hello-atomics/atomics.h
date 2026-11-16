/* Multi-threaded atomics / memory-order stress (hello-atomics app; standalone: aarch64-linux-android26-clang -O2 -march=armv8.2-a+lse -DATOMICS_MAIN -static *.c -o atomics).
 * Every test prints "RES <name> PASS|FAIL <detail>"; a FAIL is an arm64-forbidden outcome (lost update, double "last", torn 128-bit value, forbidden litmus result...). */
#pragma once
#define _GNU_SOURCE
#include <pthread.h>
#include <sched.h>
#include <stdarg.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#pragma clang attribute push(__attribute__((target("+lse"))), apply_to = function)  /* the app build passes no -march */
typedef uint8_t u8; typedef uint16_t u16; typedef uint32_t u32; typedef uint64_t u64; typedef int32_t s32; typedef int64_t s64;
#define CL __attribute__((aligned(128)))
#define ADD(p, v) __atomic_fetch_add((p), (v), __ATOMIC_SEQ_CST)
extern int NT, PIN; extern long IT; extern const char *ONLY;
typedef void (*wfn)(int tid);
void say(const char *fmt, ...) __attribute__((format(printf, 1, 2)));
void result(const char *name, long bad, const char *fmt, ...) __attribute__((format(printf, 3, 4)));
int want(const char *name);
int spin_ok(void);   /* 0 when threads share cpus (PIN>=2) or outnumber them: spin-waiting tests (tickets, barriers, once waiters) would crawl */
void run_threads(int n, wfn f);   /* n threads, tid 0..n-1, all released together; PIN: 0 free, 1 one cpu each, 2 two cpus, 3 one cpu */
void rmw_all(void); void pair_all(void); void idiom_all(void); void monitor_all(void); void litmus_all(void);
static inline void *amem(size_t a, size_t n) { void *p; return posix_memalign(&p, a, n) ? 0 : p; }
