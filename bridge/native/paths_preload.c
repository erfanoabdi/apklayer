/* LD_PRELOAD part of the path layer (PLAN P7.1 step 0.3): some Android libraries (libbase, libcutils, libprocessgroup) reach glibc's
 * file functions by name, past bionic_translation's libc wrappers, so Android's fixed paths must be mapped at the glibc level too.
 * This library is first in symbol lookup for every library of the process (the JVM running the native runtime, or btload) and maps
 * the path arguments through apklayer_apply_path_overrides (paths.c: an overlay of $APKLAYER_ANDROID_ROOT, else the host's file).
 * The real functions are reached with dlsym(RTLD_NEXT). Only functions that take a path are wrapped. */
#define _GNU_SOURCE
#include <dirent.h>
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <stdarg.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/statfs.h>
#include <sys/statvfs.h>
#include <sys/time.h>
#include <unistd.h>

bool apklayer_apply_path_overrides(char **path);
/* the userspace binder's nodes (binder_route.c): opened through its daemon, -2 when the path is not one */
int apklayer_binder_route_open(const char *path, int flags);
int apklayer_binder_route_exists(const char *path);
/* the sandbox's file broker (broker.c): an fd for a refused open the user may make, else the caller's result */
int apklayer_broker_open(const char *path, int flags, int mode, int failed);
int apklayer_broker_openat(int dirfd, const char *path, int flags, int mode, int failed);
/* mkdir, unlink, rmdir, rename, access in the granted home: 0 in place of the caller's failed result when apklayerd did it */
int apklayer_broker_change(const char *op, int dirfd, const char *path, int dirfd2, const char *to, int x, int failed);
#define CHANGE(r, op, dirfd, p, x) r = apklayer_broker_change(op, dirfd, p, AT_FDCWD, NULL, x, r)
#ifndef AT_STATX_SYNC_TYPE
#define AT_STATX_SYNC_TYPE 0x6000
#endif
#define BROKER(r, p, flags, mode) r = apklayer_broker_open(p, flags, mode, r)
/* a stat the broker answers, from an O_PATH fd it opened (a file inside a directory the app's uid may not search) */
#define BROKER_STAT(r, p, nofollow, call) BROKER_STATAT(r, AT_FDCWD, p, nofollow, call)
#define BROKER_STATAT(r, dirfd, p, nofollow, call) do { if (r != 0 && errno == EACCES) { int fd_ = apklayer_broker_openat(dirfd, p, O_PATH | O_CLOEXEC | ((nofollow) ? O_NOFOLLOW : 0), 0, -1); \
    if (fd_ >= 0) { r = call; int e_ = errno; close(fd_); errno = e_; } } } while (0)
#define ROUTE(p, flags) do { int h_ = apklayer_binder_route_open(p, flags); if (h_ != -2) { DONE(m); return h_; } } while (0)  /* m: MAP's */

#define REAL(name) ({ static void *f_; if (!f_) f_ = dlsym(RTLD_NEXT, #name); f_; })
#define MAP(var, p) char *var = (char *)(p); bool var##_alloc = (p) && apklayer_apply_path_overrides(&var)
#define DONE(var) do { if (var##_alloc) free(var); } while (0)
#define AT(var, p) MAP(var, (p) && (p)[0] == '/' ? (p) : NULL); const char *var##_use = var ? var : (p)
#define MODE(flags, mode) do { mode = 0; if ((flags) & (O_CREAT | O_TMPFILE)) { va_list ap; va_start(ap, flags); mode = va_arg(ap, int); va_end(ap); } } while (0)

