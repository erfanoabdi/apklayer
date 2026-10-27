/* The Android object stores a pointer to a host mutex. Every lock entry point
 * must unwrap it, including timed locking (SF dumps, HWUI resize). */
#include <assert.h>
#include <dlfcn.h>
#include <errno.h>
#include <pthread.h>
#include <stdint.h>
#include <stdio.h>
#include <time.h>

typedef struct { _Alignas(8) int32_t words[10]; } android_mutex;
static int (*lock_mutex)(android_mutex *);
static int (*unlock_mutex)(android_mutex *);
static int (*timed_lock)(android_mutex *, const struct timespec *);
static int (*destroy_mutex)(android_mutex *);
static android_mutex mutex;

static struct timespec deadline(void)
{
    struct timespec ts;
    assert(clock_gettime(CLOCK_REALTIME, &ts) == 0);
    ts.tv_nsec += 20000000;
    if (ts.tv_nsec >= 1000000000) { ts.tv_nsec -= 1000000000; ++ts.tv_sec; }
    return ts;
}
static void *contended(void *unused)
{
    (void)unused;
    struct timespec ts = deadline();
    assert(timed_lock(&mutex, &ts) == ETIMEDOUT);
    return NULL;
}
int main(int argc, char **argv)
{
    assert(argc == 2);
    void *lib = dlopen(argv[1], RTLD_NOW);
    if (!lib) { fprintf(stderr, "%s\n", dlerror()); return 1; }
    lock_mutex = dlsym(lib, "bionic_pthread_mutex_lock");
    unlock_mutex = dlsym(lib, "bionic_pthread_mutex_unlock");
    timed_lock = dlsym(lib, "bionic_pthread_mutex_timedlock");
    destroy_mutex = dlsym(lib, "bionic_pthread_mutex_destroy");
    assert(lock_mutex && unlock_mutex && timed_lock && destroy_mutex);
    struct timespec ts = deadline();
    assert(timed_lock(&mutex, &ts) == 0); /* also initializes a static mutex */
    assert(unlock_mutex(&mutex) == 0);
    assert(lock_mutex(&mutex) == 0);
    pthread_t other;
    assert(pthread_create(&other, NULL, contended, NULL) == 0);
    assert(pthread_join(other, NULL) == 0);
    assert(unlock_mutex(&mutex) == 0);
    ts = deadline();
    assert(timed_lock(&mutex, &ts) == 0);
    assert(unlock_mutex(&mutex) == 0);
    assert(destroy_mutex(&mutex) == 0);
    puts("PASS translated timed mutex: static init, contention timeout, reuse");
    dlclose(lib);
    return 0;
}
