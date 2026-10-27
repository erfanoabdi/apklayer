/* Clipboard: the Wayland selection (wl_data_device) <-> the app's ClipboardManager (text only). */
#include "shell.h"

#define MAX_CLIP 60000 /* fits one shell message */

struct wl_data_device_manager *data_mgr;
uint32_t last_input_serial;     /* serial of the last key/button event, needed for set_selection */

static struct wl_data_device *data_dev;
static struct wl_data_offer *cur_offer;      /* current selection of another client, or NULL */
static struct wl_data_source *our_source;    /* our selection (the app copied something) */
struct source_text { char *text; size_t len; };
static struct { int fd; struct client *target; int pid; size_t len; char text[MAX_CLIP + 1]; }
    incoming = { .fd = -1 };
static struct { int fd; char *text; size_t len, offset; } outgoing[CLIPBOARD_FDS - 1];

struct offer_info { int text; };             /* user data of every wl_data_offer */

static void offer_mime(void *d, struct wl_data_offer *o, const char *mime)
{
    (void)o;
    if (!strcmp(mime, "text/plain;charset=utf-8") || !strcmp(mime, "text/plain")
        || !strcmp(mime, "UTF8_STRING"))
        ((struct offer_info *)d)->text = 1;
}
static const struct wl_data_offer_listener offer_listener = { .offer = offer_mime };

static void dd_data_offer(void *d, struct wl_data_device *dev, struct wl_data_offer *o)
{
    (void)d; (void)dev;
    struct offer_info *info = calloc(1, sizeof *info);
    wl_data_offer_add_listener(o, &offer_listener, info);
}

/* Selection reads and writes share the main event loop. Blocking here would
 * deadlock when the offered text is owned by another app in this same shell. */
static void cancel_read(void) {
    if (incoming.fd >= 0) close(incoming.fd);
    incoming.fd = -1;
    incoming.target = NULL;
}

static void remember(struct client *c, const char *text, size_t len)
{
    free(c->last_clipboard);
    c->last_clipboard = malloc(len + 1);
    c->last_clipboard_len = len;
    if (c->last_clipboard) {
        memcpy(c->last_clipboard, text, len);
        c->last_clipboard[len] = 0;
    }
}

void clipboard_client_drop(struct client *c)
{
    if (incoming.target == c) cancel_read();
    free(c->last_clipboard);
    c->last_clipboard = NULL;
}

void clipboard_push(struct window *w)
{
    if (!w || !cur_offer) return;
    struct offer_info *info = wl_data_offer_get_user_data(cur_offer);
    if (!info || !info->text) return;
    int fds[2];
    if (pipe2(fds, O_CLOEXEC) < 0) return;
    fcntl(fds[0], F_SETFL, fcntl(fds[0], F_GETFL) | O_NONBLOCK);
    cancel_read();
    incoming.fd = fds[0];
    incoming.target = W_TARGET(w);
    incoming.pid = incoming.target->pid;
    incoming.len = 0;
    wl_data_offer_receive(cur_offer, "text/plain;charset=utf-8", fds[1]);
    close(fds[1]);
    wl_display_flush(display);
}

void clipboard_pollfds(struct pollfd *p)
{
    p[0] = (struct pollfd){ .fd = incoming.fd, .events = POLLIN };
    for (int i = 0; i < CLIPBOARD_FDS - 1; i++)
        p[i + 1] = (struct pollfd){ .fd = outgoing[i].text ? outgoing[i].fd : -1, .events = POLLOUT };
}

void clipboard_dispatch(struct pollfd *p)
{
    if (incoming.fd >= 0 && p[0].fd == incoming.fd && p[0].revents) {
        ssize_t n = read(incoming.fd, incoming.text + incoming.len,
                         incoming.len < MAX_CLIP ? MAX_CLIP - incoming.len : 0);
        if (n > 0) incoming.len += (size_t)n;
        if (n == 0 || incoming.len == MAX_CLIP) {
            struct client *c = incoming.target;
            incoming.text[incoming.len] = 0;
            if (c && c->fd >= 0 && c->pid == incoming.pid &&
                (!c->last_clipboard || c->last_clipboard_len != incoming.len ||
                 memcmp(c->last_clipboard, incoming.text, incoming.len))) {
                send_bytes(c, MSG_CLIPBOARD, incoming.text, incoming.len);
                remember(c, incoming.text, incoming.len);
            }
            cancel_read();
        } else if (n < 0 && errno != EAGAIN && errno != EINTR) cancel_read();
    }
    for (int i = 0; i < CLIPBOARD_FDS - 1; i++) {
        if (!outgoing[i].text || p[i + 1].fd != outgoing[i].fd || !p[i + 1].revents) continue;
        ssize_t n = write(outgoing[i].fd, outgoing[i].text + outgoing[i].offset,
                          outgoing[i].len - outgoing[i].offset);
        if (n > 0) outgoing[i].offset += (size_t)n;
        if (outgoing[i].offset == outgoing[i].len ||
            (n < 0 && errno != EAGAIN && errno != EINTR)) {
            close(outgoing[i].fd);
            free(outgoing[i].text);
            outgoing[i].text = NULL;
        }
    }
}

