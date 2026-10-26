/* Wayland globals: seat, output (HiDPI scale), registry binding. */
#include "shell.h"
#include "text-input-v3-client-protocol.h"

uint32_t dmabuf_linear[DMABUF_LINEAR_MAX];
int dmabuf_linear_n;

/* what the compositor accepts (debug: APKLAYER_RUNTIME_DEBUG), to see why a buffer might not show */
static void dmabuf_format(void *d, struct zwp_linux_dmabuf_v1 *m, uint32_t fmt) { (void)d; (void)m; if (getenv("APKLAYER_RUNTIME_DEBUG")) fprintf(stderr, "[shell] dmabuf format %.4s\n", (char *)&fmt); }
static void dmabuf_modifier(void *d, struct zwp_linux_dmabuf_v1 *m, uint32_t fmt, uint32_t hi, uint32_t lo)
{
    (void)d; (void)m;
    if (hi == 0 && lo == 0 && dmabuf_linear_n < DMABUF_LINEAR_MAX) dmabuf_linear[dmabuf_linear_n++] = fmt;  /* DRM_FORMAT_MOD_LINEAR is accepted for this format */
    if (getenv("APKLAYER_RUNTIME_DEBUG") && (fmt == 0x34325241 || fmt == 0x34324241 || fmt == 0x34325258)) fprintf(stderr, "[shell] dmabuf modifier %.4s 0x%08x%08x\n", (char *)&fmt, hi, lo);
}
static const struct zwp_linux_dmabuf_v1_listener dmabuf_listener = { dmabuf_format, dmabuf_modifier };

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

/* ---- output: integer scale feeds CONFIGURE (P2 HiDPI) ---- */

static void out_geometry(void *d, struct wl_output *o, int32_t x, int32_t y, int32_t pw,
                         int32_t ph, int32_t sub, const char *make, const char *model,
                         int32_t tf)
{ (void)d; (void)o; (void)x; (void)y; (void)pw; (void)ph; (void)sub; (void)make; (void)model; (void)tf; }
static void out_mode(void *d, struct wl_output *o, uint32_t f, int32_t w, int32_t h,
                     int32_t refresh)
{
    (void)d; (void)o; (void)w; (void)h;
    if ((f & WL_OUTPUT_MODE_CURRENT) && refresh > 0) output_refresh_mhz = refresh;
}
static void out_scale(void *d, struct wl_output *o, int32_t s)
{ (void)d; (void)o; output_scale = s * 100; }
static void out_done(void *d, struct wl_output *o)
{
    (void)d; (void)o;
    static int told_refresh;
    if (told_refresh != output_refresh_mhz) {   /* the output's mode changed: apps follow the real refresh rate */
        told_refresh = output_refresh_mhz;
        for (int i = 0; i < MAX_CLIENTS; i++) send_msg(&clients[i], MSG_DISPLAY, &(int32_t){ output_refresh_mhz }, 1);
    }
    /* Scale changed (HiDPI, or moved to another output): tell every app so it re-renders. */
    for (int i = 0; i < MAX_WINDOWS; i++) {
        struct window *w = &windows[i];
        if (!w->client || !w->configured || w->told_scale == output_scale) continue;
        int32_t v[4] = { W_ID(w), w->w * 100 / w->scale, w->h * 100 / w->scale, output_scale };
        w->told_scale = output_scale;
        send_msg(W_TARGET(w), MSG_CONFIGURE, v, 4);
    }
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
    {
        dmabuf_mgr = wl_registry_bind(r, name, &zwp_linux_dmabuf_v1_interface, ver < 3 ? ver : 3);
        zwp_linux_dmabuf_v1_add_listener(dmabuf_mgr, &dmabuf_listener, NULL);
    }
    else if (!strcmp(iface, wl_shm_interface.name))
        shm = wl_registry_bind(r, name, &wl_shm_interface, 1);
    else if (!strcmp(iface, xdg_wm_base_interface.name)) {
        wm_base = wl_registry_bind(r, name, &xdg_wm_base_interface, 1);
        xdg_wm_base_add_listener(wm_base, &wm_listener, NULL);
    } else if (!strcmp(iface, wl_seat_interface.name) && !seat) {
        /* v5: pointer frame/axis events; every listener slot is filled. */
        seat = wl_registry_bind(r, name, &wl_seat_interface, ver < 5 ? ver : 5);
        wl_seat_add_listener(seat, &seat_listener, NULL);
    } else if (!strcmp(iface, wl_output_interface.name) && !output) {
        output = wl_registry_bind(r, name, &wl_output_interface, ver < 2 ? ver : 2); /* scale/done */
        wl_output_add_listener(output, &out_listener, NULL);
    }
}
static void registry_rm(void *d, struct wl_registry *r, uint32_t n)
{ (void)d; (void)r; (void)n; }
const struct wl_registry_listener registry_listener = { registry, registry_rm };

