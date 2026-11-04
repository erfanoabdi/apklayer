/* Wayland globals: seat, output (HiDPI scale), registry binding. */
#include "shell.h"
#include "text-input-v3-client-protocol.h"
#include "xdg-activation-v1-client-protocol.h"

static void seat_caps(void *d, struct wl_seat *st, uint32_t caps)
{
    (void)d;
    static int have_ptr, have_kb, have_touch;
    if ((caps & WL_SEAT_CAPABILITY_POINTER) && !have_ptr) {
        have_ptr = 1;
        wl_pointer_add_listener(wl_seat_get_pointer(st), &ptr_listener, NULL);
    }
    if ((caps & WL_SEAT_CAPABILITY_TOUCH) && !have_touch) {
        have_touch = 1;
        wl_touch_add_listener(wl_seat_get_touch(st), &touch_listener, NULL);
    }
    if ((caps & WL_SEAT_CAPABILITY_KEYBOARD) && !have_kb) {
        have_kb = 1;
        wl_keyboard_add_listener(wl_seat_get_keyboard(st), &kb_listener, NULL);
    }
}
static void seat_name(void *d, struct wl_seat *st, const char *n)
{ (void)d; (void)st; (void)n; }
static const struct wl_seat_listener seat_listener = { .capabilities = seat_caps, .name = seat_name };

/* ---- outputs: every wl_output is tracked (a VM's display change replaces its output); the largest scale and size are the fallback
 * for compositors without wp_fractional_scale_v1 and configure_bounds ---- */

struct wp_viewporter *viewporter;
struct wp_fractional_scale_manager_v1 *fscale_mgr;
#define MAX_OUTPUTS 8
static struct out { struct wl_output *o; uint32_t name; int w, h, scale; } outs[MAX_OUTPUTS];

static void outputs_changed(void)
{
    int scale = 0, mw = 0, mh = 0;
    for (int i = 0; i < MAX_OUTPUTS; i++) {
        if (!outs[i].o) continue;
        int s = outs[i].scale > 0 ? outs[i].scale : 1;
        if (s * 100 > scale) scale = s * 100;
        if (outs[i].w / s * (outs[i].h / s) > mw * mh) { mw = outs[i].w / s; mh = outs[i].h / s; }
    }
    output_scale = scale ? scale : 100;
    output_mode_w = mw;
    output_mode_h = mh;
    for (int i = 0; i < MAX_WINDOWS; i++)   /* windows without a per-surface preferred scale follow the outputs */
        if (windows[i].client && !windows[i].pref_scale) window_scale_changed(&windows[i], 0);
}

static void out_geometry(void *d, struct wl_output *o, int32_t x, int32_t y, int32_t pw,
                         int32_t ph, int32_t sub, const char *make, const char *model,
                         int32_t tf)
{ (void)d; (void)o; (void)x; (void)y; (void)pw; (void)ph; (void)sub; (void)make; (void)model; (void)tf; }
static void out_mode(void *d, struct wl_output *o, uint32_t f, int32_t w, int32_t h,
                     int32_t refresh)
{
    (void)o;
    struct out *out = d;
    if ((f & WL_OUTPUT_MODE_CURRENT) && refresh > 0) output_refresh_mhz = refresh;
    if (f & WL_OUTPUT_MODE_CURRENT) { out->w = w; out->h = h; }
}
static void out_scale(void *d, struct wl_output *o, int32_t s)
{ (void)o; ((struct out *)d)->scale = s; }
static void out_done(void *d, struct wl_output *o)
{
    (void)d; (void)o;
    static int told_refresh;
    if (told_refresh != output_refresh_mhz) {   /* the output's mode changed: apps follow the real refresh rate */
        told_refresh = output_refresh_mhz;
        for (int i = 0; i < MAX_CLIENTS; i++) send_msg(&clients[i], MSG_DISPLAY, &(int32_t){ output_refresh_mhz }, 1);
    }
    outputs_changed();
}
static const struct wl_output_listener out_listener = {
    .geometry = out_geometry, .mode = out_mode, .done = out_done, .scale = out_scale };

