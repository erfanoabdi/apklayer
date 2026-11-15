/* Stylus input (zwp_tablet_v2): pen and eraser tools to the app as MSG_TABLET, one message per tool frame (the host makes the MotionEvent:
 * HostInput.tablet). The compositor's frame event ends a group of events that belong together, as pointer frames do. */
#include "shell.h"
#include "tablet-v2-client-protocol.h"

static struct zwp_tablet_manager_v2 *tablet_mgr;
static struct zwp_tablet_seat_v2 *tablet_seat;

/* Android MotionEvent constants */
enum { A_DOWN = 0, A_UP = 1, A_MOVE = 2, A_HOVER_MOVE = 7, A_HOVER_ENTER = 9, A_HOVER_EXIT = 10 };
enum { T_FINGER = 1, T_STYLUS = 2, T_MOUSE = 3, T_ERASER = 4 };
enum { BTN_STYLUS = 0x14b, BTN_STYLUS2 = 0x14c };

struct tool {
    struct window *w;                 /* the window the tool is over (NULL: out of proximity, or over a bar) */
    int type, down;
    int f_in, f_out, f_down, f_up, dirty;   /* what happened since the last frame */
    double x, y, tilt_x, tilt_y, rot;       /* surface-local pixels; degrees */
    int pressure, distance, slider;         /* the protocol's 0..65535 (slider -65535..65535) */
    int wheel;                              /* tool wheel clicks since the last frame */
    int buttons;                            /* Android BUTTON_STYLUS_* bits */
    struct tool *next;
};
static struct tool *tools;                  /* the compositor's tools: a closed window is forgotten by all of them */

static void emit(struct tool *t, int action)
{
    struct window *w = t->w;
    int x = (int)t->x, y = (int)t->y;
    window_to_app(w, &x, &y);
    int32_t v[13] = { W_ID(w), action, x * w->scale / 100, y * w->scale / 100, t->type, t->buttons,
                      t->pressure * 10000 / 65535, t->distance * 10000 / 65535,
                      (int32_t)(t->tilt_x * 100), (int32_t)(t->tilt_y * 100), (int32_t)(t->rot * 100), t->slider * 10000 / 65535, t->wheel };
    if (debug_input) fprintf(stderr, "[shell] tablet action %d at %d,%d pressure %d tool %d\n", action, x, y, t->pressure, t->type);
    send_msg(W_TARGET(w), MSG_TABLET, v, 13);
}

