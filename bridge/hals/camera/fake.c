/* The fake camera backend (libapklayer-camera-fake.so): deterministic frames for tests and for apps that need a camera to start.
 * Options: source=pattern (default: 100% colour bars over the top three quarters, a white square moving across black below) or the path
 * of a still image or a video file (decoded by GStreamer, fake-file.c, looped); cameras=back,front (the facings, one camera each);
 * sizes=WxH,... (the sizes it streams; default 320x240,640x480,1280x720,1920x1080); fps=30. Frames are I420 at the size the provider asks for. */
#include "apklayer/camera.h"
#include "fake.h"

#include <errno.h>
#include <pthread.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

static char source[1024] = "pattern";
static int facings[2] = {APKLAYER_CAM_BACK, APKLAYER_CAM_FRONT}, n_facings = 2;
static int max_fps = 30;
static uint32_t sizes[APKLAYER_CAMERA_MAX_SIZES][2] = {{320, 240}, {640, 480}, {1280, 720}, {1920, 1080}};
static int n_sizes = 4;

/* BT.601 limited-range Y, U, V of the bars: white, yellow, cyan, green, magenta, red, blue, black (tests/apps/hello-camera checks them) */
static const uint8_t bars[8][3] = {
    {235, 128, 128}, {210, 16, 146}, {170, 166, 16}, {145, 54, 34}, {106, 202, 222}, {81, 90, 240}, {41, 240, 110}, {16, 128, 128},
};

struct apklayer_camera {
    struct apklayer_camera_callbacks cb;
    pthread_t thread;
    int running;          /* read by the capture thread */
    uint32_t width, height, fps;
    pthread_mutex_t lock;
    pthread_cond_t ready_cond;
    int ready;            /* 0 starting, 1 streaming, <0 -errno */
};

int apklayer_camera_probe(void) { return 0; }

int apklayer_camera_init(int count, const char *const *options)
{
    for (int i = 0; i < count; i++) {
        const char *eq = strchr(options[i], '=');
        if (!eq) return -EINVAL;
        size_t key = eq - options[i];
        const char *v = eq + 1;
        if (key == 6 && !strncmp(options[i], "source", 6)) snprintf(source, sizeof source, "%s", v);
        else if (key == 5 && !strncmp(options[i], "sizes", 5)) {  /* WxH,WxH...: the sizes the camera streams (a real camera's list) */
            n_sizes = 0;
            for (const char *p = v; *p && n_sizes < APKLAYER_CAMERA_MAX_SIZES; p += strcspn(p, ",") + (p[strcspn(p, ",")] == ',')) {
                unsigned w, h;
                if (sscanf(p, "%ux%u", &w, &h) != 2 || w < 16 || h < 16 || (w | h) & 1) return -EINVAL;
                sizes[n_sizes][0] = w, sizes[n_sizes++][1] = h;
            }
            if (!n_sizes) return -EINVAL;
        }
        else if (key == 3 && !strncmp(options[i], "fps", 3)) { max_fps = atoi(v); if (max_fps < 1 || max_fps > 120) return -EINVAL; }
        else if (key == 7 && !strncmp(options[i], "cameras", 7)) {
            n_facings = 0;
            for (const char *p = v; *p && n_facings < 2; p += strcspn(p, ",") + (p[strcspn(p, ",")] == ',')) {
                if (!strncmp(p, "back", 4)) facings[n_facings++] = APKLAYER_CAM_BACK;
                else if (!strncmp(p, "front", 5)) facings[n_facings++] = APKLAYER_CAM_FRONT;
                else return -EINVAL;
            }
            if (!n_facings) return -EINVAL;
        } else {
            fprintf(stderr, "camera fake: unknown option %s\n", options[i]);
            return -EINVAL;
        }
    }
    return 0;
}

int apklayer_camera_list(struct apklayer_camera_info *out, int max)
{
    int n = 0;
    for (int i = 0; i < n_facings && n < max; i++, n++) {
        struct apklayer_camera_info *c = &out[n];
        memset(c, 0, sizeof *c);
        int back = facings[i] == APKLAYER_CAM_BACK;
        snprintf(c->id, sizeof c->id, "fake-%s", back ? "back" : "front");
        snprintf(c->name, sizeof c->name, "apklayer fake camera (%s)", back ? "back" : "front");
        c->facing = facings[i];
        c->orientation = back ? 90 : 270;  /* a phone's: the sensor's long side along the device's short side */
        c->max_fps = max_fps;
        c->num_sizes = n_sizes;
        memcpy(c->sizes, sizes, sizeof c->sizes);
    }
    return n;
}

struct apklayer_camera *apklayer_camera_open(const char *id, const struct apklayer_camera_callbacks *callbacks)
{
    if (strcmp(id, "fake-back") && strcmp(id, "fake-front")) return NULL;
    struct apklayer_camera *c = calloc(1, sizeof *c);
    if (!c) return NULL;
    c->cb = *callbacks;
    pthread_mutex_init(&c->lock, NULL);
    pthread_cond_init(&c->ready_cond, NULL);
    return c;
}

void apklayer_camera_close(struct apklayer_camera *c)
{
    if (!c) return;
    apklayer_camera_stop(c);
    pthread_cond_destroy(&c->ready_cond);
    pthread_mutex_destroy(&c->lock);
    free(c);
}

