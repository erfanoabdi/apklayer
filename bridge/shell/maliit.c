/* The on-screen keyboard of compositors without zwp_text_input_v3 (Sailfish OS's Lipstick, Ubuntu Touch's Lomiri): Maliit's own protocol, the
 * one Qt's Maliit input context speaks. The session bus names the server's peer-to-peer address (org.maliit.server); on that connection the
 * shell is an input context (com.meego.inputmethod.inputcontext1 at /com/meego/inputmethod/inputcontext) and calls the server
 * (com.meego.inputmethod.uiserver1): a focused field activates the context, sends the field's state and shows the keyboard. The keyboard's
 * commits, preedit and keys go to that window as IME and key messages; the area it covers becomes the window's bottom inset (geometry.c).
 * GDBus dispatches into the shell's own loop through a GMainContext of its own (maliit_pollfds / maliit_dispatch). */
#include "shell.h"
#include <gio/gio.h>

static GMainContext *ctx;
static GDBusConnection *conn;
static int tried;
static struct window *mwin;  /* the window whose field has the keyboard */
static GPollFD gfds[MALIIT_FDS];
static int gn;
static gint gprio;

/* What the server calls (maliit-framework's minputcontext1interface.xml, the methods it calls with an answer and those we act on). */
static const char introspection[] =
    "<node><interface name='com.meego.inputmethod.inputcontext1'>"
    "<method name='activationLostEvent'/><method name='imInitiatedHide'/>"
    "<method name='commitString'><arg type='s'/><arg type='i'/><arg type='i'/><arg type='i'/></method>"
    "<method name='updatePreedit'><arg type='s'/><arg type='a(iii)'/><arg type='i'/><arg type='i'/><arg type='i'/></method>"
    "<method name='keyEvent'><arg type='i'/><arg type='i'/><arg type='i'/><arg type='s'/><arg type='b'/><arg type='i'/><arg type='y'/></method>"
    "<method name='updateInputMethodArea'><arg type='i'/><arg type='i'/><arg type='i'/><arg type='i'/></method>"
    "<method name='setGlobalCorrectionEnabled'><arg type='b'/></method>"
    "<method name='preeditRectangle'><arg direction='out' type='b'/><arg direction='out' type='i'/><arg direction='out' type='i'/>"
    "<arg direction='out' type='i'/><arg direction='out' type='i'/></method>"
    "<method name='setRedirectKeys'><arg type='b'/></method><method name='setDetectableAutoRepeat'><arg type='b'/></method>"
    "<method name='setSelection'><arg type='i'/><arg type='i'/></method>"
    "<method name='selection'><arg direction='out' type='b'/><arg direction='out' type='s'/></method>"
    "<method name='setLanguage'><arg type='s'/></method>"
    "</interface></node>";

/* Qt key codes the keyboards send as key events -> Android key codes */
static int32_t qt_to_android(int32_t key)
{
    switch (key) {
    case 0x01000000: return 4;    /* Escape: Back */
    case 0x01000001: return 61;   /* Tab */
    case 0x01000003: return 67;   /* Backspace */
    case 0x01000004: case 0x01000005: return 66;  /* Return, Enter */
    case 0x01000007: return 112;  /* Delete */
    case 0x01000012: return 21;   /* Left */
    case 0x01000013: return 19;   /* Up */
    case 0x01000014: return 22;   /* Right */
    case 0x01000015: return 20;   /* Down */
    default: return 0;
    }
}

/* The keyboard covers h output pixels at the bottom: the window's inset in the app's pixels. */
static void set_area(struct window *w, int h)
{
    int px = h > 0 ? (int)((long)h * window_scale(w) / output_scale) : 0;
    if (w->ime_px == px) return;
    w->ime_px = px;
    if (w->configured) window_send_configure(w);
}

