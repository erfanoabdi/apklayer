/* Window geometry and scale, as an xdg-shell client: the compositor's configures (size, maximized/fullscreen/tiled states, bounds), its
 * preferred scale (wp_fractional_scale_v1, else the outputs' integer scale), and what the app is told (CONFIGURE: logical content size and
 * scale; the app renders size*scale pixels). Sizes here are logical; the content is shown at the told size through wp_viewporter whatever
 * its buffer's pixel size, and the title bar is drawn at the window's scale. Window geometry = content + title bar. */
#include "shell.h"

int window_scale(const struct window *w) { return w->pref_scale > 0 ? w->pref_scale : output_scale; }

/* The logical content size for an app asking for lw x lh: that shape, the whole window at most 85% of the work area; grow: exactly that
 * (an orientation turn fills the screen as the first portrait window does, which the display's default shape makes larger than the box). */
static void fit_size(const struct window *w, int lw, int lh, int grow, int *fw, int *fh)
{
    int aw = w->bounds_w, ah = w->bounds_h;
    if (aw <= 0 || ah <= 0) { aw = output_mode_w; ah = output_mode_h > 0 ? output_mode_h - 32 : 0; }  /* no bounds: the output less a top bar */
    if (w->mobile) {  /* the compositor did not maximize it: the work area; an orientation request: that shape inside the phone's window */
        if (grow && w->cfg_w > 0 && w->cfg_h > 0) { aw = w->cfg_w; ah = w->cfg_h; }
        *fw = aw > 0 ? aw : lw; *fh = ah > 0 ? ah : lh;
        if (grow && lw > 0 && lh > 0 && *fw > 0 && *fh > 0) {
            if ((long)lw * *fh > (long)lh * *fw) *fh = (int)((long)lh * *fw / lw); else *fw = (int)((long)lw * *fh / lh);
        }
        return;
    }
    int maxw = aw * 85 / 100, maxh = ah * 85 / 100 - DECOR_H;
    *fw = lw; *fh = lh;
    if (maxw <= 0 || maxh <= 0 || lw <= 0 || lh <= 0 || (!grow && lw <= maxw && lh <= maxh)) return;
    if ((long)lw * maxh > (long)lh * maxw) { *fw = maxw; *fh = (int)((long)lh * maxw / lw); }
    else { *fh = maxh; *fw = (int)((long)lw * maxh / lh); }
}

/* An orientation the phone is not in (mobile style), as Android rotates the display: the app gets the screen turned (its configure is the
 * window's swapped size, so a landscape config), and the display's owner (aosp/hals/graphics/vdisplay) composes it turned into a buffer
 * of the window's shape (SurfaceFlinger's display projection: ROTATION_90 for landscape on a portrait window, the content a quarter turn
 * clockwise on the panel; 270 the way back); the user turns the phone. Not wl_surface.set_buffer_transform: Lomiri's Mir 1 ignores it.
 * Our navigation bar stays at the window's bottom, the phone's physical bottom, where a phone's 3-button bar sits in landscape (beside the
 * app's content, which it shortens: the app has no side inset for it). A display owner that composes the bar itself (Lipstick, nav.remote)
 * keeps the letterbox (fit_size). */
static int want_turn(const struct window *w)
{
    if (!w->mobile || !w->lock || w->nav.remote || !w->proxy || w->cfg_w <= 0 || w->cfg_h <= 0 || (w->cfg_w > w->cfg_h) == (w->lock == 1)) return 0;
    return w->lock == 1 ? 90 : 270;
}
static int turned_nav(const struct window *w) { return w->turn && !w->nav_hidden && w->nav_h > 0 ? w->nav_h : 0; }

void window_app_size(const struct window *w, int *aw, int *ah)
{
    if (w->turn) { *aw = w->cfg_h - turned_nav(w); *ah = w->cfg_w; }
    else { *aw = w->cfg_w; *ah = w->cfg_h; }
}

/* (x, y) on the window's surface (logical) to the app's content: the inverse of the display's turn */
void window_to_app(const struct window *w, int *x, int *y)
{
    int sx = *x, sy = *y;
    if (w->turn == 90) { *x = sy; *y = w->cfg_w - sx; }
    else if (w->turn == 270) { *x = w->cfg_h - turned_nav(w) - sy; *y = sx; }
}

/* Tells the app its logical content size and the scale to render at, and the display's owner (when an app took over the window) the
 * window's, with the turn it composes the app at. */
