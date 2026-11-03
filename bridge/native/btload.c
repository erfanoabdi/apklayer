/* btload: loads a bionic library with bionic_translation the way apklayer.host.NativeLoader does, and prints the result.
 * Usage: btload LIBDIR NAME [--main ARGS...]      (--main: also calls the library's main(argc, argv) with NAME ARGS..., how
 * a Soong executable rebuilt as a shared library, e.g. libapklayer_servicemanager, is run; needs LD_LIBRARY_PATH to bionic_translation's build dir, XDG_DATA_DIRS to its cfg.d,
 * LD_PRELOAD=libgcc_s.so.1 plus the platform/stub libraries). Used for PLAN P7.0. */
#define _GNU_SOURCE
#include <link.h>
#include <dlfcn.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
extern void dl_parse_library_path(const char *path, char *delim);
extern void *bionic_dlsym(void *handle, const char *symbol);
extern void *bionic_dlopen(const char *filename, int flag);
extern char *bionic_dlerror(void);
extern struct r_debug *_r_debug_ptr;
int main(int argc, char **argv)
{
    if (argc < 3) return 2;
    _r_debug_ptr = &_r_debug;
    extern void apklayer_log_to_stderr(void);
    dl_parse_library_path(argv[1], ":");
    /* host libraries the Android code calls into (APKLAYER_HOST_LIBS, colon separated), global so the bionic linker finds their symbols */
    if (getenv("APKLAYER_HOST_LIBS")) {
        char *libs = strdup(getenv("APKLAYER_HOST_LIBS"));
        for (char *save, *l = strtok_r(libs, ":", &save); l; l = strtok_r(NULL, ":", &save))
            if (!dlopen(l, RTLD_NOW | RTLD_GLOBAL)) fprintf(stderr, "btload: %s\n", dlerror());
    }
    apklayer_log_to_stderr();
    if (getenv("APKLAYER_CRASH_TRACE")) { extern void apklayer_crash_trace_signals(void); apklayer_crash_trace_signals(); }
    void *h = bionic_dlopen(argv[2], 2 /* RTLD_NOW */);
    if (h) printf("LOADED %s\n", argv[2]); else printf("FAILED %s: %s\n", argv[2], bionic_dlerror());
    if (h) { extern void apklayer_install_path_layer(void); apklayer_install_path_layer(); } /* libc_bio is loaded now */
    if (!h || argc < 4 || strcmp(argv[3], "--main")) return h ? 0 : 1;
    int (*lib_main)(int, char **) = bionic_dlsym(h, "main");
    if (!lib_main) { printf("no main in %s\n", argv[2]); return 1; }
    argv[3] = argv[2]; /* argv[0] of main */
    return lib_main(argc - 3, argv + 3);
}
