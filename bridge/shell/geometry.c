/* Window geometry and scale, as an xdg-shell client: the compositor's configures (size, maximized/fullscreen/tiled states, bounds), its
 * preferred scale (wp_fractional_scale_v1, else the outputs' integer scale), and what the app is told (CONFIGURE: logical content size and
 * scale; the app renders size*scale pixels). Sizes here are logical; the content is shown at the told size through wp_viewporter whatever
 * its buffer's pixel size, and the title bar is drawn at the window's scale. Window geometry = content + title bar. */
#include "shell.h"

int window_scale(const struct window *w) { return w->pref_scale > 0 ? w->pref_scale : output_scale; }

/* The logical content size for an app asking for lw x lh: that shape, the whole window at most 85% of the work area. */
static void fit_size(const struct window *w, int lw, int lh, int *fw, int *fh)
{
    int aw = w->bounds_w, ah = w->bounds_h;
    if (aw <= 0 || ah <= 0) { aw = output_mode_w; ah = output_mode_h > 0 ? output_mode_h - 32 : 0; }  /* no bounds: the output less a top bar */
    if (w->mobile) { *fw = aw > 0 ? aw : lw; *fh = ah > 0 ? ah : lh; return; }  /* the compositor did not maximize it: the work area */
    int maxw = aw * 85 / 100, maxh = ah * 85 / 100 - DECOR_H;
    *fw = lw; *fh = lh;
    if (maxh > 0 && *fh > maxh) { *fw = *fw * maxh / *fh; *fh = maxh; }
    if (maxw > 0 && *fw > maxw) { *fh = *fh * maxw / *fw; *fw = maxw; }
}

/* Tells the app (and the display's owner, when an app took over the window) its logical content size and the scale to render at. */
void window_send_configure(struct window *w)
{
    int s = window_scale(w), nav = w->mobile ? (w->nav_h * s + 50) / 100 : 0;  /* the bar's height in the app's pixels */
    w->told_scale = s;
    w->told_dpi = w->dpi;
    w->told_nav = w->nav_h;
    send_msg(W_TARGET(w), MSG_CONFIGURE, (int32_t[]){ W_ID(w), w->cfg_w, w->cfg_h, s, w->dpi, nav }, 6);
    if (w->proxy) send_msg(w->client, MSG_CONFIGURE, (int32_t[]){ w->id, w->cfg_w, w->cfg_h, s, w->dpi, nav }, 6);
    fprintf(stderr, "[shell] %s: configure content %dx%d @%d%% (%dx%d px), title bar %d logical = %d px%s, %s style, %d dpi, navigation bar %d logical = %d px\n",
            w->client->pkg, w->cfg_w, w->cfg_h, s, w->cfg_w * s / 100, w->cfg_h * s / 100, W_BAR(w), (W_BAR(w) * s + 99) / 100,
            w->maximized ? ", maximized" : w->constrained ? ", tiled" : "", w->mobile ? "mobile" : "desktop", w->dpi, w->nav_h, nav);
}

void window_layout(struct window *w)
{
    int lw = w->cfg_w > 0 ? w->cfg_w : w->w, lh = w->cfg_h > 0 ? w->cfg_h : w->h;
    if (w->viewport) wp_viewport_set_destination(w->viewport, lw, lh);
    else  /* without wp_viewporter only whole multiples of the logical size are shown at their size (every desktop compositor has it) */
        wl_surface_set_buffer_scale(w->surface, w->w % lw == 0 && w->h % lh == 0 && w->w / lw == w->h / lh ? w->w / lw : 1);
    w->scale = w->w * 100 / lw;
    if (!w->mobile) decor_update(&w->decor, lw, window_scale(w), w->maximized, w->bar_hover, w->activated);
    else if (w->nav_hidden || w->nav_h <= 0) navbar_hide(&w->nav);
    else navbar_update(&w->nav, lh - w->nav_h, lw, w->nav_h, window_scale(w), w->dark, w->nav.pressed);
    if (w->xdg_surface) xdg_surface_set_window_geometry(w->xdg_surface, 0, -W_BAR(w), lw, lh + W_BAR(w));
}

