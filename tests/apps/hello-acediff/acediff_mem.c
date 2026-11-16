/* acediff, part 4: a library restoring its own text from the file. Anti-tamper code (Tencent ACE) maps the pristine file over the running text of libc and of
 * itself (MAP_FIXED) and reopens the paths it found in /proc/self/maps; the translator must keep running such a process and the path in the maps must open. */
#include "acediff.h"

struct mline { uintptr_t lo, hi, off; char path[512]; int found; };
static struct mline find_text(const char *needle, uintptr_t addr)
{
    struct mline m; memset(&m, 0, sizeof m);
    FILE *f = fopen("/proc/self/maps", "r"); char l[1024];
    while (f && fgets(l, sizeof l, f)) {
        unsigned long lo, hi, off; char pr[8], p[512] = "";
        if (sscanf(l, "%lx-%lx %7s %lx %*s %*s %511s", &lo, &hi, pr, &off, p) < 4 || pr[2] != 'x' || !p[0]) continue;
        if ((addr && addr >= lo && addr < hi) || (needle && strstr(p, needle))) { m.lo = lo; m.hi = hi; m.off = off; strcpy(m.path, p); m.found = 1; break; }
    }
    if (f) fclose(f);
    return m;
}
static int selftext_marker(void) { return 4242; }

uint64_t t_selftext(void)
{
    uint64_t h = 0;
    struct mline own = find_text(NULL, (uintptr_t)selftext_marker), libc = find_text("/libc.so", 0);
    FL("own text line in maps", own.found);
    struct mline *ms[2] = {&own, &libc}; const char *nm[2] = {"own", "libc"};
    for (int k = 0; k < 2; k++) {
        if (!ms[k]->found) continue;   /* a static program has no libc.so line: not printed on either side */
        int fd = open(ms[k]->path, O_RDONLY | O_CLOEXEC);
        say("selftext %s: open of the maps path %s", nm[k], fd >= 0 ? "ok" : strerror(errno));
        if (fd < 0) continue;
        /* the same bytes over the running text (MAP_FIXED, private, read+exec): execution goes on and libc still answers */
        void *r = mmap((void *)ms[k]->lo, ms[k]->hi - ms[k]->lo, PROT_READ | PROT_EXEC, MAP_PRIVATE | MAP_FIXED, fd, (off_t)ms[k]->off);
        say("selftext %s: mmap fixed over the whole text %s", nm[k], r == (void *)ms[k]->lo ? "ok" : strerror(errno));
        close(fd);
        say("selftext %s: after the remap getpid %d marker %d", nm[k], getpid() == (pid_t)syscall(SYS_getpid), selftext_marker());
        /* only the first pages (ACE's hole was the start of libc's text), then a libc call */
        fd = open(ms[k]->path, O_RDONLY | O_CLOEXEC);
        if (fd >= 0) {
            size_t part = ms[k]->hi - ms[k]->lo > 0x9000 ? 0x9000 : 0x1000;
            r = mmap((void *)ms[k]->lo, part, PROT_READ | PROT_EXEC, MAP_PRIVATE | MAP_FIXED, fd, (off_t)ms[k]->off); close(fd);
            say("selftext %s: partial remap %s", nm[k], r == (void *)ms[k]->lo ? "ok" : strerror(errno));
            errno = 0; (void)open("/nonexistent-acediff", O_RDONLY);
            say("selftext %s: errno after the partial remap %d", nm[k], errno);
        }
        FL("selftext remap done", 1);
    }
    return h;
}
/* After a munmap of its OWN 16 KB block the process must see the hole through every introspection channel the same way (ACE unmaps a block, then cross-checks
 * maps, mincore, process_vm_readv and re-reads maps): maps no longer covers it, mincore says ENOMEM, process_vm_readv EFAULT, pagemap shows no page there. */
