/* Single-instruction RMW forms (LL/SC, LSE, CAS) on counters, bit masks, min/max and swap, all sizes and orderings, neighbours sharing one cache line. */
#include "atomics.h"

#define LLSC(N, LD, ST, M, T) static void N(void *q) { T v; unsigned s; asm volatile("1: " LD " %" M "0,[%2]\n add %" M "0,%" M "0,#1\n " ST " %w1,%" M "0,[%2]\n cbnz %w1,1b" : "=&r"(v), "=&r"(s) : "r"(q) : "memory"); }
#define LLSC4(S, SFX, M, T) LLSC(ll_##S##_n, "ldxr" SFX, "stxr" SFX, M, T) LLSC(ll_##S##_a, "ldaxr" SFX, "stxr" SFX, M, T) LLSC(ll_##S##_l, "ldxr" SFX, "stlxr" SFX, M, T) LLSC(ll_##S##_al, "ldaxr" SFX, "stlxr" SFX, M, T)
#define LSE(N, INS, M, T) static void N(void *q) { T r; asm volatile(INS " %" M "1,%" M "0,[%2]" : "=&r"(r) : "r"((T)1), "r"(q) : "memory"); }
#define STO(N, INS, M, T) static void N(void *q) { asm volatile(INS " %" M "0,[%1]" :: "r"((T)1), "r"(q) : "memory"); }
#define CAS(N, INS, M, T) static void N(void *q) { T o, n, r; do { o = *(volatile T *)q; n = o + 1; r = o; asm volatile(INS " %" M "0,%" M "1,[%2]" : "+r"(r) : "r"(n), "r"(q) : "memory"); } while (r != o); }
#define DEFS(S, SFX, M, T, SZ) LLSC4(S, SFX, M, T) \
    LSE(la_##S##_n, "ldadd" SFX, M, T) LSE(la_##S##_a, "ldadda" SFX, M, T) LSE(la_##S##_al, "ldaddal" SFX, M, T) LSE(la_##S##_l, "ldaddl" SFX, M, T) \
    STO(sa_##S##_n, "stadd" SFX, M, T) STO(sa_##S##_l, "staddl" SFX, M, T) \
    CAS(ca_##S##_n, "cas" SFX, M, T) CAS(ca_##S##_a, "casa" SFX, M, T) CAS(ca_##S##_al, "casal" SFX, M, T) CAS(ca_##S##_l, "casl" SFX, M, T)
#define FORMS(X) X(b, "b", "w", u8, 1) X(h, "h", "w", u16, 2) X(w, "", "w", u32, 4) X(x, "", "x", u64, 8)
#define ENT(S, SFX, M, T, SZ) {"ll_" #S "_n", ll_##S##_n, SZ}, {"ll_" #S "_a", ll_##S##_a, SZ}, {"ll_" #S "_l", ll_##S##_l, SZ}, {"ll_" #S "_al", ll_##S##_al, SZ}, \
    {"ldadd_" #S, la_##S##_n, SZ}, {"ldadda_" #S, la_##S##_a, SZ}, {"ldaddal_" #S, la_##S##_al, SZ}, {"ldaddl_" #S, la_##S##_l, SZ}, \
    {"stadd_" #S, sa_##S##_n, SZ}, {"staddl_" #S, sa_##S##_l, SZ}, \
    {"cas_" #S, ca_##S##_n, SZ}, {"casa_" #S, ca_##S##_a, SZ}, {"casal_" #S, ca_##S##_al, SZ}, {"casl_" #S, ca_##S##_l, SZ},
#define X(...) DEFS(__VA_ARGS__)
FORMS(X)
#undef X
typedef struct { const char *name; void (*f)(void *); int size; } form_t;
static const form_t forms[] = {
#define X(...) ENT(__VA_ARGS__)
    FORMS(X)
#undef X
};
#define NF (sizeof forms / sizeof *forms)
static u8 mem[1024] CL; static u8 *ptr[NF];

static u64 load(void *p, int sz) { return sz == 1 ? *(volatile u8 *)p : sz == 2 ? *(volatile u16 *)p : sz == 4 ? *(volatile u32 *)p : *(volatile u64 *)p; }
static u64 mask(int sz) { return sz == 8 ? ~0ULL : (1ULL << (8 * sz)) - 1; }

static void counters(int t)
{   /* all forms interleaved: each form's cell is next to the others of its size (and the sizes share lines): a wide read-modify-write would clobber a neighbour */
    (void)t; for (long i = 0; i < IT; i++) for (unsigned f = 0; f < NF; f++) forms[f].f(ptr[f]);
}

/* ---- bit masks: own bit set/clear/toggle through every ordering; old value of the own bit is known, any lost update of a neighbour's bit shows up here ---- */
#define RMW(N, INS, M, T) static u64 N(void *q, u64 v) { T r; asm volatile(INS " %" M "1,%" M "0,[%2]" : "=&r"(r) : "r"((T)v), "r"(q) : "memory"); return r; }
#define BITDEF(S, SFX, M, T, SZ) \
    RMW(set_##S##_n, "ldset" SFX, M, T) RMW(set_##S##_a, "ldseta" SFX, M, T) RMW(set_##S##_al, "ldsetal" SFX, M, T) RMW(set_##S##_l, "ldsetl" SFX, M, T) \
    RMW(clr_##S##_n, "ldclr" SFX, M, T) RMW(clr_##S##_a, "ldclra" SFX, M, T) RMW(clr_##S##_al, "ldclral" SFX, M, T) RMW(clr_##S##_l, "ldclrl" SFX, M, T) \
    RMW(eor_##S##_n, "ldeor" SFX, M, T) RMW(eor_##S##_a, "ldeora" SFX, M, T) RMW(eor_##S##_al, "ldeoral" SFX, M, T) RMW(eor_##S##_l, "ldeorl" SFX, M, T) \
    RMW(smax_##S, "ldsmax" SFX, M, T) RMW(umax_##S, "ldumax" SFX, M, T) RMW(smin_##S, "ldsmin" SFX, M, T) RMW(umin_##S, "ldumin" SFX, M, T) \
    RMW(smaxal_##S, "ldsmaxal" SFX, M, T) RMW(umaxal_##S, "ldumaxal" SFX, M, T) RMW(sminal_##S, "ldsminal" SFX, M, T) RMW(uminal_##S, "lduminal" SFX, M, T) \
    RMW(swp_##S##_n, "swp" SFX, M, T) RMW(swp_##S##_a, "swpa" SFX, M, T) RMW(swp_##S##_al, "swpal" SFX, M, T) RMW(swp_##S##_l, "swpl" SFX, M, T)
#define X(...) BITDEF(__VA_ARGS__)
FORMS(X)
#undef X
typedef u64 (*rmw_t)(void *, u64);
typedef struct { const char *name; int size; rmw_t set, clr, eor; } bit_t;
#define BE(S, SZ, O) {#S "_" #O, SZ, set_##S##_##O, clr_##S##_##O, eor_##S##_##O}
#define BS(S, SZ) BE(S, SZ, n), BE(S, SZ, a), BE(S, SZ, al), BE(S, SZ, l)
static const bit_t bits[] = {BS(b, 1), BS(h, 2), BS(w, 4), BS(x, 8)};
static const bit_t *cb; static u8 bmem[256] CL; static long bbad;

static void bitw(int t)
{
    int sz = cb->size, nb = 8 * sz; u8 *w = bmem, *par = bmem + 128;
    if (t >= nb) return;   /* more threads than bits: the surplus idles */
    u64 bit = 1ULL << t, bad = 0;
    for (long i = 0; i < IT; i++) {
        u64 o = cb->set(w, bit); if (o & bit) bad++;
        o = cb->clr(w, bit); if (!(o & bit)) bad++;
        o = cb->eor(w, bit); if (o & bit) bad++;
        o = cb->eor(w, bit); if (!(o & bit)) bad++;
        cb->eor(par, 1);
    }
    ADD(&bbad, bad);
}

/* ---- max/min: after the op the cell must be at least (at most) the operand, whoever wrote last; values rise (fall) so every op really stores ---- */
typedef struct { const char *name; int size; rmw_t op; int isMax, isSigned; } mm_t;
#define ME(S, SZ, OP, MX, SG) {#OP "_" #S, SZ, OP##_##S, MX, SG}
#define MS(S, SZ) ME(S, SZ, smax, 1, 1), ME(S, SZ, umax, 1, 0), ME(S, SZ, smin, 0, 1), ME(S, SZ, umin, 0, 0), ME(S, SZ, smaxal, 1, 1), ME(S, SZ, umaxal, 1, 0), ME(S, SZ, sminal, 0, 1), ME(S, SZ, uminal, 0, 0)
static const mm_t mms[] = {MS(b, 1), MS(h, 2), MS(w, 4), MS(x, 8)};
static const mm_t *cm; static u8 mmem[128] CL; static long mbad;
static int cmp(u64 a, u64 b, int sz, int sg)
{
    if (sg) { int s = 64 - 8 * sz; s64 x = (s64)(a << s) >> s, y = (s64)(b << s) >> s; return x < y ? -1 : x > y; }
    a &= mask(sz); b &= mask(sz); return a < b ? -1 : a > b;
}
static void mmw(int t)
{
    long bad = 0;
    for (long i = 0; i < IT; i++) {
        u64 v = (cm->isMax ? (u64)(i * NT + t) : -(u64)(i * NT + t)) & mask(cm->size);
        if (!cm->isSigned && !cm->isMax) v = (mask(cm->size) - (u64)(i * NT + t)) & mask(cm->size);
        cm->op(mmem, v); u64 m = load(mmem, cm->size);
        if (cm->isMax ? cmp(m, v, cm->size, cm->isSigned) < 0 : cmp(m, v, cm->size, cm->isSigned) > 0) bad++;
    }
    ADD(&mbad, bad);
}

/* ---- swap: values only move between the cell and the threads' private holders: the multiset is conserved ---- */
static u64 held[64]; static const bit_t *cs;
static void swpw(int t)
{
    u64 v = (u64)t + 1; u8 *c = bmem + 192;
    for (long i = 0; i < IT; i++) v = cs->set(c, v) & mask(cs->size);   /* bit_t.set is the swp function here */
    held[t] = v;
}
static const bit_t swps[] = {
#define SE(S, SZ, O) {#S "_" #O, SZ, swp_##S##_##O, 0, 0}
#define SS(S, SZ) SE(S, SZ, n), SE(S, SZ, a), SE(S, SZ, al), SE(S, SZ, l)
    SS(b, 1), SS(h, 2), SS(w, 4), SS(x, 8)
};

void rmw_all(void)
{
    unsigned off[9] = {0}; unsigned pos = 0;
    for (unsigned f = 0; f < NF; f++) { int sz = forms[f].size; pos = (pos + sz - 1) / sz * sz; ptr[f] = mem + pos; pos += sz; }
    if (want("counter")) {
        memset(mem, 0, sizeof mem); run_threads(NT, counters); (void)off;
        for (unsigned f = 0; f < NF; f++) {
            u64 want_ = ((u64)NT * IT) & mask(forms[f].size), got = load(ptr[f], forms[f].size);
            char n[64]; snprintf(n, sizeof n, "counter_%s", forms[f].name);
            result(n, got != want_, "got %llu want %llu (lost %lld)", (unsigned long long)got, (unsigned long long)want_, (long long)(want_ - got));
        }
    }
    for (unsigned i = 0; i < sizeof bits / sizeof *bits; i++) {
        char n[64]; snprintf(n, sizeof n, "bits_%s", bits[i].name); if (!want(n)) continue;
        cb = &bits[i]; memset(bmem, 0, sizeof bmem); bbad = 0; run_threads(NT, bitw);
        u64 exp_par = ((u64)NT * IT) & 1, got = load(bmem, cb->size), par = load(bmem + 128, 1) & 1;
        result(n, bbad || got || par != exp_par, "wrong-old %ld final %llx parity %llu want %llu", bbad, (unsigned long long)got, (unsigned long long)par, (unsigned long long)exp_par);
    }
    for (unsigned i = 0; i < sizeof mms / sizeof *mms; i++) {
        char n[64]; snprintf(n, sizeof n, "minmax_%s", mms[i].name); if (!want(n)) continue;
        cm = &mms[i]; memset(mmem, 0, sizeof mmem); if (!mms[i].isMax) { u64 init = mms[i].isSigned ? mask(mms[i].size) >> 1 : mask(mms[i].size); memcpy(mmem, &init, mms[i].size); }
        mbad = 0; run_threads(NT, mmw); result(n, mbad, "violations %ld", mbad);
    }
    for (unsigned i = 0; i < sizeof swps / sizeof *swps; i++) {
        char n[64]; snprintf(n, sizeof n, "swp_%s", swps[i].name); if (!want(n)) continue;
        cs = &swps[i]; memset(bmem, 0, sizeof bmem); memset(held, 0, sizeof held); run_threads(NT, swpw);
        u64 sum = load(bmem + 192, cs->size), want_ = 0; int dup = 0;
        for (int t = 0; t < NT; t++) { want_ += (u64)t + 1; sum += held[t]; for (int u = 0; u < t; u++) dup += held[u] == held[t]; }
        sum &= mask(cs->size); want_ &= mask(cs->size);
        result(n, sum != want_ || dup, "sum %llu want %llu duplicated tokens %d", (unsigned long long)sum, (unsigned long long)want_, dup);
    }
}
#pragma clang attribute pop