void fake_report_ready(struct apklayer_camera *c, int result)
{
    pthread_mutex_lock(&c->lock);
    c->ready = result;
    pthread_cond_broadcast(&c->ready_cond);
    pthread_mutex_unlock(&c->lock);
}

int fake_running(const struct apklayer_camera *c) { return __atomic_load_n(&c->running, __ATOMIC_ACQUIRE); }

/* one I420 frame to the provider */
void fake_deliver(struct apklayer_camera *c, const uint8_t *const planes[3], const uint32_t strides[3], uint32_t w, uint32_t h,
                  int64_t timestamp_ns, uint32_t sequence)
{
    struct apklayer_camera_frame f = {
        .fourcc = APKLAYER_CAM_I420, .width = w, .height = h, .num_planes = 3,
        .data = {planes[0], planes[1], planes[2]},
        .stride = {strides[0], strides[1], strides[2]},
        .size = {strides[0] * h, strides[1] * (h / 2), strides[2] * (h / 2)},
        .dmabuf_fd = {-1, -1, -1, -1},
        .timestamp_ns = timestamp_ns, .sequence = sequence,
    };
    c->cb.frame(c->cb.user, &f);
}

static void draw_pattern(uint8_t *buf, uint32_t w, uint32_t h, uint32_t sequence)
{
    uint8_t *y = buf, *u = buf + w * h, *v = u + (w / 2) * (h / 2);
    uint32_t top = h * 3 / 4 & ~1u, box = w / 8 & ~1u, x0 = (sequence * 8) % (w - box) & ~1u;
    for (uint32_t row = 0; row < h; row++)
        for (uint32_t col = 0; col < w; col++) {
            const uint8_t *px = row < top ? bars[col * 8 / w] : (col >= x0 && col < x0 + box && row - top < box) ? bars[0] : bars[7];
            y[row * w + col] = px[0];
            if (!(row & 1) && !(col & 1)) u[row / 2 * (w / 2) + col / 2] = px[1], v[row / 2 * (w / 2) + col / 2] = px[2];
        }
}

static void *pattern_thread(void *arg)
{
    struct apklayer_camera *c = arg;
    uint8_t *buf = malloc(c->width * c->height * 3 / 2);
    fake_report_ready(c, buf ? 1 : -ENOMEM);
    struct timespec next;
    clock_gettime(CLOCK_MONOTONIC, &next);
    for (uint32_t seq = 0; buf && fake_running(c); seq++) {
        draw_pattern(buf, c->width, c->height, seq);
        const uint8_t *planes[3] = {buf, buf + c->width * c->height, buf + c->width * c->height * 5 / 4};
        const uint32_t strides[3] = {c->width, c->width / 2, c->width / 2};
        fake_deliver(c, planes, strides, c->width, c->height, next.tv_sec * 1000000000LL + next.tv_nsec, seq);
        next.tv_nsec += 1000000000L / c->fps;
        if (next.tv_nsec >= 1000000000L) next.tv_sec++, next.tv_nsec -= 1000000000L;
        clock_nanosleep(CLOCK_MONOTONIC, TIMER_ABSTIME, &next, NULL);
    }
    free(buf);
    return NULL;
}

int apklayer_camera_start(struct apklayer_camera *c, uint32_t width, uint32_t height, uint32_t fps, const uint32_t *fourccs)
{
    int i420 = 0;
    for (const uint32_t *f = fourccs; *f; f++) i420 |= *f == APKLAYER_CAM_I420;
    if (!i420 || width < 16 || height < 16 || (width | height) & 1) return -EINVAL;
    apklayer_camera_stop(c);
    c->width = width, c->height = height, c->fps = fps < 1 || (int)fps > max_fps ? (uint32_t)max_fps : fps;
    c->ready = 0;
    __atomic_store_n(&c->running, 1, __ATOMIC_RELEASE);
    int file = strcmp(source, "pattern") != 0;
    if (pthread_create(&c->thread, NULL, file ? fake_file_thread : pattern_thread, c)) { c->running = 0; return -EAGAIN; }
    pthread_mutex_lock(&c->lock);
    while (!c->ready) pthread_cond_wait(&c->ready_cond, &c->lock);
    int result = c->ready;
    pthread_mutex_unlock(&c->lock);
    if (result < 0) apklayer_camera_stop(c);
    return result < 0 ? result : 0;
}

int apklayer_camera_stop(struct apklayer_camera *c)
{
    if (!__atomic_exchange_n(&c->running, 0, __ATOMIC_ACQ_REL)) return 0;
    pthread_join(c->thread, NULL);
    return 0;
}

void apklayer_camera_format(const struct apklayer_camera *c, uint32_t *fourcc, uint32_t *width, uint32_t *height, uint32_t *fps)
{
    *fourcc = APKLAYER_CAM_I420, *width = c->width, *height = c->height, *fps = c->fps;
}

const char *fake_source(void) { return source; }
uint32_t fake_width(const struct apklayer_camera *c) { return c->width; }
uint32_t fake_height(const struct apklayer_camera *c) { return c->height; }
uint32_t fake_fps(const struct apklayer_camera *c) { return c->fps; }
