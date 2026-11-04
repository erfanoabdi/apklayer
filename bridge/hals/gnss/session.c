/* The D-Bus GNSS backends' shared part (session.h). */
#include "session.h"

#include <errno.h>
#include <pthread.h>
#include <stdio.h>

#define IDLE_STOP_US (10 * G_USEC_PER_SEC)  /* no next_fix for this long: the front stopped, the session ends */

static GBusType bus_type;
static const char *backend;
static GMainContext *context;
static GDBusConnection *session;  /* the running session's connection, NULL while stopped (the backend's thread only) */
static pthread_mutex_t lock = PTHREAD_MUTEX_INITIALIZER;
static struct apklayer_gnss_fix latest;
static int have_fix;
static gint64 last_asked;  /* monotonic µs of the last next_fix */

void gnss_session_report(const struct apklayer_gnss_fix *fix)
{
    pthread_mutex_lock(&lock);
    latest = *fix;
    have_fix = 1;
    pthread_mutex_unlock(&lock);
}

static void stop(void)
{
    g_dbus_connection_close_sync(session, NULL, NULL);
    g_clear_object(&session);
    pthread_mutex_lock(&lock);
    have_fix = 0;  /* a new session starts without a stale fix */
    pthread_mutex_unlock(&lock);
    fprintf(stderr, "gnss %s: session stopped\n", backend);
}

static gboolean start(gpointer data)
{
    if (session) return G_SOURCE_REMOVE;
    GError *error = NULL;
    char *address = g_dbus_address_get_for_bus_sync(bus_type, NULL, &error);
    if (address)
        session = g_dbus_connection_new_for_address_sync(address, G_DBUS_CONNECTION_FLAGS_AUTHENTICATION_CLIENT | G_DBUS_CONNECTION_FLAGS_MESSAGE_BUS_CONNECTION,
                                                         NULL, NULL, &error);
    g_free(address);
    if (!session) { fprintf(stderr, "gnss %s: %s\n", backend, error->message); g_error_free(error); return G_SOURCE_REMOVE; }
    if (gnss_source_start(session)) { stop(); return G_SOURCE_REMOVE; }  /* asked again after the next pause */
    fprintf(stderr, "gnss %s: session started\n", backend);
    return G_SOURCE_REMOVE;
}

static gboolean idle_check(gpointer data)
{
    pthread_mutex_lock(&lock);
    gint64 asked = last_asked;
    pthread_mutex_unlock(&lock);
    if (session && g_get_monotonic_time() - asked > IDLE_STOP_US) stop();
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

int gnss_session_init(GBusType bus, const char *name)
{
    bus_type = bus;
    backend = name;
    context = g_main_context_new();
    pthread_t thread;
    return pthread_create(&thread, NULL, loop, NULL) ? -EAGAIN : 0;
}

int gnss_session_ping(GBusType bus, const char *name, const char *path)
{
    GDBusConnection *connection = g_bus_get_sync(bus, NULL, NULL);
    if (!connection) return 0;
    GVariant *pong = g_dbus_connection_call_sync(connection, name, path, "org.freedesktop.DBus.Peer", "Ping", NULL, NULL, G_DBUS_CALL_FLAGS_NONE, 3000, NULL, NULL);
    g_object_unref(connection);
    if (pong) g_variant_unref(pong);
    return pong != NULL;
}

int apklayer_gnss_next_fix(struct apklayer_gnss_fix *fix)
{
    pthread_mutex_lock(&lock);
    gint64 now = g_get_monotonic_time();
    int idle = now - last_asked > IDLE_STOP_US / 2;
    last_asked = now;
    int ok = have_fix;
    if (ok) *fix = latest;
    pthread_mutex_unlock(&lock);
    if (idle) g_main_context_invoke(context, start, NULL);  /* the first fix asked for after a pause: (re)start the session */
    return ok ? 0 : -EAGAIN;
}