#include <sys/stat.h>
#include <sys/uio.h>
static int maps_covers(uintptr_t a, int *lines_in, uintptr_t lo, uintptr_t hi)
{
    FILE *f = fopen("/proc/self/maps", "r"); char l[1024]; int covers = 0; *lines_in = 0;
    while (f && fgets(l, sizeof l, f)) {
        unsigned long s, e; if (sscanf(l, "%lx-%lx", &s, &e) != 2) continue;
        if (a >= s && a < e) covers = 1;
        if (s < hi && e > lo) (*lines_in)++;
    }
    if (f) fclose(f);
    return covers;
}
static void views_of(const char *what, uintptr_t base, size_t pages, size_t hole_page)
{
    uintptr_t hole = base + hole_page * 4096; int nl;
    int cov = maps_covers(hole, &nl, base, base + pages * 4096);
    say("munmap views %s: maps covers the hole %d, map lines over the block %d", what, cov, nl);
    unsigned char vec[16]; errno = 0; int r = mincore((void *)hole, 4096, vec);
    say("munmap views %s: mincore of the hole %d errno %d", what, r, r ? errno : 0);
    errno = 0; r = mincore((void *)(base + (hole_page ? 0 : 4096)), 4096, vec);
    say("munmap views %s: mincore of a mapped page %d", what, r);
    char buf[16]; struct iovec lo = {buf, 8}, ro = {(void *)hole, 8};
    errno = 0; ssize_t n = process_vm_readv(getpid(), &lo, 1, &ro, 1, 0);
    say("munmap views %s: process_vm_readv of the hole %zd errno %d", what, n, n < 0 ? errno : 0);
    ro.iov_base = (void *)(base + (hole_page ? 0 : 4096)); n = process_vm_readv(getpid(), &lo, 1, &ro, 1, 0);
    say("munmap views %s: process_vm_readv of a mapped page %zd", what, n);
    int pm = open("/proc/self/pagemap", O_RDONLY); uint64_t e = 7;
    if (pm >= 0) { ssize_t k = pread(pm, &e, 8, (off_t)(hole / 4096 * 8)); say("munmap views %s: pagemap of the hole %zd entry %llx", what, k, (unsigned long long)e); close(pm); }
    else say("munmap views %s: pagemap open %s", what, strerror(errno));
    char path[96], tgt[256]; snprintf(path, sizeof path, "/proc/self/map_files/%lx-%lx", (unsigned long)hole, (unsigned long)hole + 4096);
    ssize_t k = readlink(path, tgt, sizeof tgt); say("munmap views %s: map_files link of the hole %zd", what, k);
}
uint64_t t_munmap_views(void)
{
    uint64_t h = 0; size_t blk = 0x4000;
    char *a = mmap(NULL, 0x10000, PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    if (a == MAP_FAILED) return 1;
    memset(a, 1, 0x10000);
    uintptr_t b = ((uintptr_t)a + blk - 1) & ~(blk - 1);      /* the 16 KB aligned block inside, as ACE's (&fn & ~0x3fff) */
    munmap((void *)b, blk);
    views_of("anonymous", (uintptr_t)a, 0x10000 / 4096, (b - (uintptr_t)a) / 4096);
    /* code that already ran (translated), then one 16 KB block of it unmapped: the guest-exec bookkeeping must forget the block too */
    {
        uint32_t *c = mmap(NULL, 0x10000 + blk, PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
        if (c != MAP_FAILED) {
            for (size_t i = 0; i < (0x10000 + blk) / 4; i += 4096 / 4) { c[i] = 0x52800540u; c[i + 1] = 0xd65f03c0u; }  /* movz w0,#42 ; ret at every page start */
            mprotect(c, 0x10000 + blk, PROT_READ | PROT_EXEC);
            uintptr_t cb = ((uintptr_t)c + blk - 1) & ~(blk - 1);
            int ok = 1; for (size_t p = 0; p < (0x10000 + blk) / 4096; p++) ok &= ((int (*)(void))((char *)c + p * 4096))() == 42;
            munmap((void *)(cb + blk), blk);
            views_of("executed code", (uintptr_t)c, (0x10000 + blk) / 4096, (cb + blk - (uintptr_t)c) / 4096);
            say("munmap views executed code: the pages before the hole still run %d", ((int (*)(void))c)() == 42 && ok);
        }
    }
    /* a file-backed code block: map a library's own text (r-x) again at a new address, then unmap one 16 KB block of it */
    struct mline own = find_text(NULL, (uintptr_t)t_munmap_views);
    if (own.found) {
        int fd = open(own.path, O_RDONLY | O_CLOEXEC);
        struct stat st; size_t len = 0x10000;
        if (fd >= 0 && fstat(fd, &st) == 0 && (size_t)st.st_size < len + blk) len = ((size_t)st.st_size & ~(size_t)0xffff) > blk ? ((size_t)st.st_size & ~(size_t)0xffff) - blk : 0;
        void *t = fd < 0 || len < 2 * blk ? MAP_FAILED : mmap(NULL, len + blk, PROT_READ | PROT_EXEC, MAP_PRIVATE, fd, 0);
        if (t != MAP_FAILED) {
            uintptr_t tb = ((uintptr_t)t + blk - 1) & ~(blk - 1);
            munmap((void *)(tb + blk), blk);               /* a middle block of the text mapping */
            views_of("file text", (uintptr_t)t, (len + blk) / 4096, (tb + blk - (uintptr_t)t) / 4096);
        } else say("munmap views file text: no mapping (%s)", fd < 0 ? strerror(errno) : "mmap failed");
        if (fd >= 0) close(fd);
    }
    FL("munmap views done", 1);
    return h;
}
#ifdef __clang__
#pragma clang attribute pop
#endif
