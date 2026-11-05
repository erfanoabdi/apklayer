/* Window style and density. Desktop style: a floating window under apklayer's title bar (decor.c). Mobile style: maximized in the work
 * area, no title bar, Android's navigation bar over the bottom of the content (navbar.c), told to the app as its navigation bar inset.
 * WM_AUTO picks mobile in a mobile session (Lomiri, Phosh, Plasma Mobile, Sailfish) or on a phone-sized output.
 * Density: on a handheld (the output's physical size says so) the app gets the Android density bucket nearest the panel's real dpi, as a
 * phone's own ro.sf.lcd_density is (a 6.1" 720x1560 panel: 282 dpi -> 280; 6.4" 1080x2400: 411 -> 420), so apps have the size they have
 * on an Android phone; elsewhere 160 * the compositor's scale, desktop logical pixels being Android's dp. */
#include "shell.h"
#include <math.h>
#include <strings.h>

/* The largest output's physical size, or APKLAYER_OUTPUT_MM=<w>x<h> (a phone simulated on a desktop compositor, tests/vm/mobile.py). */
static void output_mm(int *w, int *h)
{
    const char *sim = getenv("APKLAYER_OUTPUT_MM");
    *w = output_mm_w; *h = output_mm_h;
    if (sim && sscanf(sim, "%dx%d", w, h) != 2) *w = *h = 0;
}

/* The physical density of a handheld's panel (dpi), 0 when the output is not one (a monitor, a laptop) or reports no usable size. */
static double handheld_dpi(void)
{
    int mw, mh;
    output_mm(&mw, &mh);
    if (mw < 20 || mh < 20 || output_px_w <= 0 || output_px_h <= 0) return 0;  /* none, or a placeholder (1x1, 16x9 "mm") */
    double inches = hypot(mw, mh) / 25.4;
    if (inches < 3 || inches > 9.5) return 0;  /* beyond a small tablet: a desktop screen, its logical pixels are the user's chosen size */
    /* pixels per mm alike on both axes, whichever way the mode is turned against the panel: else the size is made up */
    double a = (double)output_px_w / mw, b = (double)output_px_h / mh, c = (double)output_px_h / mw, d = (double)output_px_w / mh;
    if (fabs(a - b) > 0.2 * fmax(a, b) && fabs(c - d) > 0.2 * fmax(c, d)) return 0;
    return hypot(output_px_w, output_px_h) / inches;
}

/* Android's density buckets (DisplayMetrics.DENSITY_*): the one nearest the panel's dpi. */
static int bucket(double dpi)
{
    static const int dpis[] = { 120, 140, 160, 180, 200, 213, 220, 240, 260, 280, 300, 320, 340, 360, 400, 420, 440, 450, 480, 560, 600, 640 };
    int best = dpis[0];
    for (size_t i = 0; i < sizeof dpis / sizeof dpis[0]; i++) if (fabs(dpis[i] - dpi) < fabs(best - dpi)) best = dpis[i];
    return best;
}

int display_density(int scale)
{
    double dpi = handheld_dpi();
    return dpi > 0 ? bucket(dpi) : 160 * (scale > 0 ? scale : 100) / 100;
}

/* A mobile shell's session: its desktop names, or Sailfish's release file (lipstick sets none). */
static int mobile_session(void)
{
    static const char *names[] = { "lomiri", "unity8", "ubuntu-touch", "phosh", "plasma-mobile", "plasmamobile", "sailfish", "lipstick", "gnome-mobile" };
    const char *vars[] = { getenv("XDG_CURRENT_DESKTOP"), getenv("XDG_SESSION_DESKTOP"), getenv("DESKTOP_SESSION") };
    for (size_t v = 0; v < 3; v++) {
        if (!vars[v]) continue;
        char list[256];
        snprintf(list, sizeof list, "%s", vars[v]);
        for (char *save = NULL, *t = strtok_r(list, ":", &save); t; t = strtok_r(NULL, ":", &save))
            for (size_t i = 0; i < sizeof names / sizeof names[0]; i++) if (!strcasecmp(t, names[i])) return 1;
    }
    const char *plasma = getenv("PLASMA_PLATFORM");  /* Plasma Mobile: KDE desktop, "phone:handset" */
    return (plasma && !strncmp(plasma, "phone", 5)) || access("/etc/sailfish-release", F_OK) == 0;
}

