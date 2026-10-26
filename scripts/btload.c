/* btload: loads a bionic library with bionic_translation the way apklayer.host.NativeLoader does, and prints the result.
 * Usage: btload LIBDIR NAME      (needs LD_LIBRARY_PATH to bionic_translation's build dir, XDG_DATA_DIRS to its cfg.d,
 * LD_PRELOAD=libgcc_s.so.1 plus the platform/stub libraries). Used for PLAN P7.0. */
#define _GNU_SOURCE
#include <link.h>
#include <stdio.h>
extern void dl_parse_library_path(const char *path, char *delim);
extern void *bionic_dlopen(const char *filename, int flag);
extern char *bionic_dlerror(void);
extern struct r_debug *_r_debug_ptr;
int main(int argc, char **argv)
{
    if (argc < 3) return 2;
    _r_debug_ptr = &_r_debug;
    dl_parse_library_path(argv[1], ":");
    void *h = bionic_dlopen(argv[2], 2 /* RTLD_NOW */);
    if (h) printf("LOADED %s\n", argv[2]); else printf("FAILED %s: %s\n", argv[2], bionic_dlerror());
    return h ? 0 : 1;
}
