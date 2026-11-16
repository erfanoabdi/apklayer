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
#ifdef __clang__
#pragma clang attribute pop
#endif
