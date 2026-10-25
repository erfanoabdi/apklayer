/* Keyboard input: xkb translation to Android key events, key repeat. */
#include "shell.h"

static struct xkb_context *xkb_ctx;
static struct xkb_keymap *xkb_keymap_g;
static struct xkb_state *xkb_state;

/* ---- keyboard: xkb -> Android key events (P2) ---- */

/* keysym -> Android keycode; printable keys derive from the char (a-z=29.., 0-9=7..),
 * specials by table. KEYCODE_UNKNOWN (0) with a nonzero unicode still types. */
static int32_t keysym_to_android(xkb_keysym_t sym, int32_t *uni)
{
    uint32_t ch = xkb_keysym_to_utf32(sym);
    *uni = (int32_t)ch;
    if (ch >= 'a' && ch <= 'z') return 29 + (int32_t)(ch - 'a');
    if (ch >= 'A' && ch <= 'Z') return 29 + (int32_t)(ch - 'A');
    if (ch >= '0' && ch <= '9') return 7 + (int32_t)(ch - '0');
    switch (sym) {
    case XKB_KEY_Return: case XKB_KEY_KP_Enter: return 66;
    case XKB_KEY_BackSpace: return 67;
    case XKB_KEY_Tab: return 61;
    case XKB_KEY_space: return 62;
    case XKB_KEY_Escape: case XKB_KEY_XF86Back: return 4; /* Back */
    case XKB_KEY_Delete: return 112;
    case XKB_KEY_Home: return 3;
    case XKB_KEY_End: return 123;
    case XKB_KEY_Page_Up: return 92;
    case XKB_KEY_Page_Down: return 93;
    case XKB_KEY_Up: return 19;
    case XKB_KEY_Down: return 20;
    case XKB_KEY_Left: return 21;
    case XKB_KEY_Right: return 22;
    case XKB_KEY_Shift_L: return 59;
    case XKB_KEY_Shift_R: return 60;
    case XKB_KEY_Control_L: return 129;
    case XKB_KEY_Control_R: return 130;
    case XKB_KEY_Alt_L: return 57;
    case XKB_KEY_Alt_R: return 58;
    case XKB_KEY_Meta_L: case XKB_KEY_Super_L: return 117;
    case XKB_KEY_Meta_R: case XKB_KEY_Super_R: return 118;
    default: return 0;
    }
}

/* Android KeyEvent meta bits: SHIFT=1, ALT=2, CTRL=0x1000, META=0x10000. */
uint32_t kb_android_meta(void)
{
    if (!xkb_state) return 0;
    uint32_t m = 0;
    if (xkb_state_mod_name_is_active(xkb_state, XKB_MOD_NAME_SHIFT, XKB_STATE_MODS_EFFECTIVE))
        m |= 1;
    if (xkb_state_mod_name_is_active(xkb_state, XKB_MOD_NAME_ALT, XKB_STATE_MODS_EFFECTIVE))
        m |= 2;
    if (xkb_state_mod_name_is_active(xkb_state, XKB_MOD_NAME_CTRL, XKB_STATE_MODS_EFFECTIVE))
        m |= 0x1000;
    if (xkb_state_mod_name_is_active(xkb_state, XKB_MOD_NAME_LOGO, XKB_STATE_MODS_EFFECTIVE))
        m |= 0x10000;
    return m;
}

