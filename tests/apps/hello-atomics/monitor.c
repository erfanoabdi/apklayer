/* Exclusive-monitor semantics the architecture guarantees and a CAS-on-loaded-value emulation does not: clrex, a second stxr after a successful one,
 * and ABA (another observer stores to the address between ldxr and stxr, even the same value: the stxr must fail). */
#include "atomics.h"
static volatile u64 v[2] CL; static volatile u32 f1 CL, f2 CL, done CL;

static int mono(int k, u64 *p)
{   /* returns the stxr status the architecture forbids to be 0 */
    u64 x, y; unsigned s, s2;
    switch (k) {
    case 0: do asm volatile("ldxr %0,[%2]\n clrex\n stxr %w1,%0,[%2]" : "=&r"(x), "=&r"(s) : "r"(p) : "memory"); while (0); return s == 0;               /* ldxr; clrex; stxr */
    case 1: do asm volatile("ldxr %0,[%3]\n stxr %w1,%0,[%3]\n stxr %w2,%0,[%3]" : "=&r"(x), "=&r"(s), "=&r"(s2) : "r"(p) : "memory"); while (s); return s2 == 0;  /* second stxr */
    case 2: do asm volatile("ldxp %0,%1,[%3]\n clrex\n stxp %w2,%0,%1,[%3]" : "=&r"(x), "=&r"(y), "=&r"(s2) : "r"(p) : "memory"); while (0); return s2 == 0;
    default: { do asm volatile("ldxp %0,%1,[%4]\n stxp %w2,%0,%1,[%4]\n stxp %w3,%0,%1,[%4]" : "=&r"(x), "=&r"(y), "=&r"(s), "=&r"(s2) : "r"(p) : "memory"); while (s); return s2 == 0; }
    }
}
static const char *mname[] = {"ldxr_clrex_stxr", "stxr_twice", "ldxp_clrex_stxp", "stxp_twice"};

static long aba_bad, aba_ctl_ok; static volatile int act; static int pairmode;
static void aba(int t)
{
    for (long i = 0; i < IT; i++) {
        if (t == 0) {   /* watcher */
            u64 x, y; unsigned z, s;
            if (!pairmode) asm volatile("ldxr %0,[%3]\n str %w4,[%5]\n 1: ldr %w1,[%6]\n cbz %w1,1b\n stxr %w2,%0,[%3]" : "=&r"(x), "=&r"(z), "=&r"(s) : "r"((void *)v), "r"(1u), "r"(&f1), "r"(&f2) : "memory");
            else asm volatile("ldxp %0,%1,[%4]\n str %w5,[%6]\n 1: ldr %w2,[%7]\n cbz %w2,1b\n stxp %w3,%0,%1,[%4]" : "=&r"(x), "=&r"(y), "=&r"(z), "=&r"(s) : "r"((void *)v), "r"(1u), "r"(&f1), "r"(&f2) : "memory");
            if (s == 0) { if (act) aba_bad++; else aba_ctl_ok++; }
            f1 = 0; f2 = 0; __atomic_store_n(&done, i + 1, __ATOMIC_RELEASE);
        } else {        /* modifier */
            while (!__atomic_load_n(&f1, __ATOMIC_ACQUIRE)) ;
            u64 a = v[0], b = a ^ 0x55;
            switch (act) {
            case 1: v[0] = b; v[0] = a; break;                                                       /* A -> B -> A */
            case 2: v[0] = a; break;                                                                 /* same value */
            case 3: __atomic_fetch_add(&v[0], 1, __ATOMIC_SEQ_CST); __atomic_fetch_sub(&v[0], 1, __ATOMIC_SEQ_CST); break;
            case 4: { u64 e = a; __atomic_compare_exchange_n(&v[0], &e, a, 0, __ATOMIC_SEQ_CST, __ATOMIC_SEQ_CST); break; }   /* CAS of the same value */
            case 5: *(volatile u8 *)&v[0] = (u8)a; break;                                            /* byte store of the same byte */
            case 6: if (pairmode) { v[1] = v[1]; } else v[0] = a; break;
            }
            __atomic_store_n(&f2, 1, __ATOMIC_RELEASE);
            while (__atomic_load_n(&done, __ATOMIC_ACQUIRE) != (u32)(i + 1)) ;
        }
    }
}
static const char *aname[] = {"control(no store, may succeed)", "A_B_A_stores", "same_value_store", "ldadd_then_sub", "cas_same_value", "byte_store_same", "second_word_store"};

void monitor_all(void)
{
    for (int k = 0; k < 4; k++) {
        char n[64]; snprintf(n, sizeof n, "monitor_%s", mname[k]); if (!want(n)) continue;
        long bad = 0; for (long i = 0; i < IT; i++) { v[0] = 5; v[1] = 7; bad += mono(k, (u64 *)v); }
        result(n, bad, "forbidden success %ld of %ld", bad, IT);
    }
    for (pairmode = 0; pairmode < 2; pairmode++) for (act = 0; act < 7; act++) {
        char n[96]; snprintf(n, sizeof n, "aba_%s_%s", pairmode ? "ldxp" : "ldxr", aname[act]);
        for (char *c = n; *c; c++) if (*c == '(' || *c == ')' || *c == ',' || *c == ' ') *c = '_';
        if (!want(n) || !spin_ok()) continue;
        v[0] = 0x1122334455667788ULL; v[1] = 9; f1 = f2 = done = 0; aba_bad = aba_ctl_ok = 0;
        long it = IT; if (it > 20000) IT = 20000; run_threads(2, aba); IT = it;
        result(n, act ? aba_bad : 0, act ? "forbidden success %ld" : "stxr succeeded %ld times (informational)", act ? aba_bad : aba_ctl_ok);
    }
}
#pragma clang attribute pop
