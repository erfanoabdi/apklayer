/* Input method (zwp_text_input_v3): preedit/commit text from the compositor's IME to the focused app. */
#include "shell.h"
#include "text-input-v3-client-protocol.h"

struct zwp_text_input_manager_v3 *text_mgr;

static struct zwp_text_input_v3 *text_input;
static struct window *entered_window;
static struct window *ime_window;           /* window whose field has the IME enabled */
static char *pending_commit, *pending_preedit;
static int pending_before, pending_after;
static uint32_t ti_serial;                  /* number of commit requests we made */

static void apply_enabled(void);
static void ti_enter(void *d, struct zwp_text_input_v3 *t, struct wl_surface *s)
{
    (void)d; (void)t;
    entered_window = window_for_surface(s);
    apply_enabled();
}
static void ti_leave(void *d, struct zwp_text_input_v3 *t, struct wl_surface *s)
{
    (void)d; (void)t; (void)s;
    entered_window = ime_window = NULL;
}
static void ti_preedit(void *d, struct zwp_text_input_v3 *t, const char *text, int32_t b, int32_t e)
{
    (void)d; (void)t; (void)b; (void)e;
    free(pending_preedit);
    pending_preedit = text ? strdup(text) : NULL;
}
static void ti_commit(void *d, struct zwp_text_input_v3 *t, const char *text)
{
    (void)d; (void)t;
    free(pending_commit);
    pending_commit = text ? strdup(text) : NULL;
}
static void ti_delete(void *d, struct zwp_text_input_v3 *t, uint32_t before, uint32_t after)
{ (void)d; (void)t; pending_before = (int)before; pending_after = (int)after; }

/* All pending changes arrive together: forward them as one IME message. Payload:
 * {i32 id, i32 delete_before, i32 delete_after} utf8 commit "\0" utf8 preedit */
static void ti_done(void *d, struct zwp_text_input_v3 *t, uint32_t serial)
{
    (void)d; (void)t; (void)serial;
    if (ime_window) {
        const char *c = pending_commit ? pending_commit : "", *p = pending_preedit ? pending_preedit : "";
        size_t cl = strlen(c), pl = strlen(p), n = 12 + cl + 1 + pl;
        if (n < 60000) {
            char *buf = malloc(n);
            int32_t head[3] = { W_ID(ime_window), pending_before, pending_after };
            memcpy(buf, head, 12);
            memcpy(buf + 12, c, cl + 1);
            memcpy(buf + 12 + cl + 1, p, pl);
            send_bytes(W_TARGET(ime_window), MSG_IME, buf, n);
            free(buf);
        }
    }
    free(pending_commit); free(pending_preedit);
    pending_commit = pending_preedit = NULL;
    pending_before = pending_after = 0;
}
static const struct zwp_text_input_v3_listener ti_listener = {
    .enter = ti_enter, .leave = ti_leave, .preedit_string = ti_preedit, .commit_string = ti_commit,
    .delete_surrounding_text = ti_delete, .done = ti_done };

static void apply_enabled(void)
{
    if (!text_input) return;
    int enable = entered_window && entered_window->text_input_enabled;
    ime_window = enable ? entered_window : NULL;
    if (enable) {
        zwp_text_input_v3_enable(text_input);
        zwp_text_input_v3_set_content_type(text_input, ZWP_TEXT_INPUT_V3_CONTENT_HINT_NONE,
                                           ZWP_TEXT_INPUT_V3_CONTENT_PURPOSE_NORMAL);
    } else zwp_text_input_v3_disable(text_input);
    zwp_text_input_v3_commit(text_input);
    ti_serial++;
    if (debug_input) fprintf(stderr, "[shell] text input %s\n", enable ? "enabled" : "disabled");
}

/* Track fields per window: an inactive JVM must not replace the focused IME. */
void text_input_enable(struct window *w, int enable)
{
    w->text_input_enabled = enable;
    if (!text_mgr || !seat) return;
    if (!text_input) {
        text_input = zwp_text_input_manager_v3_get_text_input(text_mgr, seat);
        zwp_text_input_v3_add_listener(text_input, &ti_listener, NULL);
    }
    if (entered_window == w) apply_enabled();
}

void text_input_forget_window(struct window *w)
{
    w->text_input_enabled = 0;
    if (entered_window == w) {
        apply_enabled();
        entered_window = ime_window = NULL;
    }
}
