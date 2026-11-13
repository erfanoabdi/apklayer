#define _GNU_SOURCE
#include <assert.h>
#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/wait.h>
#include <unistd.h>

extern int apklayer_pack_guest_environment(char *const[]);
extern char **apklayer_guest_exec_environment(void);

static void check(char *const values[], int close_stdin)
{
    pid_t pid = fork();
    assert(pid >= 0);
    if (!pid) {
        if (close_stdin) close(0);
        int fd = apklayer_pack_guest_environment(values);
        assert(fd >= 3 && !(fcntl(fd, F_GETFD) & FD_CLOEXEC));
        assert(write(fd, "x", 1) == -1 && errno == EPERM);
        char text[32];
        snprintf(text, sizeof text, "%d", fd);
        assert(!setenv("APKLAYER_GUEST_ENV_FD", text, 1));
        assert(!setenv("HOME", "/host/backend", 1));
        char **got = apklayer_guest_exec_environment();
        assert(got && got == apklayer_guest_exec_environment());
        size_t n = 0;
        while (values && values[n]) { assert(got[n] && !strcmp(values[n], got[n])); n++; }
        assert(!got[n]);
        assert(!strcmp(getenv("HOME"), "/host/backend"));
        assert(fcntl(fd, F_GETFD) == -1 && errno == EBADF);
        _exit(0);
    }
    int status;
    assert(waitpid(pid, &status, 0) == pid && WIFEXITED(status) && WEXITSTATUS(status) == 0);
}

int main(void)
{
    assert(!unsetenv("APKLAYER_GUEST_ENV_FD"));
    char *values[] = {"HOME=/guest/home", "EMPTY=", "APKLAYER_NATIVE=guest-defined", "DUP=1", "DUP=2", "NO_EQUALS", "UTF8=سلام", NULL};
    check(values, 0);
    check(values, 1);
    check(NULL, 0);
    pid_t pid = fork();
    assert(pid >= 0);
    if (!pid) {
        assert(!setenv("APKLAYER_GUEST_ENV_FD", "not-a-descriptor", 1));
        apklayer_guest_exec_environment();
        _exit(1);
    }
    int status;
    assert(waitpid(pid, &status, 0) == pid && WIFEXITED(status) && WEXITSTATUS(status) == 127);
    assert(apklayer_guest_exec_environment() == NULL);
    puts("PASS explicit guest environment transport");
}
