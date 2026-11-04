/* The sensorfw sensors (Sailfish OS, Ubuntu Touch: com.nokia.SensorService on the system bus, the daemon that owns the device's sensors HAL).
 * Its client protocol, as libsensorfw's SocketReader and repowerd's adapter speak it: per sensor loadPlugin and requestSensor (a session id)
 * on /SensorManager, then a Unix socket ($SENSORFW_SOCKET_PATH/var/run/sensord.sock) on which the client writes its session id and reads one
 * tag byte, then frames of a uint32 sample count followed by the samples (the daemon's own structs: TimedXyzData, TimedUnsigned, ...); start
 * and setInterval on /SensorManager/<sensor>. Ranges and the fastest interval are the daemon's (getCurrentDataRange, getAvailableIntervals),
 * converted from its units (mG, milli-degrees/s, nT, lux, Pa) to Android's.
 * shortcut: every sensor runs from init while the front runs (the backend ABI has no activate), continuous ones at most at 100 Hz; start and
 * stop per sensor when the ABI gets an activate. */
#include "apklayer/sensors.h"

#include <errno.h>
#include <gio/gio.h>
#include <math.h>
#include <poll.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>

#define BUS "com.nokia.SensorService"
#define MANAGER "/SensorManager"

/* the daemon's sample structs (sensorfw datatypes/genericdata.h, orientationdata.h): a 64-bit timestamp, then the values */
struct timed_xyz { uint64_t timestamp; int32_t x, y, z; };                     /* TimedXyzData */
struct timed_unsigned { uint64_t timestamp; uint32_t value; };                 /* TimedUnsigned */
struct proximity_data { uint64_t timestamp; uint32_t value; uint8_t within; }; /* ProximityData */
struct magnetic_data { uint64_t timestamp; int32_t x, y, z, rx, ry, rz, level; };  /* CalibratedMagneticFieldData */

enum kind { XYZ, MAGNETIC, UNSIGNED, PROXIMITY };
static const struct plugin {
    const char *name, *iface;
    int type;
    enum kind kind;
    float scale;  /* the daemon's unit to Android's */
} plugins[] = {
    {"accelerometersensor", "local.AccelerometerSensor", APKLAYER_SENSOR_ACCELEROMETER, XYZ, 9.80665e-3f},    /* mG */
    {"gyroscopesensor", "local.GyroscopeSensor", APKLAYER_SENSOR_GYROSCOPE, XYZ, (float)(M_PI / 180 / 1000)},  /* milli-degrees/s */
    {"magnetometersensor", "local.MagnetometerSensor", APKLAYER_SENSOR_MAGNETIC_FIELD, MAGNETIC, 1e-3f},      /* nT */
    {"alssensor", "local.ALSSensor", APKLAYER_SENSOR_LIGHT, UNSIGNED, 1},                                      /* lux */
    {"pressuresensor", "local.PressureSensor", APKLAYER_SENSOR_PRESSURE, UNSIGNED, 0.01f},                    /* Pa */
    {"proximitysensor", "local.ProximitySensor", APKLAYER_SENSOR_PROXIMITY, PROXIMITY, 1},
};
#define N_PLUGINS ((int)(sizeof plugins / sizeof *plugins))

static GDBusConnection *bus;
static GMutex lock;
static struct sensor {
    const struct plugin *plugin;
    struct apklayer_sensor_info info;
    int session, fd;
    float values[3];
} sensors[N_PLUGINS];
static int n_sensors;

static GVariant *call(const char *path, const char *iface, const char *method, GVariant *args, const char *reply)
{
    GError *error = NULL;
    GVariant *r = g_dbus_connection_call_sync(bus, BUS, path, iface, method, args, reply ? G_VARIANT_TYPE(reply) : NULL, G_DBUS_CALL_FLAGS_NONE,
                                              2000, NULL, &error);
    if (!r) { fprintf(stderr, "sensors sensorfw: %s %s: %s\n", path, method, error->message); g_error_free(error); }
    return r;
}

