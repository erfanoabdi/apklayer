/* Memory-ordering litmus tests (SB, MP, IRIW, CoRR) with per-iteration rendezvous, seqlock and Peterson lock. A "forbidden" outcome (arm64 forbids it) is a FAIL;
 * an "allowed" one is only counted (informational: x86 and arm64 both may show it). */
#include "atomics.h"
typedef struct { volatile u64 x CL; volatile u64 y CL; volatile u32 arr; volatile u32 pad; volatile u64 r[4]; char fill[96]; } L;
static L *ls; static long NL; static int curt;
#define LDAR(d, p) asm volatile("ldar %0,[%1]" : "=r"(d) : "r"(p) : "memory")
#define LDR(d, p) asm volatile("ldr %0,[%1]" : "=r"(d) : "r"(p) : "memory")
#define STR(v, p) asm volatile("str %0,[%1]" :: "r"((u64)(v)), "r"(p) : "memory")
#define STLR(v, p) asm volatile("stlr %0,[%1]" :: "r"((u64)(v)), "r"(p) : "memory")
#define DMB(k) asm volatile("dmb " k ::: "memory")
#define LD(m, d, p) m(d, p)
/* body(l, tid): the code of thread tid for one iteration; results in l->r[] */
typedef void (*body_t)(L *, int);
#define STORE_(KIND, v, p) KIND(v, p)
#define SB(NAME, ST, FENCE, LDK) \
    static void NAME(L *l, int t) { u64 r; if (t == 0) { ST(1, &l->x); FENCE; LDK(r, &l->y); l->r[0] = r; } else { ST(1, &l->y); FENCE; LDK(r, &l->x); l->r[1] = r; } }
SB(sb_dmb, STR, DMB("ish"), LDR) SB(sb_rcsc, STLR, (void)0, LDAR) SB(sb_plain, STR, (void)0, LDR) SB(sb_ishst, STR, DMB("ishst"), LDR) SB(sb_ishld, STR, DMB("ishld"), LDR) SB(sb_stlr_ldr, STLR, (void)0, LDR) SB(sb_str_ldar, STR, (void)0, LDAR)
#define MP(NAME, ST, F0, LDK, F1) \
    static void NAME(L *l, int t) { u64 a, b; if (t == 0) { STR(1, &l->x); F0; ST(1, &l->y); } else { LDK(a, &l->y); F1; LDR(b, &l->x); l->r[0] = a; l->r[1] = b; } }
MP(mp_rel_acq, STLR, (void)0, LDAR, (void)0) MP(mp_dmb, STR, DMB("ishst"), LDR, DMB("ishld")) MP(mp_plain, STR, (void)0, LDR, (void)0) MP(mp_ishst_ldar, STR, DMB("ishst"), LDAR, (void)0)
MP(mp_stlr_ishld, STLR, (void)0, LDR, DMB("ishld")) MP(mp_dmbish, STR, DMB("ish"), LDR, DMB("ish"))
#define IRIW(NAME, ST, LDK, F) \
    static void NAME(L *l, int t) { u64 a, b; switch (t) { case 0: ST(1, &l->x); break; case 1: ST(1, &l->y); break; \
        case 2: LDK(a, &l->x); F; LDK(b, &l->y); l->r[0] = a; l->r[1] = b; break; default: LDK(a, &l->y); F; LDK(b, &l->x); l->r[2] = a; l->r[3] = b; } }
IRIW(iriw_ldar, STLR, LDAR, (void)0) IRIW(iriw_dmb, STR, LDR, DMB("ish")) IRIW(iriw_dmbld, STR, LDR, DMB("ishld")) IRIW(iriw_plain, STR, LDR, (void)0) IRIW(iriw_str_ldar, STR, LDAR, (void)0)
static void corr(L *l, int t) { u64 a, b; if (t == 0) { STLR(1, &l->x); STLR(2, &l->x); } else { LDR(a, &l->x); LDR(b, &l->x); l->r[0] = a; l->r[1] = b; } }
static void corr_ldar(L *l, int t) { u64 a, b; if (t == 0) { STR(1, &l->x); STR(2, &l->x); } else { LDAR(a, &l->x); LDAR(b, &l->x); l->r[0] = a; l->r[1] = b; } }
static int w_sb(L *l) { return !l->r[0] && !l->r[1]; }
static int w_mp(L *l) { return l->r[0] == 1 && !l->r[1]; }
static int w_iriw(L *l) { return l->r[0] == 1 && !l->r[1] && l->r[2] == 1 && !l->r[3]; }
static int w_corr(L *l) { return l->r[1] < l->r[0]; }
static const struct { const char *n; int nt; body_t b; int (*weak)(L *); int forbidden; } lits[] = {
    {"sb_dmb_ish", 2, sb_dmb, w_sb, 1}, {"sb_stlr_ldar", 2, sb_rcsc, w_sb, 1}, {"sb_plain", 2, sb_plain, w_sb, 0}, {"sb_dmb_ishst", 2, sb_ishst, w_sb, 0}, {"sb_dmb_ishld", 2, sb_ishld, w_sb, 0},
    {"sb_stlr_ldr", 2, sb_stlr_ldr, w_sb, 0}, {"sb_str_ldar", 2, sb_str_ldar, w_sb, 0},
    {"mp_stlr_ldar", 2, mp_rel_acq, w_mp, 1}, {"mp_ishst_ishld", 2, mp_dmb, w_mp, 1}, {"mp_ishst_ldar", 2, mp_ishst_ldar, w_mp, 1}, {"mp_stlr_ishld", 2, mp_stlr_ishld, w_mp, 1}, {"mp_dmb_ish", 2, mp_dmbish, w_mp, 1}, {"mp_plain", 2, mp_plain, w_mp, 0},
    {"iriw_stlr_ldar", 4, iriw_ldar, w_iriw, 1}, {"iriw_dmb_ish", 4, iriw_dmb, w_iriw, 1}, {"iriw_dmb_ishld", 4, iriw_dmbld, w_iriw, 1}, {"iriw_plain", 4, iriw_plain, w_iriw, 0}, {"iriw_str_ldar", 4, iriw_str_ldar, w_iriw, 1},
    {"corr_ldr", 2, corr, w_corr, 1}, {"corr_ldar", 2, corr_ldar, w_corr, 1},
};
static const int *cur; static int ci;
static void litw(int t)
{
    for (long i = 0; i < NL; i++) {
        L *l = &ls[i]; unsigned h = (unsigned)((i + 1) * (2 * t + 3) * 2654435761u) >> 27 & 15;
        __atomic_fetch_add(&l->arr, 1, __ATOMIC_SEQ_CST); while (__atomic_load_n(&l->arr, __ATOMIC_ACQUIRE) < (u32)lits[ci].nt) ;
        for (unsigned k = 0; k < h; k++) asm volatile("nop");
        lits[ci].b(l, t);
    }
}

