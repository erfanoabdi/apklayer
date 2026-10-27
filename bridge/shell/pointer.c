/* Pointer input: title bar clicks, edge resize, wheel, cursors. */
#include "shell.h"

static void update_cursor(void);

static struct wp_cursor_shape_device_v1 *cursor_dev; /* shape of the pointer, set per region */
static uint32_t ptr_enter_serial;
static struct wl_cursor_theme *cursor_theme;  /* fallback when the compositor has no cursor-shape */
static struct wl_surface *cursor_surface;
static struct wl_pointer *the_pointer;
static const char *cursor_name_cur;
static int ptr_x, ptr_y, ptr_down;   /* surface-local logical coordinates */

/* ---- input ---- */

static struct window *pointer_decor_win; /* window whose title bar the pointer is over, if any */

void pointer_forget_window(struct window *w)
{
    if (pointer_focus != w && pointer_decor_win != w) return;
    pointer_focus = pointer_decor_win = NULL;
    ptr_down = 0;
    cursor_name_cur = NULL;
}

static void bar_set_hover(struct window *w, int hover)
{
    if (!w || w->bar_hover == hover) return;
    w->bar_hover = hover;
    window_layout(w);
    wl_surface_commit(w->surface); /* geometry/position apply with the parent commit */
}


static void ptr_enter(void *d, struct wl_pointer *p, uint32_t s, struct wl_surface *sf,
                      wl_fixed_t x, wl_fixed_t y)
{
    (void)d;
    ptr_enter_serial = s;
    cursor_name_cur = NULL; /* every enter needs the cursor set again */
    the_pointer = p;
    if (!cursor_dev && cursor_mgr) cursor_dev = wp_cursor_shape_manager_v1_get_pointer(cursor_mgr, p);
    struct window *w = window_for_surface(sf);
    ptr_x = wl_fixed_to_int(x); ptr_y = wl_fixed_to_int(y);
    if (w && sf == w->decor.surface) { pointer_decor_win = w; pointer_focus = NULL; }
    else { pointer_decor_win = NULL; pointer_focus = w; }
    if (debug_input) fprintf(stderr, "[shell] pointer enter %s at %d,%d\n",
                             pointer_decor_win ? "title bar" : w ? "content" : "?", ptr_x, ptr_y);
    update_cursor();
}
static void ptr_leave(void *d, struct wl_pointer *p, uint32_t s, struct wl_surface *sf)
{
    (void)d; (void)p; (void)s; (void)sf;
    if (ptr_down) { ptr_down = 0; send_input(pointer_focus, EV_UP, ptr_x, ptr_y); }
    if (pointer_decor_win) bar_set_hover(pointer_decor_win, DECOR_NONE);
    pointer_focus = NULL;
    pointer_decor_win = NULL;
}
static void ptr_motion(void *d, struct wl_pointer *p, uint32_t t, wl_fixed_t x, wl_fixed_t y)
{
    (void)d; (void)p; (void)t;
    ptr_x = wl_fixed_to_int(x); ptr_y = wl_fixed_to_int(y);
    update_cursor();
    if (pointer_decor_win) {
        bar_set_hover(pointer_decor_win, decor_hit(&pointer_decor_win->decor, ptr_x, ptr_y));
        return;
    }
    if (ptr_down) send_input(pointer_focus, EV_MOVE, ptr_x, ptr_y);
    else if (pointer_focus) send_input(pointer_focus, EV_HOVER, ptr_x, ptr_y);
}


/* xdg_toplevel resize edge under (x, y) given in window geometry coordinates (bar included). */
static uint32_t resize_edge(const struct window *w, int x, int y)
{
    const int E = 8;
    int lw = w->w * 100 / w->scale, lh = w->h * 100 / w->scale + DECOR_H;
    uint32_t e = 0;
    if (x < E) e |= XDG_TOPLEVEL_RESIZE_EDGE_LEFT;
    if (x >= lw - E) e |= XDG_TOPLEVEL_RESIZE_EDGE_RIGHT;
    if (y < E) e |= XDG_TOPLEVEL_RESIZE_EDGE_TOP;
    if (y >= lh - E) e |= XDG_TOPLEVEL_RESIZE_EDGE_BOTTOM;
    return w->maximized ? 0 : e;
}

/* Cursor names (xcursor) for a resize edge; NULL = the default arrow. */
static const char *edge_cursor_name(uint32_t edge)
{
    switch (edge) {
    case XDG_TOPLEVEL_RESIZE_EDGE_TOP: return "top_side";
    case XDG_TOPLEVEL_RESIZE_EDGE_BOTTOM: return "bottom_side";
    case XDG_TOPLEVEL_RESIZE_EDGE_LEFT: return "left_side";
    case XDG_TOPLEVEL_RESIZE_EDGE_RIGHT: return "right_side";
    case XDG_TOPLEVEL_RESIZE_EDGE_TOP_LEFT: return "top_left_corner";
    case XDG_TOPLEVEL_RESIZE_EDGE_TOP_RIGHT: return "top_right_corner";
    case XDG_TOPLEVEL_RESIZE_EDGE_BOTTOM_LEFT: return "bottom_left_corner";
    case XDG_TOPLEVEL_RESIZE_EDGE_BOTTOM_RIGHT: return "bottom_right_corner";
    default: return "left_ptr";
    }
}

