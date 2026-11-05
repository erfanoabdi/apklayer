/* The iio-sensor-proxy sensors (D-Bus net.hadess.SensorProxy on the system bus): what it exposes, mapped to Android's types honestly.
 * - Accelerometer: the proxy gives only a screen orientation ("normal", "bottom-up", "left-up", "right-up"), never acceleration, so it becomes
 *   Android's DEVICE_ORIENTATION sensor (type 27, what WindowManager's rotation reads on devices with one: 0 natural, 1 rotated 90 degrees
 *   counter-clockwise, 2 upside down, 3 rotated 90 degrees clockwise); "undefined" (flat) keeps the last value. No ACCELEROMETER is listed: an app
 *   finds none, as on an Android device without one, instead of made-up m/s^2.
 * - Ambient light: LIGHT, only when the proxy reports lux (LightLevelUnit "vendor" is a driver scale, not lux: then no light sensor).
 * - Proximity: PROXIMITY as Android's binary sensors report it: 0 cm near, the 5 cm maximum far.
 * - Compass: not listed. The proxy gives a heading only; Android's types need a field vector (MAGNETIC_FIELD) or azimuth with pitch and roll
 *   (the deprecated ORIENTATION), so either would carry made-up values. (Its policy lets only geoclue claim it on most systems anyway.)
 * Values are cached from PropertiesChanged on a thread of our own; the HAL front samples them at the rate an app asks for.
 * shortcut: every available sensor is claimed at init and held while the front runs (the backend ABI has no activate); claim per sensor on
 * activation when the ABI gets one. */
#include "apklayer/sensors.h"

#include <errno.h>
#include <gio/gio.h>
#include <stdio.h>
#include <string.h>

#define BUS "net.hadess.SensorProxy"
#define PATH "/net/hadess/SensorProxy"

static GDBusConnection *bus;
static GMutex lock;
static struct sensor {
    struct apklayer_sensor_info info;
    const char *iface, *property;  /* the property holding its value */
    int count;
    float values[3];
} sensors[3];
static int n_sensors;

static GVariant *get(const char *path, const char *iface, const char *property)
{
    GVariant *r = g_dbus_connection_call_sync(bus, BUS, path, "org.freedesktop.DBus.Properties", "Get", g_variant_new("(ss)", iface, property),
                                              G_VARIANT_TYPE("(v)"), G_DBUS_CALL_FLAGS_NONE, 1000, NULL, NULL);
    GVariant *v = NULL;
    if (r) { g_variant_get(r, "(v)", &v); g_variant_unref(r); }
    return v;
}

static int has(const char *path, const char *iface, const char *property)
{
    GVariant *v = get(path, iface, property);
    int yes = v && g_variant_is_of_type(v, G_VARIANT_TYPE_BOOLEAN) && g_variant_get_boolean(v);
    if (v) g_variant_unref(v);
    return yes;
}

/* a property's value in Android's units */
static void apply(struct sensor *s, GVariant *v)
{
    if (!strcmp(s->property, "AccelerometerOrientation")) {
        const char *o = g_variant_get_string(v, NULL);
        static const char *const rotation[] = {"normal", "right-up", "bottom-up", "left-up"};
        for (int i = 0; i < 4; i++) if (!strcmp(o, rotation[i])) s->values[0] = i;
    } else if (!strcmp(s->property, "ProximityNear")) {
        s->values[0] = g_variant_get_boolean(v) ? 0.0f : s->info.max_range;
    } else {  /* LightLevel (lux) */
        s->values[0] = g_variant_get_double(v);
    }
}

static void changed(GDBusConnection *c, const char *sender, const char *path, const char *iface, const char *signal, GVariant *params, gpointer data)
{
    (void)c; (void)sender; (void)path; (void)iface; (void)signal; (void)data;
    const char *which;
    GVariant *props;
    g_variant_get(params, "(&s@a{sv}as)", &which, &props, NULL);
    g_mutex_lock(&lock);
    for (int i = 0; i < n_sensors; i++) {
        GVariant *v = strcmp(which, sensors[i].iface) ? NULL : g_variant_lookup_value(props, sensors[i].property, NULL);
        if (v) { apply(&sensors[i], v); g_variant_unref(v); }
    }
    g_mutex_unlock(&lock);
    g_variant_unref(props);
}

static gpointer loop(gpointer context)
{
    for (;;) g_main_context_iteration(context, TRUE);
    return NULL;
}