static void call(const char *method, GVariant *args)
{
    g_dbus_connection_call(conn, NULL, "/com/meego/inputmethod/uiserver1", "com.meego.inputmethod.uiserver1", method, args, NULL,
                           G_DBUS_CALL_FLAGS_NONE, -1, NULL, NULL, NULL);
}

static void method_call(GDBusConnection *c, const gchar *sender, const gchar *path, const gchar *iface, const gchar *method,
                        GVariant *p, GDBusMethodInvocation *inv, gpointer data)
{
    (void)c; (void)sender; (void)path; (void)iface; (void)data;
    if (debug_input) {
        gchar *args = g_variant_print(p, FALSE);
        fprintf(stderr, "[shell] maliit %s%s\n", method, args);
        g_free(args);
    }
    if (!strcmp(method, "preeditRectangle")) { g_dbus_method_invocation_return_value(inv, g_variant_new("(biiii)", FALSE, 0, 0, 0, 0)); return; }
    if (!strcmp(method, "selection")) { g_dbus_method_invocation_return_value(inv, g_variant_new("(bs)", FALSE, "")); return; }
    g_dbus_method_invocation_return_value(inv, NULL);
    if (!strcmp(method, "updateInputMethodArea")) {
        gint32 x, y, w, h;
        g_variant_get(p, "(iiii)", &x, &y, &w, &h);
        fprintf(stderr, "[shell] host keyboard area %dx%d at %d,%d\n", w, h, x, y);
        if (mwin) set_area(mwin, h);
        return;
    }
    if (!mwin) return;
    if (!strcmp(method, "commitString")) {
        const gchar *text;
        gint32 start, len, cursor;
        g_variant_get(p, "(&siii)", &text, &start, &len, &cursor);
        /* replace len characters from start (relative to the cursor): those before and after the cursor go */
        int before = len > 0 && start < 0 ? -start : 0, after = len > 0 && start + len > 0 ? start + len : 0;
        if (before > len) before = len;
        text_input_send(mwin, before, after, text, "");
    } else if (!strcmp(method, "updatePreedit")) {
        const gchar *text;
        g_variant_get(p, "(&sa(iii)iii)", &text, NULL, NULL, NULL, NULL);
        text_input_send(mwin, 0, 0, "", text);
    } else if (!strcmp(method, "keyEvent")) {
        gint32 type, key, mods, count;
        const gchar *text;
        gboolean rep;
        guchar req;
        g_variant_get(p, "(iii&sbiy)", &type, &key, &mods, &text, &rep, &count, &req);
        int32_t code = qt_to_android(key);
        /* Backspace and Delete go as IME deletions: in order with the preedit the keyboard sends next (Lomiri takes the word back into its
         * preedit after a Backspace), which a key event, passed through the app's input pipeline later, is not.
         * shortcut: a selection is not deleted by them (the keyboard knows no surrounding text); upgrade when the field's state is sent */
        if (code == 67 || code == 112) { if (type == 6) text_input_send(mwin, code == 67, code == 112, "", ""); }
        else if (code) send_msg(W_TARGET(mwin), MSG_KEY, (int32_t[]){ W_ID(mwin), type == 6 ? 0 : 1, code, 0, 0 }, 5);  /* QEvent::KeyPress */
        else if (type == 6 && *text) text_input_send(mwin, 0, 0, text, "");
    }
}
static const GDBusInterfaceVTable vtable = { .method_call = method_call };

