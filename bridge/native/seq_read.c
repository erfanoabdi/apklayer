/* apklayer patch: the multi-record proc files the translated route serves from a memfd (maps, smaps, numa_maps, mounts, mountinfo; the translator's maps
 * memfd "[guest /proc/self/maps]") read like the kernel's seq_file: a read returns whole records, at most 4095 bytes (a record that does not fit in it
 * grows the buffer), whatever the count; fstat says size 0 and lseek(SEEK_END/DATA/HOLE) fails with EINVAL. A memfd returns the full count, cut in the
 * middle of a line, with a size: a reader that sizes its buffer for the page a real read never exceeds (ACE's malloc(4096) slots) overruns it.
 * (readv/preadv of one buffer too; of several buffers the kernel fills across fills). Reached only through the preload's syscall(): what the translator runs for the guest's read, pread64, readv, preadv, lseek, fstat, close.
 * shortcut: x86_64 hosts only (a native arm64 app's libc does not call syscall()); a chunk start that is not remembered is found at most 8 KB back. */
#define _GNU_SOURCE
#if defined(__x86_64__)
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdint.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <sys/uio.h>
#include <unistd.h>

#define PAGE 4096
#define WINDOW (3 * PAGE)
#define FDS 65536
static unsigned char kind[FDS];  /* 0 not seen, 1 other, 2 seq memfd */
static uint32_t next_chunk[FDS]; /* where the next fill starts (a record boundary) after a read that ended a chunk; 0: unknown */

static long raw(long nr, long a, long b, long c, long d, long e, long f)
{
    static long (*real)(long, ...);
    if (!real) real = dlsym(RTLD_NEXT, "syscall");
    return real(nr, a, b, c, d, e, f);
}

static int seq_fd(int fd)
{
    if (fd < 0 || fd >= FDS) return 0;
    if (!kind[fd]) {
        struct stat st;
        char link[48], name[96];
        kind[fd] = 1;
        if (raw(SYS_fstat, fd, (long)&st, 0, 0, 0, 0) == 0 && S_ISREG(st.st_mode) && st.st_nlink == 0) {
            snprintf(link, sizeof link, "/proc/self/fd/%d", fd);
            long n = raw(SYS_readlinkat, AT_FDCWD, (long)link, (long)name, sizeof name - 1, 0, 0);
            if (n > 0) {
                name[n] = 0;
                if (!strcmp(name, "/memfd:maps (deleted)") || !strcmp(name, "/memfd:mounts (deleted)") || !strcmp(name, "/memfd:[guest /proc/self/maps] (deleted)")) kind[fd] = 2;
            }
        }
    }
    return kind[fd] == 2;
}

/* a line of smaps that belongs to the VMA above it ("Size:   4 kB"): not a record of its own */
static int continuation(const char *line, size_t n)
{
    if (n < 3 || line[0] < 'A' || line[0] > 'Z') return 0;
    for (size_t i = 0; i < n && i < 24; i++) if (line[i] == ':') return 1;
    return 0;
}

/* length of the record at buf[at]: its line and the continuation lines after it; 0 when the window ends inside it */
static long record_len(const char *buf, long at, long got)
{
    long len = 0;
    do {
        const char *nl = memchr(buf + at + len, '\n', got - at - len);
        if (!nl) return 0;
        len = nl - buf - at + 1;
    } while (at + len < got && continuation(buf + at + len, got - at - len));
    return len;
}

/* End of the chunk that holds file offset pos: fills of 4095 bytes of whole records from a record boundary at or before pos. 0: no limit (EOF, odd data) */
static long chunk_end(int fd, long pos)
{
    char buf[WINDOW];
    long base = next_chunk[fd] && next_chunk[fd] <= pos && pos - next_chunk[fd] < 4 * PAGE ? next_chunk[fd] : pos < 2 * PAGE ? 0 : -1;
    if (base < 0) {  /* not remembered: back to a record start within 8 KB */
        long lo = pos - 2 * PAGE, got = raw(SYS_pread64, fd, (long)buf, 2 * PAGE, lo, 0, 0), at = 0;
        if (got <= 0) return 0;
        while (at < got && buf[at] != '\n') at++;  /* a line start after the first newline */
        if (++at >= got) return 0;
        for (; at < got && continuation(buf + at, got - at); ) { const char *nl = memchr(buf + at, '\n', got - at); if (!nl) return 0; at = nl - buf + 1; }
        base = lo + at;
    }
    for (int round = 0; round < 8; round++) {
        long got = raw(SYS_pread64, fd, (long)buf, WINDOW, base, 0, 0), size = PAGE, total = 0;
        if (got <= 0) return 0;
        for (long at = 0; at < got; ) {
            long len = record_len(buf, at, got);
            if (!len && got < WINDOW) len = got - at;  /* the last record has no newline */
            if (!len) return 0;
            while (len > size - 1 && !total) size *= 2;  /* a record larger than the buffer grows it */
            if (total + len > size - 1 || size > WINDOW) break;
            total += len; at += len;
        }
        if (!total) return 0;
        if (pos < base + total) return base + total;
        base += total;
    }
    return 0;
}

