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
#include <sys/stat.h>
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
