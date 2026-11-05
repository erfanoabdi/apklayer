/* apklayer sensors plugin API: what the Android-side front (aosp/hals/sensors, AOSP's ISensors example with a plugin's sensors) calls. A
 * plugin is a library libapklayer-sensors-<backend>.so exporting these (one per front process, chosen in providers.toml; common rules,
 * probe, options, init and the ABI version: apklayer/plugin.h; docs/PLUGINS.md). Plain C, no Android types; sensor types and units are
 * Android's (SensorType; m/s^2, rad/s, uT, lux, hPa, cm).
 * Threads: list is called once, on the front's main thread, right after init. read is called by the example's sampling thread of each
 * active sensor (one thread per sensor, at the rate the app asked for, no faster than min_delay_us): calls for different sensors can run at
 * the same time. read must not block: return the latest value (a plugin with an asynchronous source keeps it from its own thread). */
#ifndef APKLAYER_SENSORS_H
#define APKLAYER_SENSORS_H

#include <stdint.h>

#include <apklayer/plugin.h>

#ifdef __cplusplus
extern "C" {
#endif
#pragma GCC visibility push(default)

/* Android's SensorType values of the sensors a plugin can have */
enum {
    APKLAYER_SENSOR_ACCELEROMETER = 1,       /* x, y, z: m/s^2, gravity included (a device lying flat reads 0, 0, 9.81) */
    APKLAYER_SENSOR_MAGNETIC_FIELD = 2,      /* x, y, z: uT */
    APKLAYER_SENSOR_GYROSCOPE = 4,           /* x, y, z: rad/s, counter-clockwise positive */
    APKLAYER_SENSOR_LIGHT = 5,               /* lux */
    APKLAYER_SENSOR_PRESSURE = 6,            /* hPa */
    APKLAYER_SENSOR_PROXIMITY = 8,           /* cm (a near/far sensor reports 0 or its max_range) */
    APKLAYER_SENSOR_DEVICE_ORIENTATION = 27, /* the screen rotation 0..3, as WindowManager reads it */
};

struct apklayer_sensor_info {
    int type;                   /* APKLAYER_SENSOR_* */
    char name[64], vendor[64];  /* NUL-terminated */
    float max_range, resolution, power_ma;  /* in the type's unit; mA */
    int32_t min_delay_us;       /* fastest sampling period */
    int on_change;              /* 1: reports only changes (light, proximity, orientation), 0: continuous */
};

int apklayer_sensors_probe(void);
extern const char apklayer_sensors_options[];  /* optional */
int apklayer_sensors_init(int count, const char *const *options);
/* writes the plugin's sensors into out (the caller's array of max entries); returns how many (0..max). Their order gives each its index. */
int apklayer_sensors_list(struct apklayer_sensor_info *out, int max);
/* the current value of sensor `index` (of the list) into values (the caller's): returns 3 for the x, y, z sensors, 1 for the others (in
 * values[0]), or -errno (the sample is reported as 0) */
int apklayer_sensors_read(int index, float values[3]);

#pragma GCC visibility pop
#ifdef __cplusplus
}
#endif
#endif
