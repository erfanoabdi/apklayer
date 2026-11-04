/* The feedbackd vibrator (Phosh, Ubuntu Touch, postmarketOS: org.sigxcpu.Feedback on the session bus). feedbackd 0.5 and later have the
 * haptic interface (org.sigxcpu.Feedback.Haptic.Vibrate: a pattern of amplitude 0..1 and milliseconds): on is one step at the last amplitude,
 * off a 1 ms step at 0 (the newer pattern replaces the running one). Older feedbackd has only events: on triggers "phone-incoming-call" with
 * the "quiet" profile hint (haptic, no ringtone) and ends it (EndFeedback) after the duration or at off; the theme decides its strength.
 * Option: app_id=<id> (default apklayer), the application feedbackd attributes the feedback to. */
#include "apklayer/vibrator.h"

#include <errno.h>
#include <gio/gio.h>
#include <stdio.h>
#include <string.h>

#define BUS "org.sigxcpu.Feedback"
#define PATH "/org/sigxcpu/Feedback"

static GDBusConnection *bus;
static GMainContext *context;  /* the event fallback's end timers */
static GMutex lock;
static const char *app_id = "apklayer";
static int haptic;             /* the daemon has org.sigxcpu.Feedback.Haptic */
static float amplitude = 1.0f;
static guint32 event;          /* the running event (fallback), 0 none */

static int call(const char *iface, const char *method, GVariant *args, const char *reply, GVariant **out)
{
    GError *error = NULL;
    GVariant *r = g_dbus_connection_call_sync(bus, BUS, PATH, iface, method, args, reply ? G_VARIANT_TYPE(reply) : NULL, G_DBUS_CALL_FLAGS_NONE,
                                              2000, NULL, &error);
    if (!r) { fprintf(stderr, "vibrator feedbackd: %s: %s\n", method, error->message); g_error_free(error); return -EIO; }
    if (out) *out = r; else g_variant_unref(r);
    return 0;
}

static int vibrate(double level, guint32 ms)
{
    GVariantBuilder pattern;
    g_variant_builder_init(&pattern, G_VARIANT_TYPE("a(du)"));
    g_variant_builder_add(&pattern, "(du)", level, ms);
    return call(BUS ".Haptic", "Vibrate", g_variant_new("(sa(du))", app_id, &pattern), "(b)", NULL);
}

static void end_event(void)  /* with lock held */
{
    if (event) call(BUS, "EndFeedback", g_variant_new("(u)", event), NULL, NULL);
    event = 0;
}

static gboolean timer(gpointer id)
{
    g_mutex_lock(&lock);
    if (event == GPOINTER_TO_UINT(id)) end_event();
    g_mutex_unlock(&lock);
    return G_SOURCE_REMOVE;
}

static gpointer loop(gpointer data)
{
    (void)data;
    for (;;) g_main_context_iteration(context, TRUE);
    return NULL;
}

/* a name the bus has an owner for or can start */
static int on_bus(GDBusConnection *c, const char *name)
{
    GVariant *r = g_dbus_connection_call_sync(c, "org.freedesktop.DBus", "/org/freedesktop/DBus", "org.freedesktop.DBus", "ListActivatableNames",
                                              NULL, G_VARIANT_TYPE("(as)"), G_DBUS_CALL_FLAGS_NONE, 1000, NULL, NULL);
    int found = 0;
    if (r) {
        const char **names;
        g_variant_get(r, "(^a&s)", &names);
        for (int i = 0; names[i]; i++) found |= !strcmp(names[i], name);
        g_free(names);
        g_variant_unref(r);
    }
    r = g_dbus_connection_call_sync(c, "org.freedesktop.DBus", "/org/freedesktop/DBus", "org.freedesktop.DBus", "NameHasOwner",
                                    g_variant_new("(s)", name), G_VARIANT_TYPE("(b)"), G_DBUS_CALL_FLAGS_NONE, 1000, NULL, NULL);
    if (r) { gboolean owned; g_variant_get(r, "(b)", &owned); found |= owned; g_variant_unref(r); }
    return found;
}

int apklayer_vibrator_probe(void)
{
    GDBusConnection *c = g_bus_get_sync(G_BUS_TYPE_SESSION, NULL, NULL);
    if (!c) return -1;
    int found = on_bus(c, BUS);
    g_object_unref(c);
    return found ? 20 : -1;  /* the desktop's own haptics service: above raw force-feedback devices */
}

int apklayer_vibrator_init(int count, const char *const *options)
{
    for (int i = 0; i < count; i++) {
        if (!strncmp(options[i], "app_id=", 7) && options[i][7]) app_id = options[i] + 7;
        else { fprintf(stderr, "vibrator feedbackd: unknown option %s\n", options[i]); return -EINVAL; }
    }
    GError *error = NULL;
    if (!(bus = g_bus_get_sync(G_BUS_TYPE_SESSION, NULL, &error))) { fprintf(stderr, "vibrator feedbackd: %s\n", error->message); return -ENOTCONN; }
    GVariant *xml = NULL;  /* introspection also starts a D-Bus activated daemon */
    if (call("org.freedesktop.DBus.Introspectable", "Introspect", NULL, "(s)", &xml)) return -ENOENT;
    const char *text;
    g_variant_get(xml, "(&s)", &text);
    haptic = strstr(text, BUS ".Haptic") != NULL;
    g_variant_unref(xml);
    fprintf(stderr, "vibrator feedbackd: %s\n", haptic ? "haptic interface" : "no haptic interface: events");
    context = g_main_context_new();
    g_thread_new("feedbackd", loop, NULL);
    return 0;
}

int apklayer_vibrator_on(int32_t duration_ms)
{
    if (haptic) return vibrate(amplitude, duration_ms);
    g_mutex_lock(&lock);
    end_event();
    GVariantBuilder hints;
    g_variant_builder_init(&hints, G_VARIANT_TYPE("a{sv}"));
    g_variant_builder_add(&hints, "{sv}", "profile", g_variant_new_string("quiet"));
    GVariant *r = NULL;
    int result = call(BUS, "TriggerFeedback", g_variant_new("(ssa{sv}i)", app_id, "phone-incoming-call", &hints, (duration_ms + 999) / 1000), "(u)", &r);
    if (!result) {
        g_variant_get(r, "(u)", &event);
        g_variant_unref(r);
        GSource *end = g_timeout_source_new(duration_ms);
        g_source_set_callback(end, timer, GUINT_TO_POINTER(event), NULL);
        g_source_attach(end, context);
        g_source_unref(end);
    }
    g_mutex_unlock(&lock);
    return result;
}

int apklayer_vibrator_off(void)
{
    if (haptic) return vibrate(0, 1);
    g_mutex_lock(&lock);
    end_event();
    g_mutex_unlock(&lock);
    return 0;
}

int apklayer_vibrator_amplitude(float value)
{
    amplitude = value;
    return 0;
}