int open(const char *path, int flags, ...) { int mode; MODE(flags, mode); MAP(m, path); ROUTE(m, flags); int r = ((int (*)(const char *, int, ...))REAL(open))(m, flags, mode); BROKER(r, m, flags, mode); DONE(m); return r; }
int open64(const char *path, int flags, ...) { int mode; MODE(flags, mode); MAP(m, path); ROUTE(m, flags); int r = ((int (*)(const char *, int, ...))REAL(open64))(m, flags, mode); BROKER(r, m, flags, mode); DONE(m); return r; }
int __open_2(const char *path, int flags) { MAP(m, path); ROUTE(m, flags); int r = ((int (*)(const char *, int, ...))REAL(open))(m, flags, 0); BROKER(r, m, flags, 0); DONE(m); return r; }
int __open64_2(const char *path, int flags) { MAP(m, path); ROUTE(m, flags); int r = ((int (*)(const char *, int, ...))REAL(open64))(m, flags, 0); BROKER(r, m, flags, 0); DONE(m); return r; }
int openat(int dirfd, const char *path, int flags, ...) { int mode; MODE(flags, mode); MAP(m, path && path[0] == '/' ? path : NULL); const char *use = m ? m : path; ROUTE(use, flags); int r = ((int (*)(int, const char *, int, ...))REAL(openat))(dirfd, use, flags, mode); r = apklayer_broker_openat(dirfd, use, flags, mode, r); DONE(m); return r; }
int openat64(int dirfd, const char *path, int flags, ...) { int mode; MODE(flags, mode); MAP(m, path && path[0] == '/' ? path : NULL); const char *use = m ? m : path; ROUTE(use, flags); int r = ((int (*)(int, const char *, int, ...))REAL(openat64))(dirfd, use, flags, mode); r = apklayer_broker_openat(dirfd, use, flags, mode, r); DONE(m); return r; }
int __openat_2(int dirfd, const char *path, int flags) { return openat(dirfd, path, flags, 0); }
int __openat64_2(int dirfd, const char *path, int flags) { return openat64(dirfd, path, flags, 0); }
/* fopen's open is glibc's own (not the wrapper above): a refused one goes to the broker here, with the flags its mode means */
static FILE *broker_fopen(const char *path, const char *mode, FILE *r)
{
    if (r || errno != EACCES) return r;
    int plus = strchr(mode, '+') != NULL, flags = mode[0] == 'r' ? (plus ? O_RDWR : O_RDONLY)
        : (plus ? O_RDWR : O_WRONLY) | O_CREAT | (mode[0] == 'w' ? O_TRUNC : mode[0] == 'a' ? O_APPEND : 0);
    if (strchr(mode, 'e')) flags |= O_CLOEXEC;
    if (strchr(mode, 'x')) flags |= O_EXCL;
    int fd = apklayer_broker_open(path, flags, 0666, -1);
    if (fd < 0) return NULL;
    FILE *f = fdopen(fd, mode);
    if (!f) close(fd);
    return f;
}
FILE *fopen(const char *path, const char *mode) { MAP(m, path); FILE *r = ((FILE * (*)(const char *, const char *))REAL(fopen))(m, mode); r = broker_fopen(m, mode, r); DONE(m); return r; }
FILE *fopen64(const char *path, const char *mode) { MAP(m, path); FILE *r = ((FILE * (*)(const char *, const char *))REAL(fopen64))(m, mode); r = broker_fopen(m, mode, r); DONE(m); return r; }
int access(const char *path, int mode) { MAP(m, path); if (apklayer_binder_route_exists(m)) { DONE(m); return 0; } int r = ((int (*)(const char *, int))REAL(access))(m, mode); CHANGE(r, "access", AT_FDCWD, m, mode); DONE(m); return r; }
int stat(const char *path, struct stat *st) { MAP(m, path); int r = ((int (*)(const char *, struct stat *))REAL(stat))(m, st); BROKER_STAT(r, m, 0, fstat(fd_, st)); DONE(m); return r; }
int stat64(const char *path, struct stat64 *st) { MAP(m, path); int r = ((int (*)(const char *, struct stat64 *))REAL(stat64))(m, st); BROKER_STAT(r, m, 0, fstat64(fd_, st)); DONE(m); return r; }
int lstat(const char *path, struct stat *st) { MAP(m, path); int r = ((int (*)(const char *, struct stat *))REAL(lstat))(m, st); BROKER_STAT(r, m, 1, fstat(fd_, st)); DONE(m); return r; }
int lstat64(const char *path, struct stat64 *st) { MAP(m, path); int r = ((int (*)(const char *, struct stat64 *))REAL(lstat64))(m, st); BROKER_STAT(r, m, 1, fstat64(fd_, st)); DONE(m); return r; }
int fstatat(int dirfd, const char *path, struct stat *st, int flags) { MAP(m, path && path[0] == '/' ? path : NULL); const char *use = m ? m : path; int r = ((int (*)(int, const char *, struct stat *, int))REAL(fstatat))(dirfd, use, st, flags); BROKER_STATAT(r, dirfd, use, flags & AT_SYMLINK_NOFOLLOW, fstat(fd_, st)); DONE(m); return r; }
DIR *opendir(const char *path) { MAP(m, path); DIR *r = ((DIR * (*)(const char *))REAL(opendir))(m);
    if (!r) { int fd = apklayer_broker_open(m, O_RDONLY | O_DIRECTORY | O_CLOEXEC, 0, -1); if (fd >= 0 && !(r = fdopendir(fd))) close(fd); } DONE(m); return r; }
