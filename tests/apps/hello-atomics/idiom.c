/* Classic lock-free / locking idioms built from the exclusive and LSE forms: refcount with "last one frees", spinlocks, once flags, ABA-prone stack. */
#include "atomics.h"

/* ---- refcount: each thread owns one reference to every object; exactly one thread must see the count reach zero ---- */
typedef struct __attribute__((aligned(64))) { s32 rc, magic, freed, last; } obj_t;
#define MAGIC 0x600dcafe
#define INC_LL(N, LD, ST) static void N(s32 *p) { s32 v; unsigned s; asm volatile("1: " LD " %w0,[%2]\n add %w0,%w0,#1\n " ST " %w1,%w0,[%2]\n cbnz %w1,1b" : "=&r"(v), "=&r"(s) : "r"(p) : "memory"); }
#define DEC_LL(N, LD, ST) static s32 N(s32 *p) { s32 v; unsigned s; asm volatile("1: " LD " %w0,[%2]\n sub %w0,%w0,#1\n " ST " %w1,%w0,[%2]\n cbnz %w1,1b" : "=&r"(v), "=&r"(s) : "r"(p) : "memory"); return v; }
INC_LL(inc_xx, "ldxr", "stxr") INC_LL(inc_aa, "ldaxr", "stlxr") DEC_LL(dec_xl, "ldxr", "stlxr") DEC_LL(dec_aa, "ldaxr", "stlxr")
static void inc_add(s32 *p) { s32 r; asm volatile("ldadd %w1,%w0,[%2]" : "=&r"(r) : "r"(1), "r"(p) : "memory"); }
static s32 dec_addal(s32 *p) { s32 r; asm volatile("ldaddal %w1,%w0,[%2]" : "=&r"(r) : "r"(-1), "r"(p) : "memory"); return r - 1; }
static s32 dec_addl(s32 *p) { s32 r; asm volatile("ldaddl %w1,%w0,[%2]\n dmb ish" : "=&r"(r) : "r"(-1), "r"(p) : "memory"); return r - 1; }
static void inc_cas(s32 *p) { s32 o, r; do { o = *(volatile s32 *)p; r = o; asm volatile("casa %w0,%w1,[%2]" : "+r"(r) : "r"(o + 1), "r"(p) : "memory"); } while (r != o); }
static s32 dec_cas(s32 *p) { s32 o, r; do { o = *(volatile s32 *)p; r = o; asm volatile("casal %w0,%w1,[%2]" : "+r"(r) : "r"(o - 1), "r"(p) : "memory"); } while (r != o); return o - 1; }
static const struct { const char *n; void (*inc)(s32 *); s32 (*dec)(s32 *); } rcs[] = {
    {"ldxr_stlxr", inc_xx, dec_xl}, {"ldaxr_stlxr", inc_aa, dec_aa}, {"ldadd_ldaddal", inc_add, dec_addal}, {"cas_casal", inc_cas, dec_cas}, {"ldadd_ldaddl_dmb", inc_add, dec_addl}, {"ldxr_ldaddal", inc_xx, dec_addal},
};
static obj_t *objs; static long robjs, rneg, rdbl, ruaf;
static int rk;
static void rcw(int t)
{
    long neg = 0, dbl = 0, uaf = 0; (void)t;
    for (long r = 0; r < robjs; r++) {
        obj_t *o = &objs[r];
        for (int k = 0; k < 3; k++) { if (o->magic != MAGIC) uaf++; rcs[rk].inc(&o->rc); if (o->magic != MAGIC) uaf++; if (rcs[rk].dec(&o->rc) < 1) neg++; }
        s32 n = rcs[rk].dec(&o->rc);
        if (n < 0) neg++;
        if (n == 0) { if (__atomic_exchange_n(&o->freed, 1, __ATOMIC_SEQ_CST)) dbl++; o->magic = 0; ADD(&o->last, 1); }
    }
    ADD(&rneg, neg); ADD(&rdbl, dbl); ADD(&ruaf, uaf);
}

