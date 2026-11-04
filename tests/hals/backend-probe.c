/* backend-probe: a sensors or vibrator backend library on its own, without Android (a device check of a host backend, e.g. sensorfw and
 * hfd-service on an Ubuntu Touch phone): its probe(), then init and the sensors' values, or one vibration.
 *   backend-probe sensors <libapklayer-sensors-X.so> [reads] [option...]   lists the sensors, then reads them every 200 ms (default 5 times)
 *   backend-probe vibrator <libapklayer-vibrator-X.so> [ms] [option...]    probe, init, amplitude 1, on for ms (default 300), off after it */
#include <dlfcn.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include "../../bridge/hals/sensors/include/apklayer/sensors.h"

#define SYM(name) void *name = dlsym(lib, #name); if (!name) { fprintf(stderr, "no %s\n", #name); return 1; }

int main(int argc, char **argv)
{
    if (argc < 3 || (strcmp(argv[1], "sensors") && strcmp(argv[1], "vibrator"))) { fprintf(stderr, "usage: see the source\n"); return 2; }
    void *lib = dlopen(argv[2], RTLD_NOW);
    if (!lib) { fprintf(stderr, "%s\n", dlerror()); return 1; }
    int n = argc > 3 ? atoi(argv[3]) : 0, options = argc > 4 ? argc - 4 : 0;
    const char *const *opts = (const char *const *)argv + 4;
    if (!strcmp(argv[1], "sensors")) {
        SYM(apklayer_sensors_probe) SYM(apklayer_sensors_init) SYM(apklayer_sensors_list) SYM(apklayer_sensors_read)
        printf("probe %d\n", ((int (*)(void))apklayer_sensors_probe)());
        int error = ((int (*)(int, const char *const *))apklayer_sensors_init)(options, opts);
        if (error) { printf("init %d\n", error); return 1; }
        struct apklayer_sensor_info info[32];
        int count = ((int (*)(struct apklayer_sensor_info *, int))apklayer_sensors_list)(info, 32);
        for (int i = 0; i < count; i++)
            printf("sensor %d type %d %s (%s) max %.2f resolution %.4f min delay %d us%s\n", i, info[i].type, info[i].name, info[i].vendor,
                   info[i].max_range, info[i].resolution, info[i].min_delay_us, info[i].on_change ? " on-change" : "");
        for (int r = 0; r < (n ? n : 5); r++) {
            usleep(200000);
            for (int i = 0; i < count; i++) {
                float v[3] = {0};
                int k = ((int (*)(int, float *))apklayer_sensors_read)(i, v);
                printf("read %d type %d:", r, info[i].type);
                for (int j = 0; j < k; j++) printf(" %.3f", v[j]);
                printf("\n");
            }
        }
        return 0;
    }
    SYM(apklayer_vibrator_probe) SYM(apklayer_vibrator_init) SYM(apklayer_vibrator_on) SYM(apklayer_vibrator_off) SYM(apklayer_vibrator_amplitude)
    printf("probe %d\n", ((int (*)(void))apklayer_vibrator_probe)());
    int error = ((int (*)(int, const char *const *))apklayer_vibrator_init)(options, opts);
    if (error) { printf("init %d\n", error); return 1; }
    int ms = n ? n : 300;
    printf("amplitude(1.0) %d\n", ((int (*)(float))apklayer_vibrator_amplitude)(1.0f));
    printf("on(%d ms) %d\n", ms, ((int (*)(int))apklayer_vibrator_on)(ms));
    usleep(ms * 1000);
    printf("off %d\n", ((int (*)(void))apklayer_vibrator_off)());
    return 0;
}