long apklayer_android_spelling(char *buf, long n);
/* /proc links (an fd, the executable) to a file the overlay served read as its Android path (paths.c) */
static ssize_t proc_link(const char *path, char *buf, ssize_t r) { return r > 0 && path && !strncmp(path, "/proc/", 6) ? apklayer_android_spelling(buf, r) : r; }
ssize_t readlink(const char *path, char *buf, size_t n) { MAP(m, path); ssize_t r = ((ssize_t (*)(const char *, char *, size_t))REAL(readlink))(m, buf, n); DONE(m); return proc_link(path, buf, r); }

/* What Java's file APIs (java.io, NIO) call besides the above, so that a mapped directory (/storage, paths.c) can be written: one path argument,
 * or an absolute one for the *at forms. */
#define WRAP(ret, name, params, types, ...) ret name params { MAP(m, path); ret r = ((ret (*) types)REAL(name))(__VA_ARGS__); DONE(m); return r; }
#define WRAPAT(ret, name, params, types, ...) ret name params { MAP(m, path && path[0] == '/' ? path : NULL); const char *use = m ? m : path; \
    ret r = ((ret (*) types)REAL(name))(__VA_ARGS__); DONE(m); return r; }
int mkdir(const char *path, mode_t mode) { MAP(m, path); int r = ((int (*)(const char *, mode_t))REAL(mkdir))(m, mode); CHANGE(r, "mkdir", AT_FDCWD, m, (int)mode); DONE(m); return r; }
int rmdir(const char *path) { MAP(m, path); int r = ((int (*)(const char *))REAL(rmdir))(m); CHANGE(r, "rmdir", AT_FDCWD, m, 0); DONE(m); return r; }
int unlink(const char *path) { MAP(m, path); int r = ((int (*)(const char *))REAL(unlink))(m); CHANGE(r, "unlink", AT_FDCWD, m, 0); DONE(m); return r; }
/* glibc's remove tries unlink then rmdir through its own calls: the broker likewise */
int remove(const char *path) { MAP(m, path); int r = ((int (*)(const char *))REAL(remove))(m);
    if (r != 0 && (errno == EACCES || errno == EPERM)) { int e = errno; CHANGE(r, "unlink", AT_FDCWD, m, 0); if (r != 0 && errno == EISDIR) { errno = e; CHANGE(r, "rmdir", AT_FDCWD, m, 0); } }
    DONE(m); return r; }
