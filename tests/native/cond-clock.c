/* Android condition deadlines must ignore the condition's default clock. */
#include <assert.h>
#include <dlfcn.h>
#include <errno.h>
#include <poll.h>
#include <signal.h>
#include <stdio.h>
#include <stdint.h>
#include <sys/wait.h>
#include <time.h>
#include <unistd.h>

typedef union { uint64_t align; char bytes[64]; } Object;
static double seconds(struct timespec t) { return t.tv_sec + t.tv_nsec / 1e9; }
int main(int argc, char **argv) {
    assert(argc == 2);
    void *lib = dlopen(argv[1], RTLD_NOW | RTLD_LOCAL);
    assert(lib);
#define FN(name, args) int (*name) args = dlsym(lib, "bionic_pthread_" #name); assert(name)
    FN(condattr_init, (void *));
    FN(condattr_setclock, (void *, clockid_t));
    FN(cond_init, (void *, const void *));
    FN(mutex_init, (void *, const void *));
    FN(mutex_lock, (void *));
    FN(mutex_unlock, (void *));
    FN(cond_timedwait_relative_np, (void *, void *, const struct timespec *));
    FN(cond_timedwait_monotonic_np, (void *, void *, const struct timespec *));
    int failures = 0;
    for (int clock = 0; clock < 2; clock++) for (int relative = 0; relative < 2; relative++) {
        int pipefd[2]; assert(pipe(pipefd) == 0);
        pid_t child = fork(); assert(child >= 0);
        if (!child) {
            close(pipefd[0]);
            Object attr = {0}, cond = {0}, mutex = {0};
            assert(condattr_init(&attr) == 0);
            assert(condattr_setclock(&attr, clock ? CLOCK_MONOTONIC : CLOCK_REALTIME) == 0);
            assert(cond_init(&cond, &attr) == 0 && mutex_init(&mutex, NULL) == 0);
            assert(mutex_lock(&mutex) == 0);
            struct timespec before, after, deadline = {0, 20000000};
            clock_gettime(CLOCK_MONOTONIC, &before);
            if (!relative) {
                deadline = before; deadline.tv_nsec += 20000000;
                if (deadline.tv_nsec >= 1000000000) { deadline.tv_sec++; deadline.tv_nsec -= 1000000000; }
            }
            int rc = relative ? cond_timedwait_relative_np(&cond, &mutex, &deadline) :
                                cond_timedwait_monotonic_np(&cond, &mutex, &deadline);
            clock_gettime(CLOCK_MONOTONIC, &after);
            double elapsed = seconds(after) - seconds(before);
            char ok = rc == ETIMEDOUT && elapsed >= .01 && elapsed < .5 && mutex_unlock(&mutex) == 0;
            assert(write(pipefd[1], &ok, 1) == 1);
            _exit(ok ? 0 : 1);
        }
        close(pipefd[1]);
        struct pollfd event = {pipefd[0], POLLIN, 0};
        char ok = 0;
        if (poll(&event, 1, 1000) > 0 && read(pipefd[0], &ok, 1) != 1) ok = 0;
        if (!ok) { kill(child, SIGKILL); failures++; }
        int status; assert(waitpid(child, &status, 0) == child); close(pipefd[0]);
        printf("%s condition clock=%s wait=%s\n", ok ? "PASS" : "FAIL",
               clock ? "monotonic" : "realtime", relative ? "relative" : "absolute monotonic");
        fflush(stdout);
    }
    dlclose(lib);
    return failures ? 1 : 0;
}