static int readn(int fd, void *buffer, size_t size)
{
    return recv(fd, buffer, size, MSG_WAITALL) == (ssize_t)size ? 0 : -1;
}

/* one frame of a sensor's socket: the last sample becomes its value */
static int frame(struct sensor *s)
{
    static const size_t sizes[] = {[XYZ] = sizeof(struct timed_xyz), [MAGNETIC] = sizeof(struct magnetic_data),
                                   [UNSIGNED] = sizeof(struct timed_unsigned), [PROXIMITY] = sizeof(struct proximity_data)};
    uint32_t count;
    union { struct timed_xyz xyz; struct magnetic_data magnetic; struct timed_unsigned u; struct proximity_data p; } sample;
    size_t size = sizes[s->plugin->kind];
    if (readn(s->fd, &count, sizeof count) || count == 0 || count > 1000) return -1;
    for (uint32_t i = 0; i < count; i++) if (readn(s->fd, &sample, size)) return -1;
    float k = s->plugin->scale, v[3] = {0};
    switch (s->plugin->kind) {
    case XYZ: v[0] = sample.xyz.x * k; v[1] = sample.xyz.y * k; v[2] = sample.xyz.z * k; break;
    case MAGNETIC: v[0] = sample.magnetic.x * k; v[1] = sample.magnetic.y * k; v[2] = sample.magnetic.z * k; break;
    case UNSIGNED: v[0] = sample.u.value * k; break;
    case PROXIMITY: v[0] = sample.p.within ? 0.0f : s->info.max_range; break;  /* Android's binary proximity: 0 near, the maximum far */
    }
    g_mutex_lock(&lock);
    memcpy(s->values, v, sizeof v);
    g_mutex_unlock(&lock);
    return 0;
}

static gpointer reader(gpointer data)
{
    (void)data;
    struct pollfd fds[N_PLUGINS];
    for (;;) {
        for (int i = 0; i < n_sensors; i++) fds[i] = (struct pollfd){sensors[i].fd, POLLIN, 0};
        if (poll(fds, n_sensors, -1) < 0) continue;
        for (int i = 0; i < n_sensors; i++)
            if (fds[i].revents && sensors[i].fd >= 0 && frame(&sensors[i])) {
                fprintf(stderr, "sensors sensorfw: %s: the daemon's socket closed\n", sensors[i].plugin->name);
                close(sensors[i].fd);
                sensors[i].fd = -1;  /* poll ignores it; the sensor keeps its last value */
            }
    }
    return NULL;
}

/* the daemon's data socket for a session: the session id out, its tag byte in */
static int connect_session(int session)
{
    struct sockaddr_un addr = {.sun_family = AF_UNIX};
    snprintf(addr.sun_path, sizeof addr.sun_path, "%s/var/run/sensord.sock", getenv("SENSORFW_SOCKET_PATH") ? getenv("SENSORFW_SOCKET_PATH") : "");
    int fd = socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0);
    char tag;
    if (fd < 0 || connect(fd, (struct sockaddr *)&addr, sizeof addr) || write(fd, &session, sizeof session) != sizeof session || readn(fd, &tag, 1)) {
        fprintf(stderr, "sensors sensorfw: %s: %s\n", addr.sun_path, strerror(errno));
        if (fd >= 0) close(fd);
        return -1;
    }
    return fd;
}

