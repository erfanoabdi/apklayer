/* A native bridge's proxy pthread_create copies the guest's attr byte for byte, writes the copy's stack fields, creates the thread and
 * destroys the copy (native_bridge_support pthread_translation.cc); the guest then destroys its own attr. The copy's destroy must leave
 * the original's glibc attr alone (bionic_translation 0040), and the thread gets the copy's stack size. */
#define _GNU_SOURCE  /* pthread_getattr_np */
#include <assert.h>
#include <dlfcn.h>
#include <pthread.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
typedef struct { uint32_t flags; void *stack_base; size_t stack_size; size_t guard_size; int32_t sched_policy, sched_priority; char reserved[16]; } android_attr;
static int (*attr_init)(android_attr *), (*attr_destroy)(android_attr *), (*attr_setstacksize)(android_attr *, size_t);
static int (*attr_getstacksize)(const android_attr *, size_t *);
static int (*create)(pthread_t *, const android_attr *, void *(*)(void *), void *);
static void *stack_size_of_self(void *unused)
{
    (void)unused;
    pthread_attr_t attr;
    size_t size = 0;
    assert(pthread_getattr_np(pthread_self(), &attr) == 0);
    assert(pthread_attr_getstacksize(&attr, &size) == 0);
    pthread_attr_destroy(&attr);
    return (void *)size;
}
int main(int argc, char **argv)
{
    assert(argc == 2);
    void *lib = dlopen(argv[1], RTLD_NOW);
    if (!lib) { fprintf(stderr, "%s\n", dlerror()); return 1; }
#define LOAD(name, symbol) name = dlsym(lib, symbol); assert(name)
    LOAD(attr_init, "bionic_pthread_attr_init");
    LOAD(attr_destroy, "bionic_pthread_attr_destroy");
    LOAD(attr_setstacksize, "bionic_pthread_attr_setstacksize");
    LOAD(attr_getstacksize, "bionic_pthread_attr_getstacksize");
    LOAD(create, "bionic_pthread_create");
    for (int round = 0; round < 64; round++) {
        android_attr guest, copy;
        assert(attr_init(&guest) == 0);
        assert(attr_setstacksize(&guest, 256 * 1024) == 0);
        memcpy(&copy, &guest, sizeof copy);
        copy.stack_base = NULL;
        copy.stack_size = 4 * 1024 * 1024;  /* the translator's larger host stack */
        pthread_t thread;
        void *size = NULL;
        assert(create(&thread, &copy, stack_size_of_self, NULL) == 0);
        assert(pthread_join(thread, &size) == 0);
        assert((size_t)size >= 4 * 1024 * 1024);
        assert(attr_destroy(&copy) == 0);
        size_t guest_size = 0;
        assert(attr_getstacksize(&guest, &guest_size) == 0 && guest_size == 256 * 1024);  /* the original's glibc attr is still there */
        assert(attr_destroy(&guest) == 0);
    }
    puts("PASS a byte copy of an attr: its stack fields create the thread, its destroy leaves the original");
    dlclose(lib);
    return 0;
}