/* What the last toplevel configure changed, applied at its xdg_surface configure (or right after an injected one). */
static void apply_configure(struct window *w)
{
    if (!w->configured) {
        w->configured = 1;
        if (w->cfg_w <= 0 || w->cfg_h <= 0) fit_size(w, w->w, w->h, &w->cfg_w, &w->cfg_h);  /* the size is ours: the display's shape, fitted */
        fprintf(stderr, "[shell] first configure: display %dx%d, bounds %dx%d, output %dx%d @%d%%, window scale %d%%\n", w->w, w->h,
                w->bounds_w, w->bounds_h, output_mode_w, output_mode_h, output_scale, window_scale(w));
        window_layout(w);  /* before the first buffer: the compositor maps a window that already fits */
        window_send_configure(w);
        window_show_first(w);
        vsync_source_changed();
    } else if (w->resized || w->state_changed) {
        window_layout(w);
        wl_surface_commit(w->surface);  /* the current frame at the new size until the app's arrives */
        if (w->resized) window_send_configure(w);
    }
    w->resized = w->state_changed = 0;
}

static void xs_configure(void *data, struct xdg_surface *xs, uint32_t serial)
{
    xdg_surface_ack_configure(xs, serial);
    apply_configure(data);
}
static const struct xdg_surface_listener xs_listener = { xs_configure };

/* cw x ch: window geometry (title bar included), 0 = the client's choice. A floating window takes the compositor's size on its first
 * configure, during an interactive resize and when it leaves maximized/fullscreen/tiled; any other floating configure only repeats the size
 * the compositor last saw, which may predate our own reshape (an orientation request), so it is not a resize. */
static void top_configure(void *d, struct xdg_toplevel *t, int32_t cw, int32_t ch, struct wl_array *st)
{
    (void)t;
    struct window *w = d;
    int maximized = 0, activated = 0, constrained = 0, resizing = 0;
    uint32_t *state;
    wl_array_for_each(state, st) switch (*state) {
        case XDG_TOPLEVEL_STATE_MAXIMIZED: case XDG_TOPLEVEL_STATE_FULLSCREEN: maximized = constrained = 1; break;
        case XDG_TOPLEVEL_STATE_TILED_LEFT: case XDG_TOPLEVEL_STATE_TILED_RIGHT:
        case XDG_TOPLEVEL_STATE_TILED_TOP: case XDG_TOPLEVEL_STATE_TILED_BOTTOM: constrained = 1; break;
        case XDG_TOPLEVEL_STATE_RESIZING: resizing = 1; break;
        case XDG_TOPLEVEL_STATE_ACTIVATED: activated = 1; break;
    }
    int restored = w->constrained && !constrained, nw = 0, nh = 0;
    if (constrained && !w->constrained && w->cfg_w > 0) { w->float_w = w->cfg_w; w->float_h = w->cfg_h; }
    if (cw > 0 && ch > W_BAR(w) && (constrained || resizing || restored || !w->configured)) { nw = cw; nh = ch - W_BAR(w); }
    else if (restored && w->float_w > 0) { nw = w->float_w; nh = w->float_h; }
    if (nw > 0 && (nw != w->cfg_w || nh != w->cfg_h)) { w->cfg_w = nw; w->cfg_h = nh; w->resized = w->configured; }
    int active_changed = activated != w->activated;
    w->state_changed |= maximized != w->maximized || active_changed;
    w->maximized = maximized;
    w->constrained = constrained;
    w->activated = activated;
    if (activated) w->minimized = 0;
    if (active_changed && debug_input) fprintf(stderr, "[shell] \"%s\" %s\n", w->title, activated ? "activated" : "deactivated");
    if (w->configured && active_changed) send_state(w);
}
static void top_bounds(void *d, struct xdg_toplevel *t, int32_t bw, int32_t bh)
{ (void)t; struct window *w = d; w->bounds_w = bw; w->bounds_h = bh; }
static void top_close(void *data, struct xdg_toplevel *t)
{
    (void)t;
    struct window *w = data;
    send_msg(W_TARGET(w), MSG_CLOSE, (int32_t[]){ W_ID(w) }, 1);
}
static const struct xdg_toplevel_listener top_listener = { .configure = top_configure, .close = top_close, .configure_bounds = top_bounds };