WRAP(int, chdir, (const char *path), (const char *), m)
WRAP(int, chmod, (const char *path, mode_t mode), (const char *, mode_t), m, mode)
WRAP(int, chown, (const char *path, uid_t u, gid_t g), (const char *, uid_t, gid_t), m, u, g)
WRAP(int, lchown, (const char *path, uid_t u, gid_t g), (const char *, uid_t, gid_t), m, u, g)
WRAP(int, truncate, (const char *path, off_t n), (const char *, off_t), m, n)
WRAP(int, truncate64, (const char *path, off64_t n), (const char *, off64_t), m, n)
WRAP(int, utimes, (const char *path, const struct timeval t[2]), (const char *, const struct timeval *), m, t)
WRAP(int, statvfs, (const char *path, struct statvfs *s), (const char *, struct statvfs *), m, s)
WRAP(int, statvfs64, (const char *path, struct statvfs64 *s), (const char *, struct statvfs64 *), m, s)
WRAP(int, statfs, (const char *path, struct statfs *s), (const char *, struct statfs *), m, s)
WRAP(int, statfs64, (const char *path, struct statfs64 *s), (const char *, struct statfs64 *), m, s)
WRAP(int, symlink, (const char *target, const char *path), (const char *, const char *), target, m)
int fstatat64(int dirfd, const char *path, struct stat64 *st, int flags) { MAP(m, path && path[0] == '/' ? path : NULL); const char *use = m ? m : path;
    int r = ((int (*)(int, const char *, struct stat64 *, int))REAL(fstatat64))(dirfd, use, st, flags); BROKER_STATAT(r, dirfd, use, flags & AT_SYMLINK_NOFOLLOW, fstat64(fd_, st)); DONE(m); return r; }
int statx(int dirfd, const char *path, int flags, unsigned int mask, struct statx *st) { MAP(m, path && path[0] == '/' ? path : NULL); const char *use = m ? m : path;
    int r = ((int (*)(int, const char *, int, unsigned int, struct statx *))REAL(statx))(dirfd, use, flags, mask, st);
    BROKER_STATAT(r, dirfd, use, flags & AT_SYMLINK_NOFOLLOW, ((int (*)(int, const char *, int, unsigned int, struct statx *))REAL(statx))(fd_, "", AT_EMPTY_PATH | (flags & AT_STATX_SYNC_TYPE), mask, st)); DONE(m); return r; }
/* glibc before 2.33 exports no stat functions: its headers make stat() a call of __xstat(ver, ...). Programs built against it reach these
 * (the Temurin JDK of the Sailfish package: java.io.File's exists/isDirectory call __xstat64, so Termux's /data/data/com.termux/files was
 * the host's). On a newer glibc they are compat symbols dlsym does not return (an old program on it binds to these): the kernel's
 * newfstatat, whose struct stat is glibc's on both 64-bit CPUs. */
#include <sys/syscall.h>
int __xstat(int ver, const char *path, struct stat *st);
int __xstat64(int ver, const char *path, struct stat64 *st);
int __lxstat(int ver, const char *path, struct stat *st);
int __lxstat64(int ver, const char *path, struct stat64 *st);
int __fxstatat(int ver, int dirfd, const char *path, struct stat *st, int flags);
int __fxstatat64(int ver, int dirfd, const char *path, struct stat64 *st, int flags);
static int old_stat(void *real, bool at, int ver, int dirfd, const char *path, void *st, int flags)
{
    MAP(m, path && path[0] == '/' ? path : NULL); const char *use = m ? m : path;
    int r = !real ? (int)((long (*)(long, ...))REAL(syscall))(SYS_newfstatat, (long)dirfd, (long)use, (long)st, (long)flags)
        : at ? ((int (*)(int, int, const char *, void *, int))real)(ver, dirfd, use, st, flags) : ((int (*)(int, const char *, void *))real)(ver, use, st);
    BROKER_STATAT(r, dirfd, use, flags & AT_SYMLINK_NOFOLLOW, (int)((long (*)(long, ...))REAL(syscall))(SYS_newfstatat, (long)fd_, (long)"", (long)st, (long)AT_EMPTY_PATH));
    DONE(m); return r;
}
int __xstat(int ver, const char *path, struct stat *st) { return old_stat(REAL(__xstat), false, ver, AT_FDCWD, path, st, 0); }
int __xstat64(int ver, const char *path, struct stat64 *st) { return old_stat(REAL(__xstat64), false, ver, AT_FDCWD, path, st, 0); }
int __lxstat(int ver, const char *path, struct stat *st) { return old_stat(REAL(__lxstat), false, ver, AT_FDCWD, path, st, AT_SYMLINK_NOFOLLOW); }
int __lxstat64(int ver, const char *path, struct stat64 *st) { return old_stat(REAL(__lxstat64), false, ver, AT_FDCWD, path, st, AT_SYMLINK_NOFOLLOW); }
int __fxstatat(int ver, int dirfd, const char *path, struct stat *st, int flags) { return old_stat(REAL(__fxstatat), true, ver, dirfd, path, st, flags); }
int __fxstatat64(int ver, int dirfd, const char *path, struct stat64 *st, int flags) { return old_stat(REAL(__fxstatat64), true, ver, dirfd, path, st, flags); }
int faccessat(int dirfd, const char *path, int mode, int flags) { AT(m, path); int r = ((int (*)(int, const char *, int, int))REAL(faccessat))(dirfd, m_use, mode, flags);
    CHANGE(r, "access", dirfd, m_use, mode); DONE(m); return r; }
