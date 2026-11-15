/* Thread lifetime stress (hello-arm64 -Dhello.threads=true; standalone: aarch64-linux-android26-clang -DTHREADS_MAIN -static threads.c): creating, joining,
 * detaching and exiting guest threads from other threads, in racing order, with signals/getattr/setname on threads that are exiting, TLS destructors that
 * allocate and free, and fork() in the middle. The result lines depend on nothing but the code (no timing): the translator must print the same as real arm64. */
#define _GNU_SOURCE
#include <errno.h>
#include <pthread.h>
#include <sched.h>
#include <signal.h>
#include <stdatomic.h>
#include <stdarg.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/syscall.h>
#include <sys/wait.h>
#include <time.h>
#include <unistd.h>

static char report[4096];
static void say(const char *fmt, ...)
{
    size_t n = strlen(report);
    va_list ap;
    va_start(ap, fmt);
    vsnprintf(report + n, sizeof report - n, fmt, ap);
    va_end(ap);
    fputs(report + n, stderr);  /* progress, also when the app hangs */
}

static uint32_t rng = 12345;  /* only the driving thread draws from it */
static uint32_t rnd(void) { rng = rng * 1664525u + 1013904223u; return rng >> 8; }
static int failures;
static int nosig;  /* thread_stress(2001): no pthread_kill(SIGUSR2) (the environment does not reach the app) */
static atomic_int progress, stage_obs, stage_main;  /* the watchdog reports them when nothing moves */
static void check(int ok, const char *what) { atomic_fetch_add(&progress, 1); if (!ok) { failures++; say("FAIL %s\n", what); } }

static void *quick(void *a) { return (void *)((uintptr_t)a * 7); }
static void *exiter(void *a) { pthread_exit((void *)((uintptr_t)a * 7)); return 0; }

/* phase 1: the creator and the joiner are different threads */
#define SLOTS 8
static pthread_t slot[SLOTS];
static atomic_int slot_full[SLOTS];
static atomic_uint_fast64_t joined_sum;
static int phase_n;
static void *joiner(void *a)
{
    (void)a;
    for (int i = 0; i < phase_n; i++) {
        int s = i % SLOTS;
        while (!atomic_load(&slot_full[s])) sched_yield();
        void *r = 0;
        if (pthread_join(slot[s], &r) != 0) r = (void *)-1;
        atomic_fetch_add(&joined_sum, (uintptr_t)r);
        atomic_fetch_add(&progress, 1);
        atomic_store(&slot_full[s], 0);
    }
    return 0;
}
static void *creator(void *a)
{
    (void)a;
    for (int i = 0; i < phase_n; i++) {
        int s = i % SLOTS;
        while (atomic_load(&slot_full[s])) sched_yield();
        if (pthread_create(&slot[s], NULL, quick, (void *)(uintptr_t)i) != 0) { failures++; return 0; }
        atomic_store(&slot_full[s], 1);
    }
    return 0;
}
static void phase_cross(int n)
{
    pthread_t c, j;
    phase_n = n;
    atomic_store(&joined_sum, 0);
    pthread_create(&j, NULL, joiner, NULL);
    pthread_create(&c, NULL, creator, NULL);
    pthread_join(c, NULL);
    pthread_join(j, NULL);
    uint64_t want = 0;
    for (int i = 0; i < n; i++) want += (uint64_t)i * 7;
    check(atomic_load(&joined_sum) == want, "cross-thread join sum");
    say("cross %llu\n", (unsigned long long)want);
}

/* phase 2: batches joined in a random order */
static void phase_order(int n)
{
    uint64_t sum = 0;
    for (int b = 0; b < n / 16; b++) {
        pthread_t t[16];
        int order[16];
        for (int i = 0; i < 16; i++) { order[i] = i; pthread_create(&t[i], NULL, quick, (void *)(uintptr_t)(b * 16 + i)); }
        for (int i = 15; i > 0; i--) { int k = rnd() % (i + 1), x = order[i]; order[i] = order[k]; order[k] = x; }
        for (int i = 0; i < 16; i++) {
            if ((rnd() & 3) == 0) sched_yield();
            void *r = 0;
            check(pthread_join(t[order[i]], &r) == 0 && (uintptr_t)r == (uintptr_t)(b * 16 + order[i]) * 7, "ordered join");
            sum += (uintptr_t)r;
        }
    }
    say("order %llu\n", (unsigned long long)sum);
}