static void kb_keymap(void *d, struct wl_keyboard *k, uint32_t fmt, int32_t fd, uint32_t sz)
{
    (void)d; (void)k; (void)fmt;
    char *map = malloc(sz + 1);
    if (map) {
        ssize_t got = 0;
        while (got < (ssize_t)sz) {
            ssize_t r = read(fd, map + got, sz - (size_t)got);
            if (r <= 0) break;
            got += r;
        }
        map[got] = 0;
        if (!xkb_ctx) xkb_ctx = xkb_context_new(XKB_CONTEXT_NO_FLAGS);
        if (xkb_state) { xkb_state_unref(xkb_state); xkb_state = NULL; }
        if (xkb_keymap_g) xkb_keymap_unref(xkb_keymap_g);
        xkb_keymap_g = xkb_ctx ? xkb_keymap_new_from_string(xkb_ctx, map,
                            XKB_KEYMAP_FORMAT_TEXT_V1, XKB_KEYMAP_COMPILE_NO_FLAGS) : NULL;
        if (xkb_keymap_g) xkb_state = xkb_state_new(xkb_keymap_g);
        fprintf(stderr, "[shell] xkb keymap loaded (%zd bytes, %s)\n", got,
                xkb_keymap_g ? "ok" : "FAILED");
    }
    free(map);
    close(fd);
}
static void kb_enter(void *d, struct wl_keyboard *k, uint32_t s, struct wl_surface *sf,
                     struct wl_array *keys)
{
    (void)d; (void)k; (void)s; (void)keys;
    keyboard_focus = window_for_surface(sf);
    if (debug_input) fprintf(stderr, "[shell] keyboard enter: %s\n", keyboard_focus ? "window" : "?");
}
static void kb_leave(void *d, struct wl_keyboard *k, uint32_t s, struct wl_surface *sf)
{ (void)d; (void)k; (void)s; (void)sf; keyboard_focus = NULL; repeat_key = -1; }

long long now_ms(void)
{
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (long long)ts.tv_sec * 1000 + ts.tv_nsec / 1000000;
}

/* Sends one xkb-translated key event to the focused window. evdev = Linux key code. */
void key_event(uint32_t evdev, int down)
{
    if (!keyboard_focus) return;
    if (!xkb_state) { send_input(keyboard_focus, EV_KEY, (int32_t)evdev, down); return; }
    xkb_keycode_t kc = evdev + 8; /* evdev -> xkb keycode */
    xkb_keysym_t sym = xkb_state_key_get_one_sym(xkb_state, kc);
    int32_t uni = 0;
    int32_t code = keysym_to_android(sym, &uni);
    uint32_t meta = kb_android_meta();
    if (sym == XKB_KEY_Left && (meta & 0x02)) code = 4; /* Alt+Left = Back */
    uni = (int32_t)xkb_state_key_get_utf32(xkb_state, kc); /* state-aware: shift, AltGr, dead keys */
    if (uni < 32 || uni == 127) uni = 0;                   /* control chars are not text */
    int32_t v[5] = { keyboard_focus->id, down ? 0 : 1, code, uni, (int32_t)meta };
    send_msg(keyboard_focus->client, MSG_KEY, v, 5);
}
static void kb_key(void *d, struct wl_keyboard *k, uint32_t s, uint32_t t, uint32_t key, uint32_t st)
{
    (void)d; (void)k; (void)s; (void)t;
    int down = st == WL_KEYBOARD_KEY_STATE_PRESSED;
    if (debug_input) fprintf(stderr, "[shell] key %u down=%d focus=%d\n", key, down, keyboard_focus != NULL);
    key_event(key, down);
    if (down && xkb_keymap_g && repeat_rate > 0 && xkb_keymap_key_repeats(xkb_keymap_g, key + 8)) {
        repeat_key = (int)key;
        repeat_next_ms = now_ms() + repeat_delay;
    } else if (!down && repeat_key == (int)key) {
        repeat_key = -1;
    }
}
static void kb_mods(void *d, struct wl_keyboard *k, uint32_t s, uint32_t dm, uint32_t lm,
                    uint32_t km, uint32_t group)
{
    (void)d; (void)k; (void)s;
    if (xkb_state) xkb_state_update_mask(xkb_state, dm, lm, km, 0, 0, group);
}
static void kb_repeat_info(void *d, struct wl_keyboard *k, int32_t rate, int32_t delay)
{ (void)d; (void)k; repeat_rate = rate; repeat_delay = delay; }
const struct wl_keyboard_listener kb_listener = {
    .keymap = kb_keymap, .enter = kb_enter, .leave = kb_leave, .key = kb_key,
    .modifiers = kb_mods, .repeat_info = kb_repeat_info };