void window_send_configure(struct window *w)
{
    int s = window_scale(w), nav = w->mobile && !w->turn ? (w->nav_h * s + 50) / 100 : 0;  /* the bar's height in the app's pixels */
    int aw, ah, ime = w->turn ? 0 : w->ime_px;  /* shortcut: turned, the keyboard is beside the content and gets no inset */
    window_app_size(w, &aw, &ah);
    w->told_scale = s;
    w->told_dpi = w->dpi;
    w->told_nav = w->nav_h;
    /* the output's pixels: the app's screen (Display.getRealSize, WindowManager.getMaximumWindowMetrics), not its window */
    send_msg(W_TARGET(w), MSG_CONFIGURE, (int32_t[]){ W_ID(w), aw, ah, s, w->dpi, nav, w->mobile, output_px_w, output_px_h, ime }, 10);
    if (w->proxy)
        send_msg(w->client, MSG_CONFIGURE, (int32_t[]){ w->id, w->turn ? ah : aw, w->turn ? aw : ah, s, w->dpi, nav, w->mobile, output_px_w, output_px_h, ime, w->turn }, 11);
    fprintf(stderr, "[shell] %s: configure content %dx%d @%d%% (%dx%d px), title bar %d logical = %d px%s, %s style, %d dpi, navigation bar %d logical = %d px, keyboard %d px%s\n",
            w->client->pkg, aw, ah, s, aw * s / 100, ah * s / 100, W_BAR(w), (W_BAR(w) * s + 99) / 100,
            w->maximized ? ", maximized" : w->constrained ? ", tiled" : "", w->mobile ? "mobile" : "desktop", w->dpi, w->nav_h, nav, ime,
            w->turn == 90 ? ", turned 90" : w->turn == 270 ? ", turned 270" : "");
}

void window_layout(struct window *w)
{
    w->turn = want_turn(w);
    int lw = w->cfg_w > 0 ? w->cfg_w : w->w, lh = w->cfg_h > 0 ? w->cfg_h - turned_nav(w) : w->h;  /* turned: the bar is beside the picture */
    if (w->viewport) wp_viewport_set_destination(w->viewport, lw, lh);
    else  /* without wp_viewporter only whole multiples of the logical size are shown at their size (every desktop compositor has it) */
        wl_surface_set_buffer_scale(w->surface, w->w % lw == 0 && w->h % lh == 0 && w->w / lw == w->h / lh ? w->w / lw : 1);
    w->scale = w->w * 100 / lw;
    lh += turned_nav(w);
    if (!w->mobile) { if (w->fullscreen) decor_hide(&w->decor); else decor_update(&w->decor, lw, window_scale(w), w->maximized, w->bar_hover, w->activated); }
    else if (w->nav_hidden || w->nav_h <= 0) navbar_hide(&w->nav);  /* fullscreen too: the bar follows the app's (Lomiri kept a stale one) */
    else navbar_update(&w->nav, lh - w->nav_h, lw, w->nav_h, window_scale(w), w->dark, w->nav.pressed);
    if (w->xdg_surface) xdg_surface_set_window_geometry(w->xdg_surface, 0, -W_BAR(w), lw, lh + W_BAR(w));
}

