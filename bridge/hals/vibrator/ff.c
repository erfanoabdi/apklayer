/* The Linux force-feedback vibrator: an evdev device with FF_RUMBLE (phone vibra motors such as drv260x, pm8xxx-vibrator,
 * regulator-haptic; gamepads). on uploads one rumble effect (strong motor at the last amplitude, replay length the duration) and plays it,
 * off stops it. Option device=/dev/input/eventN; by default the first rumble device we may write to, preferring one that is not a gamepad.
 * probe: a writable non-gamepad rumble device is a default candidate; a gamepad alone is usable but never the default (a controller is not
 * the device's vibrator: Android gives controllers their own InputDevice vibrators). */
#include "apklayer/vibrator.h"

#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <linux/input.h>
#include <stdio.h>
#include <sys/ioctl.h>
#include <string.h>
#include <unistd.h>

#define BIT(array, bit) ((array)[(bit) / 8] & (1 << ((bit) % 8)))

static int fd = -1;
static struct ff_effect effect = {.type = FF_RUMBLE, .id = -1};
static float amplitude = 1.0f;

/* 2: writable rumble device, 1: writable rumble gamepad, 0: none */
static int rank(int dev)
{
    unsigned char ff[FF_MAX / 8 + 1] = {0}, keys[KEY_MAX / 8 + 1] = {0};
    if (ioctl(dev, EVIOCGBIT(EV_FF, sizeof ff), ff) < 0 || !BIT(ff, FF_RUMBLE)) return 0;
    ioctl(dev, EVIOCGBIT(EV_KEY, sizeof keys), keys);
    return BIT(keys, BTN_GAMEPAD) || BIT(keys, BTN_JOYSTICK) ? 1 : 2;
}

/* the best rumble device under /dev/input (its fd, opened read-write), or -1 */
static int find(int *best_rank, char *name, size_t size)
{
    DIR *dir = opendir("/dev/input");
    int best = -1;
    *best_rank = 0;
    for (struct dirent *e; dir && (e = readdir(dir));) {
        if (strncmp(e->d_name, "event", 5)) continue;
        char path[300];
        snprintf(path, sizeof path, "/dev/input/%s", e->d_name);
        int dev = open(path, O_RDWR | O_CLOEXEC), r = dev < 0 ? 0 : rank(dev);
        if (r > *best_rank) {
            if (best >= 0) close(best);
            best = dev, *best_rank = r;
            snprintf(name, size, "%s", path);
        } else if (dev >= 0) close(dev);
    }
    if (dir) closedir(dir);
    return best;
}

const int apklayer_plugin_abi = APKLAYER_PLUGIN_ABI;
const char apklayer_vibrator_needs[] = "a force-feedback input device that can rumble";  /* shown when probe() finds nothing (plugin.h) */
int apklayer_vibrator_probe(void)
{
    char path[300];
    int r, dev = find(&r, path, sizeof path);
    if (dev >= 0) close(dev);
    return r == 2 ? 10 : r == 1 ? 0 : -1;
}

int apklayer_vibrator_init(int count, const char *const *options)
{
    char path[300] = "";
    for (int i = 0; i < count; i++) {
        if (!strncmp(options[i], "device=", 7)) snprintf(path, sizeof path, "%s", options[i] + 7);
        else { fprintf(stderr, "vibrator ff: unknown option %s\n", options[i]); return -EINVAL; }
    }
    int r = 0;
    if (*path) {
        if ((fd = open(path, O_RDWR | O_CLOEXEC)) >= 0 && !rank(fd)) { close(fd); fd = -1; errno = ENOTSUP; }
    } else {
        fd = find(&r, path, sizeof path);
        errno = ENODEV;
    }
    if (fd < 0) { fprintf(stderr, "vibrator ff: %s: %s\n", *path ? path : "no writable rumble device", strerror(errno)); return -errno; }
    fprintf(stderr, "vibrator ff: %s\n", path);
    return 0;
}

int apklayer_vibrator_on(int32_t duration_ms)
{
    effect.u.rumble.strong_magnitude = (unsigned short)(amplitude * 0xffff);
    effect.u.rumble.weak_magnitude = 0;
    effect.replay.length = duration_ms > 0xffff ? 0xffff : duration_ms;  /* the kernel's limit: about 65 s */
    if (ioctl(fd, EVIOCSFF, &effect) < 0) return -errno;  /* id -1 uploads a new effect, then the same id is updated */
    struct input_event play = {.type = EV_FF, .code = effect.id, .value = 1};
    return write(fd, &play, sizeof play) == sizeof play ? 0 : -errno;
}

int apklayer_vibrator_off(void)
{
    if (effect.id < 0) return 0;
    struct input_event stop = {.type = EV_FF, .code = effect.id, .value = 0};
    return write(fd, &stop, sizeof stop) == sizeof stop ? 0 : -errno;
}

int apklayer_vibrator_amplitude(float value)
{
    amplitude = value;
    return 0;
}
