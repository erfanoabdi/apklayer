/* The GeoClue 2 GNSS backend: the desktop's location service (org.freedesktop.GeoClue2 on the system bus) as the receiver. Its client asks
 * for the exact level as DesktopId "apklayer" (option desktop-id=), so the desktop's GeoClue agent (or the location portal's setting) decides,
 * as for the network provider (bridge/portal/geoclue.py). Sessions: session.c. */
#include "session.h"

#include <errno.h>
#include <float.h>
#include <stdio.h>
#include <string.h>

#define GEOCLUE "org.freedesktop.GeoClue2"

static const char *desktop_id = "apklayer";

static GVariant *call(GDBusConnection *bus, const char *path, const char *interface, const char *method, GVariant *args, GError **error)
{
    return g_dbus_connection_call_sync(bus, GEOCLUE, path, interface, method, args, NULL, G_DBUS_CALL_FLAGS_NONE, 5000, NULL, error);
}

static double property(GVariant *all, const char *name)
{
    double v = 0;
    return g_variant_lookup(all, name, "d", &v) ? v : 0;
}

static void on_update(GDBusConnection *bus, const char *sender, const char *path, const char *interface, const char *signal, GVariant *parameters, gpointer data)
{
    const char *location;
    g_variant_get(parameters, "(&o&o)", NULL, &location);
    GVariant *reply = call(bus, location, "org.freedesktop.DBus.Properties", "GetAll", g_variant_new("(s)", GEOCLUE ".Location"), NULL);
    if (!reply) return;
    GVariant *all = g_variant_get_child_value(reply, 0);
    struct apklayer_gnss_fix fix = {property(all, "Latitude"), property(all, "Longitude"), property(all, "Altitude"),
                                    property(all, "Speed"), property(all, "Heading"), property(all, "Accuracy")};
    /* GeoClue's unknowns: altitude -DBL_MAX, speed and heading -1 */
    if (fix.altitude <= -DBL_MAX) fix.altitude = 0;
    if (fix.speed < 0) fix.speed = 0;
    if (fix.bearing < 0) fix.bearing = 0;
    g_variant_unref(all);
    g_variant_unref(reply);
    gnss_session_report(&fix);
}

static void set(GDBusConnection *bus, const char *path, const char *name, GVariant *value, GError **error)
{
    GVariant *r = call(bus, path, "org.freedesktop.DBus.Properties", "Set", g_variant_new("(ssv)", GEOCLUE ".Client", name, value), error);
    if (r) g_variant_unref(r);
}

/* a client, its updates, Start (refused when the desktop's agent denies the DesktopId) */
int gnss_source_start(GDBusConnection *bus)
{
    GError *error = NULL;
    const char *client = NULL;
    GVariant *reply = call(bus, "/org/freedesktop/GeoClue2/Manager", GEOCLUE ".Manager", "GetClient", NULL, &error);
    if (reply) {
        g_variant_get(reply, "(&o)", &client);
        set(bus, client, "DesktopId", g_variant_new_string(desktop_id), &error);
        if (!error) set(bus, client, "RequestedAccuracyLevel", g_variant_new_uint32(8), &error);  /* GCLUE_ACCURACY_LEVEL_EXACT */
        g_dbus_connection_signal_subscribe(bus, GEOCLUE, GEOCLUE ".Client", "LocationUpdated", client, NULL, G_DBUS_SIGNAL_FLAGS_NONE, on_update, NULL, NULL);
        GVariant *started = error ? NULL : call(bus, client, GEOCLUE ".Client", "Start", NULL, &error);
        if (started) g_variant_unref(started);
        g_variant_unref(reply);
    }
    if (!error) return 0;
    fprintf(stderr, "gnss geoclue: %s\n", error->message);
    g_error_free(error);
    return -1;
}

const char apklayer_gnss_options[] = "desktop-id\tapklayer\ttext\n";
int apklayer_gnss_probe(void) { return gnss_session_ping(G_BUS_TYPE_SYSTEM, GEOCLUE, "/org/freedesktop/GeoClue2/Manager") ? 10 : -1; }

int apklayer_gnss_init(int count, const char *const *options)
{
    for (int i = 0; i < count; i++) {
        if (!strncmp(options[i], "desktop-id=", 11)) desktop_id = options[i] + 11;
        else { fprintf(stderr, "gnss geoclue: unknown option %s\n", options[i]); return -EINVAL; }
    }
    return gnss_session_init(G_BUS_TYPE_SYSTEM, "geoclue");
}