int mkdirat(int dirfd, const char *path, mode_t mode) { AT(m, path); int r = ((int (*)(int, const char *, mode_t))REAL(mkdirat))(dirfd, m_use, mode);
    CHANGE(r, "mkdir", dirfd, m_use, (int)mode); DONE(m); return r; }
int unlinkat(int dirfd, const char *path, int flags) { AT(m, path); int r = ((int (*)(int, const char *, int))REAL(unlinkat))(dirfd, m_use, flags);
    CHANGE(r, (flags & AT_REMOVEDIR) ? "rmdir" : "unlink", dirfd, m_use, 0); DONE(m); return r; }
WRAPAT(int, fchmodat, (int dirfd, const char *path, mode_t mode, int flags), (int, const char *, mode_t, int), dirfd, use, mode, flags)
WRAPAT(int, utimensat, (int dirfd, const char *path, const struct timespec t[2], int flags), (int, const char *, const struct timespec *, int), dirfd, use, t, flags)
int rename(const char *from, const char *to) { MAP(a, from); MAP(b, to); int r = ((int (*)(const char *, const char *))REAL(rename))(a, b);
    r = apklayer_broker_change("rename", AT_FDCWD, a, AT_FDCWD, b, 0, r); DONE(a); DONE(b); return r; }
int link(const char *from, const char *to) { MAP(a, from); MAP(b, to); int r = ((int (*)(const char *, const char *))REAL(link))(a, b); DONE(a); DONE(b); return r; }
/* A removable drive's /storage/<uuid> is a link in the storage directory to the desktop's mount (HostVolumes.Removable): a path that resolved
 * into that mount is spelled through the link again (/storage/<uuid>/..., or /mnt/media_rw/<uuid>/... as asked), as vold's mounts are.
 * Returns r, or a new string when resolved was NULL. */
static char *drive_spelling(const char *path, const char *storage, size_t n, char *r, char *resolved)
{
    const char *name = !strncmp(path, "/storage/", 9) ? path + 9 : !strncmp(path, "/mnt/media_rw/", 14) ? path + 14 : NULL;
    if (!name) return r;
    const char *slash = strchr(name, '/');
    char link[4096], target[4096];
    if (snprintf(link, sizeof link, "%s/%.*s", storage, slash ? (int)(slash - name) : (int)strlen(name), name) >= (int)sizeof link) return r;
    struct stat st;
    if (lstat(link, &st) != 0 || !S_ISLNK(st.st_mode) || !((char *(*)(const char *, char *))REAL(realpath))(link, target)) return r;
    size_t t = strlen(target);
    if (strncmp(r, target, t) || (r[t] && r[t] != '/')) return r;
    char spelled[4096];
    if (snprintf(spelled, sizeof spelled, "%.*s%s%s", (int)(name - 1 - path), path, link + n, r + t) >= (int)sizeof spelled) return r;
    if (resolved) { if (strlen(spelled) < 4096) strcpy(r, spelled); return r; }  /* the caller's buffer holds PATH_MAX */
    char *copy = strdup(spelled);
    if (!copy) return r;
    free(r);
    return copy;
}

