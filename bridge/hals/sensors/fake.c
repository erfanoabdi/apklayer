/* The fake sensors: fixed values, set by the options (providers.toml): accel=x,y,z (default upright gravity), gyro=, magnetic= (x,y,z),
 * light=, pressure=, proximity= (one value). Deterministic, for tests and for apps that need a sensor to start. */
#include "apklayer/sensors.h"

#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static struct {
    const char *key;
    struct apklayer_sensor_info info;
    int count;
    float values[3];
} sensors[] = {
    {"accel", {APKLAYER_SENSOR_ACCELEROMETER, "apklayer fake accelerometer", "apklayer", 78.4f, 0.01f, 0.1f, 10000, 0}, 3, {0, 9.80665f, 0}},
    {"gyro", {APKLAYER_SENSOR_GYROSCOPE, "apklayer fake gyroscope", "apklayer", 34.9f, 0.001f, 0.1f, 10000, 0}, 3, {0, 0, 0}},
    {"magnetic", {APKLAYER_SENSOR_MAGNETIC_FIELD, "apklayer fake magnetometer", "apklayer", 1300.0f, 0.01f, 0.1f, 20000, 0}, 3, {0, 20.0f, -40.0f}},
    {"light", {APKLAYER_SENSOR_LIGHT, "apklayer fake light", "apklayer", 43000.0f, 1.0f, 0.1f, 200000, 1}, 1, {300.0f}},
    {"pressure", {APKLAYER_SENSOR_PRESSURE, "apklayer fake barometer", "apklayer", 1100.0f, 0.01f, 0.1f, 40000, 0}, 1, {1013.25f}},
    {"proximity", {APKLAYER_SENSOR_PROXIMITY, "apklayer fake proximity", "apklayer", 5.0f, 5.0f, 0.1f, 200000, 1}, 1, {5.0f}},
};
#define N_SENSORS ((int)(sizeof sensors / sizeof *sensors))

const int apklayer_plugin_abi = APKLAYER_PLUGIN_ABI;
int apklayer_sensors_probe(void) { return 0; }
const char apklayer_sensors_options[] = "accel\t0,9.80665,0\ttext\ngyro\t0,0,0\ttext\nmagnetic\t0,20,-40\ttext\nlight\t300\ttext\npressure\t1013.25\ttext\n"
                                        "proximity\t5\ttext\n";

int apklayer_sensors_init(int count, const char *const *options)
{
    for (int i = 0; i < count; i++) {
        const char *eq = strchr(options[i], '=');
        int s = 0;
        while (s < N_SENSORS && (!eq || strncmp(options[i], sensors[s].key, eq - options[i]) || sensors[s].key[eq - options[i]])) s++;
        if (s == N_SENSORS) { fprintf(stderr, "sensors fake: unknown option %s\n", options[i]); return -EINVAL; }
        float v[3];
        int n = sscanf(eq + 1, "%f,%f,%f", &v[0], &v[1], &v[2]);
        if (n != sensors[s].count) { fprintf(stderr, "sensors fake: %s needs %d value(s)\n", sensors[s].key, sensors[s].count); return -EINVAL; }
        memcpy(sensors[s].values, v, n * sizeof *v);
    }
    return 0;
}

int apklayer_sensors_list(struct apklayer_sensor_info *out, int max)
{
    int n = max < N_SENSORS ? max : N_SENSORS;
    for (int i = 0; i < n; i++) out[i] = sensors[i].info;
    return n;
}

int apklayer_sensors_read(int index, float values[3])
{
    if (index < 0 || index >= N_SENSORS) return -EINVAL;
    memcpy(values, sensors[index].values, sensors[index].count * sizeof *values);
    return sensors[index].count;
}
