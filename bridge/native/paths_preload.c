/* LD_PRELOAD part of the path layer (PLAN P7.1 step 0.3): some Android libraries (libbase, libcutils, libprocessgroup) reach glibc's
 * file functions by name, past bionic_translation's libc wrappers, so Android's fixed paths must be mapped at the glibc level too.
 * This library is first in symbol lookup for every library of the process (the JVM running the native runtime, or btload) and maps
 * the path arguments through apklayer_apply_path_overrides (paths.c: an overlay of $APKLAYER_ANDROID_ROOT, else the host's file).
 * The real functions are reached with dlsym(RTLD_NEXT). Only functions that take a path are wrapped. */
#define _GNU_SOURCE
#include <dirent.h>
#include <dlfcn.h>
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

#define REAL(name) ({ static void *f_; if (!f_) f_ = dlsym(RTLD_NEXT, #name); f_; })
#define MAP(var, p) char *var = (char *)(p); bool var##_alloc = (p) && apklayer_apply_path_overrides(&var)
#define DONE(var) do { if (var##_alloc) free(var); } while (0)
#define MODE(flags, mode) do { mode = 0; if ((flags) & (O_CREAT | O_TMPFILE)) { va_list ap; va_start(ap, flags); mode = va_arg(ap, int); va_end(ap); } } while (0)

int open(const char *path, int flags, ...) { int mode; MODE(flags, mode); MAP(m, path); int r = ((int (*)(const char *, int, ...))REAL(open))(m, flags, mode); DONE(m); return r; }
int open64(const char *path, int flags, ...) { int mode; MODE(flags, mode); MAP(m, path); int r = ((int (*)(const char *, int, ...))REAL(open64))(m, flags, mode); DONE(m); return r; }
int __open_2(const char *path, int flags) { MAP(m, path); int r = ((int (*)(const char *, int, ...))REAL(open))(m, flags, 0); DONE(m); return r; }
int __open64_2(const char *path, int flags) { MAP(m, path); int r = ((int (*)(const char *, int, ...))REAL(open64))(m, flags, 0); DONE(m); return r; }
int openat(int dirfd, const char *path, int flags, ...) { int mode; MODE(flags, mode); MAP(m, path && path[0] == '/' ? path : NULL); const char *use = m ? m : path; int r = ((int (*)(int, const char *, int, ...))REAL(openat))(dirfd, use, flags, mode); DONE(m); return r; }
int openat64(int dirfd, const char *path, int flags, ...) { int mode; MODE(flags, mode); MAP(m, path && path[0] == '/' ? path : NULL); const char *use = m ? m : path; int r = ((int (*)(int, const char *, int, ...))REAL(openat64))(dirfd, use, flags, mode); DONE(m); return r; }
int __openat_2(int dirfd, const char *path, int flags) { return openat(dirfd, path, flags, 0); }
int __openat64_2(int dirfd, const char *path, int flags) { return openat64(dirfd, path, flags, 0); }
FILE *fopen(const char *path, const char *mode) { MAP(m, path); FILE *r = ((FILE * (*)(const char *, const char *))REAL(fopen))(m, mode); DONE(m); return r; }
FILE *fopen64(const char *path, const char *mode) { MAP(m, path); FILE *r = ((FILE * (*)(const char *, const char *))REAL(fopen64))(m, mode); DONE(m); return r; }
int access(const char *path, int mode) { MAP(m, path); int r = ((int (*)(const char *, int))REAL(access))(m, mode); DONE(m); return r; }
int stat(const char *path, struct stat *st) { MAP(m, path); int r = ((int (*)(const char *, struct stat *))REAL(stat))(m, st); DONE(m); return r; }
int stat64(const char *path, struct stat64 *st) { MAP(m, path); int r = ((int (*)(const char *, struct stat64 *))REAL(stat64))(m, st); DONE(m); return r; }
int lstat(const char *path, struct stat *st) { MAP(m, path); int r = ((int (*)(const char *, struct stat *))REAL(lstat))(m, st); DONE(m); return r; }
int lstat64(const char *path, struct stat64 *st) { MAP(m, path); int r = ((int (*)(const char *, struct stat64 *))REAL(lstat64))(m, st); DONE(m); return r; }
int fstatat(int dirfd, const char *path, struct stat *st, int flags) { MAP(m, path && path[0] == '/' ? path : NULL); const char *use = m ? m : path; int r = ((int (*)(int, const char *, struct stat *, int))REAL(fstatat))(dirfd, use, st, flags); DONE(m); return r; }
DIR *opendir(const char *path) { MAP(m, path); DIR *r = ((DIR * (*)(const char *))REAL(opendir))(m); DONE(m); return r; }
ssize_t readlink(const char *path, char *buf, size_t n) { MAP(m, path); ssize_t r = ((ssize_t (*)(const char *, char *, size_t))REAL(readlink))(m, buf, n); DONE(m); return r; }