static void tool_type(void *d, struct zwp_tablet_tool_v2 *tt, uint32_t type)
{
    (void)tt;
    struct tool *t = d;
    t->type = type == ZWP_TABLET_TOOL_V2_TYPE_ERASER ? T_ERASER : type == ZWP_TABLET_TOOL_V2_TYPE_FINGER ? T_FINGER
            : type == ZWP_TABLET_TOOL_V2_TYPE_MOUSE ? T_MOUSE : T_STYLUS;  /* pen, brush, pencil, airbrush, lens */
}
static void tool_serial(void *d, struct zwp_tablet_tool_v2 *tt, uint32_t hi, uint32_t lo) { (void)d; (void)tt; (void)hi; (void)lo; }
static void tool_capability(void *d, struct zwp_tablet_tool_v2 *tt, uint32_t c) { (void)d; (void)tt; (void)c; }
static void tool_done(void *d, struct zwp_tablet_tool_v2 *tt) { (void)d; (void)tt; }
static void tool_removed(void *d, struct zwp_tablet_tool_v2 *tt)
{
    for (struct tool **p = &tools; *p; p = &(*p)->next)
        if (*p == d) { *p = (*p)->next; break; }
    free(d);
    zwp_tablet_tool_v2_destroy(tt);
}
static void tool_in(void *d, struct zwp_tablet_tool_v2 *tt, uint32_t serial, struct zwp_tablet_v2 *tablet, struct wl_surface *sf)
{
    (void)tt; (void)tablet;
    struct tool *t = d;
    struct window *w = window_for_surface(sf);
    last_input_serial = serial;
    t->w = w && sf == w->surface ? w : NULL;  /* a title bar or navigation bar is the compositor's and the shell's */
    t->f_in = t->dirty = 1;
}
static void tool_out(void *d, struct zwp_tablet_tool_v2 *tt)
{
    (void)tt;
    struct tool *t = d;
    t->f_out = t->dirty = 1;
}
static void tool_down(void *d, struct zwp_tablet_tool_v2 *tt, uint32_t serial)
{
    (void)tt;
    struct tool *t = d;
    last_input_serial = serial;
    t->down = t->f_down = t->dirty = 1;
}
static void tool_up(void *d, struct zwp_tablet_tool_v2 *tt)
{
    (void)tt;
    struct tool *t = d;
    t->down = 0;
    t->f_up = t->dirty = 1;
}
static void tool_motion(void *d, struct zwp_tablet_tool_v2 *tt, wl_fixed_t x, wl_fixed_t y)
{
    (void)tt;
    struct tool *t = d;
    t->x = wl_fixed_to_double(x);
    t->y = wl_fixed_to_double(y);
    t->dirty = 1;
}
static void tool_pressure(void *d, struct zwp_tablet_tool_v2 *tt, uint32_t p) { (void)tt; ((struct tool *)d)->pressure = p; ((struct tool *)d)->dirty = 1; }
static void tool_distance(void *d, struct zwp_tablet_tool_v2 *tt, uint32_t p) { (void)tt; ((struct tool *)d)->distance = p; ((struct tool *)d)->dirty = 1; }
static void tool_tilt(void *d, struct zwp_tablet_tool_v2 *tt, wl_fixed_t x, wl_fixed_t y)
{
    (void)tt;
    struct tool *t = d;
    t->tilt_x = wl_fixed_to_double(x);
    t->tilt_y = wl_fixed_to_double(y);
    t->dirty = 1;
}
static void tool_rotation(void *d, struct zwp_tablet_tool_v2 *tt, wl_fixed_t r) { (void)tt; ((struct tool *)d)->rot = wl_fixed_to_double(r); ((struct tool *)d)->dirty = 1; }
static void tool_slider(void *d, struct zwp_tablet_tool_v2 *tt, int32_t p) { (void)tt; ((struct tool *)d)->slider = p; ((struct tool *)d)->dirty = 1; }
static void tool_wheel(void *d, struct zwp_tablet_tool_v2 *tt, int32_t deg, int32_t clicks) { (void)tt; (void)deg; ((struct tool *)d)->wheel += clicks; ((struct tool *)d)->dirty = 1; }
static void tool_button(void *d, struct zwp_tablet_tool_v2 *tt, uint32_t serial, uint32_t button, uint32_t state)
{
    (void)tt; (void)serial;
    struct tool *t = d;
    int bit = button == BTN_STYLUS ? 32 : button == BTN_STYLUS2 ? 64 : 0;  /* BUTTON_STYLUS_PRIMARY / _SECONDARY */
    if (state == ZWP_TABLET_TOOL_V2_BUTTON_STATE_PRESSED) t->buttons |= bit; else t->buttons &= ~bit;
    t->dirty = 1;
}
/* The end of a group of events: one MotionEvent. Down and up first; a proximity change when the tool only moved in or out. */
static void tool_frame(void *d, struct zwp_tablet_tool_v2 *tt, uint32_t time)
{
    (void)tt; (void)time;
    struct tool *t = d;
    if (t->dirty && t->w) {
        if (t->f_up) emit(t, A_UP);
        else if (t->f_down) emit(t, A_DOWN);
        else if (t->f_out) emit(t, t->down ? A_UP : A_HOVER_EXIT);
        else if (t->f_in && !t->down) emit(t, A_HOVER_ENTER);
        else emit(t, t->down ? A_MOVE : A_HOVER_MOVE);
        if (t->f_out && t->f_up) emit(t, A_HOVER_EXIT);
    }
    if (t->f_out) { t->w = NULL; t->down = t->buttons = 0; }
    t->f_in = t->f_out = t->f_down = t->f_up = t->dirty = t->wheel = 0;
}
static const struct zwp_tablet_tool_v2_listener tool_listener = {
    .type = tool_type, .hardware_serial = tool_serial, .hardware_id_wacom = tool_serial, .capability = tool_capability, .done = tool_done,
    .removed = tool_removed, .proximity_in = tool_in, .proximity_out = tool_out, .down = tool_down, .up = tool_up, .motion = tool_motion,
    .pressure = tool_pressure, .distance = tool_distance, .tilt = tool_tilt, .rotation = tool_rotation, .slider = tool_slider,
    .wheel = tool_wheel, .button = tool_button, .frame = tool_frame };

