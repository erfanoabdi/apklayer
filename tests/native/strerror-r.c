/* Bionic's strerror_r is POSIX's (int); glibc's symbol of that name is the GNU one (char *). OpenAL Soft (FCL's game) aborted when the
 * pointer came back as an "error" (bionic_translation 0053). */
#include <assert.h>
#include <dlfcn.h>
#include <errno.h>
#include <stdio.h>
#include <string.h>
int main(int argc, char **argv)
{
    assert(argc == 2);
    void *lib = dlopen(argv[1], RTLD_NOW);
    if (!lib) { fprintf(stderr, "%s\n", dlerror()); return 1; }
    int (*strerror_r_bionic)(int, char *, size_t) = (int (*)(int, char *, size_t))dlsym(lib, "bionic_strerror_r");
    assert(strerror_r_bionic);
    char buf[256] = {0};
    assert(strerror_r_bionic(ENOENT, buf, sizeof buf) == 0 && strstr(buf, "No such file"));
    char small[4];
    assert(strerror_r_bionic(ENOENT, small, sizeof small) == ERANGE);
    puts("PASS strerror_r returns POSIX's int");
    dlclose(lib);
    return 0;
}
