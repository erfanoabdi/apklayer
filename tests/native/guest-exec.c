#define _GNU_SOURCE
#include <assert.h>
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <limits.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/wait.h>
#include <unistd.h>

int main(int argc, char **argv)
{
    if (argc == 5 && !strcmp(argv[1], "--child")) {
        assert(getenv("HOME") && !strcmp(getenv("HOME"), argv[2]));
        assert(getenv("PATH") && !strcmp(getenv("PATH"), argv[3]));
        assert(getenv("CHILD_VALUE") && !strcmp(getenv("CHILD_VALUE"), argv[4]));
        assert(!getenv("ENV_HOST_ONLY"));
        return 0;
    }
    assert(argc == 2);
    void *libc = dlopen(argv[1], RTLD_NOW | RTLD_GLOBAL); assert(libc);
    int (*clear)(void) = dlsym(libc, "bionic_clearenv");
    int (*put)(char *) = dlsym(libc, "bionic_putenv");
    int (*set)(const char *, const char *, int) = dlsym(libc, "bionic_setenv");
    int (*v)(const char *, char *const []) = dlsym(libc, "bionic_execv");
    int (*vp)(const char *, char *const []) = dlsym(libc, "bionic_execvp");
    int (*vpe)(const char *, char *const [], char *const []) = dlsym(libc, "bionic_execvpe");
    int (*l)(const char *, const char *, ...) = dlsym(libc, "bionic_execl");
    int (*lp)(const char *, const char *, ...) = dlsym(libc, "bionic_execlp");
    int (*le)(const char *, const char *, ...) = dlsym(libc, "bionic_execle");
    int (*fv)(int, char *const [], char *const []) = dlsym(libc, "bionic_fexecve");
    assert(clear && put && set && v && vp && vpe && l && lp && le && fv);
    char root[] = "/tmp/apklayer-exec-XXXXXX", exe[PATH_MAX], program[PATH_MAX];
    assert(mkdtemp(root));
    ssize_t size = readlink("/proc/self/exe", exe, sizeof exe - 1); assert(size > 0); exe[size] = 0;
    snprintf(program, sizeof program, "%s/guest-program", root); assert(!symlink(exe, program));
    char home[] = "HOME=/guest-home", value[] = "CHILD_VALUE=guest";
    assert(!clear() && !put(home) && !put(value) && !set("PATH", root, 1)); /* Termux JNI sequence */
    assert(getenv("ENV_HOST_ONLY") && strcmp(getenv("PATH"), root));
    char *inherited[] = {"guest-program", "--child", "/guest-home", root, "guest", NULL};
    char *explicit_args[] = {"guest-program", "--child", "/explicit-home", "/unused-child-path", "explicit", NULL};
    char *explicit_env[] = {"HOME=/explicit-home", "PATH=/unused-child-path", "CHILD_VALUE=explicit", NULL};
    for (int mode = 0; mode < 8; mode++) {
        pid_t child = fork(); assert(child >= 0);
        if (!child) {
            switch (mode) {
            case 0: v(program, inherited); break;
            case 1: vp(program, inherited); break;
            case 2: vp("guest-program", inherited); break;
            case 3: vpe("guest-program", explicit_args, explicit_env); break;
            case 4: l(program, "guest-program", "--child", "/guest-home", root, "guest", (char *)NULL); break;
            case 5: lp("guest-program", "guest-program", "--child", "/guest-home", root, "guest", (char *)NULL); break;
            case 6: le(program, "guest-program", "--child", "/explicit-home", "/unused-child-path", "explicit", (char *)NULL, explicit_env); break;
            case 7: { int fd = open(program, O_RDONLY); assert(fd >= 0); fv(fd, explicit_args, explicit_env); break; }
            }
            perror("guest exec failed"); _exit(127);
        }
        int status; assert(waitpid(child, &status, 0) == child);
        if (!WIFEXITED(status) || WEXITSTATUS(status)) fprintf(stderr, "guest exec mode %d failed: %d\n", mode, status);
        assert(WIFEXITED(status) && WEXITSTATUS(status) == 0);
    }
    errno = 0; assert(vp("missing-guest-program", inherited) == -1 && errno == ENOENT);
    errno = 0; assert(vpe("", explicit_args, explicit_env) == -1 && errno == ENOENT);
    assert(!unlink(program) && !rmdir(root));
    puts("PASS native guest exec: Termux clear/put sequence, inherited env, explicit env, parent PATH, variadic forms and fd exec");
}
