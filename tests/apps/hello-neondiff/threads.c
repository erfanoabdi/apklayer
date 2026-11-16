/* Multi-thread checks of the exclusive / atomic / barrier lowerings (a single-thread differential cannot see a lost update or a missing fence): 4 threads, hot loops (heavy tier after 1000 passes),
 * each line "[nd] thr_<name> OK|FAIL <detail>". Real hardware must print OK for every line (thr_sb counts store-buffering outcomes with dmb ish: must be 0). */
#include <pthread.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#pragma clang attribute push(__attribute__((target("+lse,+rcpc"))), apply_to = function)
#define NT 4
#define ITER 100000
static volatile uint64_t cnt, cnt2, plain, lock, flag[NT], data[NT][8], seq, sa, sb, ra, rb, sb_bad, go;
static uint32_t cnt32;
static void run(void *(*f)(void *)) { pthread_t t[NT]; for (long i = 0; i < NT; i++) pthread_create(&t[i], 0, f, (void *)i); for (int i = 0; i < NT; i++) pthread_join(t[i], 0); }
static void rep(const char *n, int ok, unsigned long long v) { fprintf(stderr, "[nd] thr_%s %s %llu\n", n, ok ? "OK" : "FAIL", v); fflush(stderr); }
static void *t_llsc(void *a) { (void)a; for (int i = 0; i < ITER; i++) { uint64_t v, s; do { __asm__ volatile("ldxr %0, [%2]\n add %0, %0, #1\n stxr %w1, %0, [%2]" : "=&r"(v), "=&r"(s) : "r"(&cnt) : "memory"); } while (s); } return 0; }
static void *t_llsc_ax(void *a) { (void)a; for (int i = 0; i < ITER; i++) { uint32_t v, s; do { __asm__ volatile("ldaxr %w0, [%2]\n add %w0, %w0, #1\n stlxr %w1, %w0, [%2]" : "=&r"(v), "=&r"(s) : "r"(&cnt32) : "memory"); } while (s); } return 0; }
static void *t_ldadd(void *a) { (void)a; for (int i = 0; i < ITER; i++) { uint64_t o; __asm__ volatile("ldaddal %2, %0, [%1]" : "=&r"(o) : "r"(&cnt2), "r"((uint64_t)1) : "memory"); } return 0; }
static void *t_lock(void *a) { (void)a; for (int i = 0; i < ITER / 4; i++) { uint64_t e;
    do { e = 0; __asm__ volatile("casa %0, %2, [%1]" : "+r"(e) : "r"(&lock), "r"((uint64_t)1) : "memory"); } while (e);
    plain = plain + 1; __asm__ volatile("stlr xzr, [%0]" :: "r"(&lock) : "memory"); } return 0; }
static void *t_mp(void *a) { long id = (long)a; uint64_t bad = 0;
  if (id == 0) { for (uint64_t i = 1; i <= ITER; i++) { for (int k = 0; k < 8; k++) data[0][k] = i; __asm__ volatile("stlr %1, [%0]" :: "r"(&flag[0]), "r"(i) : "memory"); } }
  else { uint64_t last = 0; while (last < ITER) { uint64_t f; __asm__ volatile("ldar %0, [%1]" : "=r"(f) : "r"(&flag[0]) : "memory"); if (f != last) { for (int k = 0; k < 8; k++) if (data[0][k] < f) bad++; last = f; } } __atomic_fetch_add(&cnt, bad * 1000000, __ATOMIC_RELAXED); }
  return 0; }
static void *t_dmb(void *a) { long id = (long)a; if (id > 1) return 0;
  for (int i = 0; i < ITER; i++) {
    if (id == 0) { while (__atomic_load_n(&go, __ATOMIC_ACQUIRE) != (uint64_t)(2 * i)) {} sa = 0; sb = 0; ra = rb = 9; __atomic_store_n(&go, 2 * i + 1, __ATOMIC_RELEASE); }
    else { while (__atomic_load_n(&go, __ATOMIC_ACQUIRE) != (uint64_t)(2 * i + 1)) {}
      /* both sides: store my flag, dmb ish, load the other's: at least one must see 1 */
      uint64_t x; sa = 1; __asm__ volatile("dmb ish" ::: "memory"); x = sb; ra = x; __atomic_store_n(&go, 2 * i + 2, __ATOMIC_RELEASE); if (0) sb_bad++; } }
  return 0; }
static volatile uint64_t x1v, y1v, r1, r2, done_a, done_b, round_;
static void *t_sb(void *a) { long id = (long)a; if (id > 1) return 0;
  for (uint64_t i = 1; i <= ITER; i++) {
    if (id == 0) { x1v = 0; y1v = 0; __atomic_store_n(&round_, i * 2, __ATOMIC_SEQ_CST); }
    while (__atomic_load_n(&round_, __ATOMIC_SEQ_CST) < i * 2) {}
    uint64_t r; if (id == 0) { x1v = 1; __asm__ volatile("dmb ish" ::: "memory"); r = y1v; r1 = r; } else { y1v = 1; __asm__ volatile("dmb ish" ::: "memory"); r = x1v; r2 = r; }
    __atomic_fetch_add(id ? &done_b : &done_a, 1, __ATOMIC_SEQ_CST);
    if (id == 0) { while (__atomic_load_n(&done_b, __ATOMIC_SEQ_CST) < i) {} if (r1 == 0 && r2 == 0) sb_bad++; __atomic_store_n(&round_, i * 2 + 2, __ATOMIC_SEQ_CST); }
    else { while (__atomic_load_n(&round_, __ATOMIC_SEQ_CST) < i * 2 + 2) {} } }
  return 0; }
int threads_run(void) {
  run(t_llsc); rep("ldxr_stxr", cnt == (uint64_t)NT * ITER, cnt); cnt = 0;
  run(t_llsc_ax); rep("ldaxr_stlxr", cnt32 == (uint32_t)NT * ITER, cnt32);
  run(t_ldadd); rep("ldaddal", cnt2 == (uint64_t)NT * ITER, cnt2);
  run(t_lock); rep("casa_lock", plain == (uint64_t)NT * (ITER / 4), plain);
  cnt = 0; run(t_mp); rep("mp_stlr_ldar", cnt == 0, cnt);
  run(t_sb); rep("sb_dmb", sb_bad == 0, sb_bad);
  return 0;
}
#pragma clang attribute pop