/* A resolved path below the storage directory is spelled as Android's again (/storage/...): callers compare it with the volume paths. */
char *realpath(const char *path, char *resolved)
{
    MAP(m, path);
    char *r = ((char *(*)(const char *, char *))REAL(realpath))(m, resolved);
    const char *storage = getenv("APKLAYER_STORAGE");
    size_t n = storage ? strlen(storage) : 0;
    if (r && n >= 8 && !strncmp(r, storage, n) && (r[n] == 0 || r[n] == '/')) { memcpy(r, "/storage", 8); memmove(r + 8, r + n, strlen(r + n) + 1); }
    else if (r && n >= 8 && path) r = drive_spelling(path, storage, n, r, resolved);
    DONE(m);
    return r;
}
char *__realpath_chk(const char *path, char *resolved, size_t n) { (void)n; return realpath(path, resolved); }  /* the JDK's libjava and libnio are built with fortify */
WRAP(int, lutimes, (const char *path, const struct timeval t[2]), (const char *, const struct timeval *), m, t)
int renameat(int fd1, const char *from, int fd2, const char *to) {
    MAP(a, from && from[0] == '/' ? from : NULL); MAP(b, to && to[0] == '/' ? to : NULL);
    int r = ((int (*)(int, const char *, int, const char *))REAL(renameat))(fd1, a ? a : from, fd2, b ? b : to);
    r = apklayer_broker_change("rename", fd1, a ? a : from, fd2, b ? b : to, 0, r); DONE(a); DONE(b); return r;
}

/* The rest that bionic code and the JDK reach by name and glibc does not route through the functions above (bionic_translation hands an unwrapped
 * libc symbol to glibc's): the other *at forms, creat and mknod, extended attributes, inotify (FileObserver), and the glibc functions that open
 * through internal calls (mkstemp family, scandir, freopen). */
#include <sys/inotify.h>
#include <sys/xattr.h>
WRAP(int, creat, (const char *path, mode_t mode), (const char *, mode_t), m, mode)
WRAP(int, creat64, (const char *path, mode_t mode), (const char *, mode_t), m, mode)
WRAP(int, mkfifo, (const char *path, mode_t mode), (const char *, mode_t), m, mode)
WRAP(int, mknod, (const char *path, mode_t mode, dev_t dev), (const char *, mode_t, dev_t), m, mode, dev)
WRAPAT(int, mkfifoat, (int dirfd, const char *path, mode_t mode), (int, const char *, mode_t), dirfd, use, mode)
WRAPAT(int, mknodat, (int dirfd, const char *path, mode_t mode, dev_t dev), (int, const char *, mode_t, dev_t), dirfd, use, mode, dev)
WRAPAT(int, fchownat, (int dirfd, const char *path, uid_t u, gid_t g, int flags), (int, const char *, uid_t, gid_t, int), dirfd, use, u, g, flags)
ssize_t readlinkat(int dirfd, const char *path, char *buf, size_t n) { MAP(m, path && path[0] == '/' ? path : NULL); const char *use = m ? m : path;
    ssize_t r = ((ssize_t (*)(int, const char *, char *, size_t))REAL(readlinkat))(dirfd, use, buf, n); DONE(m); return proc_link(path, buf, r); }
