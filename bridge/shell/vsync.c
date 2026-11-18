/* Real compositor frame timing shared by Android clients. The primary display
 * may be covered by an app, so its own surface cannot be the sole clock. */
#include "shell.h"

struct request { struct client *client; int id; };
static struct request requests[MAX_WINDOWS];
_Static_assert(MAX_WINDOWS > 0 && MAX_WINDOWS <= 64, "vsync request slots must fit in a uint64_t");
/* Keep the old highest-free slot choice and ascending reply order. */
static uint64_t request_slots;
static struct wl_callback *callback;
static struct wl_surface *source;

static void done(void *data, struct wl_callback *cb, uint32_t ms)
{
    (void)data;
    wl_callback_destroy(cb);
    callback = NULL;
    source = NULL;
    uint64_t slots = request_slots;
    request_slots = 0;
    for (; slots; slots &= slots - 1) {
        int i = __builtin_ctzll(slots);
        struct request r = requests[i];
        if (r.client->fd >= 0)
            send_msg(r.client, MSG_VSYNC, (int32_t[]){ r.id, (int32_t)ms }, 2);
    }
}
static const struct wl_callback_listener listener = { .done = done };

static struct window *timing_window(void)
{
    struct window *chosen = NULL;
    for (int i = 0; i < MAX_WINDOWS; i++) {
        struct window *w = &windows[i];
        if (!w->client || !w->surface || !w->configured || w->minimized) continue;
        if (!chosen || w->activated || !chosen->activated) chosen = w;
    }
    return chosen;
}

void client_display_refresh(struct client *c, int mhz)
{
    if (c->fd < 0 || c->refresh_mhz == mhz) return;
    if (send_msg(c, MSG_DISPLAY, &(int32_t){ mhz }, 1) == 0) c->refresh_mhz = mhz;
}

void display_refresh_changed(void)
{
    int window_rates[MAX_CLIENTS] = { 0 };
    for (int i = 0; i < MAX_WINDOWS; i++) {
        const struct window *w = &windows[i];
        if (!w->client) continue;
        int mhz = window_refresh_mhz(w);
        window_rates[w->client - clients] = mhz;
        if (w->proxy) window_rates[w->proxy - clients] = mhz;
    }
    int active_rate = window_refresh_mhz(timing_window());
    for (int i = 0; i < MAX_CLIENTS; i++)
        client_display_refresh(&clients[i], window_rates[i] ? window_rates[i] : active_rate);
}

void vsync_source_changed(void)
{
    struct window *chosen = request_slots ? timing_window() : NULL;
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
    uint64_t free_slots = ~request_slots & (UINT64_MAX >> (64 - MAX_WINDOWS));
    if (!free_slots) return;
    for (uint64_t slots = request_slots; slots; slots &= slots - 1) {
        int i = __builtin_ctzll(slots);
        if (requests[i].client == c && requests[i].id == id) return;
    }
    int free_slot = 63 - __builtin_clzll(free_slots);
    requests[free_slot] = (struct request){ c, id };
    request_slots |= UINT64_C(1) << free_slot;
    vsync_source_changed();
}

void client_vsync_forget(struct client *c)
{
    for (uint64_t slots = request_slots; slots; slots &= slots - 1) {
        int i = __builtin_ctzll(slots);
        if (requests[i].client == c) request_slots &= ~(UINT64_C(1) << i);
    }
    vsync_source_changed();
}
