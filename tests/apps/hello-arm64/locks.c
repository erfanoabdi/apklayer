/* Lock owner identity under translation (PGR's resource download: Mono's monitor threw "Object has not been locked by this thread" inside correct
 * lock blocks, CHANGELOG 2026-11-21). An il2cpp-style monitor (owner id set by compare-and-swap, the exit checks the owner is itself), once with
 * pthread_self() and once with gettid() as the id, taken by guest threads and by Java threads entering through short JNI calls, while a
 * signaller sends a GC-like suspend signal to every thread each millisecond (Boehm GC's stop-the-world; the handler does an atomic and checks its
 * own identity). Counted: unlocks by a non-owner, two threads inside at once, a thread's ids changing. Every count is 0 on real arm64. */
#include <errno.h>
#include <jni.h>
#include <pthread.h>
#include <sys/epoll.h>
#include <sys/ucontext.h>
#include <sys/socket.h>
#include <signal.h>
#include <stdatomic.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <time.h>
#include <unistd.h>

#define GUEST_THREADS 8
#define MAX_THREADS 32

typedef struct { _Atomic uint64_t owner; atomic_int inside; } monitor;
static monitor by_self, by_tid;
static atomic_long not_owner[2], both_inside[2], id_changed, handler_mismatch, handled, rounds;
static atomic_int stop;
static atomic_int nthreads;
static pthread_t threads[MAX_THREADS];
static uint64_t thread_self[MAX_THREADS];
static int thread_tid[MAX_THREADS];
static atomic_long handler_bump;
/* Boehm GC scans a thread between the stack pointer its suspend handler sees and the top pthread_getattr_np(pthread_self()) reports: each thread's
 * range as that call reports it, and how often a frame of the thread (its own locals, the handler's, the interrupted sp) lies outside it */
static uintptr_t stack_lo[MAX_THREADS], stack_hi[MAX_THREADS];
static atomic_long local_outside[2], handler_outside, sp_outside;  /* local_outside[0]: guest threads, [1]: Java threads entered by JNI */
static int is_java[MAX_THREADS];

static int in_stack(int slot, uintptr_t p) { return p >= stack_lo[slot] && p < stack_hi[slot]; }

/* Pointers held only in registers when the suspend signal comes: on arm64 the kernel saves the interrupted registers in the signal frame on the
 * thread's stack, so the GC's scan from the handler's frame up to the stack top sees them. The spinner keeps MAGIC in x19-x28 and nothing in memory. */
#define MAGIC 0x6a09e667f3bcc909ull
static int spinner_slot = -1;
static atomic_long regs_found, regs_missed;

static void take(monitor* m, uint64_t me, int which) {
    for (;;) {
        uint64_t expected = 0;
        if (atomic_compare_exchange_weak_explicit(&m->owner, &expected, me, memory_order_acquire, memory_order_relaxed)) break;
        sched_yield();
    }
    if (atomic_fetch_add(&m->inside, 1) != 0) atomic_fetch_add(&both_inside[which], 1);
    atomic_fetch_sub(&m->inside, 1);
    if (atomic_load_explicit(&m->owner, memory_order_relaxed) != me) atomic_fetch_add(&not_owner[which], 1);  /* the il2cpp exit check */
    atomic_store_explicit(&m->owner, 0, memory_order_release);
}

/* one thread's round: both monitors with the ids it sees now; its ids against the ones it registered */
static void work(int slot, int n) {
    for (int i = 0; i < n; i++) {
        uint64_t self = (uint64_t)pthread_self();
        int tid = gettid();
        if (slot >= 0 && (self != thread_self[slot] || tid != thread_tid[slot])) atomic_fetch_add(&id_changed, 1);
        volatile int here = 0;
        if (slot >= 0 && !in_stack(slot, (uintptr_t)&here)) atomic_fetch_add(&local_outside[is_java[slot]], 1);
        take(&by_self, self, 0);
        take(&by_tid, (uint64_t)tid, 1);
        atomic_fetch_add(&rounds, 1);
    }
}

static int register_me(void) {
    int slot = atomic_fetch_add(&nthreads, 1);
    if (slot >= MAX_THREADS) return -1;
    thread_self[slot] = (uint64_t)pthread_self();
    thread_tid[slot] = gettid();
    threads[slot] = pthread_self();
    pthread_attr_t a;
    void* base = NULL;
    size_t size = 0;
    if (pthread_getattr_np(pthread_self(), &a) == 0) { pthread_attr_getstack(&a, &base, &size); pthread_attr_destroy(&a); }
    stack_lo[slot] = (uintptr_t)base;
    stack_hi[slot] = (uintptr_t)base + size;
    return slot;
}