static long limit(long end, long pos, unsigned long want)
{
    return end > pos && (unsigned long)(end - pos) < want ? end - pos : (long)want;
}

/* a descriptor number the kernel hands out anew (openat in the path layer) */
void apklayer_seq_new_fd(long fd)
{
    if (fd >= 0 && fd < FDS) kind[fd] = 0, next_chunk[fd] = 0;
}

/* returns 1 and *r when the call is handled here */
int apklayer_seq_syscall(long nr, const long *a, long *r)
{
    int fd = (int)a[0];
    switch (nr) {
    case SYS_close:
        if (fd >= 0 && fd < FDS) kind[fd] = 0, next_chunk[fd] = 0;
        return 0;
    case SYS_read: case SYS_pread64: case SYS_readv: case SYS_preadv: {
        if (!seq_fd(fd)) return 0;
        int vec = nr == SYS_readv || nr == SYS_preadv, at = nr == SYS_pread64 || nr == SYS_preadv;
        long pos = at ? a[3] : raw(SYS_lseek, fd, 0, SEEK_CUR, 0, 0, 0);
        if (pos < 0 || (unsigned long)pos > UINT32_MAX) return 0;
        long end = chunk_end(fd, pos);
        struct iovec iov;
        unsigned long want = a[2];
        if (vec) {  /* the kernel fills a vector of several buffers across fills (measured on 6.8 and 7.0: readv(10,4096,10) returns 4116); one buffer is a plain read */
            if (a[2] != 1) return 0;
            iov = *(const struct iovec *)a[1];
            iov.iov_len = limit(end, pos, iov.iov_len);
        } else want = limit(end, pos, want);
        *r = raw(nr, a[0], vec ? (long)&iov : a[1], vec ? a[2] : (long)want, a[3], a[4], a[5]);
        if (*r > 0 && !at && pos + *r == end) next_chunk[fd] = (uint32_t)(pos + *r);
        return 1;
    }
    case SYS_lseek:
        if (a[2] != SEEK_SET && a[2] != SEEK_CUR && seq_fd(fd)) { errno = EINVAL; *r = -1; return 1; }  /* seq_lseek: SEEK_END, SEEK_DATA, SEEK_HOLE */
        if (seq_fd(fd) && (a[2] == SEEK_SET || a[2] == SEEK_CUR)) next_chunk[fd] = 0;
        return 0;
    case SYS_fstat:
        if (!seq_fd(fd)) return 0;
        *r = raw(nr, a[0], a[1], 0, 0, 0, 0);
        if (*r == 0) { struct stat *st = (struct stat *)a[1]; st->st_size = 0; st->st_blocks = 0; st->st_blksize = 1024; st->st_nlink = 1; st->st_mode = S_IFREG | 0444; }
        return 1;
    case SYS_dup: case SYS_dup2: case SYS_dup3: case SYS_fcntl:
        if (nr == SYS_fcntl && a[1] != F_DUPFD && a[1] != F_DUPFD_CLOEXEC) return 0;
        *r = raw(nr, a[0], a[1], a[2], a[3], a[4], a[5]);
        { int n = (nr == SYS_dup2 || nr == SYS_dup3) ? (int)a[1] : (int)*r; if (n >= 0 && n < FDS) kind[n] = 0, next_chunk[n] = 0; }
        return 1;
    }
    return 0;
}
#else
void apklayer_seq_new_fd(long fd) { (void)fd; }
int apklayer_seq_syscall(long nr, const long *a, long *r) { (void)nr; (void)a; (void)r; return 0; }
#endif