/* The compositor prefers another scale for the window (pref: percent, 0 = follow the outputs): the bars redraw, the app re-renders. */
void window_scale_changed(struct window *w, int pref)
{
    if (pref) w->pref_scale = pref;
    window_display_changed(w);  /* mobile.c: the density and the navigation bar's height follow the scale */
}
static void preferred_scale(void *d, struct wp_fractional_scale_v1 *f, uint32_t s)
{ (void)f; window_scale_changed(d, (int)((s * 100 + 60) / 120)); }  /* s: 120ths */
static const struct wp_fractional_scale_v1_listener fscale_listener = { preferred_scale };

void window_geometry_init(struct window *w)
{
    if (w->toplevel) {
        xdg_surface_add_listener(w->xdg_surface, &xs_listener, w);
        xdg_toplevel_add_listener(w->toplevel, &top_listener, w);
    }
    if (viewporter) w->viewport = wp_viewporter_get_viewport(viewporter, w->surface);
    if (fscale_mgr) {
        w->fscale = wp_fractional_scale_manager_v1_get_fractional_scale(fscale_mgr, w->surface);
        wp_fractional_scale_v1_add_listener(w->fscale, &fscale_listener, w);
    }
}

void window_geometry_destroy(struct window *w)
{
    if (w->fscale) wp_fractional_scale_v1_destroy(w->fscale);
    if (w->viewport) wp_viewport_destroy(w->viewport);
    w->fscale = NULL;
    w->viewport = NULL;
}

/* apklayer patch: the app asks for another shape (portrait <-> landscape): fitted to the work area, sent to the app and to the display's
 * owner. A maximized or tiled window keeps the compositor's size; the shape is used when it floats again. */
void window_request_size(struct window *w, int lw, int lh)
{
    int fw, fh;
    if (!w->configured) return;
    fit_size(w, lw, lh, &fw, &fh);  /* the app turns its window (as a rotated phone), shrunk if it does not fit */
    if (w->constrained) { w->float_w = fw; w->float_h = fh; return; }
    if (fw == w->cfg_w && fh == w->cfg_h) return;
    w->cfg_w = fw;
    w->cfg_h = fh;
    window_layout(w);
    wl_surface_commit(w->surface);
    window_send_configure(w);
}

/* Test input: the configures a compositor sends. Focus (cw = 0): the current size, activated or not; resize: an interactive resize to the
 * content size cw x ch; maximize: the maximized state with window geometry gw x gh (unmaximize with 0 x 0: the size is ours). */
static void inject(struct window *w, int cw, int ch, int maximized, int resizing, int activated)
{
    struct wl_array states;
    wl_array_init(&states);
    if (maximized) *(uint32_t *)wl_array_add(&states, 4) = XDG_TOPLEVEL_STATE_MAXIMIZED;
    if (resizing) *(uint32_t *)wl_array_add(&states, 4) = XDG_TOPLEVEL_STATE_RESIZING;
    if (activated) *(uint32_t *)wl_array_add(&states, 4) = XDG_TOPLEVEL_STATE_ACTIVATED;
    top_configure(w, w->toplevel, cw, ch, &states);
    wl_array_release(&states);
    apply_configure(w);
}
void window_inject_configure(struct window *w, int cw, int ch, int activated)
{
    if (cw <= 0 || ch <= 0) inject(w, w->cfg_w, w->cfg_h + W_BAR(w), w->maximized, 0, activated);
    else inject(w, cw, ch + W_BAR(w), w->maximized, 1, activated);
}
void window_inject_maximize(struct window *w, int on, int gw, int gh)
{
    inject(w, gw, gh, on, 0, w->activated);
}