static void dd_selection(void *d, struct wl_data_device *dev, struct wl_data_offer *o)
{
    (void)d; (void)dev;
    if (o == cur_offer) return;
    cancel_read();
    if (cur_offer) {
        free(wl_data_offer_get_user_data(cur_offer));
        wl_data_offer_destroy(cur_offer);
    }
    cur_offer = o;
    if (debug_input) fprintf(stderr, "[shell] clipboard selection %s\n", o ? "offered" : "cleared");
    if (o && keyboard_focus) clipboard_push(keyboard_focus);
}

static void dd_enter(void *d, struct wl_data_device *dev, uint32_t serial, struct wl_surface *s,
                     wl_fixed_t x, wl_fixed_t y, struct wl_data_offer *o)
{ (void)d; (void)dev; (void)serial; (void)s; (void)x; (void)y; (void)o; }
static void dd_leave(void *d, struct wl_data_device *dev) { (void)d; (void)dev; }
static void dd_motion(void *d, struct wl_data_device *dev, uint32_t t, wl_fixed_t x, wl_fixed_t y)
{ (void)d; (void)dev; (void)t; (void)x; (void)y; }
static void dd_drop(void *d, struct wl_data_device *dev) { (void)d; (void)dev; }
static const struct wl_data_device_listener dd_listener = {
    .data_offer = dd_data_offer, .enter = dd_enter, .leave = dd_leave, .motion = dd_motion,
    .drop = dd_drop, .selection = dd_selection };

/* ---- our own selection ---- */

static void src_target(void *d, struct wl_data_source *s, const char *m) { (void)d; (void)s; (void)m; }
static void src_send(void *d, struct wl_data_source *s, const char *mime, int32_t fd)
{
    (void)s; (void)mime;
    struct source_text *text = d;
    for (int i = 0; i < CLIPBOARD_FDS - 1; i++) {
        if (outgoing[i].text) continue;
        outgoing[i].text = malloc(text->len);
        if (!outgoing[i].text) { close(fd); return; }
        memcpy(outgoing[i].text, text->text, text->len);
        outgoing[i].len = text->len;
        outgoing[i].offset = 0;
        outgoing[i].fd = fd;
        fcntl(fd, F_SETFL, fcntl(fd, F_GETFL) | O_NONBLOCK);
        return;
    }
    close(fd);
}
static void source_destroy(struct wl_data_source *s)
{
    struct source_text *text = wl_data_source_get_user_data(s);
    free(text->text);
    free(text);
    wl_data_source_destroy(s);
}
static void src_cancelled(void *d, struct wl_data_source *s)
{
    (void)d;
    if (s == our_source) our_source = NULL;
    source_destroy(s);
}
static void src_dnd_drop(void *d, struct wl_data_source *s) { (void)d; (void)s; }
static void src_dnd_finished(void *d, struct wl_data_source *s) { (void)d; (void)s; }
static void src_action(void *d, struct wl_data_source *s, uint32_t a) { (void)d; (void)s; (void)a; }
static const struct wl_data_source_listener src_listener = {
    .target = src_target, .send = src_send, .cancelled = src_cancelled,
    .dnd_drop_performed = src_dnd_drop, .dnd_finished = src_dnd_finished, .action = src_action };

/* The app copied text: become the selection owner. */
void clipboard_set(struct client *c, const char *text, size_t len)
{
    if (!data_dev || !data_mgr || len == 0) return;
    remember(c, text, len); /* suppress an echo to the source JVM only */
    struct source_text *old = our_source ? wl_data_source_get_user_data(our_source) : NULL;
    if (old && old->len == len && !memcmp(old->text, text, len)) return;
    struct source_text *snapshot = calloc(1, sizeof *snapshot);
    if (!snapshot) return;
    snapshot->text = malloc(len + 1);
    if (!snapshot->text) { free(snapshot); return; }
    memcpy(snapshot->text, text, len);
    snapshot->text[len] = 0;
    snapshot->len = len;
    struct wl_data_source *src = wl_data_device_manager_create_data_source(data_mgr);
    wl_data_source_add_listener(src, &src_listener, snapshot);
    wl_data_source_offer(src, "text/plain;charset=utf-8");
    wl_data_source_offer(src, "text/plain");
    wl_data_source_offer(src, "UTF8_STRING");
    wl_data_device_set_selection(data_dev, src, last_input_serial);
    if (our_source) source_destroy(our_source);
    our_source = src;
    if (debug_input) fprintf(stderr, "[shell] clipboard set from app (%zu bytes)\n", len);
}

/* Called once the seat and the data-device manager are bound. */
void clipboard_init(void)
{
    if (data_dev || !data_mgr || !seat) return;
    data_dev = wl_data_device_manager_get_data_device(data_mgr, seat);
    wl_data_device_add_listener(data_dev, &dd_listener, NULL);
}
