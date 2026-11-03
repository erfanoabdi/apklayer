/* Android __fread_chk ABI and real bounds checks through bionic_translation. */
#include <assert.h>
#include <dlfcn.h>
#include <signal.h>
#include <stdio.h>
#include <string.h>
#include <sys/wait.h>
#include <sys/resource.h>
#include <unistd.h>

int main(int argc, char **argv) {
    assert(argc == 2);
    const struct rlimit no_core = {0, 0};
    assert(setrlimit(RLIMIT_CORE, &no_core) == 0);
    void *library = dlopen(argv[1], RTLD_NOW | RTLD_LOCAL);
    assert(library);
    size_t (*read_checked)(void *, size_t, size_t, FILE *, size_t) =
        dlsym(library, "bionic___fread_chk");
    assert(read_checked);
    FILE *file = tmpfile();
    assert(file && fwrite("abcdef", 1, 6, file) == 6);
    rewind(file);
    char buffer[6] = {0};
    assert(read_checked(buffer, 2, 3, file, sizeof buffer) == 3);
    assert(!memcmp(buffer, "abcdef", sizeof buffer));
    assert(read_checked(buffer, 2, 3, file, sizeof buffer) == 0 && feof(file));
    pid_t child = fork();
    assert(child >= 0);
    if (!child) {
        read_checked(buffer, 1, sizeof buffer + 1, file, sizeof buffer);
        _exit(1);
    }
    int status = 0;
    assert(waitpid(child, &status, 0) == child);
    assert(WIFSIGNALED(status) && WTERMSIG(status) == SIGABRT);
    fclose(file); dlclose(library);
    puts("PASS Android fread fortify ABI: exact bound, EOF and real overflow abort");
    return 0;
}
