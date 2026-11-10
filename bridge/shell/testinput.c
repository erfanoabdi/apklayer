/* Test input (tests/lib/instance.py inject): a test drives one window of this shell without the shared desktop, so tests that need real
 * input run in parallel. The events enter the same handlers the Wayland seat's and the compositor's events reach (keyboard.c, pointer.c,
 * touch.c, textinput.c, window.c); everything after the seat is what real input exercises.
 * Only with APKLAYER_TEST_INPUT=1 in the shell's environment: otherwise any app of the session could type into another app's window. */
#include "shell.h"

/* The window an app knows as its own: by the package of the app that gets its input (the display HAL's window: the app attached to it),
 * or by its title. */
static struct window *find(const char *name)
{
    for (int i = 0; i < MAX_WINDOWS; i++) {
        struct window *w = &windows[i];
        if (w->client && w->configured && (!strcmp(W_TARGET(w)->pkg, name) || !strcmp(w->title, name))) return w;
    }
    return NULL;
}

/* v: {kind, a, b, c}; text: the step's text (TEXT). Result: 0 done, -2 not possible now. */
static int32_t inject(struct window *w, const int32_t *v, const char *text)
{
    switch (v[0]) {
    case TEST_FOCUS:   /* what the compositor does on a click: activate it, deactivate the others, keyboard enter; a = 1: focus leaves it */
        if (v[1]) { w->test_focus = 1; window_inject_configure(w, 0, 0, 0); keyboard_inject_leave(w); return 0; }
        for (int i = 0; i < MAX_WINDOWS; i++)
            if (windows[i].client && windows[i].configured) { windows[i].test_focus = 1; window_inject_configure(&windows[i], 0, 0, &windows[i] == w); }
        keyboard_inject_focus(w);
        return 0;
    case TEST_KEY: keyboard_inject_key(w, (uint32_t)v[1], v[2]); return 0;
    case TEST_POINTER: pointer_inject(w, v[1], v[2], v[3]); return 0;
    case TEST_TOUCH: touch_inject(w, v[1], v[2], v[3]); return 0;
    case TEST_TEXT: return text_input_inject(w, text, v[1]);
    case TEST_RESIZE: window_inject_configure(w, v[1], v[2], w->activated); return 0;
    case TEST_SCALE: if (v[1] < 100 || v[1] > 400) return -3; window_scale_changed(w, v[1]); return 0;
    case TEST_MAXIMIZE: window_inject_maximize(w, v[1], v[2], v[3]); return 0;
    case TEST_IME: if (v[1] < 0) return -3; w->ime_px = v[1]; window_send_configure(w); return 0;  /* as maliit.c's set_area */
    case TEST_CLOSE: send_msg(W_TARGET(w), MSG_CLOSE, (int32_t[]){ W_ID(w) }, 1); return 0;
    default: return -3;
    }
}

/* TEST_INPUT {i32 kind, a, b, c} utf8 window "\0" utf8 text; answered with TEST_DONE {i32 result}: 0, -1 no such window, -2 not possible
 * (IME text for a window without an enabled field), -3 refused (no APKLAYER_TEST_INPUT=1) or malformed. */
void test_input(struct client *c, const uint8_t *p, uint32_t len)
{
    static int allowed = -1;
    if (allowed < 0) {
        const char *e = getenv("APKLAYER_TEST_INPUT");
        allowed = e && !strcmp(e, "1");
    }
    int32_t result = -3;
    if (allowed && len > 16 && len - 16 < 4096) {
        int32_t v[4];
        char text[4096];
        size_t n = len - 16;
        memcpy(v, p, 16);
        memcpy(text, p + 16, n);
        text[n] = 0;
        size_t name_len = strlen(text);
        struct window *w = find(text);
        result = w ? inject(w, v, name_len < n ? text + name_len + 1 : "") : -1;
        if (debug_input) fprintf(stderr, "[shell] test input %d (%d,%d,%d) to \"%s\": %d\n", v[0], v[1], v[2], v[3], text, result);
    }
    send_msg(c, MSG_TEST_DONE, &result, 1);
}