static void on_suspend(int sig, siginfo_t* info, void* context) {
    (void)sig; (void)info;
    volatile int here = 0;
    uintptr_t sp = (uintptr_t)((ucontext_t*)context)->uc_mcontext.sp;
    atomic_fetch_add(&handler_bump, 1);  /* an exclusive/atomic access inside the handler, as Boehm's sem_post */
    uint64_t self = (uint64_t)pthread_self();
    int tid = gettid(), n = atomic_load(&nthreads), known = 0;
    for (int i = 0; i < n && i < MAX_THREADS; i++)
        if (thread_tid[i] == tid) {
            known = 1;
            if (thread_self[i] != self) atomic_fetch_add(&handler_mismatch, 1);
            if (!in_stack(i, (uintptr_t)&here)) atomic_fetch_add(&handler_outside, 1);
            if (!in_stack(i, sp)) atomic_fetch_add(&sp_outside, 1);
            if (i == spinner_slot) {  /* Boehm's view: every word from this frame to the stack top */
                int seen = 0;
                for (uintptr_t* w = (uintptr_t*)((uintptr_t)&here & ~(uintptr_t)7); (uintptr_t)w < stack_hi[i]; w++)
                    if (*w == MAGIC) { seen = 1; break; }
                atomic_fetch_add(seen ? &regs_found : &regs_missed, 1);
            }
        }
    if (known) atomic_fetch_add(&handled, 1);
}

static void* guest_thread(void* arg) {
    (void)arg;
    int slot = register_me();
    while (!atomic_load(&stop)) work(slot, 64);
    return NULL;
}

/* Socket reads under the same signals (the IO threads of Mono's socket completions): a writer streams 32-bit sequence numbers into a socketpair,
 * the reader takes them with epoll_wait + recv of varying sizes and checks the stream, the return values and values live across each call. */
#define IO_PAIRS 4
static atomic_long seq_errors, ret_errors, live_errors, io_bytes;

static void* io_writer(void* arg) {
    int fd = *(int*)arg;
    uint32_t seq = 0, buf[256];
    while (!atomic_load(&stop)) {
        for (int i = 0; i < 256; i++) buf[i] = seq++;
        const char* p = (const char*)buf;
        size_t left = sizeof buf;
        while (left > 0 && !atomic_load(&stop)) {
            ssize_t w = send(fd, p, left, MSG_NOSIGNAL);
            if (w < 0) { if (errno == EINTR || errno == EAGAIN) continue; return NULL; }
            p += w; left -= (size_t)w;
        }
    }
    return NULL;
}

static void* io_reader(void* arg) {
    int fd = *(int*)arg;
    int slot = register_me();
    int ep = epoll_create1(EPOLL_CLOEXEC);
    struct epoll_event ev = {.events = EPOLLIN, .data.fd = fd};
    epoll_ctl(ep, EPOLL_CTL_ADD, fd, &ev);
    uint32_t expect = 0, carry = 0;
    int have = 0;  /* bytes of a partial number in carry */
    unsigned char buf[4099];
    uint64_t a = 0x9e3779b97f4a7c15ull ^ (uint64_t)slot;
    while (!atomic_load(&stop)) {
        uint64_t b = a * 3 + 1, c = a ^ 0x5555, d = a + (uint64_t)slot;  /* live across the calls below: callee-saved registers */
        struct epoll_event out;
        int n = epoll_wait(ep, &out, 1, 5);
        if (n < 0 && errno != EINTR) atomic_fetch_add(&ret_errors, 1);
        if (n > 1) atomic_fetch_add(&ret_errors, 1);
        size_t want = 1 + (size_t)((a >> 7) % (sizeof buf - 1));
        ssize_t r = recv(fd, buf, want, MSG_DONTWAIT);
        if (r < 0 && errno != EAGAIN && errno != EINTR) atomic_fetch_add(&ret_errors, 1);
        if (r > (ssize_t)want || r == 0) atomic_fetch_add(&ret_errors, 1);
        for (ssize_t i = 0; i < r; i++) {
            carry |= (uint32_t)buf[i] << (8 * have);
            if (++have == 4) { if (carry != expect) { atomic_fetch_add(&seq_errors, 1); expect = carry; } expect++; carry = 0; have = 0; }
        }
        if (r > 0) atomic_fetch_add(&io_bytes, r);
        if (b != a * 3 + 1 || c != (a ^ 0x5555) || d != a + (uint64_t)slot) atomic_fetch_add(&live_errors, 1);
        a = a * 6364136223846793005ull + 1442695040888963407ull;
    }
    close(ep);
    return NULL;
}

