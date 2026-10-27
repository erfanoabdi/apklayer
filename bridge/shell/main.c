/* apklayer-shell entry point: Wayland connection, listening socket, event loop. */
#include "shell.h"

/* state shared between the modules (declared in shell.h) */
struct wl_display *display;
struct wl_compositor *compositor;
struct wl_subcompositor *subcompositor;
struct client clients[MAX_CLIENTS];
struct wp_cursor_shape_manager_v1 *cursor_mgr;
struct wl_shm *shm;
struct xdg_wm_base *wm_base;
struct wl_seat *seat;
struct wl_output *output;
int output_refresh_mhz = 60000;
int output_scale = 100;            /* percent: 100 = 1x, 200 = 2x (P2 HiDPI) */
struct window windows[MAX_WINDOWS];
struct window *pointer_focus, *keyboard_focus;
int debug_input;               /* APKLAYER_DEBUG_INPUT=1: log focus changes and key codes */
int repeat_rate = 25, repeat_delay = 400;   /* keys/s, ms (wl_keyboard.repeat_info) */
int repeat_key = -1;                        /* evdev code of the held repeating key */
long long repeat_next_ms;

static int listen_socket(const char *path)
{
    int fd = socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0);
    struct sockaddr_un addr = { .sun_family = AF_UNIX };
    if (strlen(path) >= sizeof addr.sun_path) {
        fprintf(stderr, "[shell] socket path too long: %s\n", path);
        exit(1);
    }
    strcpy(addr.sun_path, path);
    unlink(path);
    if (fd < 0 || bind(fd, (struct sockaddr *)&addr, sizeof addr) < 0 || listen(fd, 8) < 0) {
        fprintf(stderr, "[shell] cannot listen on %s: %s\n", path, strerror(errno));
        exit(1);
    }
    return fd;
}

int main(void)
{
    const char *rt = getenv("XDG_RUNTIME_DIR");
    if (!rt) { fprintf(stderr, "[shell] XDG_RUNTIME_DIR is not set\n"); return 1; }
    char dir[512], path[600];
    snprintf(dir, sizeof dir, "%s/apklayer", rt);
    mkdir(dir, 0700);
    snprintf(path, sizeof path, "%s/shell.sock", dir);

    signal(SIGPIPE, SIG_IGN);
    const char *dbg = getenv("APKLAYER_DEBUG_INPUT");
    debug_input = dbg && *dbg;
    display = wl_display_connect(NULL);
    if (!display) { fprintf(stderr, "[shell] no Wayland compositor\n"); return 1; }
    struct wl_registry *reg = wl_display_get_registry(display);
    wl_registry_add_listener(reg, &registry_listener, NULL);
    wl_display_roundtrip(display);
    wl_display_roundtrip(display); /* bound wl_output objects deliver their initial mode/scale */
    decor_setup(compositor, subcompositor, shm);
    if (!compositor || !shm || !wm_base) { fprintf(stderr, "[shell] missing Wayland globals\n"); return 1; }

    clipboard_init();
    for (int i = 0; i < MAX_CLIENTS; i++) clients[i].fd = -1;
    int lfd = listen_socket(path);
    fprintf(stderr, "[shell] listening on %s\n", path);

    for (;;) {
        struct pollfd pfd[2 + CLIPBOARD_FDS + MAX_CLIENTS];
        struct client *who[2 + CLIPBOARD_FDS + MAX_CLIENTS];
        int n = 0;
        while (wl_display_prepare_read(display) != 0) wl_display_dispatch_pending(display);
        wl_display_flush(display);
        pfd[n++] = (struct pollfd){ .fd = wl_display_get_fd(display), .events = POLLIN };
        pfd[n++] = (struct pollfd){ .fd = lfd, .events = POLLIN };
        clipboard_pollfds(pfd + n);
        n += CLIPBOARD_FDS;
        for (int i = 0; i < MAX_CLIENTS; i++) {
            if (clients[i].fd < 0) continue;
            who[n] = &clients[i];
            pfd[n++] = (struct pollfd){ .fd = clients[i].fd, .events = POLLIN };
        }
        int timeout = -1;
        if (repeat_key >= 0) {
            long long wait = repeat_next_ms - now_ms();
            timeout = wait > 0 ? (int)wait : 0;
        }
        if (poll(pfd, (nfds_t)n, timeout) < 0 && errno != EINTR) { wl_display_cancel_read(display); break; }

        if (pfd[0].revents & POLLIN) {
            if (wl_display_read_events(display) < 0) break;
        } else {
            wl_display_cancel_read(display);
        }
        wl_display_dispatch_pending(display);
        clipboard_dispatch(pfd + 2);
        if (repeat_key >= 0 && now_ms() >= repeat_next_ms) {
            key_event((uint32_t)repeat_key, 1);
            repeat_next_ms += 1000 / repeat_rate;
        }

        if (pfd[1].revents & POLLIN) {
            int cfd = accept4(lfd, NULL, NULL, SOCK_CLOEXEC);
            struct client *c = NULL;
            for (int i = 0; i < MAX_CLIENTS && !c; i++)
                if (clients[i].fd < 0) c = &clients[i];
            if (c && cfd >= 0) { memset(c, 0, sizeof *c); c->fd = cfd; }
            else if (cfd >= 0) close(cfd);
        }
        for (int i = 2 + CLIPBOARD_FDS; i < n; i++) {
            if (who[i]->fd >= 0 && (pfd[i].revents & (POLLIN | POLLHUP | POLLERR))) client_read(who[i]);
        }
    }
    fprintf(stderr, "[shell] Wayland connection lost\n");
    return 1;
}

