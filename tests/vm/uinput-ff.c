/* uinput-ff: a uinput force-feedback (FF_RUMBLE) device for the vibrator ff backend's test (tests/vm/providers-host.sh). Needs /dev/uinput
 * (root). Prints its event node, makes it writable for the test's user, then logs every effect upload and play until <seconds> have passed:
 *   upload id=<id> strong=<magnitude> length=<ms>      play id=<id> value=<1 start|0 stop>
 * Usage: uinput-ff <seconds> */
#include <fcntl.h>
#include <linux/uinput.h>
#include <poll.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/stat.h>
#include <time.h>
#include <unistd.h>

int main(int argc, char **argv)
{
    int seconds = argc > 1 ? atoi(argv[1]) : 60;
    int fd = open("/dev/uinput", O_RDWR);
    if (fd < 0) { perror("/dev/uinput"); return 1; }
    ioctl(fd, UI_SET_EVBIT, EV_FF);
    ioctl(fd, UI_SET_FFBIT, FF_RUMBLE);
    struct uinput_setup setup = {.id = {.bustype = BUS_VIRTUAL, .vendor = 0x1d6b, .product = 0x0104}, .ff_effects_max = 4};
    strcpy(setup.name, "apklayer test vibra");
    if (ioctl(fd, UI_DEV_SETUP, &setup) < 0 || ioctl(fd, UI_DEV_CREATE) < 0) { perror("uinput setup"); return 1; }
    char name[64] = "", node[96];
    ioctl(fd, UI_GET_SYSNAME(sizeof name), name);
    snprintf(node, sizeof node, "/sys/devices/virtual/input/%s", name);
    /* the event node under it: eventN */
    char cmd[256];
    snprintf(cmd, sizeof cmd, "ls %s | grep '^event'", node);
    FILE *ls = popen(cmd, "r");
    char event[32] = "";
    if (!ls || !fgets(event, sizeof event, ls)) { fprintf(stderr, "no event node\n"); return 1; }
    pclose(ls);
    event[strcspn(event, "\n")] = 0;
    snprintf(node, sizeof node, "/dev/input/%s", event);
    for (int i = 0; i < 100 && access(node, F_OK); i++) usleep(10000);  /* udev creates the node */
    chmod(node, 0666);
    printf("node %s\n", node);
    fflush(stdout);
    time_t end = time(NULL) + seconds;
    while (time(NULL) < end) {
        struct pollfd p = {fd, POLLIN, 0};
        if (poll(&p, 1, 500) <= 0) continue;
        struct input_event e;
        if (read(fd, &e, sizeof e) != sizeof e) break;
        if (e.type == EV_UINPUT && e.code == UI_FF_UPLOAD) {
            struct uinput_ff_upload up = {.request_id = e.value};
            ioctl(fd, UI_BEGIN_FF_UPLOAD, &up);
            printf("upload id=%d strong=%u length=%u\n", up.effect.id, up.effect.u.rumble.strong_magnitude, up.effect.replay.length);
            up.retval = 0;
            ioctl(fd, UI_END_FF_UPLOAD, &up);
        } else if (e.type == EV_UINPUT && e.code == UI_FF_ERASE) {
            struct uinput_ff_erase er = {.request_id = e.value};
            ioctl(fd, UI_BEGIN_FF_ERASE, &er);
            er.retval = 0;
            ioctl(fd, UI_END_FF_ERASE, &er);
        } else if (e.type == EV_FF) {
            printf("play id=%d value=%d\n", e.code, e.value);
        }
        fflush(stdout);
    }
    ioctl(fd, UI_DEV_DESTROY);
    return 0;
}