static void add(const struct plugin *p)
{
    GVariant *r = call(MANAGER, "local.SensorManager", "loadPlugin", g_variant_new("(s)", p->name), "(b)");
    gboolean loaded = FALSE;
    if (r) { g_variant_get(r, "(b)", &loaded); g_variant_unref(r); }
    if (!loaded) return;
    int session = -1;
    if ((r = call(MANAGER, "local.SensorManager", "requestSensor", g_variant_new("(sx)", p->name, (gint64)getpid()), "(i)"))) {
        g_variant_get(r, "(i)", &session);
        g_variant_unref(r);
    }
    if (session < 0) return;
    char path[96];
    snprintf(path, sizeof path, MANAGER "/%s", p->name);
    struct sensor *s = &sensors[n_sensors];
    *s = (struct sensor){p, {p->type, "", "sensorfw", 0, 0, 0.1f, 200000, p->kind >= UNSIGNED}, session, -1, {0}};
    snprintf(s->info.name, sizeof s->info.name, "sensorfw %s", p->name);
    double min = 0, max = 0, resolution = 0, fastest = 0;
    if ((r = call(path, p->iface, "getCurrentDataRange", NULL, "((ddd))"))) { g_variant_get(r, "((ddd))", &min, &max, &resolution); g_variant_unref(r); }
    s->info.max_range = fmax(fabs(min), fabs(max)) * p->scale;
    s->info.resolution = resolution * p->scale;
    if (p->kind == PROXIMITY) s->values[0] = s->info.max_range = s->info.resolution = 5;  /* near/far (0..1): Android's binary 5 cm, far until told */
    if (!s->info.on_change) {  /* the fastest interval the daemon offers (ms), at most 100 Hz */
        GVariant *ranges = call(path, p->iface, "getAvailableIntervals", NULL, "(a(ddd))");
        if (ranges) {
            GVariantIter *it;
            double lo, hi, step;
            g_variant_get(ranges, "(a(ddd))", &it);
            while (g_variant_iter_next(it, "(ddd)", &lo, &hi, &step)) if (fastest == 0 || lo < fastest) fastest = lo;
            g_variant_iter_free(it);
            g_variant_unref(ranges);
        }
        int interval = fastest > 10 ? (int)ceil(fastest) : 10;
        s->info.min_delay_us = interval * 1000;
        if ((r = call(path, p->iface, "setInterval", g_variant_new("(ii)", session, interval), NULL))) g_variant_unref(r);
    }
    if ((s->fd = connect_session(session)) < 0) return;
    if (!(r = call(path, p->iface, "start", g_variant_new("(i)", session), NULL))) { close(s->fd); return; }
    g_variant_unref(r);
    n_sensors++;
}

int apklayer_sensors_probe(void)
{
    GDBusConnection *c = g_bus_get_sync(G_BUS_TYPE_SYSTEM, NULL, NULL);
    if (!c) return -1;
    GVariant *r = g_dbus_connection_call_sync(c, BUS, MANAGER, "local.SensorManager", "availableSensorPlugins", NULL, G_VARIANT_TYPE("(as)"),
                                              G_DBUS_CALL_FLAGS_NONE, 1000, NULL, NULL);
    int quality = -1;
    if (r) {
        const char **names;
        g_variant_get(r, "(^a&s)", &names);
        for (int i = 0; names[i]; i++)
            for (int p = 0; p < N_PLUGINS; p++) if (!strcmp(names[i], plugins[p].name)) quality = 20;  /* raw sensors: above iio-sensor-proxy */
        g_free(names);
        g_variant_unref(r);
    }
    g_object_unref(c);
    return quality;
}

int apklayer_sensors_init(int count, const char *const *options)
{
    if (count) { fprintf(stderr, "sensors sensorfw: no options (%s)\n", options[0]); return -EINVAL; }
    GError *error = NULL;
    if (!(bus = g_bus_get_sync(G_BUS_TYPE_SYSTEM, NULL, &error))) { fprintf(stderr, "sensors sensorfw: %s\n", error->message); return -ENOTCONN; }
    for (int p = 0; p < N_PLUGINS; p++) add(&plugins[p]);
    if (!n_sensors) { fprintf(stderr, "sensors sensorfw: no sensor could be started\n"); return -ENODEV; }
    g_thread_new("sensorfw", reader, NULL);
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
    memcpy(values, sensors[index].values, sizeof sensors[index].values);
    g_mutex_unlock(&lock);
    return sensors[index].plugin->kind <= MAGNETIC ? 3 : 1;
}