/* the fortified spellings (Android code built with FORTIFY: Berberis's guest readlink): the buffer is at least n here */
ssize_t __readlinkat_chk(int dirfd, const char *path, char *buf, size_t n, size_t buflen) { if (n > buflen) abort(); return readlinkat(dirfd, path, buf, n); }
ssize_t __readlink_chk(const char *path, char *buf, size_t n, size_t buflen) { if (n > buflen) abort(); return readlink(path, buf, n); }
WRAPAT(int, symlinkat, (const char *target, int dirfd, const char *path), (const char *, int, const char *), target, dirfd, use)
WRAP(int, setxattr, (const char *path, const char *name, const void *v, size_t n, int f), (const char *, const char *, const void *, size_t, int), m, name, v, n, f)
WRAP(int, lsetxattr, (const char *path, const char *name, const void *v, size_t n, int f), (const char *, const char *, const void *, size_t, int), m, name, v, n, f)
WRAP(ssize_t, getxattr, (const char *path, const char *name, void *v, size_t n), (const char *, const char *, void *, size_t), m, name, v, n)
WRAP(ssize_t, lgetxattr, (const char *path, const char *name, void *v, size_t n), (const char *, const char *, void *, size_t), m, name, v, n)
WRAP(ssize_t, listxattr, (const char *path, char *list, size_t n), (const char *, char *, size_t), m, list, n)
WRAP(ssize_t, llistxattr, (const char *path, char *list, size_t n), (const char *, char *, size_t), m, list, n)
WRAP(int, removexattr, (const char *path, const char *name), (const char *, const char *), m, name)
WRAP(int, lremovexattr, (const char *path, const char *name), (const char *, const char *), m, name)
int inotify_add_watch(int fd, const char *path, uint32_t mask) { MAP(m, path); int r = ((int (*)(int, const char *, uint32_t))REAL(inotify_add_watch))(fd, m, mask); DONE(m); return r; }
int linkat(int fd1, const char *from, int fd2, const char *to, int flags) {
    AT(a, from); AT(b, to); int r = ((int (*)(int, const char *, int, const char *, int))REAL(linkat))(fd1, a_use, fd2, b_use, flags); DONE(a); DONE(b); return r;
}
int renameat2(int fd1, const char *from, int fd2, const char *to, unsigned int flags) {
    AT(a, from); AT(b, to); int r = ((int (*)(int, const char *, int, const char *, unsigned int))REAL(renameat2))(fd1, a_use, fd2, b_use, flags);
    if (!flags) r = apklayer_broker_change("rename", fd1, a_use, fd2, b_use, 0, r);  /* RENAME_NOREPLACE/EXCHANGE: not brokered */
    DONE(a); DONE(b); return r;
}
FILE *freopen(const char *path, const char *mode, FILE *f) { MAP(m, path); FILE *r = ((FILE * (*)(const char *, const char *, FILE *))REAL(freopen))(m, mode, f); DONE(m); return r; }
FILE *freopen64(const char *path, const char *mode, FILE *f) { MAP(m, path); FILE *r = ((FILE * (*)(const char *, const char *, FILE *))REAL(freopen64))(m, mode, f); DONE(m); return r; }
int scandir(const char *path, struct dirent ***list, int (*filter)(const struct dirent *), int (*compare)(const struct dirent **, const struct dirent **)) {
    MAP(m, path); int r = ((int (*)(const char *, struct dirent ***, void *, void *))REAL(scandir))(m, list, filter, compare); DONE(m); return r;
}
int scandir64(const char *path, struct dirent64 ***list, int (*filter)(const struct dirent64 *), int (*compare)(const struct dirent64 **, const struct dirent64 **)) {
    MAP(m, path); int r = ((int (*)(const char *, struct dirent64 ***, void *, void *))REAL(scandir64))(m, list, filter, compare); DONE(m); return r;
}
/* The mkstemp family fills the template's last characters in: the mapped copy differs only in its prefix, so its tail is the caller's name. */
#define TEMPLATE(ret, name, params, types, tail, ...) ret name params { \
    MAP(m, path); ret r = ((ret (*) types)REAL(name))(__VA_ARGS__); \
    if (m_alloc) { size_t n_ = (tail), lp_ = strlen(path), lm_ = strlen(m); if (n_ <= lp_ && n_ <= lm_) memcpy(path + lp_ - n_, m + lm_ - n_, n_); free(m); } \
    return r; }