/* ---- globals ---- */

static void wm_ping(void *d, struct xdg_wm_base *wm, uint32_t s)
{ (void)d; xdg_wm_base_pong(wm, s); }
static const struct xdg_wm_base_listener wm_listener = { wm_ping };

static void registry(void *d, struct wl_registry *r, uint32_t name, const char *iface, uint32_t ver)
{
    (void)d;
    if (debug_input) fprintf(stderr, "[shell] global %s v%u\n", iface, ver);
    if (!strcmp(iface, wl_compositor_interface.name))
        compositor = wl_registry_bind(r, name, &wl_compositor_interface, 4);
    else if (!strcmp(iface, wl_subcompositor_interface.name))
        subcompositor = wl_registry_bind(r, name, &wl_subcompositor_interface, 1);
    else if (!strcmp(iface, wp_cursor_shape_manager_v1_interface.name))
        cursor_mgr = wl_registry_bind(r, name, &wp_cursor_shape_manager_v1_interface, 1);
    else if (!strcmp(iface, wl_data_device_manager_interface.name))
        data_mgr = wl_registry_bind(r, name, &wl_data_device_manager_interface, ver < 3 ? ver : 3);
    else if (!strcmp(iface, zwp_text_input_manager_v3_interface.name))
        text_mgr = wl_registry_bind(r, name, &zwp_text_input_manager_v3_interface, 1);
    else if (!strcmp(iface, zwp_linux_dmabuf_v1_interface.name))
        dmabuf_bind(r, name, ver);
    else if (!strcmp(iface, wl_shm_interface.name))
        shm = wl_registry_bind(r, name, &wl_shm_interface, 1);
    else if (!strcmp(iface, xdg_wm_base_interface.name)) {
        wm_base = wl_registry_bind(r, name, &xdg_wm_base_interface, ver < 4 ? ver : 4);  /* v4: configure_bounds */
        xdg_wm_base_add_listener(wm_base, &wm_listener, NULL);
    } else if (!strcmp(iface, wl_seat_interface.name) && !seat) {
        /* v5: pointer frame/axis events; every listener slot is filled. */
        seat = wl_registry_bind(r, name, &wl_seat_interface, ver < 5 ? ver : 5);
        wl_seat_add_listener(seat, &seat_listener, NULL);
    } else if (!strcmp(iface, wl_output_interface.name)) {
        for (int i = 0; i < MAX_OUTPUTS; i++)
            if (!outs[i].o) {
                outs[i] = (struct out){ wl_registry_bind(r, name, &wl_output_interface, ver < 2 ? ver : 2), name, 0, 0, 1 }; /* scale/done */
                wl_output_add_listener(outs[i].o, &out_listener, &outs[i]);
                break;
            }
    } else if (!strcmp(iface, wp_viewporter_interface.name))
        viewporter = wl_registry_bind(r, name, &wp_viewporter_interface, 1);
    else if (!strcmp(iface, wp_fractional_scale_manager_v1_interface.name))
        fscale_mgr = wl_registry_bind(r, name, &wp_fractional_scale_manager_v1_interface, 1);
    else if (!strcmp(iface, xdg_activation_v1_interface.name))
        activation = wl_registry_bind(r, name, &xdg_activation_v1_interface, 1);
}
static void registry_rm(void *d, struct wl_registry *r, uint32_t n)
{
    (void)d; (void)r;
    for (int i = 0; i < MAX_OUTPUTS; i++)
        if (outs[i].o && outs[i].name == n) {
            wl_output_destroy(outs[i].o);
            outs[i] = (struct out){ 0 };
            outputs_changed();
        }
}
const struct wl_registry_listener registry_listener = { registry, registry_rm };

