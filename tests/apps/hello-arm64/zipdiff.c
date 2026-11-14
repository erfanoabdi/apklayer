/* Differential instruction/zip test (Fennec on x86, CHANGELOG 2026-11-14 part 8): the same code runs natively on arm64 (ground truth) and translated
 * on x86_64; every line is deterministic, so the two outputs must be identical. Part 1 re-implements Gecko's nsZipArchive (central directory parse
 * from an mmap'd MAP_SHARED file at an odd offset, HashName = h*37+c & 0xff, GetItem = chain walk + ldrh length + memcmp) as libxul +0x33884b0
 * compiles it; part 2 runs one instruction family per line (unaligned loads, rev/rbit/clz, crc32, multiplies, bitfields, ccmp/csel, NEON compares,
 * ldp/stp, exclusive/acquire/release atomics, memcmp, sigsetjmp + siglongjmp from a SIGBUS handler as Gecko's MmapAccessScope). */
#include <arm_acle.h>
#include <arm_neon.h>
#include <jni.h>
#include <setjmp.h>
#include <signal.h>
#include <stdarg.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>
#include <sys/syscall.h>
#include <unistd.h>

static char out[8192];
static size_t on;
static void say(const char *fmt, ...) __attribute__((format(printf, 1, 2)));
static void say(const char *fmt, ...)
{
    char line[256]; va_list ap; va_start(ap, fmt); vsnprintf(line, sizeof line, fmt, ap); va_end(ap);
    on += snprintf(out + on, sizeof out - on, "%s\n", line);
}
static uint64_t rng = 0x9e3779b97f4a7c15ull;
static uint64_t next(void) { rng ^= rng << 13; rng ^= rng >> 7; rng ^= rng << 17; return rng; }
static uint64_t fnv(uint64_t h, uint64_t v) { return (h ^ v) * 1099511628211ull; }

/* ---- part 1: nsZipArchive ---- */
struct item { struct item *next; const uint8_t *central; uint32_t pad; uint16_t namelen; };
static uint16_t le16(const uint8_t *p) { return (uint16_t)(p[0] | p[1] << 8); }
static uint32_t le32(const uint8_t *p) { return le16(p) | (uint32_t)le16(p + 2) << 16; }
static uint8_t hash_name(const uint8_t *s, uint16_t n) { uint32_t v = 0; for (uint16_t i = 0; i < n; i++) v = v * 37 + s[i]; return v & 0xff; }
static struct item *table[256];
static struct item *get_item(const char *name)
{
    uint32_t len = strlen(name); uint16_t l16 = len & 0xffff;
    for (struct item *it = table[l16 ? hash_name((const uint8_t *)name, l16) : 0]; it; it = it->next)
        if (it->namelen == len && !memcmp(name, it->central + 0x2e, len)) return it;
    return NULL;
}
static void put16(uint8_t *p, unsigned v) { p[0] = v; p[1] = v >> 8; }
static void put32(uint8_t *p, uint32_t v) { put16(p, v); put16(p + 2, v >> 16); }