static void* spinner(void* arg) {
    (void)arg;
    int slot = register_me();
    spinner_slot = slot;
    while (!atomic_load(&stop)) {
        __asm__ volatile(
            "mov x19, %0\n mov x20, x19\n mov x21, x19\n mov x22, x19\n mov x23, x19\n"
            "mov x24, x19\n mov x25, x19\n mov x26, x19\n mov x27, x19\n mov x28, x19\n"
            "mov x9, #20000\n 1: subs x9, x9, #1\n b.ne 1b\n"
            "mov x19, xzr\n mov x20, xzr\n mov x21, xzr\n mov x22, xzr\n mov x23, xzr\n"
            "mov x24, xzr\n mov x25, xzr\n mov x26, xzr\n mov x27, xzr\n mov x28, xzr\n"
            : : "r"(MAGIC) : "x9", "x19", "x20", "x21", "x22", "x23", "x24", "x25", "x26", "x27", "x28", "cc");
    }
    return NULL;
}

/* Store-load ordering (Dekker): each side stores its flag, fences, loads the other's; both reading 0 is forbidden on arm64. Variant 0: plain
 * store + DMB ISH + plain load; variant 1: STLR + LDAR (seq_cst store and load). x86 reorders a later load before an earlier store unless fenced. */
#define DEKKER_ROUNDS 2000000
static _Atomic int dk_x, dk_y, dk_go[2], dk_done[2];
static _Atomic int dk_r[2];
static long dekker_both_zero[2];

static void dekker_side(int me, int variant) {
    _Atomic int* mine = me ? &dk_y : &dk_x;
    _Atomic int* other = me ? &dk_x : &dk_y;
    for (int round = 1; round <= DEKKER_ROUNDS; round++) {
        while (atomic_load_explicit(&dk_go[me], memory_order_acquire) != round) {}
        int r;
        if (variant == 0) {
            atomic_store_explicit(mine, 1, memory_order_relaxed);
            __asm__ volatile("dmb ish" ::: "memory");
            r = atomic_load_explicit(other, memory_order_relaxed);
        } else {
            atomic_store_explicit(mine, 1, memory_order_seq_cst);  /* stlr */
            r = atomic_load_explicit(other, memory_order_seq_cst);  /* ldar */
        }
        atomic_store_explicit(&dk_r[me], r, memory_order_relaxed);
        atomic_store_explicit(&dk_done[me], round, memory_order_release);
    }
}

static int dk_variant;
static void* dekker_thread(void* arg) { dekker_side((int)(intptr_t)arg, dk_variant); return NULL; }

static void dekker(int variant) {
    dk_variant = variant;
    atomic_store(&dk_go[0], 0); atomic_store(&dk_go[1], 0); atomic_store(&dk_done[0], 0); atomic_store(&dk_done[1], 0);
    pthread_t t[2];
    for (intptr_t i = 0; i < 2; i++) pthread_create(&t[i], NULL, dekker_thread, (void*)i);
    for (int round = 1; round <= DEKKER_ROUNDS; round++) {
        atomic_store_explicit(&dk_x, 0, memory_order_relaxed);
        atomic_store_explicit(&dk_y, 0, memory_order_relaxed);
        atomic_store_explicit(&dk_go[0], round, memory_order_release);
        atomic_store_explicit(&dk_go[1], round, memory_order_release);
        while (atomic_load_explicit(&dk_done[0], memory_order_acquire) != round || atomic_load_explicit(&dk_done[1], memory_order_acquire) != round) {}
        if (atomic_load_explicit(&dk_r[0], memory_order_relaxed) == 0 && atomic_load_explicit(&dk_r[1], memory_order_relaxed) == 0) dekker_both_zero[variant]++;
    }
    for (int i = 0; i < 2; i++) pthread_join(t[i], NULL);
}

static void* signaller(void* arg) {
    (void)arg;
    struct timespec ms = {0, 1000000};
    while (!atomic_load(&stop)) {
        int n = atomic_load(&nthreads);
        for (int i = 0; i < n && i < MAX_THREADS; i++) pthread_kill(threads[i], SIGPWR);
        nanosleep(&ms, NULL);
    }
    return NULL;
}