/* phase 3: detached, joined, and detached after the thread has exited */
static atomic_int done_flag[1 << 16];
static atomic_uint_fast64_t detached_sum;
static void *flagger(void *a)
{
    uintptr_t i = (uintptr_t)a;
    atomic_fetch_add(&detached_sum, i * 7);
    atomic_store(&done_flag[i & 0xffff], 1);
    return (void *)(i * 7);
}
static void phase_detach(int n)
{
    uint64_t sum = 0;
    atomic_store(&detached_sum, 0);
    for (int i = 0; i < n; i++) {
        pthread_t t;
        atomic_store(&done_flag[i & 0xffff], 0);
        check(pthread_create(&t, NULL, flagger, (void *)(uintptr_t)i) == 0, "create");
        switch (rnd() & 3) {
        case 0: { void *r = 0; check(pthread_join(t, &r) == 0, "join"); sum += (uintptr_t)r; break; }
        case 1: check(pthread_detach(t) == 0, "detach"); sum += (uint64_t)i * 7; break;
        case 2: sched_yield(); check(pthread_detach(t) == 0, "detach after yield"); sum += (uint64_t)i * 7; break;
        default: while (!atomic_load(&done_flag[i & 0xffff])) sched_yield(); /* an exiting or exited thread */
                 check(pthread_detach(t) == 0, "detach of a finished thread"); sum += (uint64_t)i * 7; break;
        }
    }
    for (long w = 0; w < 20000000 && atomic_load(&detached_sum) != sum; w++) sched_yield();  /* every thread ran */
    check(atomic_load(&detached_sum) == sum, "detached threads ran");
    say("detach %llu\n", (unsigned long long)sum);
}

/* phase 4: pthread_exit; phase 5 is the NSPR way (the thread overwrites its handle with pthread_self(), the creator joins that) */
static void phase_exit(int n)
{
    uint64_t sum = 0;
    for (int i = 0; i < n; i++) {
        pthread_t t;
        void *r = 0;
        pthread_create(&t, NULL, exiter, (void *)(uintptr_t)i);
        check(pthread_join(t, &r) == 0 && (uintptr_t)r == (uintptr_t)i * 7, "pthread_exit value");
        sum += (uintptr_t)r;
    }
    say("exit %llu\n", (unsigned long long)sum);
}
struct self { pthread_t h; atomic_int published; };
static void *selfer(void *a)
{
    struct self *s = a;
    s->h = pthread_self();
    atomic_store(&s->published, 1);
    return 0;
}
static void phase_self(int n)
{
    for (int i = 0; i < n; i++) {
        struct self s = {0, 0};
        pthread_t t;
        pthread_create(&t, NULL, selfer, &s);
        while (!atomic_load(&s.published)) sched_yield();
        check(pthread_join(s.h, NULL) == 0, "join of pthread_self()");
    }
    say("self %d\n", n);
}

/* a process-directed signal while the caller blocks it: pending for the process, or taken by another thread (which one) */
static atomic_int blk_hits, blk_tid;
static void on_blk(int sig) { (void)sig; atomic_fetch_add(&blk_hits, 1); atomic_store(&blk_tid, (int)gettid()); }
static void phase_blocked_kill(void)
{
    struct sigaction sa;
    memset(&sa, 0, sizeof sa);
    sa.sa_handler = on_blk;
    sigaction(SIGUSR1, &sa, NULL);
    sigset_t set, old, pend;
    syscall(SYS_tgkill, getpid(), gettid(), SIGUSR1);
    say("tgkill hits %d\n", atomic_load(&blk_hits));
    kill(getpid(), SIGUSR1);
    say("kill hits %d tid==mine %d\n", atomic_load(&blk_hits), atomic_load(&blk_tid) == (int)gettid());
    sigemptyset(&set);
    sigaddset(&set, SIGUSR1);
    pthread_sigmask(SIG_BLOCK, &set, &old);
    kill(getpid(), SIGUSR1);
    sigpending(&pend);
    say("blocked-kill pending %d hits %d handler-tid==mine %d\n", sigismember(&pend, SIGUSR1), atomic_load(&blk_hits), atomic_load(&blk_tid) == (int)gettid());
    pthread_sigmask(SIG_SETMASK, &old, NULL);
}

