/* uinput-keys: send real key / wheel events through the kernel to the compositor (VM tests).
 * Usage: uinput-keys <step>...   (needs /dev/uinput, i.e. root)
 *   key:<evdev code>      press+release      combo:<mod>+<code>   mod = alt|super|ctrl|shift
 *   wheel:<notches>       vertical wheel (+ = up)    sleep:<ms>
 *   move:<dx>,<dy>        relative pointer motion     click | rclick   (left / right button)
 *   altdrag:<dx>,<dy>     hold Alt + right button and move (interactive resize via the shell)
 */
#include <fcntl.h>
#include <linux/uinput.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

static int fd;
static void ev(int type, int code, int val)
{
    struct input_event e = { .type = type, .code = code, .value = val };
    if (write(fd, &e, sizeof e) < 0) perror("write");
}
static void syn(void) { ev(EV_SYN, SYN_REPORT, 0); }
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
    ioctl(fd, UI_SET_KEYBIT, BTN_LEFT);
    ioctl(fd, UI_SET_KEYBIT, BTN_RIGHT);
    ioctl(fd, UI_SET_EVBIT, EV_REL);
    ioctl(fd, UI_SET_RELBIT, REL_X);
    ioctl(fd, UI_SET_RELBIT, REL_Y);
    ioctl(fd, UI_SET_RELBIT, REL_WHEEL);
    struct uinput_setup us = { .id = { .bustype = BUS_USB, .vendor = 0x1234, .product = 0x5678 } };
    strcpy(us.name, "apklayer-test");
    ioctl(fd, UI_DEV_SETUP, &us);
    ioctl(fd, UI_DEV_CREATE);
    usleep(1500000); /* let the compositor pick the device up */
    for (int i = 1; i < argc; i++) {
        char *a = argv[i];
        int x, y;
        char mod[16];
        if (sscanf(a, "key:%d", &x) == 1) tap(x);
        else if (sscanf(a, "combo:%15[a-z]+%d", mod, &x) == 2) {
            key(modcode(mod), 1); usleep(80000); tap(x); key(modcode(mod), 0); usleep(100000);
        } else if (sscanf(a, "wheel:%d", &x) == 1) { ev(EV_REL, REL_WHEEL, x); syn(); usleep(100000); }
        else if (sscanf(a, "sleep:%d", &x) == 1) usleep((useconds_t)x * 1000);
        else if (sscanf(a, "move:%d,%d", &x, &y) == 2) { ev(EV_REL, REL_X, x); ev(EV_REL, REL_Y, y); syn(); usleep(60000); }
        else if (!strcmp(a, "click")) { key(BTN_LEFT, 1); usleep(50000); key(BTN_LEFT, 0); usleep(100000); }
        else if (!strcmp(a, "rclick")) { key(BTN_RIGHT, 1); usleep(50000); key(BTN_RIGHT, 0); usleep(100000); }
        else if (sscanf(a, "altdrag:%d,%d", &x, &y) == 2) {
            key(KEY_LEFTALT, 1); usleep(100000); key(BTN_RIGHT, 1); usleep(150000);
            for (int s = 1; s <= 10; s++) { ev(EV_REL, REL_X, x / 10); ev(EV_REL, REL_Y, y / 10); syn(); usleep(40000); }
            key(BTN_RIGHT, 0); usleep(100000); key(KEY_LEFTALT, 0); usleep(150000);
        } else fprintf(stderr, "unknown step %s\n", a);
    }
    usleep(300000);
    ioctl(fd, UI_DEV_DESTROY);
    close(fd);
    return 0;
}
