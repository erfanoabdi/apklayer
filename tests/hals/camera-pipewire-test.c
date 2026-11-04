/* The PipeWire camera backend against the desktop's PipeWire (tests/vm/camera-pipewire.sh provides a camera node): the list, one second of
 * frames at 640x480. Usage: camera-pipewire-test [option...] (the backend's options, e.g. role=any) */
#include "apklayer/camera.h"

#include <stdio.h>
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
    for (int tries = 0; tries < 30 && n < 1; tries++)  /* the fixture's node appears a moment after it starts */
        if ((n = apklayer_camera_list(info, 8)) < 1) usleep(100000);
    for (int i = 0; i < n; i++)
        printf("camera %s \"%s\" facing %d, %d sizes, largest %ux%u\n", info[i].id, info[i].name, info[i].facing, info[i].num_sizes,
               info[i].sizes[info[i].num_sizes - 1][0], info[i].sizes[info[i].num_sizes - 1][1]);
    if (n < 1) { puts("FAIL no camera"); return 1; }
    struct apklayer_camera_callbacks cb = {NULL, on_frame, on_error};
    struct apklayer_camera *cam = apklayer_camera_open(info[0].id, &cb);
    const uint32_t fourccs[] = {APKLAYER_CAM_I420, APKLAYER_CAM_NV12, APKLAYER_CAM_YUYV, APKLAYER_CAM_XRGB, 0};
    int r = apklayer_camera_start(cam, 640, 480, 30, fourccs);
    if (r < 0) { printf("FAIL start %d\n", r); return 1; }
    sleep(1);
    apklayer_camera_close(cam);
    printf("%d frames %.4s %ux%u, first byte %u\n", frames, (char *)&fourcc, width, height, y0);
    if (frames < 5) { puts("FAIL too few frames"); return 1; }
    puts("PASS camera pipewire: list, stream");
    return 0;
}
