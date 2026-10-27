/* apklayer display HAL, host side: one display = one window of apklayer-shell (the Wayland client), fed with dmabufs. Real
 * pacing: vsync() is the compositor's own frame callback for the window, refresh() the output's mode (wl_output), nothing is
 * simulated. Plain C for the Android-side composer HAL (aosp/hals/graphics/composer). One display per instance (PLAN P9). */
#ifndef APKLAYER_DISPLAY_H
#define APKLAYER_DISPLAY_H

#include <stdint.h>

#include "apklayer/graphics.h"

#ifdef __cplusplus
extern "C" {
#endif

struct apklayer_display_callbacks {
    void *user;
    void (*vsync)(void *user, int64_t timestamp_ns);          /* answer to request_vsync: the window's next real frame */
    void (*refresh)(void *user, uint32_t refresh_mhz);        /* the output's refresh rate (also once right after open) */
    void (*released)(void *user, int slot);                   /* the compositor is done with that presented buffer */
};

struct apklayer_display;

/* Connects to the shell ($APKLAYER_SHELL, else $XDG_RUNTIME_DIR/apklayer/shell.sock) and opens a window of this size.
 * NULL on failure (errno). Callbacks run on the display's reader thread. */
struct apklayer_display *apklayer_display_open(const char *name, uint32_t width, uint32_t height,
                                               const struct apklayer_display_callbacks *callbacks);
/* A primary timing anchor with no toplevel. Refresh comes from the output,
 * vsync from an active mapped app surface; present retains no buffers. */
struct apklayer_display *apklayer_display_open_headless(const char *name,
                                                      const struct apklayer_display_callbacks *callbacks);
void apklayer_display_close(struct apklayer_display *d);

/* The window's logical size and scale (percent), as the shell reports it: once the window is up and whenever it is resized. Runs on
 * the reader thread. A separate setter, not a field of the callbacks struct: the composer was built against that struct. */
void apklayer_display_set_configure_callback(struct apklayer_display *d,
                                             void (*configure)(void *user, int32_t width, int32_t height, int32_t scale_percent), void *user);

uint32_t apklayer_display_refresh_mhz(struct apklayer_display *d);  /* 0 until the shell has said (set during open) */

/* One vsync() callback at the active mapped surface's next real compositor frame. */
int apklayer_display_request_vsync(struct apklayer_display *d);

/* Shows a buffer: slot 0..7 names it; its fds are sent the first time the slot is used (or after invalidate_slot), later
 * calls just show the slot. damage in buffer pixels (all zero = whole buffer). The buffer stays the caller's: keep it unchanged
 * until released() for that slot. Returns 0 or -errno. */
int apklayer_display_present(struct apklayer_display *d, int slot, const struct apklayer_gfx_buffer *buf, int32_t damage[4]);
void apklayer_display_invalidate_slot(struct apklayer_display *d, int slot);

#ifdef __cplusplus
}
#endif
#endif
