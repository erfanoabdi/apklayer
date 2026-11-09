/* bionic's stdin/stdout/stderr are bionic-shaped FILEs that the stdio wrappers map to glibc's: gnulib (Termux's ln) reads their fields
 * as bionic lays them out (bionic_translation 0056). */
#include <assert.h>
#include <dlfcn.h>
#include <stdio.h>
int main(int argc, char **argv)
{
    assert(argc == 2);
    void *lib = dlopen(argv[1], RTLD_NOW);
    if (!lib) { fprintf(stderr, "%s\n", dlerror()); return 1; }
    FILE **out = dlsym(lib, "bionic_stdout"), **in = dlsym(lib, "bionic_stdin");
    int (*fileno_)(FILE *) = dlsym(lib, "bionic_fileno"), (*fflush_)(FILE *) = dlsym(lib, "bionic_fflush");
    int (*fprintf_)(FILE *, const char *, ...) = dlsym(lib, "bionic_fprintf");
    assert(out && in && fileno_ && fflush_ && fprintf_ && *out != stdout && *in != stdin);
    const char *f = (const char *)*in;
    void *const *ext = *(void *const *const *)(f + 88);
    assert(*(const int *)(f + 8) == 0 && *(const int *)(f + 20) == 0 && ext && !ext[0]);  /* gnulib's freadahead: _r 0, no ungetc buffer */
    assert(fileno_(*out) == 1 && fprintf_(*out, "PASS bionic's standard streams\n") > 0 && fflush_(*out) == 0);
    return 0;
}
