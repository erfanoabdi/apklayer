/* Harness and entry points (see atomics.h). */
#include "atomics.h"
#include <time.h>
int NT = 4, PIN = 0; long IT = 20000; const char *ONLY; static int fails, ncpu = 1;
static char rep[1 << 16];

void say(const char *fmt, ...)
{
    char l[512]; va_list ap; va_start(ap, fmt); vsnprintf(l, sizeof l, fmt, ap); va_end(ap);
    size_t n = strlen(rep); if (n + strlen(l) < sizeof rep) strcpy(rep + n, l);
    fputs(l, stderr);
}
void result(const char *name, long bad, const char *fmt, ...)
{
    char d[300]; va_list ap; va_start(ap, fmt); vsnprintf(d, sizeof d, fmt, ap); va_end(ap);
    struct timespec ts; clock_gettime(CLOCK_MONOTONIC, &ts); long ms = ts.tv_sec * 1000 + ts.tv_nsec / 1000000;   /* ms since the previous result: the test's own time */
    static long last; long took = last ? ms - last : 0; last = ms;
    say("RES %-30s %s %s [%ldms]\n", name, bad ? "FAIL" : "PASS", d, took); if (bad) fails++;
}
int want(const char *name)
{   /* ONLY: comma separated substrings; a leading "-" excludes */
    if (!ONLY) return 1;
    char b[256]; strncpy(b, ONLY, sizeof b - 1); b[sizeof b - 1] = 0; int any = 0, hit = 0;
    for (char *t = strtok(b, ","); t; t = strtok(0, ",")) { if (*t == '-') { if (strstr(name, t + 1)) return 0; } else { any = 1; hit |= strstr(name, t) != 0; } }
    return !any || hit;
}
int spin_ok(void) { return PIN < 2 && (PIN == 0 || NT <= ncpu) && NT <= ncpu; }

static wfn curf; static pthread_barrier_t bar;
static void *tmain(void *a)
{
    long t = (long)a;
    if (PIN) { cpu_set_t s; CPU_ZERO(&s); CPU_SET(PIN == 1 ? t % ncpu : PIN == 2 ? t % 2 % ncpu : 0, &s); sched_setaffinity(0, sizeof s, &s); }
    pthread_barrier_wait(&bar); curf((int)t); return 0;
}
void run_threads(int n, wfn f)
{
    pthread_t th[64]; curf = f; pthread_barrier_init(&bar, 0, n);
    for (long t = 0; t < n; t++) pthread_create(&th[t], 0, tmain, (void *)t);
    for (int t = 0; t < n; t++) pthread_join(th[t], 0);
    pthread_barrier_destroy(&bar);
}

static const char *run_all(int nt, long it, int pin, const char *only)
{
    NT = nt > 64 ? 64 : nt < 2 ? 2 : nt; IT = it; PIN = pin; ONLY = only && *only ? only : 0; fails = 0; rep[0] = 0;
    ncpu = (int)sysconf(_SC_NPROCESSORS_ONLN); if (ncpu < 1) ncpu = 1;
    say("CONF threads=%d iter=%ld pin=%d cpus=%d only=%s\n", NT, IT, PIN, ncpu, ONLY ? ONLY : "-");
    rmw_all(); pair_all(); idiom_all(); monitor_all(); litmus_all();
    say("DONE fails=%d\n", fails);
    return rep;
}

#ifdef ATOMICS_MAIN
int main(int argc, char **argv)
{   /* atomics [threads [iter [pin [only]]]] */
    run_all(argc > 1 ? atoi(argv[1]) : 4, argc > 2 ? atol(argv[2]) : 20000, argc > 3 ? atoi(argv[3]) : 0, argc > 4 ? argv[4] : "");
    return fails != 0;
}
#else
#include <jni.h>
JNIEXPORT jstring JNICALL Java_hello_atomics_Main_run(JNIEnv *env, jclass c, jint nt, jlong it, jint pin, jstring only)
{
    (void)c; const char *o = (*env)->GetStringUTFChars(env, only, 0);
    const char *r = run_all(nt, it, pin, o); (*env)->ReleaseStringUTFChars(env, only, o);
    return (*env)->NewStringUTF(env, r);
}
#endif
#pragma clang attribute pop
