/* The GeoClue GNSS backend: the desktop's location service (org.freedesktop.GeoClue2 on the system bus) as the receiver. Its client asks
 * for the exact level with DesktopId "apklayer", so the desktop's GeoClue agent (or the location portal's setting) decides, as for the
 * network provider (bridge/portal/geoclue.py). The session runs only while the front asks for fixes (a started GeoClue client turns the
 * desktop's location indicator on); each interval gets the latest fix, -EAGAIN before the first. Options: desktop-id=<id>. */
#include "apklayer/gnss.h"

#include <errno.h>
#include <float.h>
#include <gio/gio.h>
#include <pthread.h>
#include <stdio.h>
#include <string.h>

#define GEOCLUE "org.freedesktop.GeoClue2"
#define IDLE_STOP_US (10 * G_USEC_PER_SEC)  /* no next_fix for this long: the front stopped, the session ends */

static GDBusConnection *bus;
static GMainContext *context;
static char *desktop_id = "apklayer";
static pthread_mutex_t lock = PTHREAD_MUTEX_INITIALIZER;
static struct apklayer_gnss_fix latest;
static int have_fix;
static gint64 last_asked;  /* monotonic µs of the last next_fix */
static char *client;       /* the started client's path, NULL while stopped */
static guint updates;

static GVariant *call(const char *path, const char *interface, const char *method, GVariant *args, GError **error)
{
    return g_dbus_connection_call_sync(bus, GEOCLUE, path, interface, method, args, NULL, G_DBUS_CALL_FLAGS_NONE, 5000, NULL, error);
}

static double property(GVariant *all, const char *name)
{
    double v = 0;
    return g_variant_lookup(all, name, "d", &v) ? v : 0;
}

static void on_update(GDBusConnection *c, const char *sender, const char *path, const char *interface, const char *signal, GVariant *parameters, gpointer data)
{
    const char *location;
    g_variant_get(parameters, "(&o&o)", NULL, &location);
    GVariant *reply = call(location, "org.freedesktop.DBus.Properties", "GetAll", g_variant_new("(s)", GEOCLUE ".Location"), NULL);
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
    pthread_mutex_lock(&lock);
    latest = fix;
    have_fix = 1;
    pthread_mutex_unlock(&lock);
}

static void stop_session(void)
{
    if (!client) return;
    g_dbus_connection_signal_unsubscribe(bus, updates);
    GVariant *r = call(client, GEOCLUE ".Client", "Stop", NULL, NULL);
    if (r) g_variant_unref(r);
    if ((r = call("/org/freedesktop/GeoClue2/Manager", GEOCLUE ".Manager", "DeleteClient", g_variant_new("(o)", client), NULL))) g_variant_unref(r);
    g_free(client);
    client = NULL;
    pthread_mutex_lock(&lock);
    have_fix = 0;  /* a new session starts without a stale fix */
    pthread_mutex_unlock(&lock);
    fprintf(stderr, "gnss geoclue: session stopped\n");
}

static void set(const char *path, const char *name, GVariant *value, GError **error)
{
    GVariant *r = call(path, "org.freedesktop.DBus.Properties", "Set", g_variant_new("(ssv)", GEOCLUE ".Client", name, value), error);
    if (r) g_variant_unref(r);
}

/* on the backend's thread: a client, its updates, Start (refused when the desktop's agent denies "apklayer") */
static gboolean start_session(gpointer data)
{
    if (client) return G_SOURCE_REMOVE;
    GError *error = NULL;
    GVariant *reply = call("/org/freedesktop/GeoClue2/Manager", GEOCLUE ".Manager", "GetClient", NULL, &error);
    if (reply) {
        g_variant_get(reply, "(o)", &client);
        g_variant_unref(reply);
        set(client, "DesktopId", g_variant_new_string(desktop_id), &error);
        if (!error) set(client, "RequestedAccuracyLevel", g_variant_new_uint32(8), &error);  /* GCLUE_ACCURACY_LEVEL_EXACT */
        updates = g_dbus_connection_signal_subscribe(bus, GEOCLUE, GEOCLUE ".Client", "LocationUpdated", client, NULL, G_DBUS_SIGNAL_FLAGS_NONE, on_update, NULL, NULL);
        if (!error) reply = call(client, GEOCLUE ".Client", "Start", NULL, &error);
        if (!error) g_variant_unref(reply);
    }
    if (error) {  /* no fixes this session: the front reports none (an honest receiver without sky), and asks again next session */
        fprintf(stderr, "gnss geoclue: %s\n", error->message);
        g_error_free(error);
        if (client) { g_dbus_connection_signal_unsubscribe(bus, updates); g_free(client); client = NULL; }
        return G_SOURCE_REMOVE;
    }
    fprintf(stderr, "gnss geoclue: session started (%s)\n", client);
    return G_SOURCE_REMOVE;
}

static gboolean idle_check(gpointer data)
{
    pthread_mutex_lock(&lock);
    gint64 asked = last_asked;
    pthread_mutex_unlock(&lock);
    if (client && g_get_monotonic_time() - asked > IDLE_STOP_US) stop_session();
    return G_SOURCE_CONTINUE;
}

static void *loop(void *data)
{
    g_main_context_push_thread_default(context);
    GSource *timer = g_timeout_source_new_seconds(2);
    g_source_set_callback(timer, idle_check, NULL, NULL);
    g_source_attach(timer, context);
    for (;;) g_main_context_iteration(context, TRUE);
    return NULL;
}

int apklayer_gnss_probe(void)
{
    GDBusConnection *system = g_bus_get_sync(G_BUS_TYPE_SYSTEM, NULL, NULL);
    if (!system) return -1;
    GVariant *pong = g_dbus_connection_call_sync(system, GEOCLUE, "/org/freedesktop/GeoClue2/Manager", "org.freedesktop.DBus.Peer", "Ping",
                                                 NULL, NULL, G_DBUS_CALL_FLAGS_NONE, 3000, NULL, NULL);  /* activates GeoClue when it is not running */
    g_object_unref(system);
    if (!pong) return -1;
    g_variant_unref(pong);
    return 10;
}

int apklayer_gnss_init(int count, const char *const *options)
{
    for (int i = 0; i < count; i++) {
        if (!strncmp(options[i], "desktop-id=", 11)) desktop_id = g_strdup(options[i] + 11);
        else { fprintf(stderr, "gnss geoclue: unknown option %s\n", options[i]); return -EINVAL; }
    }
    GError *error = NULL;
    bus = g_bus_get_sync(G_BUS_TYPE_SYSTEM, NULL, &error);
    if (!bus) { fprintf(stderr, "gnss geoclue: %s\n", error->message); g_error_free(error); return -ENODEV; }
    context = g_main_context_new();
    pthread_t thread;
    return pthread_create(&thread, NULL, loop, NULL) ? -EAGAIN : 0;
}

int apklayer_gnss_next_fix(struct apklayer_gnss_fix *fix)
{
    pthread_mutex_lock(&lock);
    int idle = g_get_monotonic_time() - last_asked > IDLE_STOP_US / 2;
    last_asked = g_get_monotonic_time();
    int ok = have_fix;
    if (ok) *fix = latest;
    pthread_mutex_unlock(&lock);
    if (idle) g_main_context_invoke(context, start_session, NULL);  /* the first fix asked for after a pause: (re)start the session */
    return ok ? 0 : -EAGAIN;
}
