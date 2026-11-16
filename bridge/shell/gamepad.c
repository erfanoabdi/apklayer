/* Game controllers: Linux evdev gamepads (/dev/input/event*, a joystick's or gamepad's buttons plus ABS_X) opened as they appear (inotify, so
 * hotplug and Bluetooth pads work) and forwarded raw to the apps: MSG_PAD_DEVICE when one is added or removed (every app, and again to a new
 * app), MSG_PAD_EVENT with its evdev events (the focused window's app). The host maps them to Android's InputDevice, KeyEvents and
 * MotionEvents with the .kl key layouts (HostGamepad.java), as Android's InputReader does. Rumble (FF_RUMBLE) comes back as MSG_PAD_RUMBLE.
 * A node the user cannot read (no `input` group, no udev uaccess) is skipped, and tried again when its permissions change. */
#include "shell.h"
#include <dirent.h>
#include <linux/input.h>
#include <sys/inotify.h>
#include <sys/ioctl.h>

#define MAX_PADS 8
#define NAME_MAX_LEN 80
#define BITS(n) (((n) + 7) / 8)

struct pad {
    int fd, dev;          /* the id the apps know it by (counted up, never reused) */
    char path[300];
    int has_ff, effect;   /* rumble: an uploaded effect (-1: none yet) */
    int msg_len;
    uint8_t *msg;         /* its MSG_PAD_DEVICE payload, kept for apps that connect later */
};
static struct pad pads[MAX_PADS];
static int watch_fd = -1, next_dev = 1;

static int test_bit(const uint8_t *b, int n) { return b[n / 8] >> (n % 8) & 1; }
static const char *input_dir(void) { const char *d = getenv("APKLAYER_INPUT_DIR"); return d ? d : "/dev/input"; }  /* tests: another directory of event nodes */

/* the pad's description for the apps: dev, added, vendor, product, version, naxes, rumble, {code, min, max, flat, value}* + name */
static int describe(struct pad *p)
{
    uint8_t abs[BITS(ABS_CNT)] = {0};
    struct input_id id = {0};
    char name[NAME_MAX_LEN] = "gamepad";
    ioctl(p->fd, EVIOCGBIT(EV_ABS, sizeof abs), abs);
    ioctl(p->fd, EVIOCGID, &id);
    ioctl(p->fd, EVIOCGNAME(sizeof name), name);
    int32_t head[7] = { p->dev, 1, id.vendor, id.product, id.version, 0, p->has_ff };
    int32_t axes[ABS_CNT * 5];
    for (int c = 0; c < ABS_CNT; c++) {
        struct input_absinfo ai;
        if (!test_bit(abs, c) || ioctl(p->fd, EVIOCGABS(c), &ai) < 0) continue;
        int32_t *a = axes + head[5]++ * 5;
        a[0] = c; a[1] = ai.minimum; a[2] = ai.maximum; a[3] = ai.flat; a[4] = ai.value;
    }
    size_t n = sizeof head + (size_t)head[5] * 20, nl = strlen(name);
    p->msg = malloc(n + nl);
    if (!p->msg) return -1;
    memcpy(p->msg, head, sizeof head);
    memcpy(p->msg + sizeof head, axes, (size_t)head[5] * 20);
    memcpy(p->msg + n, name, nl);
    p->msg_len = (int)(n + nl);
    return 0;
}

static void broadcast(uint32_t type, const char *data, size_t len)
{
    for (int i = 0; i < MAX_CLIENTS; i++)
        if (clients[i].fd >= 0 && clients[i].pid) send_bytes(&clients[i], type, data, len);
}

static void try_open(const char *path)
{
    struct pad *p = NULL;
    for (int i = 0; i < MAX_PADS; i++) {
        if (pads[i].fd >= 0 && !strcmp(pads[i].path, path)) return;
        if (!p && pads[i].fd < 0) p = &pads[i];
    }
    int rw = 1, fd = open(path, O_RDWR | O_NONBLOCK | O_CLOEXEC);  /* read-write for force feedback */
    if (fd < 0) { rw = 0; fd = open(path, O_RDONLY | O_NONBLOCK | O_CLOEXEC); }
    if (fd < 0) return;
    uint8_t keys[BITS(KEY_CNT)] = {0}, abs[BITS(ABS_CNT)] = {0}, ff[BITS(FF_CNT)] = {0};
    ioctl(fd, EVIOCGBIT(EV_KEY, sizeof keys), keys);
    ioctl(fd, EVIOCGBIT(EV_ABS, sizeof abs), abs);
    ioctl(fd, EVIOCGBIT(EV_FF, sizeof ff), ff);
    /* a gamepad's or joystick's button and a stick (mice, touchpads, tablets and keyboards have neither) */
    if (!p || !(test_bit(keys, BTN_SOUTH) || test_bit(keys, BTN_TRIGGER)) || !test_bit(abs, ABS_X)) { close(fd); return; }
    memset(p, 0, sizeof *p);
    p->fd = fd; p->dev = next_dev++; p->effect = -1;
    snprintf(p->path, sizeof p->path, "%s", path);
    p->has_ff = rw && test_bit(ff, FF_RUMBLE);
    if (describe(p) < 0) { close(fd); p->fd = -1; return; }
    fprintf(stderr, "[shell] gamepad %d added: %s%s\n", p->dev, path, p->has_ff ? " (rumble)" : "");
    broadcast(MSG_PAD_DEVICE, (char *)p->msg, (size_t)p->msg_len);
}

