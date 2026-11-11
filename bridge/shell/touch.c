/* Touch input (wl_touch): finger events to the app as multi-pointer touch messages. */
#include "shell.h"

static struct window *touch_window;     /* window the current touch sequence started in */
static int fingers;                     /* fingers currently down */
static struct window *nav_window;       /* a finger on this window's navigation bar (mobile.c) */
static int nav_finger;
static int swallowed;                   /* fingers of a sequence taken from the app (an edge swipe, edgeswipe.c) still down */

/* TOUCH {i32 id, i32 action(0 down,1 up,2 move,3 cancel), i32 finger, i32 x, i32 y} (pixels) */
static void send_touch(int action, int finger, wl_fixed_t fx, wl_fixed_t fy)
{
    if (!touch_window) return;
    int scale = touch_window->scale, x = wl_fixed_to_int(fx), y = wl_fixed_to_int(fy);
    window_to_app(touch_window, &x, &y);
    int32_t v[5] = { W_ID(touch_window), action, finger, x * scale / 100, y * scale / 100 };
    send_msg(W_TARGET(touch_window), MSG_TOUCH, v, 5);
}

static void t_down(void *d, struct wl_touch *t, uint32_t serial, uint32_t time,
                   struct wl_surface *sf, int32_t id, wl_fixed_t x, wl_fixed_t y)
{
    (void)d; (void)t; (void)time;
    last_input_serial = serial;
    struct window *w = window_for_surface(sf);
    if (debug_input) fprintf(stderr, "[shell] touch down %d at %d,%d on %s\n", id, wl_fixed_to_int(x), wl_fixed_to_int(y), w ? w->title : "no window");
    if (!w || sf == w->decor.surface) return; /* title bar touches are not forwarded */
    /* the navigation bar (a subsurface, or the bottom nav_h pixels where the display composes it): its button acts when the finger lifts */
    if ((w->nav.surface && sf == w->nav.surface) || (w->nav.remote && w->nav.shown && wl_fixed_to_int(y) >= w->cfg_h - w->nav_h)) {
        if (nav_window) return;
        int hit = navbar_hit(&w->nav, wl_fixed_to_int(x));
        nav_window = w;
        nav_finger = id;
        w->nav.pressed = hit == NAV_BAR ? NAV_NONE : hit;
        window_layout(w);
        wl_surface_commit(w->surface);
        return;
    }
    if (swallowed) { swallowed++; return; }  /* until every finger of the swipe is up */
    if (!touch_window) touch_window = w;
    if (w == touch_window) {
        fingers++;
        send_touch(0, id, x, y);
        edgeswipe_down(w, id, wl_fixed_to_int(x), wl_fixed_to_int(y));
    }
}
static void t_up(void *d, struct wl_touch *t, uint32_t serial, uint32_t time, int32_t id)
{
    (void)d; (void)t; (void)serial; (void)time;
    if (nav_window && id == nav_finger) {
        struct window *w = nav_window;
        int held = w->nav.pressed;
        nav_window = NULL;
        if (!w->client || !w->mobile) return;  /* the window went away or changed style meanwhile */
        w->nav.pressed = NAV_NONE;
        window_layout(w);
        wl_surface_commit(w->surface);
        if (held != NAV_NONE) window_nav_action(w, held);
        return;
    }
    if (swallowed) { swallowed--; return; }
    edgeswipe_up(id);
    send_touch(1, id, 0, 0);
    if (fingers > 0 && --fingers == 0) touch_window = NULL;
}
static void t_motion(void *d, struct wl_touch *t, uint32_t time, int32_t id, wl_fixed_t x, wl_fixed_t y)
{
    (void)d; (void)t; (void)time;
    if ((nav_window && id == nav_finger) || swallowed) return;
    if (touch_window && edgeswipe_motion(id, wl_fixed_to_int(x), wl_fixed_to_int(y))) {
        send_touch(3, 0, 0, 0);  /* the desktop's gesture now, not the app's */
        swallowed = fingers;
        touch_window = NULL;
        fingers = 0;
        return;
    }
    send_touch(2, id, x, y);
}
static void t_frame(void *d, struct wl_touch *t) { (void)d; (void)t; }
static void t_cancel(void *d, struct wl_touch *t)
{
    (void)d; (void)t;
    if (nav_window && nav_window->client && nav_window->mobile) { nav_window->nav.pressed = NAV_NONE; window_layout(nav_window); wl_surface_commit(nav_window->surface); }
    nav_window = NULL;
    edgeswipe_cancel();
    send_touch(3, 0, 0, 0);
    touch_window = NULL;
    fingers = swallowed = 0;
}
static void t_shape(void *d, struct wl_touch *t, int32_t id, wl_fixed_t a, wl_fixed_t b)
{ (void)d; (void)t; (void)id; (void)a; (void)b; }
static void t_orientation(void *d, struct wl_touch *t, int32_t id, wl_fixed_t o)
{ (void)d; (void)t; (void)id; (void)o; }

const struct wl_touch_listener touch_listener = {
    .down = t_down, .up = t_up, .motion = t_motion, .frame = t_frame, .cancel = t_cancel,
    .shape = t_shape, .orientation = t_orientation };

/* Test input: one finger (id 0) on w's content (in mobile style its bottom nav_h pixels: the navigation bar), through the wl_touch handlers.
 * action 0 down, 1 up, 2 move, 3 cancel. */
void touch_inject(struct window *w, int action, int x, int y)
{
    int nav = w->mobile && w->nav.shown && !w->nav.remote && y >= w->cfg_h - w->nav_h;  /* a remote bar is on w->surface itself */
    wl_fixed_t fx = wl_fixed_from_int(x), fy = wl_fixed_from_int(nav ? y - (w->cfg_h - w->nav_h) : y);
    if (action == 0) t_down(NULL, NULL, last_input_serial, 0, nav ? w->nav.surface : w->surface, 0, fx, fy);
    else if (action == 1) t_up(NULL, NULL, last_input_serial, 0, 0);
    else if (action == 2) t_motion(NULL, NULL, 0, 0, fx, fy);
    else t_cancel(NULL, NULL);
    t_frame(NULL, NULL);
}
