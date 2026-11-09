/* Input method (zwp_text_input_v3): preedit/commit text from the compositor's IME to the focused app. Compositors without it (Lipstick,
 * Lomiri) show their on-screen keyboard through Maliit's D-Bus (maliit.c), which delivers through text_input_send. */
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

/* One IME message to w. Payload: {i32 id, i32 delete_before, i32 delete_after} utf8 commit "\0" utf8 preedit */
void text_input_send(struct window *w, int before, int after, const char *commit, const char *preedit)
{
    const char *c = commit ? commit : "", *p = preedit ? preedit : "";
    size_t cl = strlen(c), pl = strlen(p), n = 12 + cl + 1 + pl;
    if (n >= 60000) return;
    char *buf = malloc(n);
    int32_t head[3] = { W_ID(w), before, after };
    memcpy(buf, head, 12);
    memcpy(buf + 12, c, cl + 1);
    memcpy(buf + 12 + cl + 1, p, pl);
    send_bytes(W_TARGET(w), MSG_IME, buf, n);
    free(buf);
}

/* All pending changes arrive together: forward them as one IME message. */
static void ti_done(void *d, struct zwp_text_input_v3 *t, uint32_t serial)
{
    (void)d; (void)t; (void)serial;
    if (ime_window) text_input_send(ime_window, pending_before, pending_after, pending_commit, pending_preedit);
    free(pending_commit); free(pending_preedit);
    pending_commit = pending_preedit = NULL;
    pending_before = pending_after = 0;
}
static const struct zwp_text_input_v3_listener ti_listener = {
    .enter = ti_enter, .leave = ti_leave, .preedit_string = ti_preedit, .commit_string = ti_commit,
    .delete_surrounding_text = ti_delete, .done = ti_done };

/* Android's EditorInfo.inputType (class, variation, flags: android.text.InputType) as text-input-v3's content hint and purpose. */
void text_input_kind(const struct window *w, uint32_t *hint, uint32_t *purpose)
{
    int t = w->input_type, cls = t & 0xf, var = t & 0xff0;
    int password = cls == 1 && (var == 0x80 || var == 0x90 || var == 0xe0);  /* TYPE_TEXT_VARIATION_(VISIBLE_,WEB_)PASSWORD */
    *hint = 0;
    *purpose = ZWP_TEXT_INPUT_V3_CONTENT_PURPOSE_NORMAL;
    if (cls == 0) *purpose = ZWP_TEXT_INPUT_V3_CONTENT_PURPOSE_TERMINAL;  /* TYPE_NULL: a raw key editor */
    else if (cls == 2) *purpose = var == 0x10 ? ZWP_TEXT_INPUT_V3_CONTENT_PURPOSE_PIN : t & 0x3000 ? ZWP_TEXT_INPUT_V3_CONTENT_PURPOSE_NUMBER
                                                                                        : ZWP_TEXT_INPUT_V3_CONTENT_PURPOSE_DIGITS;
    else if (cls == 3) *purpose = ZWP_TEXT_INPUT_V3_CONTENT_PURPOSE_PHONE;
    else if (cls == 4) *purpose = var == 0x10 ? ZWP_TEXT_INPUT_V3_CONTENT_PURPOSE_DATE : var == 0x20 ? ZWP_TEXT_INPUT_V3_CONTENT_PURPOSE_TIME
                                                                                       : ZWP_TEXT_INPUT_V3_CONTENT_PURPOSE_DATETIME;
    else if (password) {
        *purpose = ZWP_TEXT_INPUT_V3_CONTENT_PURPOSE_PASSWORD;
        *hint = ZWP_TEXT_INPUT_V3_CONTENT_HINT_SENSITIVE_DATA | (var == 0x90 ? 0 : ZWP_TEXT_INPUT_V3_CONTENT_HINT_HIDDEN_TEXT);
    } else {
        if (var == 0x20 || var == 0xd0) *purpose = ZWP_TEXT_INPUT_V3_CONTENT_PURPOSE_EMAIL;
        else if (var == 0x10) *purpose = ZWP_TEXT_INPUT_V3_CONTENT_PURPOSE_URL;
        else if (var == 0x60) *purpose = ZWP_TEXT_INPUT_V3_CONTENT_PURPOSE_NAME;
        if (!(t & 0x80000)) *hint |= ZWP_TEXT_INPUT_V3_CONTENT_HINT_COMPLETION | ZWP_TEXT_INPUT_V3_CONTENT_HINT_SPELLCHECK;  /* no NO_SUGGESTIONS */
        if (t & 0x1000) *hint |= ZWP_TEXT_INPUT_V3_CONTENT_HINT_UPPERCASE;  /* CAP_CHARACTERS */
        else if (t & 0x2000) *hint |= ZWP_TEXT_INPUT_V3_CONTENT_HINT_TITLECASE;  /* CAP_WORDS */
        else if (t & 0x4000) *hint |= ZWP_TEXT_INPUT_V3_CONTENT_HINT_AUTO_CAPITALIZATION;  /* CAP_SENTENCES */
        if (t & 0x20000) *hint |= ZWP_TEXT_INPUT_V3_CONTENT_HINT_MULTILINE;
    }
}