static int connect_server(void)
{
    GError *err = NULL;
    GDBusConnection *bus = g_bus_get_sync(G_BUS_TYPE_SESSION, NULL, &err);
    GVariant *r = bus ? g_dbus_connection_call_sync(bus, "org.maliit.server", "/org/maliit/server/address", "org.freedesktop.DBus.Properties", "Get",
                                                    g_variant_new("(ss)", "org.maliit.Server.Address", "address"), G_VARIANT_TYPE("(v)"),
                                                    G_DBUS_CALL_FLAGS_NONE, 2000, NULL, &err) : NULL;
    if (bus) g_object_unref(bus);
    if (!r) {
        fprintf(stderr, "[shell] no text input protocol and no Maliit server (%s): no on-screen keyboard\n", err ? err->message : "?");
        g_clear_error(&err);
        return 0;
    }
    GVariant *v;
    g_variant_get(r, "(v)", &v);
    ctx = g_main_context_new();
    g_main_context_acquire(ctx);  /* this thread runs it (maliit_dispatch) */
    g_main_context_push_thread_default(ctx);  /* the connection's callbacks go to it */
    conn = g_dbus_connection_new_for_address_sync(g_variant_get_string(v, NULL), G_DBUS_CONNECTION_FLAGS_AUTHENTICATION_CLIENT, NULL, NULL, &err);
    GDBusNodeInfo *node = conn ? g_dbus_node_info_new_for_xml(introspection, NULL) : NULL;
    if (node) g_dbus_connection_register_object(conn, "/com/meego/inputmethod/inputcontext", node->interfaces[0], &vtable, NULL, NULL, &err);
    g_main_context_pop_thread_default(ctx);
    fprintf(stderr, "[shell] Maliit server %s: %s\n", g_variant_get_string(v, NULL), conn ? "connected" : err ? err->message : "?");
    if (node) g_dbus_node_info_unref(node);
    g_variant_unref(v);
    g_variant_unref(r);
    g_clear_error(&err);
    return conn != NULL;
}

void maliit_enable(struct window *w, int enable)
{
    if (!enable && mwin != w) return;
    if (!conn && (tried++ || !connect_server())) return;
    GVariantBuilder b;
    g_variant_builder_init(&b, G_VARIANT_TYPE("a{sv}"));
    g_variant_builder_add(&b, "{sv}", "focusState", g_variant_new_boolean(enable));
    if (enable) {
        mwin = w;
        g_variant_builder_add(&b, "{sv}", "contentType", g_variant_new_int32(0));  /* Maliit::FreeTextContentType */
        g_variant_builder_add(&b, "{sv}", "predictionEnabled", g_variant_new_boolean(TRUE));
        g_variant_builder_add(&b, "{sv}", "correctionEnabled", g_variant_new_boolean(TRUE));
        g_variant_builder_add(&b, "{sv}", "autocapitalizationEnabled", g_variant_new_boolean(TRUE));
        g_variant_builder_add(&b, "{sv}", "hiddenText", g_variant_new_boolean(FALSE));
        call("activateContext", NULL);
    }
    call("updateWidgetInformation", g_variant_new("(a{sv}b)", &b, TRUE));
    call(enable ? "showInputMethod" : "hideInputMethod", NULL);
    if (!enable) { set_area(w, 0); mwin = NULL; }
    if (debug_input) fprintf(stderr, "[shell] host keyboard %s\n", enable ? "shown" : "hidden");
}

/* The main loop's poll takes the context's descriptors (unused slots: -1) and hands back what happened. Returns the context's timeout (ms,
 * -1: none). */
int maliit_pollfds(struct pollfd *p)
{
    int timeout = -1;
    gn = 0;
    if (ctx) {
        g_main_context_prepare(ctx, &gprio);
        gn = g_main_context_query(ctx, gprio, &timeout, gfds, MALIIT_FDS);
        if (gn > MALIIT_FDS) gn = MALIIT_FDS;
    }
    for (int i = 0; i < MALIIT_FDS; i++) p[i] = (struct pollfd){ .fd = i < gn ? gfds[i].fd : -1, .events = i < gn ? gfds[i].events : 0 };
    return timeout;
}
void maliit_dispatch(struct pollfd *p)
{
    if (!ctx) return;
    for (int i = 0; i < gn; i++) gfds[i].revents = p[i].revents;
    if (g_main_context_check(ctx, gprio, gfds, gn)) g_main_context_dispatch(ctx);
}
