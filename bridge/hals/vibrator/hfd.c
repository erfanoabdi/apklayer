/* The hfd-service vibrator (Ubuntu Touch's haptics daemon: com.lomiri.hfd on the system bus, /com/lomiri/hfd, com.lomiri.hfd.Vibrator; it
 * drives the device's own vibrator through its Android HAL, sysfs or force feedback). Its API is vibrate(durationMs) (and rumble, a repeat of
 * it): no amplitude, no off. on is vibrate(duration); off is vibrate(1), the shortest vibration replacing the running one (the daemon's
 * backends restart the motor with each call); amplitude is not passed on (the device's strength). */
#include "apklayer/vibrator.h"

#include <errno.h>
#include <gio/gio.h>
#include <stdio.h>

#define BUS "com.lomiri.hfd"
#define PATH "/com/lomiri/hfd"

static GDBusConnection *bus;

static int vibrate(int32_t ms)
{
    GError *error = NULL;
    GVariant *r = g_dbus_connection_call_sync(bus, BUS, PATH, BUS ".Vibrator", "vibrate", g_variant_new("(i)", ms), NULL, G_DBUS_CALL_FLAGS_NONE,
                                              2000, NULL, &error);
    if (!r) { fprintf(stderr, "vibrator hfd: vibrate: %s\n", error->message); g_error_free(error); return -EIO; }
    g_variant_unref(r);
    return 0;
}

int apklayer_vibrator_probe(void)
{
    GDBusConnection *c = g_bus_get_sync(G_BUS_TYPE_SYSTEM, NULL, NULL);
    if (!c) return -1;
    GVariant *r = g_dbus_connection_call_sync(c, BUS, PATH, "org.freedesktop.DBus.Peer", "Ping", NULL, NULL, G_DBUS_CALL_FLAGS_NONE, 1000, NULL, NULL);
    if (r) g_variant_unref(r);
    g_object_unref(c);
    return r ? 25 : -1;  /* the daemon Ubuntu Touch runs for its own haptics: above feedbackd where both exist */
}

int apklayer_vibrator_init(int count, const char *const *options)
{
    if (count) { fprintf(stderr, "vibrator hfd: no options (%s)\n", options[0]); return -EINVAL; }
    GError *error = NULL;
    if (!(bus = g_bus_get_sync(G_BUS_TYPE_SYSTEM, NULL, &error))) { fprintf(stderr, "vibrator hfd: %s\n", error->message); return -ENOTCONN; }
    return 0;
}

int apklayer_vibrator_on(int32_t duration_ms) { return vibrate(duration_ms); }
int apklayer_vibrator_off(void) { return vibrate(1); }

int apklayer_vibrator_amplitude(float value)
{
    (void)value;
    return 0;
}
