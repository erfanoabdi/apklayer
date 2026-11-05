/* gnss-backend-fixes LIB [SECONDS [OPTION...]]: a GNSS backend (libapklayer-gnss-<backend>.so) without the HAL, as the front drives it:
 * probe(), init(options), then next_fix() once a second for SECONDS (default 60), printing each fix. For a host whose location service
 * cannot be faked (a phone's: the Ubuntu Touch check in docs/HALIUM.md). Ends with `fixes N`; exit 0 when at least one came. */
#include "apklayer/gnss.h"

#include <dlfcn.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

int main(int argc, char **argv)
{
    if (argc < 2) { fprintf(stderr, "usage: %s LIB [SECONDS [OPTION...]]\n", argv[0]); return 2; }
    void *lib = dlopen(argv[1], RTLD_NOW);
    if (!lib) { fprintf(stderr, "%s\n", dlerror()); return 2; }
    int (*probe)(void) = (int (*)(void))dlsym(lib, "apklayer_gnss_probe");
    int (*init)(int, const char *const *) = (int (*)(int, const char *const *))dlsym(lib, "apklayer_gnss_init");
    int (*next_fix)(struct apklayer_gnss_fix *) = (int (*)(struct apklayer_gnss_fix *))dlsym(lib, "apklayer_gnss_next_fix");
    if (!probe || !init || !next_fix) { fprintf(stderr, "%s: not a GNSS backend\n", argv[1]); return 2; }
    int seconds = argc > 2 ? atoi(argv[2]) : 60, fixes = 0;
    printf("probe %d\n", probe());
    int error = init(argc > 3 ? argc - 3 : 0, (const char *const *)argv + 3);
    printf("init %d\n", error);
    fflush(stdout);
    for (int s = 0; !error && s < seconds; s++) {
        struct apklayer_gnss_fix fix;
        if (!next_fix(&fix)) {
            printf("fix %.7f %.7f alt %.1f accuracy %.1f speed %.2f bearing %.1f\n", fix.latitude, fix.longitude, fix.altitude, fix.accuracy, fix.speed, fix.bearing);
            fflush(stdout);
            if (++fixes >= 3) break;
        }
        sleep(1);  /* the front's reporting interval */
    }
    printf("fixes %d\n", fixes);
    return fixes ? 0 : 1;
}
