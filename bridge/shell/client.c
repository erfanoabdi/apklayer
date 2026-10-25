/* App connections: the unix socket protocol (see shell.h) and sending messages to apps. */
#include "shell.h"

/* ---- sending to apps ---- */

void send_msg(struct client *c, uint32_t type, const int32_t *v, int n)
{
    if (!c || c->fd < 0) return;
    uint32_t buf[2 + 8] = { type, (uint32_t)(n * 4) };
    memcpy(buf + 2, v, (size_t)n * 4);
    if (write(c->fd, buf, 8 + (size_t)n * 4) < 0 && errno != EAGAIN) {
        fprintf(stderr, "[shell] write to %s failed: %s\n", c->pkg, strerror(errno));
    }
}

/* Variable-length message (text payloads): header and data in one write. */
void send_bytes(struct client *c, uint32_t type, const char *data, size_t len)
{
    if (!c || c->fd < 0 || len > 60000) return;
    uint8_t *buf = malloc(8 + len);
    if (!buf) return;
    uint32_t hdr[2] = { type, (uint32_t)len };
    memcpy(buf, hdr, 8);
    memcpy(buf + 8, data, len);
    if (write(c->fd, buf, 8 + len) < 0 && errno != EAGAIN)
        fprintf(stderr, "[shell] write to %s failed: %s\n", c->pkg, strerror(errno));
    free(buf);
}

void send_input(struct window *w, int32_t type, int32_t a, int32_t b)
{
    if (!w) return;
    if (type <= EV_SECONDARY) { a = a * w->scale / 100; b = b * w->scale / 100; } /* logical -> px */
    int32_t v[4] = { w->id, type, a, b };
    send_msg(w->client, MSG_INPUT, v, 4);
}


/* ---- app connections ---- */

static void client_drop(struct client *c)
{
    for (int i = 0; i < MAX_WINDOWS; i++)
        if (windows[i].client == c) window_destroy(&windows[i]);
    fprintf(stderr, "[shell] %s (pid %d) disconnected\n", c->pkg[0] ? c->pkg : "?", c->pid);
    close(c->fd);
    memset(c, 0, sizeof *c);
    c->fd = -1;
}

static void client_message(struct client *c, uint32_t type, const uint8_t *p, uint32_t len)
{
    const int32_t *v = (const int32_t *)p;
    switch (type) {
    case MSG_HELLO:
        if (len < 4) return;
        c->pid = v[0];
        snprintf(c->pkg, sizeof c->pkg, "%.*s", (int)(len - 4), (const char *)p + 4);
        fprintf(stderr, "[shell] %s (pid %d) connected\n", c->pkg, c->pid);
        break;
    case MSG_WINDOW_CREATE: {
        if (len < 13 || len - 12 >= 1024) return;
        char text[1024];                        /* "path\0title", copied by length */
        memcpy(text, p + 12, len - 12);
        text[len - 12] = 0;
        size_t path_len = strlen(text);
        const char *title = path_len + 1 < len - 12 ? text + path_len + 1 : "";
        window_create(c, v[0], v[1], v[2], text, title);
        break;
    }
    case MSG_TEXT_INPUT: {
        struct window *w = len >= 8 ? window_find(c, v[0]) : NULL;
        if (w) text_input_enable(w, v[1]);
        break;
    }
    case MSG_CLIPBOARD_SET:
        clipboard_set((const char *)p, len);
        break;
    case MSG_FRAME: {
        struct window *w = len >= 24 ? window_find(c, v[0]) : NULL;
        if (w) window_frame(w, v[1], v + 2);
        break;
    }
    case MSG_WINDOW_DESTROY: {
        struct window *w = len >= 4 ? window_find(c, v[0]) : NULL;
        if (w) window_destroy(w);
        break;
    }
    }
}

void client_read(struct client *c)
{
    ssize_t n = read(c->fd, c->rx + c->rx_len, sizeof c->rx - c->rx_len);
    if (n <= 0) { client_drop(c); return; }
    c->rx_len += (size_t)n;
    size_t off = 0;
    while (c->rx_len - off >= 8) {
        uint32_t type, len;
        memcpy(&type, c->rx + off, 4);
        memcpy(&len, c->rx + off + 4, 4);
        if (len > sizeof c->rx - 8) { client_drop(c); return; }
        if (c->rx_len - off - 8 < len) break;
        client_message(c, type, c->rx + off + 8, len);
        if (c->fd < 0) return;
        off += 8 + len;
    }
    memmove(c->rx, c->rx + off, c->rx_len - off);
    c->rx_len -= off;
}

