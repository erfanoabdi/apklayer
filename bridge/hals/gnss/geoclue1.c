/* The GeoClue 1 GNSS backend (geoclue 0.12, org.freedesktop.Geoclue.Master on the session bus, as Sailfish OS ships it with its hybris and
 * MLS providers): a master client at the detailed level from any resource, its PositionChanged updates. GeoClue 1 has no agent: the OS's own
 * location switch (Sailfish: Settings > Location) governs its providers. Sessions: session.c. */
#include "session.h"

#include <errno.h>
#include <stdio.h>

#define MASTER "org.freedesktop.Geoclue.Master"
enum { FIELD_LATITUDE = 1, FIELD_LONGITUDE = 2, FIELD_ALTITUDE = 4 };  /* GeocluePositionFields */

/* (i fields, i timestamp, d latitude, d longitude, d altitude, (idd) accuracy: level, horizontal, vertical) of PositionChanged and GetPosition */
static void report(GVariant *position)
{
    int fields, level;
    struct apklayer_gnss_fix fix = {0};
    double horizontal, vertical;
    g_variant_get(position, "(iiddd(idd))", &fields, NULL, &fix.latitude, &fix.longitude, &fix.altitude, &level, &horizontal, &vertical);
    if ((fields & (FIELD_LATITUDE | FIELD_LONGITUDE)) != (FIELD_LATITUDE | FIELD_LONGITUDE)) return;
    if (!(fields & FIELD_ALTITUDE)) fix.altitude = 0;
    fix.accuracy = horizontal;
    gnss_session_report(&fix);
}

static void on_position(GDBusConnection *bus, const char *sender, const char *path, const char *interface, const char *signal, GVariant *parameters, gpointer data)
{
    if (g_variant_is_of_type(parameters, G_VARIANT_TYPE("(iiddd(idd))"))) report(parameters);
}

int gnss_source_start(GDBusConnection *bus)
{
    GError *error = NULL;
    const char *client = NULL;
    GVariant *created = g_dbus_connection_call_sync(bus, MASTER, "/org/freedesktop/Geoclue/Master", MASTER, "Create", NULL, G_VARIANT_TYPE("(o)"),
                                                    G_DBUS_CALL_FLAGS_NONE, 5000, NULL, &error);
    if (created) {
        g_variant_get(created, "(&o)", &client);
        g_dbus_connection_signal_subscribe(bus, NULL, "org.freedesktop.Geoclue.Position", "PositionChanged", client, NULL, G_DBUS_SIGNAL_FLAGS_NONE, on_position, NULL, NULL);
        /* GEOCLUE_ACCURACY_LEVEL_DETAILED, no minimum interval, updates, GEOCLUE_RESOURCE_ALL */
        GVariant *r = g_dbus_connection_call_sync(bus, MASTER, client, "org.freedesktop.Geoclue.MasterClient", "SetRequirements", g_variant_new("(iibi)", 6, 0, TRUE, 1023),
                                                  NULL, G_DBUS_CALL_FLAGS_NONE, 5000, NULL, &error);
        if (r) g_variant_unref(r);
        if (!error && (r = g_dbus_connection_call_sync(bus, MASTER, client, "org.freedesktop.Geoclue.MasterClient", "PositionStart", NULL, NULL, G_DBUS_CALL_FLAGS_NONE, 5000, NULL, &error)))
            g_variant_unref(r);
        /* the provider's current position, when it has one already (later ones come as PositionChanged) */
        if (!error && (r = g_dbus_connection_call_sync(bus, MASTER, client, "org.freedesktop.Geoclue.Position", "GetPosition", NULL, G_VARIANT_TYPE("(iiddd(idd))"),
                                                       G_DBUS_CALL_FLAGS_NONE, 5000, NULL, NULL))) {
            report(r);
            g_variant_unref(r);
        }
        g_variant_unref(created);
    }
    if (!error) return 0;
    fprintf(stderr, "gnss geoclue1: %s\n", error->message);
    g_error_free(error);
    return -1;
}

/* below GeoClue 2 (10): a host with both picks the newer one */
const int apklayer_plugin_abi = APKLAYER_PLUGIN_ABI;
const char apklayer_gnss_needs[] = "GeoClue 1 (its Master on the session bus)";  /* shown when probe() finds nothing (plugin.h) */
int apklayer_gnss_probe(void) { return gnss_session_ping(G_BUS_TYPE_SESSION, MASTER, "/org/freedesktop/Geoclue/Master") ? 5 : -1; }

int apklayer_gnss_init(int count, const char *const *options)
{
    if (count) { fprintf(stderr, "gnss geoclue1: unknown option %s\n", options[0]); return -EINVAL; }
    return gnss_session_init(G_BUS_TYPE_SESSION, "geoclue1");
}