/* phase 6: an observer calls kill/getattr/setname/getcpuclockid on the target while it exits (and after it exited, before the join) */
static atomic_uintptr_t target;
static atomic_int in_use, observing, handled;
static void on_usr2(int sig) { (void)sig; atomic_fetch_add(&handled, 1); }
static void *observer(void *a)
{
    (void)a;
    while (atomic_load(&observing)) {
        pthread_t t = (pthread_t)atomic_load(&target);
        if (!t) { sched_yield(); continue; }
        atomic_fetch_add(&in_use, 1);
        if ((pthread_t)atomic_load(&target) == t) {
            clockid_t cid;
            pthread_attr_t at;
            atomic_store(&stage_obs, 1); pthread_kill(t, 0);
            atomic_store(&stage_obs, 2); if (!nosig) pthread_kill(t, SIGUSR2);
            atomic_store(&stage_obs, 3); pthread_setname_np(t, "stress");
            atomic_store(&stage_obs, 4); pthread_getcpuclockid(t, &cid);
            atomic_store(&stage_obs, 5); if (pthread_getattr_np(t, &at) == 0) pthread_attr_destroy(&at);
            atomic_store(&stage_obs, 0);
        }
        atomic_fetch_sub(&in_use, 1);
    }
    return 0;
}
static void phase_observe(int n)
{
    struct sigaction sa;
    memset(&sa, 0, sizeof sa);
    sa.sa_handler = on_usr2;
    sigaction(SIGUSR2, &sa, NULL);
    pthread_t o;
    atomic_store(&observing, 1);
    pthread_create(&o, NULL, observer, NULL);
    for (int i = 0; i < n; i++) {
        pthread_t t;
        atomic_store(&stage_main, 1); pthread_create(&t, NULL, quick, (void *)(uintptr_t)i);
        atomic_store(&target, (uintptr_t)t);
        if (rnd() & 1) sched_yield();
        atomic_store(&stage_main, 2); if (!nosig) pthread_kill(t, SIGUSR2);  /* the creator too */
        pthread_attr_t at;
        atomic_store(&stage_main, 3); if (pthread_getattr_np(t, &at) == 0) pthread_attr_destroy(&at);
        atomic_store(&target, 0);
        atomic_store(&stage_main, 4); while (atomic_load(&in_use)) sched_yield();
        void *r = 0;
        atomic_store(&stage_main, 5);
        check(pthread_join(t, &r) == 0 && (uintptr_t)r == (uintptr_t)i * 7, "join after observation");
    }
    atomic_store(&observing, 0);
    pthread_join(o, NULL);
    say("observe %d\n", n);
}

/* phase 7: thread-local destructors that allocate and free, re-arming their key (three rounds) */
static pthread_key_t key;
static atomic_int dtor_runs;
static void dtor(void *p)
{
    int *c = p;
    void *x = malloc(64 + (size_t)*c * 100);
    memset(x, 1, 64);
    free(x);
    atomic_fetch_add(&dtor_runs, 1);
    if (++*c < 3) pthread_setspecific(key, c); else free(c);
}
static void *tls_thread(void *a)
{
    pthread_setspecific(key, calloc(1, sizeof(int)));
    free(malloc(1000));
    return a;
}
static void phase_tls(int n)
{
    pthread_key_create(&key, dtor);
    atomic_store(&dtor_runs, 0);
    for (int i = 0; i < n; i++) {
        pthread_t t;
        pthread_create(&t, NULL, tls_thread, 0);
        if (i & 1) pthread_detach(t); else pthread_join(t, NULL);
    }
    for (int w = 0; w < 100000 && atomic_load(&dtor_runs) != 3 * n; w++) sched_yield();  /* the detached ones */
    check(atomic_load(&dtor_runs) == 3 * n, "destructor runs");
    pthread_key_delete(key);
    say("tls %d\n", 3 * n);
}