static void zip_test(void)
{
    enum { N = 700, PREFIX = 4099 };  /* the zip starts at an odd offset, as omni.ja stored inside an APK */
    static char names[N][64]; static uint8_t zip[1 << 18]; static struct item items[N];
    uint8_t *z = zip + PREFIX, *p = z; unsigned cd[N], nlen[N];
    for (int i = 0; i < N; i++) {
        const char *dirs[] = {"chrome/toolkit/content/", "defaults/pref/", "modules/", "res/", ""};
        if (i == 0) strcpy(names[i], "greprefs.js"); else if (i == 1) strcpy(names[i], "defaults/pref/geckoview-prefs.js");
        else snprintf(names[i], sizeof names[i], "%s%s%u.%s", dirs[i % 5], i % 3 ? "file" : "m", (unsigned)(next() & 0xffff), i % 2 ? "js" : "xhtml");
        nlen[i] = strlen(names[i]); cd[i] = p - z;
        put32(p, 0x04034b50); memset(p + 4, 0, 22); put16(p + 26, nlen[i]); put16(p + 28, i % 4);  /* local header, extra 0..3 */
        memcpy(p + 30, names[i], nlen[i]); p += 30 + nlen[i] + i % 4;
        memset(p, 'A' + i % 26, 7 + i % 13); p += 7 + i % 13;  /* stored data of odd length */
    }
    uint8_t *cds = p;
    for (int i = 0; i < N; i++) {
        put32(p, 0x02014b50); memset(p + 4, 0, 42); put16(p + 28, nlen[i]); put32(p + 42, cd[i]);
        memcpy(p + 46, names[i], nlen[i]); p += 46 + nlen[i];
    }
    put32(p, 0x06054b50); memset(p + 4, 0, 18); put16(p + 8, N); put16(p + 10, N); put32(p + 12, p - cds); put32(p + 16, cds - z); p += 22;
    size_t total = p - zip;
    int fd = syscall(SYS_memfd_create, "zipdiff", 0);
    if (fd < 0 || write(fd, zip, total) != (ssize_t)total) { say("zip memfd FAIL"); return; }
    const uint8_t *map = mmap(NULL, total, PROT_READ, MAP_SHARED, fd, 0);
    if (map == MAP_FAILED) { say("zip mmap FAIL"); return; }
    const uint8_t *base = map + PREFIX, *end = map + total;  /* EOCD scan backwards, as nsZipArchive::BuildFileList */
    const uint8_t *e = end - 22; while (e > base && le32(e) != 0x06054b50) e--;
    unsigned count = le16(e + 10), built = 0; const uint8_t *c = base + le32(e + 16);
    for (; built < count && le32(c) == 0x02014b50; built++) {
        struct item *it = &items[built]; it->central = c; it->namelen = le16(c + 28);
        uint8_t h = hash_name(c + 46, it->namelen); it->next = table[h]; table[h] = it;
        c += 46 + it->namelen + le16(c + 30) + le16(c + 32);
    }
    say("zip entries %u built %u eocd@%ld", count, built, (long)(e - base));
    uint64_t hh = 0; int found = 0, wrong = 0;
    for (int i = 0; i < N; i++) {
        struct item *it = get_item(names[i]);
        if (it) { found++; if (it - items != i) wrong++; hh = fnv(hh, le32(it->central + 42)); }
        char bad[80]; snprintf(bad, sizeof bad, "%s", names[i]); bad[nlen[i] - 1] ^= 1;
        if (get_item(bad)) wrong += 1000;
    }
    struct item *g = get_item("greprefs.js");
    say("zip lookups found %d wrong %d hash %016llx greprefs@%d hash(greprefs.js)=%02x", found, wrong, (unsigned long long)hh, g ? (int)(g - items) : -1, hash_name((const uint8_t *)"greprefs.js", 11));
    munmap((void *)map, total); close(fd);
}

/* ---- part 2: instruction families ---- */
static uint8_t buf[256] __attribute__((aligned(16)));
static sigjmp_buf jb;
static void on_bus(int s) { (void)s; siglongjmp(jb, 7); }

__attribute__((target("crc"))) static uint64_t crc_family(uint64_t a, uint64_t b)
{
    uint32_t c = (uint32_t)a;
    c = __crc32b(c, (uint8_t)b); c = __crc32h(c, (uint16_t)(b >> 8)); c = __crc32w(c, (uint32_t)(b >> 24)); c = __crc32d(c, b);
    c = __crc32cb(c, (uint8_t)a); c = __crc32ch(c, (uint16_t)b); c = __crc32cw(c, (uint32_t)a); c = __crc32cd(c, a ^ b);
    return c;
}

