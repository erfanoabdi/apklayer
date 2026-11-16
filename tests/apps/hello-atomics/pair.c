/* LDXP/STXP and CASP pair counters (x and 2x32): the pair holds (lo, ~lo); a pair seen with hi != ~lo after a successful exclusive store, or returned by CASP, is torn. */
#include "atomics.h"
static u64 pm[4] CL; static long torn;

#define XP(N, LD, ST, M, T, P) static void N(int t) { (void)t; long bad = 0; void *q = P; for (long i = 0; i < IT; i++) { T lo, hi, nl, nh; unsigned s; \
    do asm volatile(LD " %" M "0,%" M "1,[%5]\n add %" M "2,%" M "0,#1\n mvn %" M "3,%" M "2\n " ST " %w4,%" M "2,%" M "3,[%5]" \
        : "=&r"(lo), "=&r"(hi), "=&r"(nl), "=&r"(nh), "=&r"(s) : "r"(q) : "memory"); while (s); if (hi != (T)~lo) bad++; } ADD(&torn, bad); }
#define CP(N, INS, R, M, T, P) static void N(int t) { (void)t; T el = 0, eh = ~(T)0; long bad = 0; void *q = P; for (long i = 0; i < IT; i++) for (;;) { T nl = el + 1, nh = ~nl, rl, rh; \
    asm volatile("mov " R "2,%" M "2\n mov " R "3,%" M "3\n mov " R "4,%" M "4\n mov " R "5,%" M "5\n " INS " " R "2," R "3," R "4," R "5,[%6]\n mov %" M "0," R "2\n mov %" M "1," R "3" \
        : "=&r"(rl), "=&r"(rh) : "r"(el), "r"(eh), "r"(nl), "r"(nh), "r"(q) : "x2", "x3", "x4", "x5", "memory"); \
    if (rh != (T)~rl) bad++; if (rl == el && rh == eh) { el = nl; eh = nh; break; } el = rl; eh = rh; } ADD(&torn, bad); }
XP(xp_n, "ldxp", "stxp", "x", u64, pm) XP(xp_a, "ldaxp", "stxp", "x", u64, pm) XP(xp_l, "ldxp", "stlxp", "x", u64, pm) XP(xp_al, "ldaxp", "stlxp", "x", u64, pm)
XP(wp_n, "ldxp", "stxp", "w", u32, pm + 2) XP(wp_a, "ldaxp", "stxp", "w", u32, pm + 2) XP(wp_l, "ldxp", "stlxp", "w", u32, pm + 2) XP(wp_al, "ldaxp", "stlxp", "w", u32, pm + 2)
CP(cx_n, "casp", "x", "x", u64, pm) CP(cx_a, "caspa", "x", "x", u64, pm) CP(cx_l, "caspl", "x", "x", u64, pm) CP(cx_al, "caspal", "x", "x", u64, pm)
CP(cw_n, "casp", "w", "w", u32, pm + 2) CP(cw_a, "caspa", "w", "w", u32, pm + 2) CP(cw_l, "caspl", "w", "w", u32, pm + 2) CP(cw_al, "caspal", "w", "w", u32, pm + 2)
static const struct { const char *n; wfn f; int w; } tests[] = {
    {"ldxp_x_n", xp_n, 0}, {"ldxp_x_a", xp_a, 0}, {"ldxp_x_l", xp_l, 0}, {"ldxp_x_al", xp_al, 0},
    {"ldxp_w_n", wp_n, 1}, {"ldxp_w_a", wp_a, 1}, {"ldxp_w_l", wp_l, 1}, {"ldxp_w_al", wp_al, 1},
    {"casp_x_n", cx_n, 0}, {"casp_x_a", cx_a, 0}, {"casp_x_l", cx_l, 0}, {"casp_x_al", cx_al, 0},
    {"casp_w_n", cw_n, 1}, {"casp_w_a", cw_a, 1}, {"casp_w_l", cw_l, 1}, {"casp_w_al", cw_al, 1},
};
void pair_all(void)
{
    for (unsigned i = 0; i < sizeof tests / sizeof *tests; i++) {
        if (!want(tests[i].n)) continue;
        u32 *w = (u32 *)(pm + 2); pm[0] = 0; pm[1] = ~0ULL; w[0] = 0; w[1] = ~0u; torn = 0;
        run_threads(NT, tests[i].f);
        u64 lo, hi, exp = (u64)NT * IT; if (tests[i].w) { lo = w[0]; hi = w[1]; exp &= 0xffffffffu; } else { lo = pm[0]; hi = pm[1]; }
        int bad = lo != exp || (tests[i].w ? (u32)hi != (u32)~lo : hi != ~lo);
        result(tests[i].n, bad || torn, "final lo %llu want %llu torn-seen %ld", (unsigned long long)lo, (unsigned long long)exp, torn);
    }
}
#pragma clang attribute pop