/* Themed cursor through wl_pointer.set_cursor (no cursor-shape protocol on this compositor). */
static void set_themed_cursor(const char *name)
{
    if (!the_pointer) return;
    if (!cursor_theme) {
        int size = 24 * ((output_scale + 50) / 100);
        cursor_theme = wl_cursor_theme_load(NULL, size, shm);
        cursor_surface = wl_compositor_create_surface(compositor);
        if (!cursor_theme) { fprintf(stderr, "[shell] no cursor theme\n"); return; }
    }
    struct wl_cursor *c = wl_cursor_theme_get_cursor(cursor_theme, name);
    if (!c && !strcmp(name, "left_ptr")) c = wl_cursor_theme_get_cursor(cursor_theme, "default");
    if (!c) return;
    struct wl_cursor_image *img = c->images[0];
    int scale = (output_scale + 50) / 100;
    wl_surface_set_buffer_scale(cursor_surface, scale);
    wl_surface_attach(cursor_surface, wl_cursor_image_get_buffer(img), 0, 0);
    wl_surface_damage_buffer(cursor_surface, 0, 0, (int)img->width, (int)img->height);
    wl_surface_commit(cursor_surface);
    wl_pointer_set_cursor(the_pointer, ptr_enter_serial, cursor_surface,
                          (int32_t)img->hotspot_x / scale, (int32_t)img->hotspot_y / scale);
}

/* Resize cursor over the window edges and corners, the default arrow elsewhere. */
static void update_cursor(void)
{
    struct window *w = pointer_decor_win ? pointer_decor_win : pointer_focus;
    uint32_t edge = 0;
    if (w) edge = resize_edge(w, ptr_x, ptr_y + (pointer_decor_win ? 0 : DECOR_H));
    const char *name = edge_cursor_name(edge);
    if (name == cursor_name_cur) return; /* string literals: identity compare is enough */
    cursor_name_cur = name;
    if (debug_input) fprintf(stderr, "[shell] cursor %s\n", name);
    if (cursor_dev) {
        int shape;
        switch (edge) {
        case XDG_TOPLEVEL_RESIZE_EDGE_TOP: shape = WP_CURSOR_SHAPE_DEVICE_V1_SHAPE_N_RESIZE; break;
        case XDG_TOPLEVEL_RESIZE_EDGE_BOTTOM: shape = WP_CURSOR_SHAPE_DEVICE_V1_SHAPE_S_RESIZE; break;
        case XDG_TOPLEVEL_RESIZE_EDGE_LEFT: shape = WP_CURSOR_SHAPE_DEVICE_V1_SHAPE_W_RESIZE; break;
        case XDG_TOPLEVEL_RESIZE_EDGE_RIGHT: shape = WP_CURSOR_SHAPE_DEVICE_V1_SHAPE_E_RESIZE; break;
        case XDG_TOPLEVEL_RESIZE_EDGE_TOP_LEFT: shape = WP_CURSOR_SHAPE_DEVICE_V1_SHAPE_NW_RESIZE; break;
        case XDG_TOPLEVEL_RESIZE_EDGE_TOP_RIGHT: shape = WP_CURSOR_SHAPE_DEVICE_V1_SHAPE_NE_RESIZE; break;
        case XDG_TOPLEVEL_RESIZE_EDGE_BOTTOM_LEFT: shape = WP_CURSOR_SHAPE_DEVICE_V1_SHAPE_SW_RESIZE; break;
        case XDG_TOPLEVEL_RESIZE_EDGE_BOTTOM_RIGHT: shape = WP_CURSOR_SHAPE_DEVICE_V1_SHAPE_SE_RESIZE; break;
        default: shape = WP_CURSOR_SHAPE_DEVICE_V1_SHAPE_DEFAULT; break;
        }
        wp_cursor_shape_device_v1_set_shape(cursor_dev, ptr_enter_serial, (uint32_t)shape);
    } else {
        set_themed_cursor(name);
    }
}

static void send_back(struct window *w)
{
    int32_t down[5] = { W_ID(w), 0, 4 /* KEYCODE_BACK */, 0, 0 };
    int32_t up[5] = { W_ID(w), 1, 4, 0, 0 };
    send_msg(W_TARGET(w), MSG_KEY, down, 5);
    send_msg(W_TARGET(w), MSG_KEY, up, 5);
}

static void toggle_maximize(struct window *w)
{
    if (w->maximized) xdg_toplevel_unset_maximized(w->toplevel);
    else xdg_toplevel_set_maximized(w->toplevel);
}

