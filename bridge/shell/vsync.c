/* Real compositor frame timing shared by Android clients. The primary display
 * may be covered by an app, so its own surface cannot be the sole clock. */
#include "shell.h"

struct request { struct client *client; int id; };
static struct request requests[MAX_WINDOWS];
static struct wl_callback *callback;
static struct wl_surface *source;

static int pending(void)
{
    for (int i = 0; i < MAX_WINDOWS; i++) if (requests[i].client) return 1;
    return 0;
}

static void done(void *data, struct wl_callback *cb, uint32_t ms)
{
    (void)data;
    wl_callback_destroy(cb);
    callback = NULL;
    source = NULL;
    for (int i = 0; i < MAX_WINDOWS; i++) {
        struct request r = requests[i];
        requests[i].client = NULL;
        if (r.client && r.client->fd >= 0)
            send_msg(r.client, MSG_VSYNC, (int32_t[]){ r.id, (int32_t)ms }, 2);
    }
}
static const struct wl_callback_listener listener = { .done = done };

void vsync_source_changed(void)
{
    struct window *chosen = NULL;
    if (pending()) for (int i = 0; i < MAX_WINDOWS; i++) {
        struct window *w = &windows[i];
        if (!w->client || !w->surface || !w->configured || w->minimized) continue;
        if (!chosen || w->activated || !chosen->activated) chosen = w;
    }
    struct wl_surface *next = chosen ? chosen->surface : NULL;
    if (callback && source == next) return;
    if (callback) wl_callback_destroy(callback);
    callback = NULL;
    source = next;
    if (!next) return; /* pending requests follow the next mapped window */
    callback = wl_surface_frame(next);
    wl_callback_add_listener(callback, &listener, NULL);
    wl_surface_commit(next);
}

void client_vsync_request(struct client *c, int id)
{
    int free_slot = -1;
    for (int i = 0; i < MAX_WINDOWS; i++) {
        if (requests[i].client == c && requests[i].id == id) return;
        if (!requests[i].client) free_slot = i;
    }
    if (free_slot < 0) return;
    requests[free_slot] = (struct request){ c, id };
    vsync_source_changed();
}

void client_vsync_forget(struct client *c)
{
    for (int i = 0; i < MAX_WINDOWS; i++)
        if (requests[i].client == c) requests[i].client = NULL;
    vsync_source_changed();
}