/* ---- locks: plain (non atomic) updates inside the critical section must never interleave ---- */
static volatile u64 cnt, aa, bb; static volatile int incs; static long lbad;
typedef void (*lk_t)(u32 *); static lk_t lockfn, unlockfn;
#define LKLL(N, LD, ST) static void N(u32 *l) { u32 x, s; asm volatile("1: " LD " %w0,[%2]\n cbnz %w0,1b\n " ST " %w1,%w3,[%2]\n cbnz %w1,1b" : "=&r"(x), "=&r"(s) : "r"(l), "r"(1) : "memory"); }
LKLL(lk_aa, "ldaxr", "stxr") LKLL(lk_al, "ldaxr", "stlxr")
static void lk_xx(u32 *l) { u32 x, s; asm volatile("1: ldxr %w0,[%2]\n cbnz %w0,1b\n stxr %w1,%w3,[%2]\n cbnz %w1,1b\n dmb ish" : "=&r"(x), "=&r"(s) : "r"(l), "r"(1) : "memory"); }
static void lk_cas(u32 *l) { u32 r; asm volatile("1: mov %w0,wzr\n casa %w0,%w1,[%2]\n cbnz %w0,1b" : "=&r"(r) : "r"(1), "r"(l) : "memory"); }
static void lk_swp(u32 *l) { u32 r; asm volatile("1: swpa %w1,%w0,[%2]\n cbnz %w0,1b" : "=&r"(r) : "r"(1), "r"(l) : "memory"); }
static void lk_ticket(u32 *l) { u32 t; asm volatile("ldadda %w1,%w0,[%2]" : "=&r"(t) : "r"(1), "r"(l) : "memory"); while (__atomic_load_n(l + 1, __ATOMIC_ACQUIRE) != t) ; }
static void un_stlr(u32 *l) { asm volatile("stlr wzr,[%0]" :: "r"(l) : "memory"); }
static void un_dmb(u32 *l) { asm volatile("dmb ish\n str wzr,[%0]" :: "r"(l) : "memory"); }
static void un_ticket(u32 *l) { __atomic_store_n(l + 1, l[1] + 1, __ATOMIC_RELEASE); }
static const struct { const char *n; lk_t lk, un; } lks[] = {{"ldaxr_stxr", lk_aa, un_stlr}, {"ldaxr_stlxr", lk_al, un_stlr}, {"ldxr_stxr_dmb", lk_xx, un_dmb}, {"casa", lk_cas, un_stlr}, {"swpa", lk_swp, un_stlr}, {"ticket_ldadda", lk_ticket, un_ticket}};
static u32 lockw[2] CL;
static void lkw(int t)
{
    long bad = 0; (void)t;
    for (long i = 0; i < IT; i++) {
        lockfn(lockw);
        if (incs) bad++; incs = 1; u64 a = aa, b = bb; if (b != ~a) bad++; aa = a + 1; bb = ~(a + 1); cnt = cnt + 1; incs = 0;
        unlockfn(lockw);
    }
    ADD(&lbad, bad);
}

/* ---- once flag: state 0 -> 1 (winner initialises) -> 2 (release); everyone else waits with acquire and must see the payload ---- */
typedef struct __attribute__((aligned(64))) { u32 st; u32 win; u64 payload; } once_t;
static once_t *ons; static long nons, obad, ostuck;
static int try_cas(u32 *s) { u32 r; asm volatile("mov %w0,wzr\n casa %w0,%w1,[%2]" : "=&r"(r) : "r"(1), "r"(s) : "memory"); return r == 0; }
static int try_ll(u32 *s) { u32 x, st; do { asm volatile("ldxr %w0,[%2]\n mov %w1,#1\n cbnz %w0,1f\n stlxr %w1,%w3,[%2]\n 1:" : "=&r"(x), "=&r"(st) : "r"(s), "r"(1) : "memory"); if (x) { asm volatile("clrex"); return 0; } } while (st); return 1; }
static int try_aa(u32 *s) { u32 x, st; do { asm volatile("ldaxr %w0,[%2]\n mov %w1,#1\n cbnz %w0,1f\n stxr %w1,%w3,[%2]\n 1:" : "=&r"(x), "=&r"(st) : "r"(s), "r"(1) : "memory"); if (x) { asm volatile("clrex"); return 0; } } while (st); return 1; }
static int (*tryf)(u32 *);
static void oncew(int t)
{
    long bad = 0, stuck = 0; (void)t;
    for (long i = 0; i < nons; i++) {
        once_t *o = &ons[i];
        if (tryf(&o->st)) { o->payload = (u64)i * 7 + 1; ADD(&o->win, 1); __atomic_store_n(&o->st, 2, __ATOMIC_RELEASE); }
        else { long spin = 0; while (__atomic_load_n(&o->st, __ATOMIC_ACQUIRE) != 2) if (++spin > 400000000L) { stuck++; break; } if (o->payload != (u64)i * 7 + 1) bad++; }
    }
    ADD(&obad, bad); ADD(&ostuck, stuck);
}