/* What Java's file APIs (java.io, NIO) call besides the above, so that a mapped directory (/storage, paths.c) can be written: one path argument,
 * or an absolute one for the *at forms. */
#define WRAP(ret, name, params, types, ...) ret name params { MAP(m, path); ret r = ((ret (*) types)REAL(name))(__VA_ARGS__); DONE(m); return r; }
#define WRAPAT(ret, name, params, types, ...) ret name params { MAP(m, path && path[0] == '/' ? path : NULL); const char *use = m ? m : path; \
    ret r = ((ret (*) types)REAL(name))(__VA_ARGS__); DONE(m); return r; }
WRAP(int, mkdir, (const char *path, mode_t mode), (const char *, mode_t), m, mode)
WRAP(int, rmdir, (const char *path), (const char *), m)
WRAP(int, unlink, (const char *path), (const char *), m)
WRAP(int, remove, (const char *path), (const char *), m)
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
WRAPAT(int, fstatat64, (int dirfd, const char *path, struct stat64 *st, int flags), (int, const char *, struct stat64 *, int), dirfd, use, st, flags)
WRAPAT(int, statx, (int dirfd, const char *path, int flags, unsigned int mask, struct statx *st), (int, const char *, int, unsigned int, struct statx *), dirfd, use, flags, mask, st)
WRAPAT(int, faccessat, (int dirfd, const char *path, int mode, int flags), (int, const char *, int, int), dirfd, use, mode, flags)
WRAPAT(int, mkdirat, (int dirfd, const char *path, mode_t mode), (int, const char *, mode_t), dirfd, use, mode)
WRAPAT(int, unlinkat, (int dirfd, const char *path, int flags), (int, const char *, int), dirfd, use, flags)
WRAPAT(int, fchmodat, (int dirfd, const char *path, mode_t mode, int flags), (int, const char *, mode_t, int), dirfd, use, mode, flags)
WRAPAT(int, utimensat, (int dirfd, const char *path, const struct timespec t[2], int flags), (int, const char *, const struct timespec *, int), dirfd, use, t, flags)
int rename(const char *from, const char *to) { MAP(a, from); MAP(b, to); int r = ((int (*)(const char *, const char *))REAL(rename))(a, b); DONE(a); DONE(b); return r; }
int link(const char *from, const char *to) { MAP(a, from); MAP(b, to); int r = ((int (*)(const char *, const char *))REAL(link))(a, b); DONE(a); DONE(b); return r; }
/* A resolved path below the storage directory is spelled as Android's again (/storage/...): callers compare it with the volume paths. */
char *realpath(const char *path, char *resolved)
{
    MAP(m, path);
    char *r = ((char *(*)(const char *, char *))REAL(realpath))(m, resolved);
    DONE(m);
    const char *storage = getenv("APKLAYER_STORAGE");
    size_t n = storage ? strlen(storage) : 0;
    if (r && n >= 8 && !strncmp(r, storage, n) && (r[n] == 0 || r[n] == '/')) { memcpy(r, "/storage", 8); memmove(r + 8, r + n, strlen(r + n) + 1); }
    return r;
}
char *__realpath_chk(const char *path, char *resolved, size_t n) { (void)n; return realpath(path, resolved); }  /* the JDK's libjava and libnio are built with fortify */
WRAP(int, lutimes, (const char *path, const struct timeval t[2]), (const char *, const struct timeval *), m, t)
int renameat(int fd1, const char *from, int fd2, const char *to) {
    MAP(a, from && from[0] == '/' ? from : NULL); MAP(b, to && to[0] == '/' ? to : NULL);
    int r = ((int (*)(int, const char *, int, const char *))REAL(renameat))(fd1, a ? a : from, fd2, b ? b : to); DONE(a); DONE(b); return r;
}
