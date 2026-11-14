/* The PipeWire camera backend against the desktop's PipeWire (tests/vm/camera-pipewire.sh provides a camera node): the list, one second of
 * frames of each camera at its smallest size (an MJPEG-only one: JPEG frames, first byte 255). Usage: camera-pipewire-test [option...] (the backend's options, e.g. role=any) */
#include "apklayer/camera.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

static int frames;
static uint32_t fourcc, width, height, y0;

static void on_frame(void *user, const struct apklayer_camera_frame *f)
{
    (void)user;
    frames++, fourcc = f->fourcc, width = f->width, height = f->height;
    y0 = ((const uint8_t *)f->data[0])[0];
}

static void on_error(void *user, int error) { (void)user; fprintf(stderr, "stream error %d\n", error); }

int main(int argc, char **argv)
{
    if (apklayer_camera_init(argc - 1, (const char *const *)argv + 1) < 0) return 2;
    printf("probe %d\n", apklayer_camera_probe());
    struct apklayer_camera_info info[8];
    int n = 0;
    const char *wait = getenv("CAMERA_WAIT");  /* the fixture's cameras by name ("a,b"); default: any camera */
    for (int tries = 0, found = 0; tries < 30 && !found; tries++) {  /* the fixture's nodes appear a moment after it starts */
        n = apklayer_camera_list(info, 8);
        found = n > 0;
        for (const char *w = wait; found && w && *w; w += strcspn(w, ",") + (w[strcspn(w, ",")] == ',')) {
            int named = 0;
            for (int i = 0; i < n; i++) named |= strlen(info[i].name) == strcspn(w, ",") && !strncmp(info[i].name, w, strcspn(w, ","));
            found = named;
        }
        if (!found) usleep(100000);
    }
    for (int i = 0; i < n; i++)
        printf("camera %s \"%s\" facing %d, orientation %d, %d sizes, largest %ux%u\n", info[i].id, info[i].name, info[i].facing,
               info[i].orientation, info[i].num_sizes,
               info[i].sizes[info[i].num_sizes - 1][0], info[i].sizes[info[i].num_sizes - 1][1]);
    if (n < 1) { puts("FAIL no camera"); return 1; }
    struct apklayer_camera_callbacks cb = {NULL, on_frame, on_error};
    const uint32_t fourccs[] = {APKLAYER_CAM_I420, APKLAYER_CAM_NV12, APKLAYER_CAM_YUYV, APKLAYER_CAM_XRGB, APKLAYER_CAM_MJPEG, 0};
    for (int i = 0; i < n; i++) {  /* every camera (or the waited-for ones), at its smallest size */
        if (wait && !strstr(wait, info[i].name)) continue;
        struct apklayer_camera *cam = apklayer_camera_open(info[i].id, &cb);
        frames = 0;
        int r = apklayer_camera_start(cam, info[i].sizes[0][0], info[i].sizes[0][1], 30, fourccs);
        if (r < 0) { printf("FAIL %s: start %d\n", info[i].id, r); return 1; }
        for (int t = 0; t < 100 && !frames; t++) usleep(100000);  /* a camera may take seconds to start (an iPhone over USB: ~4 s) */
        sleep(1);
        apklayer_camera_close(cam);
        printf("%s: %d frames %.4s %ux%u, first byte %u\n", info[i].name, frames, (char *)&fourcc, width, height, y0);
        if (frames < 5) { puts("FAIL too few frames"); return 1; }
    }
    puts("PASS camera pipewire: list, stream");
    return 0;
}