/* phase 8: fork while two other threads sit parked between create/join rounds (a fork while one of them creates a thread hangs real bionic too);
 * the child makes threads too */
static atomic_int churn_run, churn_gate, churn_parked;
static void *churn(void *a)
{
    (void)a;
    while (atomic_load(&churn_run)) {
        if (atomic_load(&churn_gate)) {
            atomic_fetch_add(&churn_parked, 1);
            while (atomic_load(&churn_gate)) sched_yield();
            atomic_fetch_sub(&churn_parked, 1);
        }
        pthread_t t;
        if (pthread_create(&t, NULL, quick, 0) == 0) pthread_join(t, NULL);
    }
    return 0;
}
static void phase_fork(int n)
{
    pthread_t c[2];
    atomic_store(&churn_run, 1);
    for (int i = 0; i < 2; i++) pthread_create(&c[i], NULL, churn, NULL);
    for (int i = 0; i < n; i++) {
        for (int k = 0; k < 200; k++) sched_yield();  /* let them churn */
        atomic_store(&churn_gate, 1);
        while (atomic_load(&churn_parked) != 2) sched_yield();
        pid_t pid = fork();
        if (pid == 0) {
            int bad = 0;
            for (int k = 0; k < 20; k++) {
                pthread_t t;
                void *r = 0;
                if (pthread_create(&t, NULL, quick, (void *)(uintptr_t)k) != 0 || pthread_join(t, &r) != 0 || (uintptr_t)r != (uintptr_t)k * 7) bad = 1;
            }
            _exit(bad);
        }
        atomic_store(&churn_gate, 0);
        int st = 0;
        check(pid > 0 && waitpid(pid, &st, 0) == pid && WIFEXITED(st) && WEXITSTATUS(st) == 0, "forked child");
    }
    atomic_store(&churn_run, 0);
    atomic_store(&churn_gate, 0);
    for (int i = 0; i < 2; i++) pthread_join(c[i], NULL);
    say("fork %d\n", n);
}

/* every second: when no phase iteration finished for 10 s, say where the threads are (stages; /proc state and wchan of every task) */
static atomic_int watching;
static void *watchdog(void *a)
{
    (void)a;
    int last = -1, still = 0;
    while (atomic_load(&watching)) {
        struct timespec ts = {1, 0};
        nanosleep(&ts, NULL);
        int now = atomic_load(&progress);
        still = now == last ? still + 1 : 0;
        last = now;
        if (still == 10) {
            fprintf(stderr, "HANG progress %d stage_main %d stage_obs %d in_use %d\n", now, atomic_load(&stage_main), atomic_load(&stage_obs), atomic_load(&in_use));
            char cmd[96];
            snprintf(cmd, sizeof cmd, "for t in /proc/%d/task/*; do echo $t $(cut -d' ' -f3 $t/stat) $(cat $t/wchan); done 1>&2", (int)getpid());
            system(cmd);
        }
    }
    return 0;
}

const char *thread_stress(int n)
{
    report[0] = 0;
    nosig = n == 2001;
    failures = 0;
    rng = 12345;
    pthread_t wd;
    atomic_store(&watching, 1);
    pthread_create(&wd, NULL, watchdog, NULL);
    if (n == 17) phase_blocked_kill();
    phase_cross(n);
    phase_order(n);
    phase_detach(n);
    phase_exit(n);
    phase_self(n);
    phase_observe(n);
    phase_tls(n);
    phase_fork(n / 100 > 0 ? n / 100 : 1);
    atomic_store(&watching, 0);
    pthread_join(wd, NULL);
    say("%s failures %d\n", failures ? "FAIL" : "PASS", failures);
    return report;
}

#ifdef THREADS_MAIN
int main(int argc, char **argv)
{
    fputs(thread_stress(argc > 1 ? atoi(argv[1]) : 2000), stdout);
    return failures != 0;
}
#else
#include <jni.h>
JNIEXPORT jstring JNICALL Java_hello_arm64_Main_threadStress(JNIEnv *env, jclass c, jint n)
{
    (void)c;
    return (*env)->NewStringUTF(env, thread_stress(n));
}
#endif