/* ---- Treiber stack, the ABA-prone way: ldaxr head; load head->next; stlxr. A node popped, its neighbour popped, the node pushed back must make the stale stlxr fail ---- */
typedef struct __attribute__((aligned(64))) node { struct node *next; u32 st, id; } node_t;
static node_t pool[16]; static node_t *head CL; static long sbad; static int P = 8, sk;
static node_t *pop_a(void) { node_t *h, *nx; unsigned s; asm volatile("1: ldaxr %0,[%3]\n cbz %0,2f\n ldr %1,[%0]\n stlxr %w2,%1,[%3]\n cbnz %w2,1b\n 2: clrex" : "=&r"(h), "=&r"(nx), "=&r"(s) : "r"(&head) : "memory"); return h; }
static node_t *pop_x(void) { node_t *h, *nx; unsigned s; asm volatile("1: ldxr %0,[%3]\n cbz %0,2f\n ldr %1,[%0]\n stxr %w2,%1,[%3]\n cbnz %w2,1b\n 2: clrex" : "=&r"(h), "=&r"(nx), "=&r"(s) : "r"(&head) : "memory"); return h; }
static void push(node_t *n) { node_t *h; unsigned s; asm volatile("1: ldxr %0,[%3]\n str %0,[%2]\n stlxr %w1,%2,[%3]\n cbnz %w1,1b" : "=&r"(h), "=&r"(s) : "r"(n), "r"(&head) : "memory"); }
static void stkw(int t)
{
    long bad = 0; node_t *(*pop)(void) = sk ? pop_x : pop_a;
    for (long i = 0; i < IT; i++) {
        node_t *a = pop(); if (!a) continue;
        if (__atomic_exchange_n(&a->st, t + 1, __ATOMIC_SEQ_CST) != 0) bad++;
        node_t *b = (i & 1) ? pop() : 0;
        if (b && __atomic_exchange_n(&b->st, t + 1, __ATOMIC_SEQ_CST) != 0) bad++;
        if (__atomic_exchange_n(&a->st, 0, __ATOMIC_SEQ_CST) != (u32)t + 1) bad++;
        push(a);
        if (b) { if (__atomic_exchange_n(&b->st, 0, __ATOMIC_SEQ_CST) != (u32)t + 1) bad++; push(b); }
    }
    ADD(&sbad, bad);
}

void idiom_all(void)
{
    char n[96];
    for (int k = 0; k < (int)(sizeof rcs / sizeof *rcs); k++) {
        snprintf(n, sizeof n, "refcount_%s", rcs[k].n); if (!want(n)) continue;
        robjs = IT / 4 > 100000 ? 100000 : IT / 4 + 1; objs = amem(64, robjs * sizeof(obj_t)); memset(objs, 0, robjs * sizeof(obj_t));
        for (long r = 0; r < robjs; r++) { objs[r].rc = NT; objs[r].magic = MAGIC; }
        rneg = rdbl = ruaf = 0; rk = k; run_threads(NT, rcw);
        long notone = 0, nz = 0; for (long r = 0; r < robjs; r++) { notone += objs[r].last != 1; nz += objs[r].rc != 0; }
        result(n, rneg || rdbl || ruaf || notone || nz, "objects %ld: last-seen!=1 %ld (double-last %ld), rc!=0 %ld, negative %ld, use-after-free %ld", robjs, notone, rdbl, nz, rneg, ruaf);
        free(objs);
    }
    for (int k = 0; k < (int)(sizeof lks / sizeof *lks); k++) {
        snprintf(n, sizeof n, "spinlock_%s", lks[k].n); if (!want(n) || (k == 5 && !spin_ok())) continue;
        lockfn = lks[k].lk; unlockfn = lks[k].un; cnt = 0; aa = 0; bb = ~0ULL; incs = 0; lbad = 0; lockw[0] = lockw[1] = 0;
        run_threads(NT, lkw);
        result(n, lbad || cnt != (u64)NT * IT || aa != cnt, "counter %llu want %llu overlaps/torn %ld", (unsigned long long)cnt, (unsigned long long)NT * IT, lbad);
    }
    static int (*const tr[])(u32 *) = {try_cas, try_ll, try_aa}; static const char *tn[] = {"casa", "ldxr_stlxr", "ldaxr_stxr"};
    for (int k = 0; k < 3; k++) {
        snprintf(n, sizeof n, "once_%s", tn[k]); if (!want(n) || !spin_ok()) continue;
        nons = IT / 2 + 1; ons = amem(64, nons * sizeof(once_t)); memset(ons, 0, nons * sizeof(once_t)); tryf = tr[k]; obad = ostuck = 0; run_threads(NT, oncew);
        long notone = 0; for (long i = 0; i < nons; i++) notone += ons[i].win != 1;
        result(n, notone || obad || ostuck, "onces %ld: winners!=1 %ld, payload not visible %ld, stuck %ld", nons, notone, obad, ostuck); free(ons);
    }
    for (sk = 0; sk < 2; sk++) {
        snprintf(n, sizeof n, "stack_aba_%s", sk ? "ldxr_stxr" : "ldaxr_stlxr"); if (!want(n)) continue;
        memset(pool, 0, sizeof pool); head = 0; for (int i = 0; i < P; i++) { pool[i].id = i; push(&pool[i]); } sbad = 0; run_threads(NT, stkw);
        int cntn = 0, dup = 0; static u8 seen[16]; memset(seen, 0, sizeof seen);
        for (node_t *p = head; p && cntn <= P + 1; p = p->next) { cntn++; if (p < pool || p >= pool + 16) break; dup += seen[p->id]++; }
        result(n, sbad || cntn != P || dup, "nodes in stack at end %d want %d, repeated %d, double-owned pops %ld", cntn, P, dup, sbad);
    }
}
#pragma clang attribute pop
