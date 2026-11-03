/* Android destroys condition state immediately, including process-exit globals
 * with sleeping workers. Retain the host object until the last waiter leaves. */
#include <assert.h>
#include <dlfcn.h>
#include <errno.h>
#include <pthread.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <time.h>
typedef struct { _Alignas(8) int32_t words[10]; } android_mutex;
typedef struct { _Alignas(8) int32_t words[12]; } android_cond;
static int (*lock_mutex)(android_mutex *), (*unlock_mutex)(android_mutex *), (*destroy_mutex)(android_mutex *);
static int (*init_cond)(android_cond *, const void *), (*destroy_cond)(android_cond *);
static int (*wait_cond)(android_cond *, android_mutex *);
static int (*timed_cond)(android_cond *, android_mutex *, const struct timespec *);
static int (*clock_cond)(android_cond *, android_mutex *, clockid_t, const struct timespec *);
static android_mutex mutex;
static android_cond cond;
static pthread_mutex_t ready_mutex = PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t ready_cond = PTHREAD_COND_INITIALIZER;
static int ready;
static void unlock(void *unused) { (void)unused; assert(unlock_mutex(&mutex) == 0); }
static void *worker(void *unused)
{
    (void)unused;
    assert(lock_mutex(&mutex) == 0);
    pthread_cleanup_push(unlock, NULL);
    pthread_mutex_lock(&ready_mutex);
    ready = 1;
    pthread_cond_signal(&ready_cond);
    pthread_mutex_unlock(&ready_mutex);
    for (;;) assert(wait_cond(&cond, &mutex) == 0);
    pthread_cleanup_pop(1);
    return NULL;
}
static void destroy_with_waiter(int explicit_init)
{
    memset(&cond, 0, sizeof cond);
    if (explicit_init) assert(init_cond(&cond, NULL) == 0);
    ready = 0;
    pthread_t thread;
    assert(pthread_create(&thread, NULL, worker, NULL) == 0);
    pthread_mutex_lock(&ready_mutex);
    while (!ready) pthread_cond_wait(&ready_cond, &ready_mutex);
    pthread_mutex_unlock(&ready_mutex);
    /* Acquiring this mutex proves the worker has entered cond_wait. */
    assert(lock_mutex(&mutex) == 0);
    assert(destroy_cond(&cond) == 0); /* must not wait for the sleeping worker */
    assert(unlock_mutex(&mutex) == 0);
    assert(pthread_cancel(thread) == 0);
    void *result = NULL;
    assert(pthread_join(thread, &result) == 0 && result == PTHREAD_CANCELED);
    printf("PASS translated cond destroy with waiter, %s init and cancellation cleanup\n", explicit_init ? "explicit" : "static");
}
int main(int argc, char **argv)
{
    assert(argc == 2);
    void *lib = dlopen(argv[1], RTLD_NOW);
    if (!lib) { fprintf(stderr, "%s\n", dlerror()); return 1; }
#define LOAD(name, symbol) name = dlsym(lib, symbol); assert(name)
    LOAD(lock_mutex, "bionic_pthread_mutex_lock");
    LOAD(unlock_mutex, "bionic_pthread_mutex_unlock");
    LOAD(destroy_mutex, "bionic_pthread_mutex_destroy");
    LOAD(init_cond, "bionic_pthread_cond_init");
    LOAD(destroy_cond, "bionic_pthread_cond_destroy");
    LOAD(wait_cond, "bionic_pthread_cond_wait");
    LOAD(timed_cond, "bionic_pthread_cond_timedwait");
    LOAD(clock_cond, "bionic_pthread_cond_clockwait");
    assert(destroy_cond(&cond) == 0); /* never initialized */
    assert(lock_mutex(&mutex) == 0);
    const struct timespec expired = {0};
    assert(timed_cond(&cond, &mutex, &expired) == ETIMEDOUT);
    assert(clock_cond(&cond, &mutex, CLOCK_MONOTONIC, &expired) == ETIMEDOUT);
    assert(unlock_mutex(&mutex) == 0);
    assert(destroy_cond(&cond) == 0);
    destroy_with_waiter(0);
    destroy_with_waiter(1);
    assert(destroy_mutex(&mutex) == 0);
    puts("PASS timed/clock waits and normal destruction");
    dlclose(lib);
    return 0;
}
