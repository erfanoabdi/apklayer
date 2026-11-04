/* apklayer sensors HAL, host side: what the Android-side front (aosp/hals/sensors, AOSP's ISensors example with a backend's sensors)
 * calls. A backend is a library libapklayer-sensors-<backend>.so exporting these (one per front process, chosen in providers.toml).
 * Plain C, no Android types; sensor types and units are Android's (SensorType, m/s^2, rad/s, uT, lux, hPa, cm). */
#ifndef APKLAYER_SENSORS_H
#define APKLAYER_SENSORS_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/* Android's SensorType values of the sensors a backend can have */
enum {
    APKLAYER_SENSOR_ACCELEROMETER = 1,
    APKLAYER_SENSOR_MAGNETIC_FIELD = 2,
    APKLAYER_SENSOR_GYROSCOPE = 4,
    APKLAYER_SENSOR_LIGHT = 5,
    APKLAYER_SENSOR_PRESSURE = 6,
    APKLAYER_SENSOR_PROXIMITY = 8,
    APKLAYER_SENSOR_DEVICE_ORIENTATION = 27, /* the screen rotation 0..3, as WindowManager reads it */
};

struct apklayer_sensor_info {
    int type;               /* APKLAYER_SENSOR_* */
    char name[64], vendor[64];
    float max_range, resolution, power_ma;
    int32_t min_delay_us;   /* fastest period */
    int on_change;          /* 1: reports only changes (light, proximity), 0: continuous */
};

/* <0: not usable on this host; 0: usable but never chosen by default (fake); >0: quality, the highest is the default */
int apklayer_sensors_probe(void);
/* the domain's options from providers.toml, "key=value" each; 0 or -errno */
int apklayer_sensors_init(int count, const char *const *options);
/* the backend's sensors, at most max; returns how many */
int apklayer_sensors_list(struct apklayer_sensor_info *out, int max);
/* the current value of sensor `index` (of the list): 3 values for the vector sensors, 1 for the others; returns the count or -errno */
int apklayer_sensors_read(int index, float values[3]);

#ifdef __cplusplus
}
#endif
#endif
