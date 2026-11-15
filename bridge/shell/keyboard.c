/* Keyboard input: xkb translation to Android key events, key repeat. */
#include "shell.h"
#include <xkbcommon/xkbcommon-compose.h>
#include <ctype.h>

static struct xkb_context *xkb_ctx;
static struct xkb_compose_state *compose;  /* dead keys and the Compose key (the locale's Compose table) */
static struct xkb_keymap *us_map;          /* the physical layout Android's keycodes name (Generic.kl): letters and digits by position under any layout */
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

/* The Android keycode of the key at this evdev position as a US keyboard has it, for the keys whose character under the layout in use
 * (Cyrillic, Arabic, Greek...) has none: shortcuts (Ctrl+С is Ctrl+C) and games read the position, as on Android. 0: no such key. */
static int32_t physical_code(xkb_keycode_t kc)
{
    if (!us_map && xkb_ctx) us_map = xkb_keymap_new_from_names(xkb_ctx, &(struct xkb_rule_names){ .layout = "us" }, XKB_KEYMAP_COMPILE_NO_FLAGS);
    const xkb_keysym_t *syms;
    int32_t uni;
    if (!us_map || xkb_keymap_key_get_syms_by_level(us_map, kc, 0, 0, &syms) < 1) return 0;
    return keysym_to_android(syms[0], &uni);
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

/* 1 when the compositor gave no usable keymap: the default keymap's state then follows the keys themselves
 * (such a compositor's modifiers event carries no xkb masks). */
static int kb_own_state;

/* ---- the layout in use as a language tag (MSG_LAYOUT: the app's input method subtype) ---- */
/* xkb names layouts by country; the language's tag where it differs. */
static const char *const lang_of[][2] = { {"ara","ar"}, {"ir","fa"}, {"il","he"}, {"gr","el"}, {"jp","ja"}, {"cn","zh"}, {"kr","ko"}, {"ua","uk"},
    {"cz","cs"}, {"dk","da"}, {"se","sv"}, {"gb","en"}, {"us","en"}, {"in","hi"}, {"ge","ka"}, {"am","hy"}, {"by","be"}, {"kz","kk"}, {"tw","zh-TW"} };
/* The keymap's symbols name is "pc_us_ru_2_inet(evdev)_group(alt_shift_toggle)" (libxkbcommon writes "+" and ":" as "_"): the layouts are the pieces
 * between "pc" and "inet", the group count following the second of a name ("ru:2"); a variant's "(...)" is dropped. */
static int layout_tag(struct xkb_keymap *km, unsigned group, char *out, size_t n)
{
    char *str = km ? xkb_keymap_get_as_string(km, XKB_KEYMAP_FORMAT_TEXT_V1) : NULL;
    char *sym = str ? strstr(str, "xkb_symbols \"") : NULL, *end;
    if (!sym || !(end = strchr(sym += 13, '"'))) { free(str); return 0; }
    *end = 0;
    int depth = 0, ok = 0, inside = 0;
    unsigned idx = 0;
    char *o = sym;
    for (char *in = sym; ; in++) {  /* without the (variants and options) */
        if (*in == '(') depth++;
        if (!depth && (*o++ = *in) == 0) break;
        if (*in == ')') depth--;
    }
    for (char *piece = strtok(sym, "+_:"); piece && !ok; piece = strtok(NULL, "+_:")) {
        if (!strcmp(piece, "inet")) break;
        if (!strcmp(piece, "pc")) { inside = 1; continue; }
        if (!inside || !isalpha((unsigned char)*piece) || idx++ != group) continue;
        snprintf(out, n, "%s", piece);
        for (size_t i = 0; i < sizeof lang_of / sizeof *lang_of; i++) if (!strcmp(piece, lang_of[i][0])) snprintf(out, n, "%s", lang_of[i][1]);
        ok = 1;
    }
    free(str);
    return ok;
}
static char layout_sent[16];
/* Tells the focused app the language of the layout now active, when it changed (force: the window just got the focus). */
static void layout_report(int force)
{
    char tag[16];
    if (!keyboard_focus || !xkb_state || !layout_tag(xkb_keymap_g, xkb_state_serialize_layout(xkb_state, XKB_STATE_LAYOUT_EFFECTIVE), tag, sizeof tag)) return;
    if (!force && !strcmp(tag, layout_sent)) return;
    snprintf(layout_sent, sizeof layout_sent, "%s", tag);
    char msg[4 + sizeof tag];
    int32_t id = W_ID(keyboard_focus);
    memcpy(msg, &id, 4);
    memcpy(msg + 4, tag, strlen(tag));
    send_bytes(W_TARGET(keyboard_focus), MSG_LAYOUT, msg, 4 + strlen(tag));
    if (debug_input) fprintf(stderr, "[shell] layout %s\n", tag);
}

static void kb_keymap(void *d, struct wl_keyboard *k, uint32_t fmt, int32_t fd, uint32_t sz)
{
    (void)d; (void)k;
    if (!xkb_ctx) xkb_ctx = xkb_context_new(XKB_CONTEXT_NO_FLAGS);
    struct xkb_keymap *km = NULL;
    /* wl_seat v7+ maps the fd MAP_PRIVATE (a compositor may hand a sealed or read-only memfd whose read offset is not 0) */
    char *map = fmt == WL_KEYBOARD_KEYMAP_FORMAT_XKB_V1 && sz > 0
        ? mmap(NULL, sz, PROT_READ, MAP_PRIVATE, fd, 0) : MAP_FAILED;
    if (map != MAP_FAILED) {
        if (xkb_ctx) km = xkb_keymap_new_from_buffer(xkb_ctx, map, strnlen(map, sz),
                                                     XKB_KEYMAP_FORMAT_TEXT_V1, XKB_KEYMAP_COMPILE_NO_FLAGS);
        munmap(map, sz);
    }
    close(fd);
    kb_own_state = !km;
    /* no/empty keymap (Lipstick's Qt 5 compositor sends NO_KEYMAP): the default RMLVO keymap, as Qt clients do */
    if (!km && xkb_ctx) km = xkb_keymap_new_from_names(xkb_ctx, NULL, XKB_KEYMAP_COMPILE_NO_FLAGS);
    if (xkb_ctx && !compose) {
        const char *loc = getenv("LC_ALL");
        if (!loc || !*loc) loc = getenv("LC_CTYPE");
        if (!loc || !*loc) loc = getenv("LANG");
        if (!loc || !*loc || !strcmp(loc, "C") || !strcmp(loc, "POSIX")) loc = "en_US.UTF-8";
        struct xkb_compose_table *table = xkb_compose_table_new_from_locale(xkb_ctx, loc, XKB_COMPOSE_COMPILE_NO_FLAGS);
        if (table) { compose = xkb_compose_state_new(table, XKB_COMPOSE_STATE_NO_FLAGS); xkb_compose_table_unref(table); }
    }
    fprintf(stderr, "[shell] xkb keymap loaded (format %u, %u bytes, %s)\n", fmt, sz,
            !km ? "FAILED" : kb_own_state ? "default" : "ok");
    if (!km) return; /* keep the previous keymap */
    if (xkb_state) xkb_state_unref(xkb_state);
    if (xkb_keymap_g) xkb_keymap_unref(xkb_keymap_g);
    xkb_keymap_g = km;
    xkb_state = xkb_state_new(km);
}
static void kb_enter(void *d, struct wl_keyboard *k, uint32_t s, struct wl_surface *sf,
                     struct wl_array *keys)
{
    (void)d; (void)k; (void)s; (void)keys;
    keyboard_focus = window_for_surface(sf);
    layout_report(1);
    clipboard_push(keyboard_focus); /* the app sees what was copied elsewhere while it had no focus */
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
    uni = (int32_t)xkb_state_key_get_utf32(xkb_state, kc); /* state-aware: shift, AltGr */
    if (down && compose) {                                 /* dead key + letter, Compose sequences: a key that starts one is not text yet */
        xkb_compose_state_feed(compose, sym);
        enum xkb_compose_status st = xkb_compose_state_get_status(compose);
        if (st == XKB_COMPOSE_COMPOSING) return;
        if (st == XKB_COMPOSE_CANCELLED) { xkb_compose_state_reset(compose); return; }
        if (st == XKB_COMPOSE_COMPOSED) {
            uni = (int32_t)xkb_keysym_to_utf32(xkb_compose_state_get_one_sym(compose));
            code = 0;
            xkb_compose_state_reset(compose);
        }
    }
    if (uni < 32 || uni == 127) uni = 0;                   /* control chars are not text */
    if (!code && uni >= 128) code = physical_code(kc);     /* the host types the character unless a shortcut modifier is down (HostInput.hostKey) */
    int32_t v[5] = { W_ID(keyboard_focus), down ? 0 : 1, code, uni, (int32_t)meta };
    send_msg(W_TARGET(keyboard_focus), MSG_KEY, v, 5);
}
static void kb_key(void *d, struct wl_keyboard *k, uint32_t s, uint32_t t, uint32_t key, uint32_t st)
{
    (void)d; (void)k; (void)t;
    last_input_serial = s;
    int down = st == WL_KEYBOARD_KEY_STATE_PRESSED;
    if (debug_input) fprintf(stderr, "[shell] key %u down=%d focus=%d\n", key, down, keyboard_focus != NULL);
    if (key == 87 /* KEY_F11 */ && keyboard_focus) {  /* the desktop's fullscreen key, as browsers take it: the window, not the app */
        if (down) window_set_fullscreen(keyboard_focus, !keyboard_focus->want_fullscreen);
        return;
    }
    key_event(key, down);
    if (kb_own_state && xkb_state && k) xkb_state_update_key(xkb_state, key + 8, down ? XKB_KEY_DOWN : XKB_KEY_UP);
    layout_report(0);
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
    if (xkb_state && !kb_own_state) { xkb_state_update_mask(xkb_state, dm, lm, km, 0, 0, group); layout_report(0); }
}
static void kb_repeat_info(void *d, struct wl_keyboard *k, int32_t rate, int32_t delay)
{ (void)d; (void)k; repeat_rate = rate; repeat_delay = delay; }
const struct wl_keyboard_listener kb_listener = {
    .keymap = kb_keymap, .enter = kb_enter, .leave = kb_leave, .key = kb_key,
    .modifiers = kb_mods, .repeat_info = kb_repeat_info };

/* Test input: a keyboard enter on w, and a key with the modifier state xkb derives from the injected keys themselves (the compositor's
 * modifiers event is what a real seat sends after a modifier key). */
void keyboard_inject_focus(struct window *w) { kb_enter(NULL, NULL, 0, w->surface, NULL); }
void keyboard_inject_leave(struct window *w) { kb_leave(NULL, NULL, 0, w->surface); }
void keyboard_inject_key(struct window *w, uint32_t evdev, int down)
{
    keyboard_focus = w;
    if (xkb_state) xkb_state_update_key(xkb_state, evdev + 8, down ? XKB_KEY_DOWN : XKB_KEY_UP);
    kb_key(NULL, NULL, last_input_serial, 0, evdev, down ? WL_KEYBOARD_KEY_STATE_PRESSED : WL_KEYBOARD_KEY_STATE_RELEASED);
    layout_report(0);
}

/* Test input: the keymap the injected keys go through, "layout[,layout2...][:variant[:options]]" (xkb names, as setxkbmap), e.g. "us,ru:,:grp:caps_toggle". */
int keyboard_inject_layout(const char *spec)
{
    if (!xkb_ctx) xkb_ctx = xkb_context_new(XKB_CONTEXT_NO_FLAGS);
    char *copy = strdup(spec), *variant = strchr(copy, ':'), *options = NULL;
    if (variant) { *variant++ = 0; options = strchr(variant, ':'); if (options) *options++ = 0; }
    struct xkb_rule_names names = { .layout = copy, .variant = variant, .options = options };
    struct xkb_keymap *km = xkb_ctx ? xkb_keymap_new_from_names(xkb_ctx, &names, XKB_KEYMAP_COMPILE_NO_FLAGS) : NULL;
    free(copy);
    if (!km) return -3;
    if (xkb_state) xkb_state_unref(xkb_state);
    if (xkb_keymap_g) xkb_keymap_unref(xkb_keymap_g);
    xkb_keymap_g = km;
    xkb_state = xkb_state_new(km);
    layout_report(1);
    kb_own_state = 1;  /* the injected keys alone move this state (the seat's modifiers events would reset it) */
    return 0;
}
