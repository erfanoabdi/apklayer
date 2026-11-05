/* The lomiri-location-service GNSS backend (Ubuntu Touch: com.lomiri.location.Service on the system bus, the OS's location service over
 * the device's GNSS and its other providers; never the GNSS HAL itself, which has one client: this service). The service's own permission
 * check (trust store, the location indicator's switch) decides. Its protocol is dbus-cpp's encoding (lomiri-location-service's codec.h):
 * CreateSessionForCriteria(Criteria as flat arguments) -> /sessions/N; StartPositionUpdates on that path; the service then calls
 * UpdatePosition on the client's connection at that path, with the Update<Position> flattened: latitude d, longitude d, then Optional<>s
 * (altitude, horizontal and vertical accuracy) each as a boolean followed by the value when true, then the time (x); UpdateVelocity and
 * UpdateHeading (d, x: m/s, degrees) follow each position, unasked. Checked against the Ubuntu Touch phone's service (docs/HALIUM.md).
 * Sessions: session.c. */
#include "session.h"

#include <errno.h>
#include <stdio.h>
#include <string.h>

#define SERVICE "com.lomiri.location.Service"
#define SESSION SERVICE ".Session"

static char *session_path;
static struct apklayer_gnss_fix last;  /* the latest position, velocity and heading together (GDBus's worker thread only) */
static int have_last;

/* the next Optional<double> at *i: its value, or `none` */
static double optional(GVariant *body, gsize *i, double none)
{
    if (*i >= g_variant_n_children(body)) return none;
    GVariant *has = g_variant_get_child_value(body, (*i)++);
    gboolean present = g_variant_is_of_type(has, G_VARIANT_TYPE_BOOLEAN) && g_variant_get_boolean(has);
    g_variant_unref(has);
    if (!present || *i >= g_variant_n_children(body)) return none;
    GVariant *v = g_variant_get_child_value(body, (*i)++);
    double value = g_variant_is_of_type(v, G_VARIANT_TYPE_DOUBLE) ? g_variant_get_double(v) : none;
    g_variant_unref(v);
    return value;
}

/* the update as a fix; 0, or -1 when the body is not an Update<Position> */
static int parse(GVariant *body, struct apklayer_gnss_fix *fix)
{
    if (!body || g_variant_n_children(body) < 3) return -1;
    GVariant *lat = g_variant_get_child_value(body, 0), *lon = g_variant_get_child_value(body, 1);
    int ok = g_variant_is_of_type(lat, G_VARIANT_TYPE_DOUBLE) && g_variant_is_of_type(lon, G_VARIANT_TYPE_DOUBLE);
    if (ok) {
        memset(fix, 0, sizeof *fix);
        fix->latitude = g_variant_get_double(lat);
        fix->longitude = g_variant_get_double(lon);
        gsize i = 2;
        fix->altitude = optional(body, &i, 0);
        fix->accuracy = optional(body, &i, 0);
    }
    g_variant_unref(lat);
    g_variant_unref(lon);
    return ok ? 0 : -1;
}

/* an Update<double> (UpdateVelocity, UpdateHeading): its value, or -1 */
static double update_value(GVariant *body)
{
    double v;
    return body && g_variant_is_of_type(body, G_VARIANT_TYPE("(dx)")) ? (g_variant_get(body, "(dx)", &v, NULL), v) : -1;
}

/* GDBus's worker thread: the service's Update* calls on our session (UpdatePosition's signature varies with the Optional<>s, so no registered
 * object can take them), answered here */
static GDBusMessage *filter(GDBusConnection *bus, GDBusMessage *message, gboolean incoming, gpointer data)
{
    const char *member = g_dbus_message_get_member(message);
    if (!incoming || g_dbus_message_get_message_type(message) != G_DBUS_MESSAGE_TYPE_METHOD_CALL || g_strcmp0(g_dbus_message_get_path(message), data) ||
        g_strcmp0(g_dbus_message_get_interface(message), SESSION))
        return message;
    GVariant *body = g_dbus_message_get_body(message);
    struct apklayer_gnss_fix fix;
    int ok = 1;
    double v = update_value(body);
    if (!g_strcmp0(member, "UpdateVelocity")) last.speed = v >= 0 ? v : 0;
    else if (!g_strcmp0(member, "UpdateHeading")) last.bearing = v >= 0 ? v : 0;
    else if (!g_strcmp0(member, "UpdatePosition") && (ok = !parse(body, &fix))) {
        fix.speed = last.speed;
        fix.bearing = last.bearing;
        last = fix;
        have_last = 1;
    }
    if (have_last) gnss_session_report(&last);  /* velocity and heading come after their position */
    GDBusMessage *reply = ok ? g_dbus_message_new_method_reply(message)
                             : g_dbus_message_new_method_error_literal(message, SESSION ".ErrorParsingUpdate", "not an Update<Position>");
    g_dbus_connection_send_message(bus, reply, G_DBUS_SEND_MESSAGE_FLAGS_NONE, NULL, NULL);
    g_object_unref(reply);
    g_object_unref(message);
    return NULL;
}

int gnss_source_start(GDBusConnection *bus)
{
    GError *error = NULL;
    have_last = 0;  /* no update of the last session is reported (none arrives before the new session exists) */
    /* Criteria: requires position (not altitude, heading, velocity), 10 m horizontally, no vertical, velocity or heading accuracy */
    GVariant *created = g_dbus_connection_call_sync(bus, SERVICE, "/com/lomiri/location/Service", SERVICE, "CreateSessionForCriteria",
                                                    g_variant_new("(bbbbdbbb)", TRUE, FALSE, FALSE, FALSE, 10.0, FALSE, FALSE, FALSE),
                                                    G_VARIANT_TYPE("(o)"), G_DBUS_CALL_FLAGS_NONE, 5000, NULL, &error);
    if (created) {
        g_free(session_path);
        g_variant_get(created, "(o)", &session_path);
        g_variant_unref(created);
        g_dbus_connection_add_filter(bus, filter, session_path, NULL);  /* the session's connection, closed with it */
        GVariant *r = g_dbus_connection_call_sync(bus, SERVICE, session_path, SESSION, "StartPositionUpdates", NULL, NULL, G_DBUS_CALL_FLAGS_NONE, 5000, NULL, &error);
        if (r) g_variant_unref(r);
    }
    if (!error) return 0;
    fprintf(stderr, "gnss lomiri: %s\n", error->message);
    g_error_free(error);
    return -1;
}

const int apklayer_plugin_abi = APKLAYER_PLUGIN_ABI;
const char apklayer_gnss_needs[] = "the Lomiri location service on the system bus";  /* shown when probe() finds nothing (plugin.h) */
int apklayer_gnss_probe(void) { return gnss_session_ping(G_BUS_TYPE_SYSTEM, SERVICE, "/com/lomiri/location/Service") ? 10 : -1; }

int apklayer_gnss_init(int count, const char *const *options)
{
    if (count) { fprintf(stderr, "gnss lomiri: unknown option %s\n", options[0]); return -EINVAL; }
    return gnss_session_init(G_BUS_TYPE_SYSTEM, "lomiri");
}
