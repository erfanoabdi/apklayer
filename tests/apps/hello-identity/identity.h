/* Shared helpers of the identity probes: a growing "key=value\n" text buffer, whole-file reads, a safe self-memory read. */
#ifndef IDENTITY_H
#define IDENTITY_H
#define _GNU_SOURCE
#include <jni.h>
#include <stddef.h>
#include <stdint.h>
#include <sys/types.h>

struct buf { char *p; size_t len, cap; };
void put(struct buf *b, const char *key, const char *fmt, ...) __attribute__((format(printf, 3, 4)));
jstring finish(JNIEnv *env, struct buf *b);
/* The whole file (NUL-terminated, malloc'd; *size its length) or NULL with errno. */
char *slurp(const char *path, size_t *size);
/* Copies n bytes at addr without faulting (process_vm_readv on self): 0 or -1. */
int peek(uintptr_t addr, void *out, size_t n);
/* "name+0xoff (module)" of an address through dladdr. */
const char *where(const void *addr, char *out, size_t n);
/* A string with newlines and '|' escaped into a single value. */
void flat(char *s);
#endif
