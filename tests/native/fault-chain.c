/* A bionic crash handler that chains to the previous handler (Unity's, Breakpad's) must end the process, as debuggerd's handler does on
 * Android: the previous handler is bionic_translation's default one (0046), never SIG_DFL, which made Unity's handler return into the
 * same fault forever ("CRASH: No handler for signal 11"). */
#include <assert.h>
#include <dlfcn.h>
#include <signal.h>
#include <stdio.h>
#include <string.h>
#include <sys/resource.h>
#include <sys/wait.h>
#include <unistd.h>
typedef struct { unsigned int sa_flags; void (*handler)(int, siginfo_t *, void *); unsigned long sa_mask; void (*restorer)(void); } android_sigaction;
static int (*bsigaction)(int, const android_sigaction *, android_sigaction *);
static android_sigaction previous;
static int unhandled;
/* defined as libjsig defines it: bionic_sigaction then installs app handlers (0042) as with the JVM */
void *JVM_get_signal_action(int sig) { (void)sig; return NULL; }
static void chain(int sig, siginfo_t *info, void *uc)
{
    if (previous.handler != (void *)SIG_DFL && previous.handler != (void *)SIG_IGN) {
        if (previous.sa_flags & SA_SIGINFO) previous.handler(sig, info, uc);
        else ((void (*)(int))previous.handler)(sig);
        return;
    }
    static const char msg[] = "No handler for the signal\n";
    write(2, msg, sizeof msg - 1);
    if (++unhandled > 3) _exit(3);  /* the loop of the bug */
}
static void child(int sig)
{
    setrlimit(RLIMIT_CORE, &(struct rlimit){0, 0});
    alarm(10);
    android_sigaction act = { .sa_flags = SA_SIGINFO, .handler = chain };
    assert(bsigaction(sig, &act, &previous) == 0);
    if (sig == SIGSEGV) *(volatile int *)0 = 1;
    else raise(sig);
    _exit(4);  /* the handler returned and the process went on */
}
int main(int argc, char **argv)
{
    assert(argc == 2);
    void *lib = dlopen(argv[1], RTLD_NOW);
    if (!lib) { fprintf(stderr, "%s\n", dlerror()); return 1; }
    bsigaction = dlsym(lib, "bionic_sigaction");
    assert(bsigaction);
    const int sigs[] = { SIGSEGV, SIGABRT };
    for (unsigned i = 0; i < sizeof sigs / sizeof *sigs; i++) {
        int err[2];
        assert(pipe(err) == 0);
        pid_t pid = fork();
        assert(pid >= 0);
        if (!pid) { dup2(err[1], 2); close(err[0]); child(sigs[i]); }
        close(err[1]);
        int status = 0;
        assert(waitpid(pid, &status, 0) == pid);
        char note[4096] = "";
        read(err[0], note, sizeof note - 1);
        close(err[0]);
        /* the default handler's note carries the frames: module and offset of the fault and of its callers (0080) */
        assert(strstr(note, "Fatal signal") && strstr(note, "#00 pc ") && strstr(note, "#01 pc ") && (sigs[i] != SIGSEGV || strstr(note, "fault-chain")));
        if (!WIFSIGNALED(status) || WTERMSIG(status) != sigs[i]) {
            fprintf(stderr, "signal %d: child status %#x, expected death by the signal\n", sigs[i], status);
            return 1;
        }
    }
    puts("ok");
    return 0;
}
