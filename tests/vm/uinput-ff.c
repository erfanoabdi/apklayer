/* uinput-ff: a uinput force-feedback (FF_RUMBLE) device for the vibrator ff backend's test (tests/vm/providers-host.sh). Needs /dev/uinput
 * (root). Makes its event node writable for the test's user, prints "<node> <pid>" and returns; that pid logs every effect upload and
 * play into <log> until <seconds> have passed (or it is killed):
 *   upload id=<id> strong=<magnitude> length=<ms>      play id=<id> value=<1 start|0 stop>
 * Usage: uinput-ff <seconds> <log> */
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
    if (argc != 3) { fprintf(stderr, "usage: uinput-ff <seconds> <log>\n"); return 2; }
    int seconds = atoi(argv[1]);
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
    if (system("udevadm settle")) fprintf(stderr, "udevadm settle failed\n");  /* udev creates the node and sets its mode: ours comes after */
    chmod(node, 0666);
    if (!freopen(argv[2], "w", stdout)) { perror(argv[2]); return 1; }
    pid_t child = fork();
    if (child) { fprintf(stderr, "%s %d\n", node, (int)child); return child < 0; }  /* the caller's line; the child serves the device */
    close(STDERR_FILENO);  /* the caller's $(...) ends with the parent */
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