TEMPLATE(int, mkstemp, (char *path), (char *), 6, m)
TEMPLATE(int, mkstemp64, (char *path), (char *), 6, m)
TEMPLATE(int, mkostemp, (char *path, int flags), (char *, int), 6, m, flags)
TEMPLATE(int, mkostemp64, (char *path, int flags), (char *, int), 6, m, flags)
TEMPLATE(int, mkstemps, (char *path, int suffix), (char *, int), 6 + (size_t)suffix, m, suffix)
TEMPLATE(int, mkostemps, (char *path, int suffix, int flags), (char *, int, int), 6 + (size_t)suffix, m, suffix, flags)
TEMPLATE(int, mkstemps64, (char *path, int suffix), (char *, int), 6 + (size_t)suffix, m, suffix)
TEMPLATE(int, mkostemps64, (char *path, int suffix, int flags), (char *, int, int), 6 + (size_t)suffix, m, suffix, flags)
char *mkdtemp(char *path) {
    MAP(m, path); char *r = ((char *(*)(char *))REAL(mkdtemp))(m);
    if (m_alloc) { size_t lp = strlen(path), lm = strlen(m); if (lp >= 6 && lm >= 6) memcpy(path + lp - 6, m + lm - 6, 6); free(m); }
    return r ? path : NULL;
}

/* Raw syscall() with a path: a native bridge's kernel emulation (Berberis) runs a guest's stat, access, readlink ... as the host syscall, so a
 * translated library's Android paths (its linker's /system/etc/ld.config.arm64.txt) reach the path layer only here. The *at forms with an
 * absolute path; the argument stays the caller's for any other number. */
#include <sys/syscall.h>
#ifndef SYS_statx  /* kernel headers before 4.11 (Sailfish OS 5.0's are 3.18); the kernel may still have it */
#if defined(__aarch64__)
#define SYS_statx 291
#elif defined(__x86_64__)
#define SYS_statx 332
#endif
#endif
#if !defined(SYS_newfstatat) && defined(SYS_fstatat64)
#define SYS_newfstatat SYS_fstatat64  /* its 32-bit name (the 32-bit app process) */
#endif
long syscall(long nr, ...)
{
    va_list ap;
    va_start(ap, nr);
    long a[6];
    for (int i = 0; i < 6; i++) a[i] = va_arg(ap, long);
    va_end(ap);
    int at = nr == SYS_openat || nr == SYS_newfstatat || nr == SYS_faccessat || nr == SYS_readlinkat || nr == SYS_statx || nr == SYS_mkdirat ||
             nr == SYS_unlinkat || nr == SYS_fchmodat || nr == SYS_fchownat || nr == SYS_utimensat || nr == SYS_mknodat ||
             nr == SYS_renameat || nr == SYS_renameat2 || nr == SYS_linkat
#ifdef SYS_faccessat2
             || nr == SYS_faccessat2
#endif
             ;
    int two = nr == SYS_renameat || nr == SYS_renameat2 || nr == SYS_linkat;  /* (fd, path, fd, path, ...) */
    const char *path = at ? (const char *)a[1] : NULL, *path2 = two ? (const char *)a[3] : NULL;
    MAP(m, path && path[0] == '/' ? path : NULL);
    MAP(m2, path2 && path2[0] == '/' ? path2 : NULL);
    if (m) a[1] = (long)m;
    if (m2) a[3] = (long)m2;
    long r = ((long (*)(long, ...))REAL(syscall))(nr, a[0], a[1], a[2], a[3], a[4], a[5]);
    DONE(m);
    DONE(m2);
    return nr == SYS_readlinkat ? proc_link(path, (char *)a[2], r) : r;
}