/* a Java thread's entry: registers once (slot < 0 asks for one), then a few rounds; returns its slot */
JNIEXPORT jint JNICALL Java_hello_arm64_Main_lockJava(JNIEnv* env, jclass cls, jint slot, jint n) {
    (void)env; (void)cls;
    if (slot < 0) { slot = register_me(); if (slot >= 0) is_java[slot] = 1; }
    work(slot, n);
    return slot;
}

JNIEXPORT jstring JNICALL Java_hello_arm64_Main_lockStress(JNIEnv* env, jclass cls, jint ms) {
    (void)cls;
    struct sigaction sa;
    memset(&sa, 0, sizeof sa);
    sa.sa_sigaction = on_suspend;
    sa.sa_flags = SA_RESTART | SA_SIGINFO;
    sigaction(SIGPWR, &sa, NULL);
    dekker(0);
    dekker(1);
    pthread_t g[GUEST_THREADS], s, rd[IO_PAIRS], wr[IO_PAIRS];
    static int pairs[IO_PAIRS][2];
    for (int i = 0; i < GUEST_THREADS; i++) pthread_create(&g[i], NULL, guest_thread, NULL);
    for (int i = 0; i < IO_PAIRS; i++) {
        socketpair(AF_UNIX, SOCK_STREAM, 0, pairs[i]);
        pthread_create(&rd[i], NULL, io_reader, &pairs[i][0]);
        pthread_create(&wr[i], NULL, io_writer, &pairs[i][1]);
    }
    pthread_t sp_thread;
    pthread_create(&sp_thread, NULL, spinner, NULL);
    pthread_create(&s, NULL, signaller, NULL);
    struct timespec d = {ms / 1000, (ms % 1000) * 1000000L};
    nanosleep(&d, NULL);
    atomic_store(&stop, 1);
    for (int i = 0; i < GUEST_THREADS; i++) pthread_join(g[i], NULL);
    for (int i = 0; i < IO_PAIRS; i++) { shutdown(pairs[i][0], SHUT_RDWR); pthread_join(rd[i], NULL); pthread_join(wr[i], NULL); close(pairs[i][0]); close(pairs[i][1]); }
    pthread_join(s, NULL);
    pthread_join(sp_thread, NULL);
    char out[1100];
    long bad = atomic_load(&not_owner[0]) + atomic_load(&not_owner[1]) + atomic_load(&both_inside[0]) + atomic_load(&both_inside[1]) +
               atomic_load(&id_changed) + atomic_load(&handler_mismatch) + atomic_load(&seq_errors) + atomic_load(&ret_errors) + atomic_load(&live_errors) +
               atomic_load(&local_outside[0]) + atomic_load(&local_outside[1]) + atomic_load(&handler_outside) + atomic_load(&sp_outside) + atomic_load(&regs_missed) + dekker_both_zero[0] + dekker_both_zero[1];
    snprintf(out, sizeof out,
             "%s threads=%d rounds=%ld not_owner self=%ld tid=%ld both_inside self=%ld tid=%ld id_changed=%ld handler_mismatch=%ld handled=%ld"
             " io_bytes=%ld seq_errors=%ld ret_errors=%ld live_errors=%ld stack: local_outside guest=%ld java=%ld handler_outside=%ld sp_outside=%ld regs found=%ld missed=%ld dekker both_zero dmb=%ld stlr_ldar=%ld of %d",
             bad == 0 ? "PASS" : "FAIL", atomic_load(&nthreads), atomic_load(&rounds), atomic_load(&not_owner[0]), atomic_load(&not_owner[1]),
             atomic_load(&both_inside[0]), atomic_load(&both_inside[1]), atomic_load(&id_changed), atomic_load(&handler_mismatch), atomic_load(&handled),
             atomic_load(&io_bytes), atomic_load(&seq_errors), atomic_load(&ret_errors), atomic_load(&live_errors),
             atomic_load(&local_outside[0]), atomic_load(&local_outside[1]), atomic_load(&handler_outside), atomic_load(&sp_outside), atomic_load(&regs_found), atomic_load(&regs_missed), dekker_both_zero[0], dekker_both_zero[1], DEKKER_ROUNDS);
    return (*env)->NewStringUTF(env, out);
}

JNIEXPORT jboolean JNICALL Java_hello_arm64_Main_lockStopped(JNIEnv* env, jclass cls) {
    (void)env; (void)cls;
    return atomic_load(&stop) != 0;
}
