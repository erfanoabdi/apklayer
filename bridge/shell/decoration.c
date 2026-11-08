/* Server-side decorations (xdg-decoration-unstable-v1) where the compositor offers them (KDE, sway and other wlroots desktops): the
 * compositor draws the window's frame and title bar, and apklayer's own bar (decor.c) is not created. GNOME has no such global: ours stays.
 * Desktop style only; mobile style has no bar (mobile.c). */
#include "shell.h"
#include "xdg-decoration-unstable-v1-client-protocol.h"

struct zxdg_decoration_manager_v1 *deco_mgr;

static void deco_configure(void *d, struct zxdg_toplevel_decoration_v1 *t, uint32_t mode)
{
    (void)t;
    struct window *w = d;
    int ssd = mode == ZXDG_TOPLEVEL_DECORATION_V1_MODE_SERVER_SIDE;
    if (ssd == w->ssd) return;
    w->ssd = ssd;
    fprintf(stderr, "[shell] %s: %s decorations\n", w->client->pkg, ssd ? "server-side" : "client-side");
    window_set_mode(w, w->mode);  /* our bar goes or comes back; before the first configure only the style changes */
}
static const struct zxdg_toplevel_decoration_v1_listener deco_listener = { deco_configure };

void decoration_create(struct window *w)
{
    if (!deco_mgr || !w->toplevel) return;
    w->deco = zxdg_decoration_manager_v1_get_toplevel_decoration(deco_mgr, w->toplevel);
    zxdg_toplevel_decoration_v1_add_listener(w->deco, &deco_listener, w);
    zxdg_toplevel_decoration_v1_set_mode(w->deco, ZXDG_TOPLEVEL_DECORATION_V1_MODE_SERVER_SIDE);
}

void decoration_destroy(struct window *w)
{
    if (w->deco) zxdg_toplevel_decoration_v1_destroy(w->deco);  /* before its toplevel (the protocol's rule) */
    w->deco = NULL;
}
