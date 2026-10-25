/* uinput-keys: send real key / pointer / wheel events through the kernel to the compositor (VM tests).
 * Usage: uinput-keys <step>...   (needs /dev/uinput, i.e. root)
 *   key:<evdev code>        press+release        combo:<mod>+<code>   mod = alt|super|ctrl|shift
 *   abs:<x>,<y>             absolute pointer, in permille of the screen (500,500 = centre)
 *   click | rclick          left / right button at the pointer      wheel:<notches> (+ = up)
 *   drag:<x1>,<y1>,<x2>,<y2>      left-button drag (title bar move, edge resize)
 *   altdrag:<x1>,<y1>,<x2>,<y2>   hold Alt, right button down at (x1,y1), drag to (x2,y2), release
 *   sleep:<ms>
 */
#include <fcntl.h>
#include <linux/uinput.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

static int fd, pfd; /* keyboard device, absolute pointer device */
static void evd(int d, int type, int code, int val)
{
    struct input_event e = { .type = type, .code = code, .value = val };
    if (write(d, &e, sizeof e) < 0) perror("write");
}
static void ev(int type, int code, int val) { evd(fd, type, code, val); }
static void syn(void) { ev(EV_SYN, SYN_REPORT, 0); }
static void psyn(void) { evd(pfd, EV_SYN, SYN_REPORT, 0); }
static void pbtn(int code, int down) { evd(pfd, EV_KEY, code, down); psyn(); usleep(30000); }
static void pabs(int px, int py)
{
    evd(pfd, EV_ABS, ABS_X, px * 32767 / 1000);
    evd(pfd, EV_ABS, ABS_Y, py * 32767 / 1000);
    psyn();
    usleep(60000);
}
static void key(int code, int down) { ev(EV_KEY, code, down); syn(); usleep(20000); }
static void tap(int code) { key(code, 1); usleep(30000); key(code, 0); usleep(60000); }
static int modcode(const char *m)
{
    if (!strcmp(m, "alt")) return KEY_LEFTALT;
    if (!strcmp(m, "super")) return KEY_LEFTMETA;
    if (!strcmp(m, "ctrl")) return KEY_LEFTCTRL;
    return KEY_LEFTSHIFT;
}
int main(int argc, char **argv)
{
    fd = open("/dev/uinput", O_WRONLY | O_NONBLOCK);
    if (fd < 0) { perror("/dev/uinput"); return 1; }
    ioctl(fd, UI_SET_EVBIT, EV_KEY);
    for (int c = 1; c < 250; c++) ioctl(fd, UI_SET_KEYBIT, c);
    struct uinput_setup us = { .id = { .bustype = BUS_USB, .vendor = 0x1234, .product = 0x5678 } };
    strcpy(us.name, "apklayer-test-keyboard");
    ioctl(fd, UI_DEV_SETUP, &us);
    ioctl(fd, UI_DEV_CREATE);
    /* absolute pointer like a VM tablet: ABS_X/Y + buttons + wheel */
    pfd = open("/dev/uinput", O_WRONLY | O_NONBLOCK);
    ioctl(pfd, UI_SET_EVBIT, EV_KEY);
    ioctl(pfd, UI_SET_KEYBIT, BTN_LEFT);
    ioctl(pfd, UI_SET_KEYBIT, BTN_RIGHT);
    ioctl(pfd, UI_SET_EVBIT, EV_REL);
    ioctl(pfd, UI_SET_RELBIT, REL_WHEEL);
    ioctl(pfd, UI_SET_EVBIT, EV_ABS);
    struct uinput_abs_setup ax = { .code = ABS_X, .absinfo = { .maximum = 32767 } };
    struct uinput_abs_setup ay = { .code = ABS_Y, .absinfo = { .maximum = 32767 } };
    ioctl(pfd, UI_ABS_SETUP, &ax);
    ioctl(pfd, UI_ABS_SETUP, &ay);
    struct uinput_setup ps = { .id = { .bustype = BUS_USB, .vendor = 0x1234, .product = 0x5679 } };
    strcpy(ps.name, "apklayer-test-pointer");
    ioctl(pfd, UI_DEV_SETUP, &ps);
    ioctl(pfd, UI_DEV_CREATE);
    usleep(1500000); /* let the compositor pick the device up */
    for (int i = 1; i < argc; i++) {
        char *a = argv[i];
        int x, y, z, w;
        char mod[16];
        if (sscanf(a, "key:%d", &x) == 1) tap(x);
        else if (sscanf(a, "combo:%15[a-z]+%d", mod, &x) == 2) {
            key(modcode(mod), 1); usleep(80000); tap(x); key(modcode(mod), 0); usleep(100000);
        } else if (sscanf(a, "wheel:%d", &x) == 1) { ev(EV_REL, REL_WHEEL, x); syn(); usleep(100000); }
        else if (sscanf(a, "sleep:%d", &x) == 1) usleep((useconds_t)x * 1000);
        else if (sscanf(a, "abs:%d,%d", &x, &y) == 2) pabs(x, y);
        else if (!strcmp(a, "click")) { pbtn(BTN_LEFT, 1); usleep(50000); pbtn(BTN_LEFT, 0); usleep(100000); }
        else if (!strcmp(a, "rclick")) { pbtn(BTN_RIGHT, 1); usleep(50000); pbtn(BTN_RIGHT, 0); usleep(100000); }
        else if (sscanf(a, "wheel:%d", &x) == 1) {
            evd(pfd, EV_REL, REL_WHEEL, x); psyn(); usleep(150000);
        }
        else if (sscanf(a, "drag:%d,%d,%d,%d", &x, &y, &z, &w) == 4) { /* left button drag */
            pabs(x, y); usleep(200000); pbtn(BTN_LEFT, 1); usleep(200000);
            for (int st = 1; st <= 12; st++) pabs(x + (z - x) * st / 12, y + (w - y) * st / 12);
            usleep(200000); pbtn(BTN_LEFT, 0); usleep(200000);
        }
        else if (sscanf(a, "altdrag:%d,%d,%d,%d", &x, &y, &z, &w) == 4) {
            pabs(x, y);
            key(KEY_LEFTALT, 1); usleep(150000); pbtn(BTN_RIGHT, 1); usleep(200000);
            for (int st = 1; st <= 12; st++) pabs(x + (z - x) * st / 12, y + (w - y) * st / 12);
            usleep(200000); pbtn(BTN_RIGHT, 0); usleep(150000); key(KEY_LEFTALT, 0); usleep(200000);
        } else fprintf(stderr, "unknown step %s\n", a);
    }
    usleep(300000);
    ioctl(pfd, UI_DEV_DESTROY);
    ioctl(fd, UI_DEV_DESTROY);
    close(fd);
    return 0;
}