static void seat_tablet(void *d, struct zwp_tablet_seat_v2 *s, struct zwp_tablet_v2 *tablet) { (void)d; (void)s; zwp_tablet_v2_destroy(tablet); }
static void seat_pad(void *d, struct zwp_tablet_seat_v2 *s, struct zwp_tablet_pad_v2 *pad) { (void)d; (void)s; zwp_tablet_pad_v2_destroy(pad); }
static void seat_tool(void *d, struct zwp_tablet_seat_v2 *s, struct zwp_tablet_tool_v2 *tt)
{
    (void)d; (void)s;
    struct tool *t = calloc(1, sizeof *t);
    t->next = tools;
    tools = t;
    zwp_tablet_tool_v2_add_listener(tt, &tool_listener, t);
}
static const struct zwp_tablet_seat_v2_listener tablet_seat_listener = { .tablet_added = seat_tablet, .tool_added = seat_tool, .pad_added = seat_pad };

void tablet_bind(struct wl_registry *r, uint32_t name, const char *iface, uint32_t ver)
{
    (void)ver;
    if (strcmp(iface, zwp_tablet_manager_v2_interface.name)) return;
    tablet_mgr = wl_registry_bind(r, name, &zwp_tablet_manager_v2_interface, 1);
    tablet_seat_try();
}
void tablet_seat_try(void)
{
    if (!tablet_mgr || !seat || tablet_seat) return;
    tablet_seat = zwp_tablet_manager_v2_get_tablet_seat(tablet_mgr, seat);
    zwp_tablet_seat_v2_add_listener(tablet_seat, &tablet_seat_listener, NULL);
}

/* ---- test input: a tool the compositor does not have, driven through the same handlers ---- */

static struct tool inj;

void tablet_forget_window(struct window *w)
{
    for (struct tool *t = tools; t; t = t->next) if (t->w == w) { t->w = NULL; t->down = 0; }
    if (inj.w == w) memset(&inj, 0, sizeof inj);
}

/* "x y pressure(0..1) tilt_x tilt_y rotation(deg) distance(0..1) buttons(bit 0 BTN_STYLUS, bit 1 BTN_STYLUS2) tool(0 pen, 1 eraser) slider(-1..1) wheel(clicks)" in content
 * pixels and degrees: the tool comes into proximity if it is not, touches while the pressure is above 0; "out": it leaves. */
int tablet_inject(struct window *w, const char *text)
{
    if (!strcmp(text, "out")) {
        if (!inj.w) return 0;
        tool_out(&inj, NULL);
        tool_frame(&inj, NULL, 0);
        return 0;
    }
    double x, y, pressure, tx = 0, ty = 0, rot = 0, dist = 0, buttons = 0, eraser = 0, slider = 0, wheel = 0;
    if (sscanf(text, "%lf %lf %lf %lf %lf %lf %lf %lf %lf %lf %lf", &x, &y, &pressure, &tx, &ty, &rot, &dist, &buttons, &eraser, &slider, &wheel) < 3) return -3;
    if (!inj.w) {
        tool_type(&inj, NULL, eraser ? ZWP_TABLET_TOOL_V2_TYPE_ERASER : ZWP_TABLET_TOOL_V2_TYPE_PEN);
        tool_in(&inj, NULL, last_input_serial, NULL, w->surface);
    }
    if (inj.w != w) return -2;
    tool_motion(&inj, NULL, wl_fixed_from_double(x), wl_fixed_from_double(y));
    tool_tilt(&inj, NULL, wl_fixed_from_double(tx), wl_fixed_from_double(ty));
    tool_rotation(&inj, NULL, wl_fixed_from_double(rot));
    tool_distance(&inj, NULL, (uint32_t)(dist * 65535));
    tool_pressure(&inj, NULL, (uint32_t)(pressure * 65535));
    tool_slider(&inj, NULL, (int32_t)(slider * 65535));
    if (wheel != 0) tool_wheel(&inj, NULL, (int32_t)wheel * 15, (int32_t)wheel);
    if (pressure > 0 && !inj.down) tool_down(&inj, NULL, last_input_serial);
    else if (pressure <= 0 && inj.down) tool_up(&inj, NULL);
    for (int i = 0; i < 2; i++)
        if (!!((int)buttons >> i & 1) != !!(inj.buttons & (32 << i)))
            tool_button(&inj, NULL, 0, i ? BTN_STYLUS2 : BTN_STYLUS, (int)buttons >> i & 1 ? ZWP_TABLET_TOOL_V2_BUTTON_STATE_PRESSED : ZWP_TABLET_TOOL_V2_BUTTON_STATE_RELEASED);
    tool_frame(&inj, NULL, 0);
    return 0;
}