static void apply_enabled(void)
{
    if (!text_input) return;
    int enable = entered_window && entered_window->text_input_enabled;
    ime_window = enable ? entered_window : NULL;
    uint32_t hint = 0, purpose = 0;
    if (enable) {
        text_input_kind(entered_window, &hint, &purpose);
        zwp_text_input_v3_enable(text_input);
        zwp_text_input_v3_set_content_type(text_input, hint, purpose);
    } else zwp_text_input_v3_disable(text_input);
    zwp_text_input_v3_commit(text_input);
    ti_serial++;
    if (debug_input) fprintf(stderr, "[shell] text input %s (hint 0x%x, purpose %u)\n", enable ? "enabled" : "disabled", hint, purpose);
}

/* Track fields per window: an inactive JVM must not replace the focused IME. */
void text_input_enable(struct window *w, int enable)
{
    if (enable == 2) {  /* showSoftInput: a tap in the focused field brings back a keyboard the user closed
                         * shortcut: text-input-v3 has no show request (compositors show theirs on enable); re-enable if a touch desktop needs it */
        if (debug_input) fprintf(stderr, "[shell] text input show request%s\n", w->text_input_enabled ? "" : " without a field");
        if (!text_mgr && w->text_input_enabled) maliit_enable(w, 2);
        return;
    }
    w->text_input_enabled = enable;
    if (!text_mgr) { maliit_enable(w, enable); return; }
    if (!seat) return;
    if (!text_input) {
        text_input = zwp_text_input_manager_v3_get_text_input(text_mgr, seat);
        zwp_text_input_v3_add_listener(text_input, &ti_listener, NULL);
    }
    if (entered_window == w) apply_enabled();
}

void text_input_forget_window(struct window *w)
{
    w->text_input_enabled = 0;
    if (!text_mgr) maliit_enable(w, 0);
    if (entered_window == w) {
        apply_enabled();
        entered_window = ime_window = NULL;
    }
}

/* Test input: what an input method sends to w's enabled field (commit, preedit, or 2: delete text chars before the cursor, then done),
 * through the text-input-v3 handlers. */
int text_input_inject(struct window *w, const char *text, int preedit)
{
    if (!w->text_input_enabled) return -2;
    struct window *was = ime_window;   /* the real IME's window stays what it was */
    ime_window = w;
    if (preedit == 2) ti_delete(NULL, NULL, (uint32_t)atoi(text), 0);
    else if (preedit) ti_preedit(NULL, NULL, text, 0, 0);
    else ti_commit(NULL, NULL, text);
    ti_done(NULL, NULL, ti_serial);
    ime_window = was;
    return 0;
}