/* ---- seqlock: writer (tid 0) with data (a, ~a, 3a); readers must never accept a torn snapshot ---- */
static volatile u64 sq CL, sa, sb_, sc; static volatile int sdone; static int sqk; static long sqbad, sqreads;
static void seqw(int t)
{
    if (t == 0) {
        for (long i = 1; i <= IT; i++) {
            u64 s = sq; STR(s + 1, &sq);
            if (sqk == 0) DMB("ishst"); else DMB("ish");
            sa = i; sb_ = ~(u64)i; sc = 3 * (u64)i;
            if (sqk == 2) { DMB("ish"); STR(s + 2, &sq); } else STLR(s + 2, &sq);
        }
        __atomic_store_n(&sdone, 1, __ATOMIC_RELEASE); return;
    }
    long bad = 0, reads = 0;
    while (!__atomic_load_n(&sdone, __ATOMIC_ACQUIRE)) {
        u64 s1, s2, a, b, c;
        if (sqk == 2) { LDR(s1, &sq); DMB("ish"); } else LDAR(s1, &sq);
        if (s1 & 1) continue;
        a = sa; b = sb_; c = sc;
        if (sqk == 0) DMB("ishld"); else DMB("ish");
        LDR(s2, &sq);
        if (s1 != s2) continue;
        reads++; if (b != ~a || c != 3 * a) bad++;
    }
    ADD(&sqbad, bad); ADD(&sqreads, reads);
}

/* ---- Peterson lock with stlr/ldar (and plain stores + dmb ish): needs the store->load ordering of RCsc ---- */
static volatile u64 flag[2] CL, turn CL; static volatile u64 pcnt CL; static volatile int pin; static int pk; static long pbad;
static void petw(int t)
{
    int o = 1 - t; long bad = 0;
    for (long i = 0; i < IT; i++) {
        u64 f, tn;
        if (pk == 0) { STLR(1, &flag[t]); STLR(o, &turn); for (;;) { LDAR(f, &flag[o]); if (!f) break; LDAR(tn, &turn); if (tn != (u64)o) break; } }
        else { STR(1, &flag[t]); STR(o, &turn); DMB("ish"); for (;;) { LDR(f, &flag[o]); if (!f) break; LDR(tn, &turn); if (tn != (u64)o) break; } DMB("ish"); }
        if (pin) bad++; pin = 1; pcnt = pcnt + 1; pin = 0;
        if (pk == 0) STLR(0, &flag[t]); else { DMB("ish"); STR(0, &flag[t]); }
    }
    ADD(&pbad, bad);
}

void litmus_all(void)
{
    char n[96]; (void)cur; (void)curt;
    if (!spin_ok()) return;
    NL = IT * 5 > 100000 ? 100000 : IT * 5 < 1000 ? 1000 : IT * 5; ls = amem(128, NL * sizeof(L));
    for (ci = 0; ci < (int)(sizeof lits / sizeof *lits); ci++) {
        snprintf(n, sizeof n, "litmus_%s", lits[ci].n); if (!want(n)) continue;
        memset(ls, 0, NL * sizeof(L)); run_threads(lits[ci].nt, litw);
        long weak = 0; for (long i = 0; i < NL; i++) weak += lits[ci].weak(&ls[i]);
        result(n, lits[ci].forbidden && weak, "%s outcome seen %ld of %ld", lits[ci].forbidden ? "forbidden" : "allowed (info)", weak, NL);
    }
    free(ls);
    static const char *sn[] = {"ishst_ishld", "dmb_ish", "full_dmb"};
    for (sqk = 0; sqk < 3; sqk++) {
        snprintf(n, sizeof n, "seqlock_%s", sn[sqk]); if (!want(n)) continue;
        sq = sa = sc = 0; sb_ = ~0ULL; sdone = 0; sqbad = sqreads = 0; run_threads(NT, seqw);
        result(n, sqbad, "torn snapshots %ld of %ld accepted reads", sqbad, sqreads);
    }
    for (pk = 0; pk < 2; pk++) {
        snprintf(n, sizeof n, "peterson_%s", pk ? "str_dmb" : "stlr_ldar"); if (!want(n)) continue;
        flag[0] = flag[1] = turn = 0; pcnt = 0; pin = 0; pbad = 0; run_threads(2, petw);
        result(n, pbad || pcnt != 2 * (u64)IT, "counter %llu want %llu overlaps %ld", (unsigned long long)pcnt, 2ULL * IT, pbad);
    }
}
#pragma clang attribute pop