int display_mobile(void)
{
    if (mobile_session() || handheld_dpi() > 0) return 1;
    int mw, mh;
    output_mm(&mw, &mh);
    int shortest = output_mode_w < output_mode_h ? output_mode_w : output_mode_h;
    return mw <= 0 && shortest > 0 && shortest < 600;  /* no physical size: a logical width below Android's sw600dp (a tablet's) */
}

/* The density and the navigation bar height (Android's 48 dp at that density) of w at its scale. */
static void metrics(struct window *w)
{
    int s = window_scale(w);
    w->dpi = display_density(s);
    int px = (48 * w->dpi + 80) / 160;
    w->nav_h = w->mobile ? (px * 100 + s / 2) / s : 0;
}

void window_set_mode(struct window *w, int mode)
{
    w->mode = mode;
    int mobile = mode == WM_MOBILE || (mode == WM_AUTO && display_mobile());
    int created = w->decor.surface || w->nav.surface;
    if (created && mobile == w->mobile) { window_display_changed(w); return; }
    pointer_forget_window(w);
    if (created) fprintf(stderr, "[shell] %s: %s style\n", w->title, mobile ? "mobile" : "desktop");
    if (mobile) {
        decor_destroy(&w->decor);
        navbar_create(&w->nav, w->surface);
    } else {
        navbar_destroy(&w->nav);
        decor_create(&w->decor, w->surface);
        w->decor.dark = w->dark;
        decor_set_title(&w->decor, w->label);
    }
    w->mobile = mobile;
    metrics(w);
    xdg_toplevel_set_min_size(w->toplevel, 160, 160 + W_BAR(w));
    if (mobile) xdg_toplevel_set_maximized(w->toplevel);  /* the work area; the compositor's configure brings its size */
    else if (created && w->maximized) xdg_toplevel_unset_maximized(w->toplevel);
    if (!w->configured) return;  /* the first configure lays it out */
    window_layout(w);
    wl_surface_commit(w->surface);
    window_send_configure(w);
}

void window_display_changed(struct window *w)
{
    if (w->mode == WM_AUTO && (w->decor.surface || w->nav.surface) && w->mobile != display_mobile()) { window_set_mode(w, WM_AUTO); return; }
    metrics(w);
    if (!w->configured || (w->told_dpi == w->dpi && w->told_nav == w->nav_h && w->told_scale == window_scale(w))) return;
    window_layout(w);
    wl_surface_commit(w->surface);
    window_send_configure(w);
}

void window_nav_visible(struct window *w, int visible)
{
    if (w->nav_hidden == !visible) return;
    w->nav_hidden = !visible;
    fprintf(stderr, "[shell] %s: navigation bar %s\n", w->title, visible ? "shown" : "hidden");
    if (!w->configured || !w->mobile) return;
    window_layout(w);
    wl_surface_commit(w->surface);
}

void window_nav_action(struct window *w, int button)
{
    if (debug_input) fprintf(stderr, "[shell] navigation bar %s\n", button == NAV_BACK ? "back" : button == NAV_HOME ? "home" : "bar");
    if (button == NAV_BACK) window_send_back(w);
    else if (button == NAV_HOME) window_minimize(w);  /* Android's Home: the app stops and the launcher (the compositor's) shows */
}

/* --display-info: what apps get on this display, for the stack's ro.sf.lcd_density (cli/stack.cpp). */
int print_display_info(void)
{
    int mw, mh;
    output_mm(&mw, &mh);
    printf("density %d\nmobile %d\noutput %dx%d px %dx%d mm scale %d%%\n", display_density(output_scale), display_mobile(),
           output_px_w, output_px_h, mw, mh, output_scale);
    return 0;
}
