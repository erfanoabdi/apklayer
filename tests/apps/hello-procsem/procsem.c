/* Semantics of reading proc/sys files, one line per file and operation: "file|op|result|errno|detail". "ovr=N/M": N bytes past the requested count changed
 * (canary-filled guard of 8 KB), the last at offset M; ret>n is flagged "MORE". The same source runs as an app (JNI, under the translator or native) and as a
 * static program on a real kernel (-DPROCSEM_MAIN: ground truth). Values that depend on the content (sizes) differ legitimately; st_size, lseek, ret>n and ovr do not. */
#define _GNU_SOURCE
#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <pthread.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <sys/sendfile.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <sys/uio.h>
#include <unistd.h>

#define GUARD 8192
static char *out; static size_t used; static const char *cur;
static void P(const char *op, const char *fmt, ...) {
  if (used > (4u << 20) - 512) return;
  used += snprintf(out + used, 512, "%s|%s|", cur, op);
  __builtin_va_list ap; __builtin_va_start(ap, fmt); used += vsnprintf(out + used, 480, fmt, ap); __builtin_va_end(ap);
  out[used++] = '\n'; out[used] = 0;
}
static long sc(long n, long a, long b, long c, long d, long e, long f) {  /* an inline svc: what a protected library does (no libc) */
  register long x8 __asm__("x8") = n, x0 __asm__("x0") = a, x1 __asm__("x1") = b, x2 __asm__("x2") = c, x3 __asm__("x3") = d, x4 __asm__("x4") = e, x5 __asm__("x5") = f;
  __asm__ volatile("svc 0" : "+r"(x0) : "r"(x8), "r"(x1), "r"(x2), "r"(x3), "r"(x4), "r"(x5) : "memory", "cc");
  return x0;
}
static char *mk(size_t n) { char *b = malloc(n + GUARD); memset(b, 0xA5, n + GUARD); return b; }
static void ovr(char *b, size_t n, char *s) {  /* guard report */
  int c = 0, last = -1; for (int i = 0; i < GUARD; i++) if ((unsigned char)b[n + i] != 0xA5) { c++; last = i; }
  sprintf(s, "ovr=%d/%d", c, last);
}
#define R(x) ((long)(x) < 0 ? -1L : (long)(x))
#define E(x) ((long)(x) < 0 ? errno : 0)
static void rd(const char *op, long r, size_t n, char *b) {
  char g[32]; ovr(b, n, g); P(op, "%ld|%ld|%s%s", R(r), E(r), g, r > (long)n ? " MORE" : "");
}
static void all(const char *path, int fl, const char *tag) {
  char t[64]; int fd = open(path, fl | O_CLOEXEC); snprintf(t, sizeof t, "open%s", tag);
  if (fd < 0) { P(t, "-1|%d|", errno); return; }
  struct stat st; int r = fstat(fd, &st);
  P(t, "%d|0|mode=%o size=%lld blksize=%ld blocks=%lld nlink=%ld", fd, st.st_mode & 07777, (long long)st.st_size, (long)st.st_blksize, (long long)st.st_blocks, (long)st.st_nlink); (void)r;
  if (*tag) { close(fd); return; }
  static const size_t ns[] = {1, 7, 100, 1000, 4095, 4096, 4097, 8192, 65536};
  char op[48];
  for (int i = 0; i < 9; i++) {
    size_t n = ns[i]; char *b = mk(n); long k;
    snprintf(op, sizeof op, "pread0(%zu)", n); k = pread(fd, b, n, 0); rd(op, k, n, b);
    memset(b, 0xA5, n + GUARD); lseek(fd, 0, SEEK_SET);
    snprintf(op, sizeof op, "read(%zu)", n); k = read(fd, b, n); rd(op, k, n, b);
    memset(b, 0xA5, n + GUARD); lseek(fd, 0, SEEK_SET);
    snprintf(op, sizeof op, "svc-read(%zu)", n); k = sc(SYS_read, fd, (long)b, n, 0, 0, 0); if (k < 0) { errno = -k; k = -1; } rd(op, k, n, b);
    memset(b, 0xA5, n + GUARD);
    snprintf(op, sizeof op, "svc-pread0(%zu)", n); k = sc(SYS_pread64, fd, (long)b, n, 0, 0, 0); if (k < 0) { errno = -k; k = -1; } rd(op, k, n, b);
    free(b);
  }
  if (st.st_size > 0 && st.st_size < (1 << 20)) { size_t n = st.st_size; char *b = mk(n); lseek(fd, 0, SEEK_SET); long k = read(fd, b, n); rd("read(st_size)", k, n, b); free(b); }
  { size_t n = 1 << 20; char *b = mk(n); lseek(fd, 0, SEEK_SET); long k = read(fd, b, n); rd("read(1M)", k, n, b);
    P("read(1M)-nul", "%d", k > 0 ? (int)(memchr(b, 0, k) != NULL) : -1); free(b); }
  { /* the whole file in 4096 chunks: calls, per-call counts bounded, total, EOF twice */
    lseek(fd, 0, SEEK_SET); char *b = mk(4096); long tot = 0, calls = 0, first = -1, mx = 0, k; int over = 0; char g[32];
    while ((k = read(fd, b, 4096)) > 0) { if (!calls) first = k; if (k > mx) mx = k; tot += k; calls++; if (k > 4096) over = 1; memset(b, 0xA5, 4096); if (calls > 100000) break; }
    ovr(b, 4096, g); P("loop4096", "tot>0=%d calls=%ld first=%ld max=%ld end=%ld over=%d %s", tot > 0, calls, first, mx, k, over, g);
    for (int i = 0; i < 2; i++) { k = read(fd, b, 4096); P("read-at-eof", "%ld|%ld", R(k), E(k)); }
    off_t offs[] = {tot, tot + 1, 1 << 20, 1L << 40, -1};
    for (int i = 0; i < 5; i++) { k = pread(fd, b, 100, offs[i]); snprintf(op, sizeof op, "pread(off=%s)", i == 0 ? "size" : i == 1 ? "size+1" : i == 2 ? "1M" : i == 3 ? "1T" : "-1"); P(op, "%ld|%ld", R(k), E(k)); }
    k = pread(fd, b, 100, 1); P("pread(off=1)", "%ld|%ld", R(k), E(k)); free(b);
  }
  { struct { const char *n; off_t o; int w; } ls[] = {{"SET 0", 0, SEEK_SET}, {"SET 10", 10, SEEK_SET}, {"SET 1G", 1 << 30, SEEK_SET}, {"SET -1", -1, SEEK_SET}, {"CUR 0", 0, SEEK_CUR},
      {"CUR -5", -5, SEEK_CUR}, {"END 0", 0, SEEK_END}, {"END -1", -1, SEEK_END}, {"END 1", 1, SEEK_END}, {"DATA 0", 0, 3}, {"HOLE 0", 0, 4}};
    for (unsigned i = 0; i < sizeof ls / sizeof *ls; i++) { lseek(fd, 0, SEEK_SET); off_t r2 = lseek(fd, ls[i].o, ls[i].w); snprintf(op, sizeof op, "lseek(%s)", ls[i].n); P(op, "%lld|%ld", (long long)(r2 < 0 ? -1 : r2), E(r2)); }
    lseek(fd, 0, SEEK_SET); off_t r2 = sc(SYS_lseek, fd, 0, SEEK_END, 0, 0, 0); P("svc-lseek(END 0)", "%lld", (long long)r2);
  }
  { char *b = mk(4116); struct iovec v[3] = {{b, 10}, {b + 10, 4096}, {b + 4106, 10}}; long k; char g[32];
    lseek(fd, 0, SEEK_SET); k = readv(fd, v, 3); ovr(b, 4116, g); P("readv(10,4096,10)", "%ld|%ld|%s%s", R(k), E(k), g, k > 4116 ? " MORE" : "");
    memset(b, 0xA5, 4116 + GUARD); k = preadv(fd, v, 3, 0); ovr(b, 4116, g); P("preadv0(10,4096,10)", "%ld|%ld|%s%s", R(k), E(k), g, k > 4116 ? " MORE" : ""); free(b); }
  { struct pollfd p = {fd, POLLIN | POLLOUT, 0}; int k = poll(&p, 1, 0); int nr = -2; int ir = ioctl(fd, FIONREAD, &nr);
    P("poll", "%d|revents=%x", k, p.revents); P("FIONREAD", "%d|%d|n=%d", ir, ir < 0 ? errno : 0, ir < 0 ? -2 : nr); P("F_GETFL", "%x", fcntl(fd, F_GETFL) & ~0x8000); }
  { void *m = mmap(0, 4096, PROT_READ, MAP_PRIVATE, fd, 0); P("mmap(4096,R,PRIV)", "%d|%d", m != MAP_FAILED, m == MAP_FAILED ? errno : 0); if (m != MAP_FAILED) munmap(m, 4096); }
  { int o = syscall(SYS_memfd_create, "ps", 0); long k; if (o >= 0) { lseek(fd, 0, SEEK_SET); k = sendfile(o, fd, NULL, 65536); P("sendfile(65536)", "%ld|%ld|out_size=%lld", R(k), E(k), (long long)lseek(o, 0, SEEK_END));
      lseek(fd, 0, SEEK_SET); lseek(o, 0, SEEK_SET); k = syscall(SYS_copy_file_range, fd, 0, o, 0, 65536, 0); P("copy_file_range(65536)", "%ld|%ld", R(k), E(k)); close(o); } }
  { int pp[2]; if (!pipe(pp)) { lseek(fd, 0, SEEK_SET); long k = splice(fd, NULL, pp[1], NULL, 65536, SPLICE_F_NONBLOCK); P("splice(65536)", "%ld|%ld", R(k), E(k)); close(pp[0]); close(pp[1]); } }
  { char l[64]; snprintf(l, sizeof l, "/proc/self/fd/%d", fd); int f2 = open(l, O_RDONLY); if (f2 < 0) P("reopen-fd", "-1|%d", errno);
    else { struct stat s2; fstat(f2, &s2); char *b = mk(4096); long k = read(f2, b, 4096); char g[32]; ovr(b, 4096, g); P("reopen-fd", "read=%ld|st_size=%lld|%s%s", R(k), (long long)s2.st_size, g, k > 4096 ? " MORE" : ""); free(b); close(f2); } }
  close(fd);
}
static void viaf(const char *path) {  /* stdio: fopen/fgets with 4096-byte caller buffer and guard, fread 4096 */
  FILE *f = fopen(path, "r"); if (!f) return; char *b = mk(4096), *line = mk(4096); char g[32];
  setvbuf(f, b, _IOFBF, 4096); long lines = 0, bytes = 0, mxl = 0; while (fgets(line, 4096, f)) { lines++; long l = strlen(line); bytes += l; if (l > mxl) mxl = l; }
  ovr(b, 4096, g); P("fgets-all", "lines>0=%d maxline<=4095=%d %s", lines > 0, mxl <= 4095, g); fclose(f);
  f = fopen(path, "r"); size_t k = fread(line, 1, 4096, f); ovr(line, 4096, g); P("fread(4096)", "%d|%s", k <= 4096, g); fclose(f); free(b); free(line); (void)bytes;
}
static const char *const files[] = {
  "/proc/self/maps", "/proc/self/smaps", "/proc/self/smaps_rollup", "/proc/self/mountinfo", "/proc/self/mounts", "/proc/self/status", "/proc/self/stat", "/proc/self/statm",
  "/proc/self/cgroup", "/proc/self/cmdline", "/proc/self/comm", "/proc/self/environ", "/proc/self/auxv", "/proc/self/limits", "/proc/self/io", "/proc/self/sched",
  "/proc/self/schedstat", "/proc/self/oom_score", "/proc/self/oom_score_adj", "/proc/self/numa_maps", "/proc/self/wchan", "/proc/self/syscall", "/proc/self/personality",
  "/proc/thread-self/maps", "/proc/thread-self/status", "/proc/thread-self/stat", "/proc/thread-self/comm", "/proc/cpuinfo", "/proc/meminfo", "/proc/version", "/proc/stat",
  "/proc/uptime", "/proc/loadavg", "/proc/vmstat", "/proc/filesystems", "/proc/cmdline", "/proc/devices", "/proc/misc", "/proc/interrupts", "/proc/net/dev", "/proc/net/tcp",
  "/proc/sys/kernel/random/boot_id", "/proc/sys/kernel/osrelease", "/proc/sys/kernel/hostname", "/proc/sys/kernel/pid_max", "/proc/sys/vm/overcommit_memory", "/proc/sys/vm/max_map_count",
  "/sys/devices/system/cpu/present", "/sys/devices/system/cpu/possible", "/sys/devices/system/cpu/online", "/sys/devices/system/cpu/kernel_max",
  "/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq", "/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq", "/sys/devices/system/cpu/cpu0/topology/core_id",
  "/sys/devices/system/cpu/cpu0/cpu_capacity", "/sys/kernel/mm/transparent_hugepage/enabled", "/sys/class/thermal/thermal_zone0/temp", "/sys/class/net/lo/address", 0};