const int apklayer_plugin_abi = APKLAYER_PLUGIN_ABI;
int apklayer_sensors_probe(void)
{
    GDBusConnection *c = g_bus_get_sync(G_BUS_TYPE_SYSTEM, NULL, NULL);
    if (!c) return -1;
    bus = c;
    int any = has(PATH, BUS, "HasAccelerometer") || has(PATH, BUS, "HasAmbientLight") || has(PATH, BUS, "HasProximity");
    bus = NULL;
    g_object_unref(c);
    return any ? 10 : -1;  /* sensorfw (raw sensors) ranks higher */
}

static void add(const char *path, const char *iface, const char *claim, const char *property, struct apklayer_sensor_info info)
{
    GError *error = NULL;
    GVariant *r = g_dbus_connection_call_sync(bus, BUS, path, iface, claim, NULL, NULL, G_DBUS_CALL_FLAGS_NONE, 2000, NULL, &error);
    if (!r) { fprintf(stderr, "sensors iio: %s: %s\n", claim, error->message); g_error_free(error); return; }
    g_variant_unref(r);
    struct sensor *s = &sensors[n_sensors++];
    *s = (struct sensor){info, iface, property, 1, {0}};
    GVariant *v = get(path, iface, property);
    if (v) { apply(s, v); g_variant_unref(v); }
}

int apklayer_sensors_init(int count, const char *const *options)
{
    if (count) { fprintf(stderr, "sensors iio: no options (%s)\n", options[0]); return -EINVAL; }
    GError *error = NULL;
    if (!(bus = g_bus_get_sync(G_BUS_TYPE_SYSTEM, NULL, &error))) { fprintf(stderr, "sensors iio: %s\n", error->message); return -ENOTCONN; }
    /* signals are delivered to the context current at subscription: ours, iterated by our thread */
    GMainContext *context = g_main_context_new();
    g_main_context_push_thread_default(context);
    g_dbus_connection_signal_subscribe(bus, BUS, "org.freedesktop.DBus.Properties", "PropertiesChanged", NULL, NULL, G_DBUS_SIGNAL_FLAGS_NONE,
                                       changed, NULL, NULL);
    g_main_context_pop_thread_default(context);
    g_mutex_lock(&lock);
    if (has(PATH, BUS, "HasAccelerometer"))
        add(PATH, BUS, "ClaimAccelerometer", "AccelerometerOrientation",
            (struct apklayer_sensor_info){APKLAYER_SENSOR_DEVICE_ORIENTATION, "iio-sensor-proxy orientation", "iio-sensor-proxy", 3, 1, 0.1f, 200000, 1});
    if (has(PATH, BUS, "HasAmbientLight")) {
        GVariant *unit = get(PATH, BUS, "LightLevelUnit");
        if (unit && !strcmp(g_variant_get_string(unit, NULL), "lux"))  /* no range from the proxy: Android's bright-sunlight bound */
            add(PATH, BUS, "ClaimLight", "LightLevel",
                (struct apklayer_sensor_info){APKLAYER_SENSOR_LIGHT, "iio-sensor-proxy light", "iio-sensor-proxy", 100000, 1, 0.1f, 200000, 1});
        else
            fprintf(stderr, "sensors iio: light in %s units, not lux: not listed\n", unit ? g_variant_get_string(unit, NULL) : "unknown");
        if (unit) g_variant_unref(unit);
    }
    if (has(PATH, BUS, "HasProximity"))
        add(PATH, BUS, "ClaimProximity", "ProximityNear",
            (struct apklayer_sensor_info){APKLAYER_SENSOR_PROXIMITY, "iio-sensor-proxy proximity", "iio-sensor-proxy", 5, 5, 0.1f, 200000, 1});
    g_mutex_unlock(&lock);
    g_thread_new("iio-sensor-proxy", loop, context);
    return 0;
}

int apklayer_sensors_list(struct apklayer_sensor_info *out, int max)
{
    int n = max < n_sensors ? max : n_sensors;
    for (int i = 0; i < n; i++) out[i] = sensors[i].info;
    return n;
}

int apklayer_sensors_read(int index, float values[3])
{
    if (index < 0 || index >= n_sensors) return -EINVAL;
    g_mutex_lock(&lock);
    memcpy(values, sensors[index].values, sensors[index].count * sizeof *values);
    g_mutex_unlock(&lock);
    return sensors[index].count;
}
