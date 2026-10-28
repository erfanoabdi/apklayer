/* Adwaita-style header bar drawing for the shell's title bar (cairo, no GTK): flat headerbar colours,
 * round window buttons, centred bold title, rounded top corners; light or dark like the desktop
 * (org.gnome.desktop.interface color-scheme). Pixels are premultiplied ARGB8888. */
#include "decor.h"

#include <cairo.h>
#include <math.h>
#include <stdio.h>
#include <string.h>

int decor_dark(void)
{
    char buf[64] = "";
    FILE *f = popen("gsettings get org.gnome.desktop.interface color-scheme 2>/dev/null", "r");
    if (f) { if (!fgets(buf, sizeof buf, f)) buf[0] = 0; pclose(f); }
    return strstr(buf, "dark") != NULL;
}

/* Shortens t (UTF-8) with an ellipsis until it fits max_w. */
static void fit_title(cairo_t *cr, char *t, double max_w)
{
    cairo_text_extents_t te;
    cairo_text_extents(cr, t, &te);
    while (te.width > max_w && t[0]) {
        size_t n = strlen(t);
        if (n >= 3 && !memcmp(t + n - 3, "\xE2\x80\xA6", 3)) n -= 3;
        while (n > 0 && (t[n - 1] & 0xC0) == 0x80) n--;
        if (n == 0) break;
        n--;
        while (n > 0 && (t[n] & 0xC0) == 0x80) n--;
        strcpy(t + n, "\xE2\x80\xA6");
        cairo_text_extents(cr, t, &te);
    }
}

void decor_draw(void *px, int pw, int ph, int lw, int s, int maximized, int hover, int focused,
                const char *title, int dark, int (*button_x)(int, int))
{
    cairo_surface_t *sf = cairo_image_surface_create_for_data(px, CAIRO_FORMAT_ARGB32, pw, ph, pw * 4);
    cairo_t *cr = cairo_create(sf);
    double k = s / 100.0;
    cairo_scale(cr, k, k);
    double W = pw / k, H = ph / k;
    cairo_set_operator(cr, CAIRO_OPERATOR_SOURCE);
    cairo_set_source_rgba(cr, 0, 0, 0, 0);
    cairo_paint(cr);
    /* headerbar background, top corners rounded unless maximized */
    double rad = maximized ? 0 : 12;
    double bg = dark ? (focused ? 0.188 : 0.141) : (focused ? 0.922 : 0.980);
    cairo_new_sub_path(cr);
    cairo_arc(cr, W - rad, rad, rad, -M_PI / 2, 0);
    cairo_line_to(cr, W, H);
    cairo_line_to(cr, 0, H);
    cairo_arc(cr, rad, rad, rad, M_PI, 1.5 * M_PI);
    cairo_close_path(cr);
    cairo_set_source_rgb(cr, bg, bg, bg);
    cairo_fill(cr);
    cairo_set_operator(cr, CAIRO_OPERATOR_OVER);
    double fg = dark ? 1.0 : 0.0, fa = focused ? (dark ? 1.0 : 0.8) : 0.5;
    cairo_set_source_rgba(cr, 0, 0, 0, dark ? 0.35 : 0.14); /* bottom border */
    cairo_rectangle(cr, 0, H - 1, W, 1);
    cairo_fill(cr);
    /* title */
    cairo_select_font_face(cr, "Cantarell", CAIRO_FONT_SLANT_NORMAL, CAIRO_FONT_WEIGHT_BOLD);
    cairo_set_font_size(cr, 14);
    int lo = button_x(lw, DECOR_BACK) + DECOR_BTN_W, ro = button_x(lw, DECOR_MIN);
    char t[96];
    snprintf(t, sizeof t, "%s", title ? title : "");
    fit_title(cr, t, ro - lo - 16);
    cairo_text_extents_t te;
    cairo_text_extents(cr, t, &te);
    double tx = (W - te.width) / 2 - te.x_bearing;
    if (tx < lo + 8) tx = lo + 8;
    cairo_set_source_rgba(cr, fg, fg, fg, fa);
    cairo_move_to(cr, tx, (H - 1) / 2 + 5);
    cairo_show_text(cr, t);
    /* round window buttons */
    cairo_set_line_width(cr, 1.8);
    cairo_set_line_cap(cr, CAIRO_LINE_CAP_ROUND);
    cairo_set_line_join(cr, CAIRO_LINE_JOIN_ROUND);
    for (int b = DECOR_BACK; b <= DECOR_CLOSE; b++) {
        double cx = button_x(lw, b) + DECOR_BTN_W / 2.0, cy = (H - 1) / 2;
        cairo_set_source_rgba(cr, fg, fg, fg, (b == hover ? 0.18 : 0.08) * (dark ? 1 : 0.9));
        cairo_arc(cr, cx, cy, 12, 0, 2 * M_PI);
        cairo_fill(cr);
        cairo_set_source_rgba(cr, fg, fg, fg, fa);
        switch (b) {
        case DECOR_BACK:
            cairo_move_to(cr, cx + 2.5, cy - 5); cairo_line_to(cr, cx - 2.5, cy); cairo_line_to(cr, cx + 2.5, cy + 5);
            break;
        case DECOR_MIN:
            cairo_move_to(cr, cx - 4.5, cy + 4); cairo_line_to(cr, cx + 4.5, cy + 4);
            break;
        case DECOR_MAX:
            if (maximized) {
                cairo_rectangle(cr, cx - 3.5, cy - 2, 7, 7);
                cairo_move_to(cr, cx - 1.5, cy - 4.5); cairo_line_to(cr, cx + 5, cy - 4.5); cairo_line_to(cr, cx + 5, cy + 2);
            } else cairo_rectangle(cr, cx - 4.5, cy - 4.5, 9, 9);
            break;
        case DECOR_CLOSE:
            cairo_move_to(cr, cx - 4, cy - 4); cairo_line_to(cr, cx + 4, cy + 4);
            cairo_move_to(cr, cx + 4, cy - 4); cairo_line_to(cr, cx - 4, cy + 4);
            break;
        }
        cairo_stroke(cr);
    }
    cairo_destroy(cr);
    cairo_surface_destroy(sf);
}
