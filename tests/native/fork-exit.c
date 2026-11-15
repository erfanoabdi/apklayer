/* native_loader.c: a forked child ends at exit() without the process's exit handlers (an app's createSubprocess: fork, failed exec, exit(127); libgallium's
 * handler joined a thread the child does not have and exit() hung). argv[1]: the JNI shim (its constructor registers the pthread_atfork hook). */
#define _GNU_SOURCE
#include <dlfcn.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/wait.h>
#include <unistd.h>

static void hang(void) { for (;;) pause(); }  /* the hung handler of a library loaded before the fork */

int main(int argc, char **argv)
{
    if (argc != 2 || !dlopen(argv[1], RTLD_LAZY)) { fprintf(stderr, "dlopen: %s\n", dlerror()); return 1; }
    atexit(hang);
    pid_t pid = fork();
    if (pid == 0) { execl("/nonexistent/apklayer-fork-exit", "x", (char *)NULL); exit(127); }
    alarm(1);  /* the parent must see the status within a second */
    int st = 0;
    if (waitpid(pid, &st, 0) != pid || !WIFEXITED(st) || WEXITSTATUS(st) != 127) { fprintf(stderr, "fork-exit: child status %d\n", st); kill(pid, SIGKILL); return 1; }
    puts("PASS a forked child's exit(127) ends at once");
    fflush(NULL);
    _exit(0);  /* the parent's own exit would run the hanging handler */
}
