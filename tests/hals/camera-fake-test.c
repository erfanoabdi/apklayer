/* The fake camera backend (bridge/hals/camera/fake.c): options, the camera list, pattern frames with the bars' colours at a steady rate, and a
 * still image file decoded to the asked size. `meson test -C b camera-fake` */
#include "apklayer/camera.h"

#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

struct seen { int frames; int64_t first, last; uint8_t y[8], u[8], v[8]; uint32_t w, h; };

static void on_frame(void *user, const struct apklayer_camera_frame *f)
{
    struct seen *s = user;
    if (!s->frames++) s->first = f->timestamp_ns;
    s->last = f->timestamp_ns;
    s->w = f->width, s->h = f->height;
    for (int bar = 0; bar < 8; bar++) {  /* the centre of each bar, a quarter down */
        uint32_t x = (2 * bar + 1) * f->width / 16, y = f->height / 4;
        s->y[bar] = ((const uint8_t *)f->data[0])[y * f->stride[0] + x];
        s->u[bar] = ((const uint8_t *)f->data[1])[y / 2 * f->stride[1] + x / 2];
        s->v[bar] = ((const uint8_t *)f->data[2])[y / 2 * f->stride[2] + x / 2];
    }
}

static void on_error(void *user, int error) { (void)user, (void)error; abort(); }

int main(void)
{
    const char *bad[] = {"cameras=side"};
    assert(apklayer_camera_init(1, bad) < 0);
    const char *sized[] = {"sizes=640x480,1760x1328"}, *odd[] = {"sizes=641x480"};
    assert(apklayer_camera_init(1, odd) < 0 && apklayer_camera_init(1, sized) == 0);
    struct apklayer_camera_info one[2];
    assert(apklayer_camera_list(one, 2) == 2 && one[0].num_sizes == 2 && one[0].sizes[1][0] == 1760 && one[0].sizes[1][1] == 1328);
    const char *opts[] = {"cameras=back,front", "fps=30", "sizes=320x240,640x480,1280x720,1920x1080"};
    assert(apklayer_camera_init(3, opts) == 0);
    struct apklayer_camera_info info[4];
    assert(apklayer_camera_list(info, 4) == 2);
    assert(info[0].facing == APKLAYER_CAM_BACK && info[1].facing == APKLAYER_CAM_FRONT && info[0].num_sizes > 0);

    struct seen s = {0};
    struct apklayer_camera_callbacks cb = {&s, on_frame, on_error};
    struct apklayer_camera *cam = apklayer_camera_open(info[0].id, &cb);
    const uint32_t fourccs[] = {APKLAYER_CAM_I420, 0};
    assert(cam && apklayer_camera_start(cam, 640, 480, 30, fourccs) == 0);
    usleep(500000);
    apklayer_camera_stop(cam);
    static const uint8_t y[8] = {235, 210, 170, 145, 106, 81, 41, 16};
    assert(s.frames >= 12 && s.frames <= 18 && s.w == 640 && s.h == 480);
    assert(!memcmp(s.y, y, 8) && s.u[1] == 16 && s.v[5] == 240);
    printf("pattern: %d frames in %.0f ms\n", s.frames, (s.last - s.first) / 1e6);

    /* a still image: the pattern's first frame written as a PPM, read back through GStreamer at another size */
    char path[] = "/tmp/camera-fake-test-XXXXXX.ppm";
    int fd = mkstemps(path, 4);
    FILE *ppm = fdopen(fd, "w");
    fprintf(ppm, "P6 64 32 255\n");
    for (int i = 0; i < 64 * 32; i++) fputc(i % 64 < 32 ? 255 : 0, ppm), fputc(0, ppm), fputc(i % 64 < 32 ? 0 : 255, ppm);  /* red | blue */
    fclose(ppm);
    char source[64];
    snprintf(source, sizeof source, "source=%s", path);
    const char *file_opts[] = {source};
    assert(apklayer_camera_init(1, file_opts) == 0);
    memset(&s, 0, sizeof s);
    assert(apklayer_camera_start(cam, 320, 240, 15, fourccs) == 0);
    usleep(400000);
    apklayer_camera_close(cam);
    unlink(path);
    printf("image: %d frames, left Y %u V %u, right Y %u U %u\n", s.frames, s.y[1], s.v[1], s.y[6], s.u[6]);
    assert(s.frames >= 3 && s.w == 320 && s.h == 240);
    assert(s.v[1] > 200 && s.u[6] > 200);  /* red on the left, blue on the right */
    puts("PASS camera fake: options, list, pattern colours and rate, still image");
    return 0;
}
