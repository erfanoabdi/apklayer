/* Touch input (wl_touch): finger events to the app as multi-pointer touch messages. */
#include "shell.h"

static struct window *touch_window;     /* window the current touch sequence started in */
static int fingers;                     /* fingers currently down */

/* TOUCH {i32 id, i32 action(0 down,1 up,2 move,3 cancel), i32 finger, i32 x, i32 y} (pixels) */
static void send_touch(int action, int finger, wl_fixed_t fx, wl_fixed_t fy)
{
    if (!touch_window) return;
    int scale = touch_window->scale;
    int32_t v[5] = { W_ID(touch_window), action, finger, wl_fixed_to_int(fx) * scale / 100,
                     wl_fixed_to_int(fy) * scale / 100 };
    send_msg(W_TARGET(touch_window), MSG_TOUCH, v, 5);
}

static void t_down(void *d, struct wl_touch *t, uint32_t serial, uint32_t time,
                   struct wl_surface *sf, int32_t id, wl_fixed_t x, wl_fixed_t y)
{
    (void)d; (void)t; (void)time;
    last_input_serial = serial;
    struct window *w = window_for_surface(sf);
    if (!w || sf == w->decor.surface) return; /* title bar touches are not forwarded */
    if (!touch_window) touch_window = w;
    if (w == touch_window) {
        fingers++;
        send_touch(0, id, x, y);
    }
}
static void t_up(void *d, struct wl_touch *t, uint32_t serial, uint32_t time, int32_t id)
{
    (void)d; (void)t; (void)serial; (void)time;
    send_touch(1, id, 0, 0);
    if (fingers > 0 && --fingers == 0) touch_window = NULL;
}
static void t_motion(void *d, struct wl_touch *t, uint32_t time, int32_t id, wl_fixed_t x, wl_fixed_t y)
{ (void)d; (void)t; (void)time; send_touch(2, id, x, y); }
static void t_frame(void *d, struct wl_touch *t) { (void)d; (void)t; }
static void t_cancel(void *d, struct wl_touch *t)
{
    (void)d; (void)t;
    send_touch(3, 0, 0, 0);
    touch_window = NULL;
    fingers = 0;
}
static void t_shape(void *d, struct wl_touch *t, int32_t id, wl_fixed_t a, wl_fixed_t b)
{ (void)d; (void)t; (void)id; (void)a; (void)b; }
static void t_orientation(void *d, struct wl_touch *t, int32_t id, wl_fixed_t o)
{ (void)d; (void)t; (void)id; (void)o; }

const struct wl_touch_listener touch_listener = {
    .down = t_down, .up = t_up, .motion = t_motion, .frame = t_frame, .cancel = t_cancel,
    .shape = t_shape, .orientation = t_orientation };