static void *work(void *a) {
  char *res = a; (void)res; char p[96];
  for (int i = 0; files[i]; i++) {
    cur = files[i]; all(cur, O_RDONLY, ""); all(cur, O_RDONLY | O_NONBLOCK, "(NONBLOCK)"); all(cur, O_RDONLY | 0x10000, "(DIRECT)"); viaf(cur);
  }
  snprintf(p, sizeof p, "/proc/self/task/%ld/maps", (long)syscall(SYS_gettid)); cur = "/proc/self/task/<tid>/maps"; all(p, O_RDONLY, ""); viaf(p);
  snprintf(p, sizeof p, "/proc/self/task/%ld/status", (long)syscall(SYS_gettid)); cur = "/proc/self/task/<tid>/status"; all(p, O_RDONLY, "");
  snprintf(p, sizeof p, "/proc/%ld/maps", (long)getpid()); cur = "/proc/<pid>/maps"; all(p, O_RDONLY, "");
  snprintf(p, sizeof p, "/proc/%ld/status", (long)getpid()); cur = "/proc/<pid>/status"; all(p, O_RDONLY, "");
  return 0;
}
static const char *run(void) {
  out = calloc(1, 4u << 20); used = 0;
  pthread_t t; pthread_create(&t, 0, work, 0); pthread_join(t, 0);  /* the thread: ACE reads from a thread arena */
  return out;
}
#if defined(PROCSEM_MAIN)
int main(void) { fputs(run(), stdout); return 0; }
#else
#include <jni.h>
JNIEXPORT jstring JNICALL Java_hello_procsem_Main_run(JNIEnv *env, jclass c) { (void)c; return (*env)->NewStringUTF(env, run()); }
#endif
