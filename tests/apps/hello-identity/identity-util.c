/* Helpers of identity.h. */
#include "identity.h"
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/uio.h>
#include <unistd.h>

void put(struct buf *b, const char *key, const char *fmt, ...)
{
    char value[16384];
    va_list ap;
    va_start(ap, fmt);
    vsnprintf(value, sizeof value, fmt, ap);
    va_end(ap);
    flat(value);
    size_t need = strlen(key) + strlen(value) + 3;
    if (b->len + need >= b->cap) {
        size_t cap = (b->cap + need) * 2;
        char *p = realloc(b->p, cap);
        if (!p) return;
        b->p = p; b->cap = cap;
    }
    b->len += (size_t)sprintf(b->p + b->len, "%s=%s\n", key, value);
}

jstring finish(JNIEnv *env, struct buf *b)
{
    jstring s = (*env)->NewStringUTF(env, b->p ? b->p : "");
    free(b->p);
    b->p = NULL; b->len = b->cap = 0;
    return s;
}

char *slurp(const char *path, size_t *size)
{
    int fd = open(path, O_RDONLY | O_CLOEXEC);
    if (fd < 0) return NULL;
    size_t cap = 65536, len = 0;
    char *p = malloc(cap + 1);
    for (;;) {
        if (len == cap) { cap *= 2; p = realloc(p, cap + 1); }
        ssize_t r = read(fd, p + len, cap - len);
        if (r < 0) { if (errno == EINTR) continue; int e = errno; free(p); close(fd); errno = e; return NULL; }
        if (r == 0) break;
        len += (size_t)r;
        if (len > (16u << 20)) break;
    }
    close(fd);
    p[len] = 0;
    if (size) *size = len;
    return p;
}

int peek(uintptr_t addr, void *out, size_t n)
{
    struct iovec local = {out, n}, remote = {(void *)addr, n};
    return process_vm_readv(getpid(), &local, 1, &remote, 1, 0) == (ssize_t)n ? 0 : -1;
}

const char *where(const void *addr, char *out, size_t n)
{
    Dl_info info;
    if (!addr) snprintf(out, n, "null");
    else if (!dladdr(addr, &info)) snprintf(out, n, "unresolved");
    else snprintf(out, n, "%s+0x%lx (%s)", info.dli_sname ? info.dli_sname : "?",
                  (unsigned long)((uintptr_t)addr - (uintptr_t)(info.dli_saddr ? info.dli_saddr : info.dli_fbase)),
                  info.dli_fname ? info.dli_fname : "?");
    return out;
}

void flat(char *s)
{
    for (; *s; s++) if (*s == '\n') *s = '|'; else if (*s == '\r' || *s == '\t') *s = ' ';
}