static void bar_button(struct window *w, uint32_t serial, uint32_t time)
{
    int hit = decor_hit(&w->decor, ptr_x, ptr_y);
    uint32_t edge = resize_edge(w, ptr_x, ptr_y);
    if (edge) { xdg_toplevel_resize(w->toplevel, seat, serial, edge); return; }
    switch (hit) {
    case DECOR_BACK: send_back(w); break;
    case DECOR_MIN:
        w->minimized = 1;
        send_state(w); /* the app stops before the window disappears */
        xdg_toplevel_set_minimized(w->toplevel);
        break;
    case DECOR_MAX: toggle_maximize(w); break;
    case DECOR_CLOSE: {
        int32_t v[1] = { W_ID(w) };
        send_msg(W_TARGET(w), MSG_CLOSE, v, 1);
        break;
    }
    case DECOR_BAR:
        if ((int)time - w->last_click_ms < 400) { toggle_maximize(w); w->last_click_ms = 0; }
        else { w->last_click_ms = (int)time; xdg_toplevel_move(w->toplevel, seat, serial); }
        break;
    default: break;
    }
}

static void ptr_button(void *d, struct wl_pointer *p, uint32_t s, uint32_t t, uint32_t b, uint32_t st)
{
    (void)d; (void)p;
    last_input_serial = s;
    int pressed = st == WL_POINTER_BUTTON_STATE_PRESSED;
    if (pressed && pointer_decor_win && b == 0x110) { bar_button(pointer_decor_win, s, t); return; }
    if (pressed && pointer_focus && (b == 0x110 || b == 0x111)) {
        if (kb_android_meta() & 0x02) {
            /* Alt+left-drag moves, Alt+right-drag resizes (also without the title bar). */
            if (b == 0x110) xdg_toplevel_move(pointer_focus->toplevel, seat, s);
            else xdg_toplevel_resize(pointer_focus->toplevel, seat, s,
                                     XDG_TOPLEVEL_RESIZE_EDGE_BOTTOM_RIGHT);
            return;
        }
        /* Content edges resize the window instead of reaching the app. */
        uint32_t edge = b == 0x110 ? resize_edge(pointer_focus, ptr_x, ptr_y + DECOR_H) : 0;
        if (edge) { xdg_toplevel_resize(pointer_focus->toplevel, seat, s, edge); return; }
    }
    if (b == 0x111 && pressed && pointer_focus) { /* right click = long press */
        send_input(pointer_focus, EV_SECONDARY, ptr_x, ptr_y);
        return;
    }
    if (b != 0x110 /* BTN_LEFT */ || pointer_decor_win) return;
    if (!pressed && !ptr_down) return; /* release of a press the shell consumed (resize edge) */
    ptr_down = pressed;
    send_input(pointer_focus, ptr_down ? EV_DOWN : EV_UP, ptr_x, ptr_y);
}
static double axis_v, axis_h; /* accumulated wheel deltas, flushed on pointer frame (P2) */
static void ptr_axis(void *d, struct wl_pointer *p, uint32_t t, uint32_t a, wl_fixed_t v)
{
    (void)d; (void)p; (void)t;
    double val = wl_fixed_to_double(v);
    if (debug_input) fprintf(stderr, "[shell] axis %u %.1f (focus %s)\n", a, val, pointer_focus ? "content" : pointer_decor_win ? "bar" : "none");
    if (a == WL_POINTER_AXIS_VERTICAL_SCROLL) axis_v += val;
    else if (a == WL_POINTER_AXIS_HORIZONTAL_SCROLL) axis_h += val;
}
static void ptr_frame(void *d, struct wl_pointer *p)
{
    (void)d; (void)p;
    if ((axis_v != 0 || axis_h != 0) && pointer_focus) {
        /* Wayland axis: ~10 per wheel notch, + = down/right. Android: AXIS_VSCROLL + = up,
         * AXIS_HSCROLL + = right, in notches (the view applies its scroll factor); sent *1000. */
        struct window *w = pointer_focus;
        if (debug_input) fprintf(stderr, "[shell] scroll v=%.1f h=%.1f at %d,%d\n", axis_v, axis_h, ptr_x, ptr_y);
        int32_t v[5] = { W_ID(w), ptr_x * w->scale / 100, ptr_y * w->scale / 100,
                         (int32_t)(-axis_v * 100), (int32_t)(axis_h * 100) };
        send_msg(W_TARGET(pointer_focus), MSG_SCROLL, v, 5);
    }
    axis_v = axis_h = 0;
}
static void ptr_axis_source(void *d, struct wl_pointer *p, uint32_t s)
{ (void)d; (void)p; (void)s; }
static void ptr_axis_stop(void *d, struct wl_pointer *p, uint32_t t, uint32_t a)
{ (void)d; (void)p; (void)t; (void)a; }
static void ptr_axis_discrete(void *d, struct wl_pointer *p, uint32_t a, int32_t n)
{ (void)d; (void)p; (void)a; (void)n; }
const struct wl_pointer_listener ptr_listener = {
    .enter = ptr_enter, .leave = ptr_leave, .motion = ptr_motion, .button = ptr_button,
    .axis = ptr_axis, .frame = ptr_frame, .axis_source = ptr_axis_source,
    .axis_stop = ptr_axis_stop, .axis_discrete = ptr_axis_discrete };

