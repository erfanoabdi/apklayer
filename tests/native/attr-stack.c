/* HotSpot (FCL's game JRE in the app process) sizes a thread's stack from pthread_getattr_np, pthread_attr_getstack and
 * pthread_attr_getguardsize. getguardsize had no bionic wrapper, so glibc's read bionic's stack_size field as the guard and the stack
 * came out as size 0 (bionic_translation 0050). The bounds must hold this thread's sp and the guard must be glibc's. */
#define _GNU_SOURCE  /* pthread_getattr_np */
#include <assert.h>
#include <dlfcn.h>
#include <pthread.h>
#include <stdint.h>
#include <stdio.h>
typedef struct { uint32_t flags; void *stack_base; size_t stack_size; size_t guard_size; int32_t sched_policy, sched_priority; char reserved[16]; } android_attr;
static int (*getattr_np)(pthread_t, android_attr *), (*attr_destroy)(android_attr *), (*attr_init)(android_attr *);
static int (*attr_getstack)(const android_attr *, void **, size_t *), (*attr_getguardsize)(const android_attr *, size_t *);
static int (*attr_setguardsize)(android_attr *, size_t), (*attr_setstack)(android_attr *, void *, size_t);
static void *check_self(void *unused)
{
    (void)unused;
    android_attr attr;
    void *bottom = NULL;
    size_t size = 0, guard = 0, real_guard = 0;
    assert(getattr_np(pthread_self(), &attr) == 0);
    assert(attr_getstack(&attr, &bottom, &size) == 0);
    assert(attr_getguardsize(&attr, &guard) == 0);
    attr_destroy(&attr);
    pthread_attr_t glibc;
    assert(pthread_getattr_np(pthread_self(), &glibc) == 0);
    pthread_attr_getguardsize(&glibc, &real_guard);
    pthread_attr_destroy(&glibc);
    char here;
    assert((char *)bottom < &here && &here < (char *)bottom + size);
    assert(guard == real_guard && guard < size);
    return NULL;
}
int main(int argc, char **argv)
{
    assert(argc == 2);
    void *lib = dlopen(argv[1], RTLD_NOW);
    if (!lib) { fprintf(stderr, "%s\n", dlerror()); return 1; }
#define LOAD(name, symbol) name = dlsym(lib, symbol); assert(name)
    LOAD(getattr_np, "bionic_pthread_getattr_np");
    LOAD(attr_init, "bionic_pthread_attr_init");
    LOAD(attr_destroy, "bionic_pthread_attr_destroy");
    LOAD(attr_getstack, "bionic_pthread_attr_getstack");
    LOAD(attr_getguardsize, "bionic_pthread_attr_getguardsize");
    LOAD(attr_setguardsize, "bionic_pthread_attr_setguardsize");
    LOAD(attr_setstack, "bionic_pthread_attr_setstack");
    pthread_t thread;
    assert(pthread_create(&thread, NULL, check_self, NULL) == 0 && pthread_join(thread, NULL) == 0);
    android_attr attr;
    static char stack[256 * 1024] __attribute__((aligned(16)));
    void *bottom = NULL;
    size_t size = 0, guard = 0;
    assert(attr_init(&attr) == 0);
    assert(attr_setguardsize(&attr, 8192) == 0 && attr_getguardsize(&attr, &guard) == 0 && guard == 8192);
    assert(attr_setstack(&attr, stack, sizeof stack) == 0 && attr_getstack(&attr, &bottom, &size) == 0);
    assert(bottom == stack && size == sizeof stack && attr.stack_base == stack && attr.stack_size == sizeof stack);
    attr_destroy(&attr);
    puts("PASS a thread's stack bounds and guard size through the bionic attr functions");
    dlclose(lib);
    return 0;
}