/* What the last toplevel configure changed, applied at its xdg_surface configure (or right after an injected one). */
static void apply_configure(struct window *w)
{
    if (!w->configured) {
        w->configured = 1;
        if (w->cfg_w <= 0 || w->cfg_h <= 0) fit_size(w, w->w, w->h, 0, &w->cfg_w, &w->cfg_h);  /* the size is ours: the display's shape, fitted */
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
 * the compositor last saw, which may predate our own reshape (an orientation request), so it is not a resize (desktop style only). */
static int injecting;  /* top_configure called by inject() (test input), not by the compositor */
static void top_configure(void *d, struct xdg_toplevel *t, int32_t cw, int32_t ch, struct wl_array *st)
{
    (void)t;
    struct window *w = d;
    int maximized = 0, activated = 0, constrained = 0, resizing = 0, fullscreen = 0;
    uint32_t *state;
    wl_array_for_each(state, st) switch (*state) {
        case XDG_TOPLEVEL_STATE_FULLSCREEN: fullscreen = 1; /* fall through */
        case XDG_TOPLEVEL_STATE_MAXIMIZED: maximized = constrained = 1; break;
        case XDG_TOPLEVEL_STATE_TILED_LEFT: case XDG_TOPLEVEL_STATE_TILED_RIGHT:
        case XDG_TOPLEVEL_STATE_TILED_TOP: case XDG_TOPLEVEL_STATE_TILED_BOTTOM: constrained = 1; break;
        case XDG_TOPLEVEL_STATE_RESIZING: resizing = 1; break;
        case XDG_TOPLEVEL_STATE_ACTIVATED: activated = 1; break;
    }
    /* a test drives this window's focus: another window opening on the shared desktop (a parallel group) must not deactivate it, which ends
     * a long press as on Android (View.onWindowFocusChanged) */
    if (w->test_focus && !injecting) activated = w->activated;
    int restored = w->constrained && !constrained, nw = 0, nh = 0;
    if (debug_input) fprintf(stderr, "[shell] \"%s\" toplevel configure %dx%d%s%s%s\n", w->title, cw, ch, maximized ? " maximized" : constrained ? " tiled" : "",
                             resizing ? " resizing" : "", restored ? " restored" : "");
    if (constrained && !w->constrained && w->cfg_w > 0) { w->float_w = w->cfg_w; w->float_h = w->cfg_h; }
    w->state_changed |= fullscreen != w->fullscreen;
    w->fullscreen = fullscreen;  /* before the sizes below: a fullscreen window has no title bar */
    /* mobile style: the phone's shell sizes the window whatever its state (Lomiri drops maximized once the app is active, then sends the
     * turned screen's size when the phone rotates) */
    if (cw > 0 && ch > W_BAR(w) && (constrained || resizing || restored || !w->configured || w->mobile)) { nw = cw; nh = ch - W_BAR(w); }
    else if (restored && w->float_w > 0) { nw = w->float_w; nh = w->float_h; }
    if (!constrained && w->lock && nw > 0 && nh > 0 && (nw > nh) != (w->lock == 1)) {
        /* an activity fixed to one orientation keeps its shape in a floating window (as Android's desktop keeps a fixed app's aspect ratio on
         * a drag, DragPositioningCallbackUtility): the largest size of its last shape inside the box (a client may take less than offered) */
        int aw = w->cfg_w, ah = w->cfg_h;
        if (aw <= 0 || ah <= 0 || (aw > ah) != (w->lock == 1)) { aw = nh; ah = nw; }
        if ((long)aw * nh > (long)ah * nw) nh = (int)((long)nw * ah / aw); else nw = (int)((long)nh * aw / ah);
    }
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
/* The compositor closes a window: the app ends on CLOSE (ShellClient), its window goes now. Lomiri kills a client whose window outlives a
 * close request by a few seconds (qtmir onCloseTimedOut, Mir's force_close): that client is this shell, every app's windows. */
void window_close(struct window *w)
{
    send_msg(W_TARGET(w), MSG_CLOSE, (int32_t[]){ W_ID(w) }, 1);
    fprintf(stderr, "[shell] \"%s\" closed by the compositor\n", w->title);
    window_destroy(w);
}
static void top_close(void *data, struct xdg_toplevel *t) { (void)t; window_close(data); }
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

/* apklayer patch: the app's top activity is fixed to a shape (portrait or landscape; 0 x 0: none): kept through drag resizes (top_configure);
 * a window of the other shape turns, fitted to the work area, sent to the app and to the display's owner. A maximized or tiled window keeps
 * the compositor's size; the shape is used when it floats again. */
void window_request_size(struct window *w, int lw, int lh)
{
    int fw, fh;
    w->lock = lw <= 0 || lh <= 0 ? 0 : lw > lh ? 1 : lw < lh ? 2 : 0;
    if (w->configured && w->turn != want_turn(w)) {  /* a phone turns the app's content, not the window (want_turn) */
        window_layout(w);
        wl_surface_commit(w->surface);
        window_send_configure(w);
        return;
    }
    if (w->mobile && !w->nav.remote) return;
    if (!w->configured || !w->lock || (w->cfg_w > w->cfg_h) == (w->lock == 1)) return;  /* already that shape */
    fit_size(w, lw, lh, 1, &fw, &fh);  /* the app turns its window (as a rotated phone), fitted to the work area */
    if (w->constrained) {
        w->float_w = fw;
        w->float_h = fh;
        /* fullscreen on a desktop has the output's shape: the other one asked for (NewPipe leaving its player) leaves it, the window turns */
        if (w->fullscreen && !w->mobile) window_set_fullscreen(w, 0);
        return;
    }
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
    if (maximized && w->fullscreen) *(uint32_t *)wl_array_add(&states, 4) = XDG_TOPLEVEL_STATE_FULLSCREEN;  /* a focus change keeps it */
    if (resizing) *(uint32_t *)wl_array_add(&states, 4) = XDG_TOPLEVEL_STATE_RESIZING;
    if (activated) *(uint32_t *)wl_array_add(&states, 4) = XDG_TOPLEVEL_STATE_ACTIVATED;
    injecting = 1;
    top_configure(w, w->toplevel, cw, ch, &states);
    injecting = 0;
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
