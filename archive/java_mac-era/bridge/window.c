/*
 * apklayer bridge — M2 first window.
 *
 * Proof-of-life that the host bridge can put pixels on a Wayland desktop:
 * plain wl_shm ARGB buffer on an xdg_toplevel, two frames, exit.
 * This is the seed of the surface bridge that will later carry HWUI output.
 *
 * Exit codes: 0 = mapped + 2 frames presented, 2 = mapped but no frames,
 * 1 = could not open a window.
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <errno.h>
#include <sched.h>
#include <poll.h>
#include <sys/mman.h>
#include <sys/syscall.h>
#include <fcntl.h>
#include <stdint.h>
#include <wayland-client.h>
#include "xdg-shell-client-protocol.h"

#define W 640
#define H 360
#define STRIDE (W * 4)

static struct wl_display *display;
static struct wl_compositor *compositor;
static struct wl_shm *shm;
static struct xdg_wm_base *wm_base;
static struct wl_surface *surface;
static struct xdg_surface *xdg_surface;
static struct xdg_toplevel *toplevel;
static int frames_done;
static int got_configure;

static void frame_done(void *data, struct wl_callback *cb, uint32_t callback_data)
{
    (void)data; (void)callback_data;
    frames_done++;
    wl_callback_destroy(cb);
}
static const struct wl_callback_listener frame_listener = { frame_done };

static void wm_base_ping(void *data, struct xdg_wm_base *shell, uint32_t serial)
{
    (void)data;
    xdg_wm_base_pong(shell, serial);
}
static const struct xdg_wm_base_listener wm_base_listener = { wm_base_ping };

static void xdg_surface_configure(void *data, struct xdg_surface *xs, uint32_t serial)
{
    (void)data;
    xdg_surface_ack_configure(xs, serial);
    got_configure = 1;
}
static const struct xdg_surface_listener xdg_surface_listener = { xdg_surface_configure };

static void xdg_toplevel_configure(void *data, struct xdg_toplevel *toplevel,
                                   int32_t width, int32_t height, struct wl_array *states)
{
    (void)data; (void)toplevel; (void)width; (void)height; (void)states;
}
static void xdg_toplevel_close(void *data, struct xdg_toplevel *toplevel)
{
    (void)toplevel;
    *(int *)data = 1; /* request quit */
}
static const struct xdg_toplevel_listener toplevel_listener = {
    xdg_toplevel_configure, xdg_toplevel_close,
};

static void registry_global(void *data, struct wl_registry *registry, uint32_t name,
                            const char *interface, uint32_t version)
{
    (void)data; (void)version;
    if (!strcmp(interface, wl_compositor_interface.name))
        compositor = wl_registry_bind(registry, name, &wl_compositor_interface, 4);
    else if (!strcmp(interface, wl_shm_interface.name))
        shm = wl_registry_bind(registry, name, &wl_shm_interface, 1);
    else if (!strcmp(interface, xdg_wm_base_interface.name))
        wm_base = wl_registry_bind(registry, name, &xdg_wm_base_interface, 1);
}
static void registry_global_remove(void *data, struct wl_registry *r, uint32_t n)
{
    (void)data; (void)r; (void)n;
}
static const struct wl_registry_listener registry_listener = {
    registry_global, registry_global_remove,
};

/* ARGB8888 gradient with a white "apklayer" baseline stripe. */
static void paint(uint32_t *px, int frame)
{
    for (int y = 0; y < H; y++) {
        for (int x = 0; x < W; x++) {
            uint32_t r = (uint32_t)(255 - x * 200 / W);
            uint32_t g = (uint32_t)(60 + y * 160 / H);
            uint32_t b = (uint32_t)(120 + (frame * 40) % 136);
            px[y * W + x] = 0xff000000u | (r << 16) | (g << 8) | b;
        }
    }
    for (int y = H - 6; y < H; y++)
        for (int x = 0; x < W; x++)
            px[y * W + x] = 0xffffffffu;
}

int main(void)
{
    display = wl_display_connect(NULL);
    if (!display) {
        fprintf(stderr, "apklayer-window: no wayland compositor (WAYLAND_DISPLAY?)\n");
        return 1;
    }
    struct wl_registry *registry = wl_display_get_registry(display);
    wl_registry_add_listener(registry, &registry_listener, NULL);
    wl_display_roundtrip(display);
    if (!compositor || !shm || !wm_base) {
        fprintf(stderr, "apklayer-window: missing globals (compositor=%p shm=%p xdg=%p)\n",
                (void *)compositor, (void *)shm, (void *)wm_base);
        return 1;
    }
    xdg_wm_base_add_listener(wm_base, &wm_base_listener, NULL);

    surface = wl_compositor_create_surface(compositor);
    xdg_surface = xdg_wm_base_get_xdg_surface(wm_base, surface);
    xdg_surface_add_listener(xdg_surface, &xdg_surface_listener, NULL);
    toplevel = xdg_surface_get_toplevel(xdg_surface);
    xdg_toplevel_add_listener(toplevel, &toplevel_listener, NULL);
    xdg_toplevel_set_title(toplevel, "apklayer");
    xdg_toplevel_set_app_id(toplevel, "dev.apklayer.bridge");
    wl_surface_commit(surface);

    /* shm buffer via memfd (mutter requires memfd_method=memfd). */
    int fd = (int)syscall(SYS_memfd_create, "apklayer-shm", 0);
    if (fd < 0) { perror("memfd_create"); return 1; }
    if (ftruncate(fd, STRIDE * H) < 0) { perror("ftruncate"); return 1; }
    void *pool_mem = mmap(NULL, STRIDE * H, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);
    if (pool_mem == MAP_FAILED) { perror("mmap"); return 1; }
    struct wl_shm_pool *pool = wl_shm_create_pool(shm, fd, STRIDE * H);
    struct wl_buffer *buffer = wl_shm_pool_create_buffer(pool, 0, W, H, STRIDE,
                                                         WL_SHM_FORMAT_ARGB8888);

    /* Wait for the initial configure (size may be compositor-chosen; we keep WxH). */
    for (int i = 0; i < 100 && !got_configure; i++) {
        if (wl_display_dispatch(display) < 0) break;
    }
    if (!got_configure) {
        fprintf(stderr, "apklayer-window: no xdg configure\n");
        return 1;
    }

    for (int f = 0; f < 2; f++) {
        paint((uint32_t *)pool_mem, f);
        wl_surface_attach(surface, buffer, 0, 0);
        wl_surface_damage(surface, 0, 0, W, H);
        struct wl_callback *cb = wl_surface_frame(surface);
        wl_callback_add_listener(cb, &frame_listener, NULL);
        wl_surface_commit(surface);
        wl_display_flush(display); /* apklayer fix: send requests; poll loop below does not flush */
    }

    /* Pump events until both frame callbacks land (max ~5s). */
    for (int i = 0; i < 500 && frames_done < 2; i++) {
        struct pollfd pfd = { .fd = wl_display_get_fd(display), .events = POLLIN };
        while (wl_display_prepare_read(display) != 0)
            wl_display_dispatch_pending(display);
        if (poll(&pfd, 1, 10) > 0)
            wl_display_read_events(display);
        else
            wl_display_cancel_read(display);
        wl_display_dispatch_pending(display);
    }

    printf("apklayer-window: mapped=1 frames=%d\n", frames_done);
    return frames_done >= 2 ? 0 : 2;
}
