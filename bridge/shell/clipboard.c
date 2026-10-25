/* Clipboard: the Wayland selection (wl_data_device) <-> the app's ClipboardManager (text only). */
#include "shell.h"

#define MAX_CLIP 60000 /* fits one shell message */

struct wl_data_device_manager *data_mgr;
uint32_t last_input_serial;     /* serial of the last key/button event, needed for set_selection */

static struct wl_data_device *data_dev;
static struct wl_data_offer *cur_offer;      /* current selection of another client, or NULL */
static struct wl_data_source *our_source;    /* our selection (the app copied something) */
static char *our_text;
static char *last_pushed;                    /* last text sent to an app, to avoid echo loops */

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

/* Reads the offered text through a pipe (bounded wait: the other client writes asynchronously). */
static char *read_offer(struct wl_data_offer *offer, size_t *len)
{
    int fds[2];
    if (pipe2(fds, O_CLOEXEC) < 0) return NULL;
    wl_data_offer_receive(offer, "text/plain;charset=utf-8", fds[1]);
    wl_display_flush(display);
    close(fds[1]);
    char *buf = malloc(MAX_CLIP + 1);
    size_t n = 0;
    if (buf) {
        for (;;) {
            struct pollfd p = { .fd = fds[0], .events = POLLIN };
            if (poll(&p, 1, 300) <= 0) break;
            ssize_t r = read(fds[0], buf + n, MAX_CLIP - n);
            if (r <= 0) break;
            n += (size_t)r;
            if (n >= MAX_CLIP) break;
        }
        buf[n] = 0;
    }
    close(fds[0]);
    *len = n;
    return buf;
}

/* Sends the current selection to the app that owns w, unless it is what we sent last. */
void clipboard_push(struct window *w)
{
    if (!w || !cur_offer || !((struct offer_info *)wl_data_offer_get_user_data(cur_offer))->text) return;
    size_t n;
    char *text = read_offer(cur_offer, &n);
    if (text && n > 0 && (!last_pushed || strcmp(last_pushed, text))) {
        send_bytes(w->client, MSG_CLIPBOARD, text, n);
        free(last_pushed);
        last_pushed = strdup(text);
    }
    free(text);
}

static void dd_selection(void *d, struct wl_data_device *dev, struct wl_data_offer *o)
{
    (void)d; (void)dev;
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
    (void)d; (void)s; (void)mime;
    if (our_text) {
        size_t left = strlen(our_text), off = 0;
        while (left > 0) {
            ssize_t w = write(fd, our_text + off, left);
            if (w <= 0) break;
            off += (size_t)w;
            left -= (size_t)w;
        }
    }
    close(fd);
}
static void src_cancelled(void *d, struct wl_data_source *s)
{
    (void)d;
    if (s == our_source) our_source = NULL;
    wl_data_source_destroy(s);
}
static void src_dnd_drop(void *d, struct wl_data_source *s) { (void)d; (void)s; }
static void src_dnd_finished(void *d, struct wl_data_source *s) { (void)d; (void)s; }
static void src_action(void *d, struct wl_data_source *s, uint32_t a) { (void)d; (void)s; (void)a; }
static const struct wl_data_source_listener src_listener = {
    .target = src_target, .send = src_send, .cancelled = src_cancelled,
    .dnd_drop_performed = src_dnd_drop, .dnd_finished = src_dnd_finished, .action = src_action };

/* The app copied text: become the selection owner. */
void clipboard_set(const char *text, size_t len)
{
    if (!data_dev || !data_mgr || len == 0) return;
    if (our_text && strlen(our_text) == len && !memcmp(our_text, text, len)) return;
    free(our_text);
    our_text = strndup(text, len);
    free(last_pushed);
    last_pushed = strdup(our_text); /* the compositor may echo our own selection back */
    struct wl_data_source *src = wl_data_device_manager_create_data_source(data_mgr);
    wl_data_source_add_listener(src, &src_listener, NULL);
    wl_data_source_offer(src, "text/plain;charset=utf-8");
    wl_data_source_offer(src, "text/plain");
    wl_data_source_offer(src, "UTF8_STRING");
    wl_data_device_set_selection(data_dev, src, last_input_serial);
    if (our_source) wl_data_source_destroy(our_source);
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
