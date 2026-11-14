/* Names in /proc/self/maps as Android's bionic gives them (prctl PR_SET_VMA_ANON_NAME, Linux 5.17+; older kernels refuse and nothing changes):
 * a thread's stack and TLS is "[anon:stack_and_tls:<tid>]". The allocator regions "[anon:scudo:primary]" and "[anon:scudo:secondary]" exist as small
 * PROT_NONE reservations: the heap here is glibc's, whose memory carries no name. Only in an app process (APKLAYER_OWN_DATA). Part of the preload:
 * its pthread_create comes first in symbol lookup for the JVM's and the translated libc's threads. */
#define _GNU_SOURCE
#include <dlfcn.h>
#include <pthread.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/mman.h>
#include <sys/prctl.h>
#include <sys/syscall.h>
#include <unistd.h>

#ifndef PR_SET_VMA
#define PR_SET_VMA 0x53564d41
#define PR_SET_VMA_ANON_NAME 0
#endif

const char *apklayer_own_data_env(void);

static void name_vma(void *at, size_t size, const char *name)
{
    prctl(PR_SET_VMA, PR_SET_VMA_ANON_NAME, (unsigned long)at, size, (unsigned long)name);
}

struct start { void *(*fn)(void *); void *arg; };

static void *named_start(void *p)
{
    struct start s = *(struct start *)p;
    free(p);
    pthread_attr_t attr;
    void *low; size_t size; char name[32];
    if (!pthread_getattr_np(pthread_self(), &attr)) {
        if (!pthread_attr_getstack(&attr, &low, &size)) {
            snprintf(name, sizeof name, "stack_and_tls:%ld", syscall(SYS_gettid));
            name_vma(low, size, name);
        }
        pthread_attr_destroy(&attr);
    }
    return s.fn(s.arg);
}

static void reserve_allocator_regions(void)
{
    static const char *const names[] = {"scudo:primary", "scudo:secondary"};
    for (int i = 0; i < 2; i++) {
        void *p = mmap(NULL, 1 << 16, PROT_NONE, MAP_PRIVATE | MAP_ANONYMOUS | MAP_NORESERVE, -1, 0);
        if (p != MAP_FAILED) name_vma(p, 1 << 16, names[i]);
    }
}

int pthread_create(pthread_t *thread, const pthread_attr_t *attr, void *(*fn)(void *), void *arg)
{
    static int (*real)(pthread_t *, const pthread_attr_t *, void *(*)(void *), void *);
    static pthread_once_t once = PTHREAD_ONCE_INIT;
    if (!real) real = dlsym(RTLD_NEXT, "pthread_create");
    struct start *s = apklayer_own_data_env() ? malloc(sizeof *s) : NULL;
    if (!s) return real(thread, attr, fn, arg);
    pthread_once(&once, reserve_allocator_regions);
    s->fn = fn; s->arg = arg;
    int r = real(thread, attr, named_start, s);
    if (r) free(s);
    return r;
}
