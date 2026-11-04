/* The fake backends' option parsing (bridge/hals/{sensors,gnss}/fake.c): sensor values, a fixed location, a GPX replay. `meson test -C b providers-fake` */
#include "apklayer/gnss.h"
#include "apklayer/sensors.h"

#include <assert.h>
#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>

int main(void)
{
    const char *sensor_options[] = {"accel=0.5,9.5,1.25", "light=42"};
    assert(apklayer_sensors_init(2, sensor_options) == 0);
    struct apklayer_sensor_info info[16];
    int n = apklayer_sensors_list(info, 16);
    float v[3];
    int accel = -1, light = -1;
    for (int i = 0; i < n; i++) {
        if (info[i].type == APKLAYER_SENSOR_ACCELEROMETER) accel = i;
        if (info[i].type == APKLAYER_SENSOR_LIGHT) light = i;
    }
    assert(accel >= 0 && apklayer_sensors_read(accel, v) == 3 && v[0] == 0.5f && v[1] == 9.5f && v[2] == 1.25f);
    assert(light >= 0 && apklayer_sensors_read(light, v) == 1 && v[0] == 42.0f && info[light].on_change);
    const char *bad_count[] = {"accel=1,2"}, *bad_key[] = {"acceleration=1,2,3"};
    assert(apklayer_sensors_init(1, bad_count) < 0 && apklayer_sensors_init(1, bad_key) < 0);

    struct apklayer_gnss_fix fix;
    const char *fixed[] = {"location=48.8584,2.2945", "accuracy=3"};
    assert(apklayer_gnss_init(2, fixed) == 0 && apklayer_gnss_next_fix(&fix) == 0);
    assert(fabs(fix.latitude - 48.8584) < 1e-9 && fabs(fix.longitude - 2.2945) < 1e-9 && fix.accuracy == 3.0f);

    char path[] = "/tmp/providers-fake-test-XXXXXX";
    int fd = mkstemp(path);
    FILE *gpx = fdopen(fd, "w");
    fputs("<gpx><trk><trkseg><trkpt lat=\"1.5\" lon='2.5'><ele>10</ele></trkpt><trkpt lat=\"3.5\" lon=\"4.5\"/></trkseg></trk></gpx>", gpx);
    fclose(gpx);
    char option[64];
    snprintf(option, sizeof option, "gpx=%s", path);
    const char *replay[] = {option};
    assert(apklayer_gnss_init(1, replay) == 0);
    unlink(path);
    assert(apklayer_gnss_next_fix(&fix) == 0 && fix.latitude == 1.5 && fix.longitude == 2.5 && fix.altitude == 10);
    assert(apklayer_gnss_next_fix(&fix) == 0 && fix.latitude == 3.5 && fix.longitude == 4.5);
    assert(apklayer_gnss_next_fix(&fix) == 0 && fix.latitude == 3.5);  /* stays at the last point */
    puts("PASS providers fakes: sensor options, fixed location, GPX replay");
    return 0;
}