static void pad_remove(struct pad *p)
{
    int32_t gone[2] = { p->dev, 0 };
    fprintf(stderr, "[shell] gamepad %d removed\n", p->dev);
    broadcast(MSG_PAD_DEVICE, (char *)gone, sizeof gone);
    close(p->fd);
    free(p->msg);
    memset(p, 0, sizeof *p);
    p->fd = -1;
}

void gamepad_init(void)
{
    for (int i = 0; i < MAX_PADS; i++) pads[i].fd = -1;
    watch_fd = inotify_init1(IN_NONBLOCK | IN_CLOEXEC);
    if (watch_fd >= 0) inotify_add_watch(watch_fd, input_dir(), IN_CREATE | IN_ATTRIB);
    DIR *d = opendir(input_dir());
    fprintf(stderr, "[shell] gamepads: watching %s (%s)\n", input_dir(), watch_fd >= 0 && d ? "ok" : strerror(errno));
    for (struct dirent *e; d && (e = readdir(d));) {
        char path[300];
        if (strncmp(e->d_name, "event", 5)) continue;
        snprintf(path, sizeof path, "%s/%s", input_dir(), e->d_name);
        try_open(path);
    }
    if (d) closedir(d);
}

/* a new app: the pads that are there */
void gamepad_client_hello(struct client *c)
{
    for (int i = 0; i < MAX_PADS; i++)
        if (pads[i].fd >= 0) send_bytes(c, MSG_PAD_DEVICE, (char *)pads[i].msg, (size_t)pads[i].msg_len);
}

void gamepad_pollfds(struct pollfd *p)
{
    p[0] = (struct pollfd){ .fd = watch_fd, .events = POLLIN };
    for (int i = 0; i < MAX_PADS; i++) p[1 + i] = (struct pollfd){ .fd = pads[i].fd, .events = POLLIN };
}

void gamepad_dispatch(struct pollfd *p)
{
    if (p[0].revents & POLLIN) {
        char buf[4096] __attribute__((aligned(8)));
        ssize_t n = read(watch_fd, buf, sizeof buf);
        for (char *q = buf; n > 0 && q < buf + n; q += sizeof(struct inotify_event) + ((struct inotify_event *)q)->len) {
            struct inotify_event *ev = (struct inotify_event *)q;
            char path[300];
            if (ev->len && !strncmp(ev->name, "event", 5)) {
                snprintf(path, sizeof path, "%s/%s", input_dir(), ev->name);
                try_open(path);
            }
        }
    }
    for (int i = 0; i < MAX_PADS; i++) {
        struct pad *pd = &pads[i];
        if (pd->fd < 0 || !p[1 + i].revents) continue;
        struct input_event ev[64];
        int32_t out[1 + 64 * 3];
        ssize_t r;
        while ((r = read(pd->fd, ev, sizeof ev)) > 0) {
            int n = 1;
            out[0] = pd->dev;
            for (size_t k = 0; k < (size_t)r / sizeof ev[0]; k++) {
                if (ev[k].type != EV_KEY && ev[k].type != EV_ABS && !(ev[k].type == EV_SYN && ev[k].code == SYN_REPORT)) continue;
                out[n++] = ev[k].type; out[n++] = ev[k].code; out[n++] = ev[k].value;
            }
            if (keyboard_focus && n > 1) {
                if (debug_input) fprintf(stderr, "[shell] gamepad %d: %d events\n", pd->dev, (n - 1) / 3);
                send_bytes(W_TARGET(keyboard_focus), MSG_PAD_EVENT, (char *)out, (size_t)n * 4);
            }
        }
        if (r < 0 && errno != EAGAIN && errno != EINTR) pad_remove(pd);  /* ENODEV: unplugged */
    }
}

/* MSG_PAD_RUMBLE {dev, strong 0..65535, weak, ms}: one rumble effect per pad, updated and played again; ms 0 stops it */
void gamepad_rumble(const int32_t *v, uint32_t len)
{
    if (len < 16) return;
    for (int i = 0; i < MAX_PADS; i++) {
        struct pad *p = &pads[i];
        if (p->fd < 0 || p->dev != v[0] || !p->has_ff) continue;
        if (debug_input) fprintf(stderr, "[shell] gamepad %d rumble %d/%d for %d ms\n", p->dev, v[1], v[2], v[3]);
        struct input_event play = { .type = EV_FF, .code = (uint16_t)p->effect, .value = 0 };
        if (v[3] > 0) {
            struct ff_effect e = { .type = FF_RUMBLE, .id = (int16_t)p->effect, .replay = { .length = (uint16_t)(v[3] > 0xffff ? 0xffff : v[3]) } };
            e.u.rumble.strong_magnitude = (uint16_t)v[1];
            e.u.rumble.weak_magnitude = (uint16_t)v[2];
            if (ioctl(p->fd, EVIOCSFF, &e) < 0) return;
            p->effect = e.id;
            play.code = (uint16_t)p->effect;
            play.value = 1;
        } else if (p->effect < 0) return;
        if (write(p->fd, &play, sizeof play) < 0) fprintf(stderr, "[shell] gamepad rumble: %s\n", strerror(errno));
        return;
    }
}