static void insn_tests(void)
{
    for (unsigned i = 0; i < sizeof buf; i++) buf[i] = (uint8_t)(next() >> 24);
    uint64_t h;
    h = 0; for (int o = 0; o < 64; o++) { uint16_t a; uint32_t b; uint64_t c; memcpy(&a, buf + o, 2); memcpy(&b, buf + o + 1, 4); memcpy(&c, buf + o + 3, 8); h = fnv(fnv(fnv(h, a), b), c); }
    say("unaligned ldurh/ldur w/x %016llx", (unsigned long long)h);
    h = 0; for (int i = 0; i < 100; i++) { uint64_t v = next(); h = fnv(fnv(fnv(fnv(h, __builtin_bswap64(v)), __rbitll(v)), __builtin_clzll(v >> (i % 60))), __builtin_bswap32((uint32_t)v) ^ __builtin_clz((uint32_t)v | 1)); }
    say("rev/rbit/clz %016llx", (unsigned long long)h);
    h = 0; for (int i = 0; i < 100; i++) { uint64_t a = next(), b = next(); h = fnv(h, crc_family(a, b)); }
    say("crc32/crc32c %016llx", (unsigned long long)h);
    h = 0; for (int i = 0; i < 100; i++) { uint64_t a = next(), b = next(), c = next(); uint32_t x = a, y = b;
        h = fnv(fnv(fnv(fnv(fnv(h, (uint32_t)(x * 37 + y)), (uint64_t)x * y), (uint64_t)(((unsigned __int128)a * b) >> 64)), (uint64_t)(((__int128)(int64_t)a * (int64_t)b) >> 64)), c - a * b); }
    say("mul/madd/msub/umull/umulh/smulh %016llx", (unsigned long long)h);
    h = 0; for (int i = 0; i < 100; i++) { uint64_t a = next(), b = next(); int s = i % 50;
        uint64_t u = (a >> s) & 0xfff, f = (a & ~(0xffull << s)) | ((b & 0xff) << s); int64_t sx = (int64_t)(a << (56 - s)) >> 56;
        h = fnv(fnv(fnv(fnv(h, u), f), (uint64_t)sx), (uint8_t)a + (uint16_t)b + ((a & 0xffff) | (b << 8) | (a >> 3))); }
    say("ubfx/bfi/sbfx/uxtb/uxth/orr-shifted %016llx", (unsigned long long)h);
    h = 0; for (int i = 0; i < 200; i++) { uint64_t a = next(), b = next(); int64_t x = a, y = b;
        int r = (x < y && (a & 1)) || (x == y); uint64_t m = a > b ? a : b, n = a < b ? a - 1 : b + 1; uint32_t w = (uint32_t)a > (uint32_t)b ? 5 : 9;
        h = fnv(fnv(fnv(fnv(h, r), m), n), w); }
    say("cmp/ccmp/csel/csinc %016llx", (unsigned long long)h);
    h = 0; for (int i = 0; i < 100; i++) { uint8x16_t a = vld1q_u8(buf + (i % 64) + 1), b = vld1q_u8(buf + ((i * 7) % 64) + 3);
        uint8x16_t eq = vceqq_u8(a, b), lt = vcltq_u8(a, b); uint8x16_t z = vceqq_u8(a, vdupq_n_u8((uint8_t)i));
        h = fnv(fnv(fnv(fnv(fnv(h, vmaxvq_u8(eq)), vaddvq_u8(vandq_u8(lt, vdupq_n_u8(1)))), vminvq_u8(a)), vaddvq_u32(vpaddlq_u16(vpaddlq_u8(z)))), vgetq_lane_u64(vreinterpretq_u64_u8(veorq_u8(a, b)), 1));
        uint64_t m; vst1_u8((uint8_t *)&m, vshrn_n_u16(vreinterpretq_u16_u8(eq), 4)); h = fnv(h, m); }
    say("NEON ldr q/cmeq/cmhi/umaxv/uminv/addv/shrn %016llx", (unsigned long long)h);
    h = 0; { uint64_t a[8], b[8]; for (int i = 0; i < 8; i++) a[i] = next(); memcpy(b, a, sizeof a);
      for (int i = 0; i < 6; i++) { uint64_t x = a[i], y = a[i + 1]; __asm__ volatile("stp %1, %2, [%0]" :: "r"(b + i), "r"(y), "r"(x) : "memory"); __asm__ volatile("ldp %0, %1, [%2, #8]" : "=r"(x), "=r"(y) : "r"(b + i) : "memory"); h = fnv(fnv(h, x), y); } }
    say("stp/ldp %016llx", (unsigned long long)h);
    { uint64_t t = 0, r; uint32_t w; static uint64_t cell = 100;
      for (int i = 0; i < 50; i++) { do { __asm__ volatile("ldxr %0, [%2]\n add %0, %0, %3\n stxr %w1, %0, [%2]" : "=&r"(r), "=&r"(w) : "r"(&cell), "r"((uint64_t)i) : "memory"); } while (w); t += r; }
      __atomic_store_n(&cell, 5, __ATOMIC_RELEASE); uint64_t l = __atomic_load_n(&cell, __ATOMIC_ACQUIRE), o = 5, d = 9; uint32_t ok = __atomic_compare_exchange_n(&cell, &o, d, 0, __ATOMIC_ACQ_REL, __ATOMIC_ACQUIRE);
      uint64_t f = __atomic_fetch_add(&cell, 3, __ATOMIC_SEQ_CST);
      say("ldxr/stxr/ldar/stlr/cas %llu %llu %u %llu %llu", (unsigned long long)t, (unsigned long long)l, ok, (unsigned long long)f, (unsigned long long)cell); }
    h = 0; for (int s1 = 0; s1 < 16; s1++) for (int s2 = 0; s2 < 16; s2++) for (unsigned n = 0; n < 40; n++) { uint8_t t[2][96]; memcpy(t[0], buf, 96); memcpy(t[1], buf, 96);
        if (n && (n % 3 == 0)) t[1][s2 + n - 1] ^= 4; int r = memcmp(t[0] + s1, t[1] + s2, n); h = fnv(h, r < 0 ? 1 : r > 0 ? 2 : 0); }
    say("memcmp 16x16 alignments %016llx", (unsigned long long)h);
}

static void jmp_test(void)
{
    struct sigaction sa = {.sa_handler = on_bus}, old; sigaction(SIGBUS, &sa, &old);
    volatile int first = 0; int back = 0;
    int r = sigsetjmp(jb, 0);  /* w1 = 0 as in 0x33886dc: 0 now, 7 after the siglongjmp */
    if (r == 0) { first++; raise(SIGBUS); } else back = r;
    sigaction(SIGBUS, &old, NULL);
    say("sigsetjmp first=%d after raise(SIGBUS)+siglongjmp=%d", first, back);
}

JNIEXPORT jstring JNICALL Java_hello_arm64_Main_zipDiff(JNIEnv *env, jclass c)
{
    (void)c; on = 0; out[0] = 0;
    zip_test(); insn_tests(); jmp_test();
    return (*env)->NewStringUTF(env, out);
}
