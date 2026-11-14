/* The preload's execvp/execvpe on a thread that has not exec'd before, in a process with an own-data mapping (an app JVM's host code:
 * jspawnhelper runs execvp for a ProcessBuilder without environment): target() allocates from the exec arena, which execvpe never
 * entered, so the program crashed at NULL (every host process the app JVM started: ip monitor, udevadm; the core dump took ~1 s). */
#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <sys/wait.h>
#include <unistd.h>

int main(void)
{
    pid_t pid = fork();
    if (pid == 0) { execvp("true", (char *[]){"true", NULL}); _exit(127); }
    int status = 0;
    if (pid < 0 || waitpid(pid, &status, 0) != pid || !WIFEXITED(status) || WEXITSTATUS(status)) {
        fprintf(stderr, "exec-vp: execvp(\"true\") status %d\n", status);
        return 1;
    }
    return 0;
}
