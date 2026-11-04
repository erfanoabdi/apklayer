/* The PipeWire camera backend's streams: a capture stream to one camera node (pipewire-list.c lists them), raw frames in the first of the
 * provider's formats the camera offers, near the asked size and rate; mapped buffers handed over on PipeWire's thread. */
#include "apklayer/camera.h"
#include "pipewire.h"

#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

#include <pipewire/pipewire.h>
#include <spa/param/video/format-utils.h>

struct apklayer_camera {
    char id[64];
    struct apklayer_camera_callbacks cb;
    struct pw_thread_loop *loop;
    struct pw_stream *stream;
    struct spa_hook listener;
    uint32_t fourcc, width, height, fps, sequence;
    int ready;  /* 0 negotiating, 1 format known, <0 -errno; under the loop's lock */
};

uint32_t pw_camera_fourcc(uint32_t f)
{
    switch (f) {
        case SPA_VIDEO_FORMAT_I420: return APKLAYER_CAM_I420;
        case SPA_VIDEO_FORMAT_NV12: return APKLAYER_CAM_NV12;
        case SPA_VIDEO_FORMAT_YUY2: return APKLAYER_CAM_YUYV;
        case SPA_VIDEO_FORMAT_BGRx: return APKLAYER_CAM_XRGB;
        default: return 0;
    }
}

static uint32_t spa_format_of(uint32_t fourcc)
{
    switch (fourcc) {
        case APKLAYER_CAM_I420: return SPA_VIDEO_FORMAT_I420;
        case APKLAYER_CAM_NV12: return SPA_VIDEO_FORMAT_NV12;
        case APKLAYER_CAM_YUYV: return SPA_VIDEO_FORMAT_YUY2;
        case APKLAYER_CAM_XRGB: return SPA_VIDEO_FORMAT_BGRx;
        default: return SPA_VIDEO_FORMAT_UNKNOWN;
    }
}

static void on_state(void *data, enum pw_stream_state old, enum pw_stream_state state, const char *error)
{
    (void)old;
    struct apklayer_camera *c = data;
    if (state != PW_STREAM_STATE_ERROR && state != PW_STREAM_STATE_UNCONNECTED) return;
    if (c->ready == 1 && c->cb.error) c->cb.error(c->cb.user, -EPIPE);
    if (!c->ready) c->ready = -EIO;
    if (error) fprintf(stderr, "camera pipewire: %s: %s\n", c->id, error);
    pw_thread_loop_signal(c->loop, false);
}

static void on_param_changed(void *data, uint32_t id, const struct spa_pod *param)
{
    struct apklayer_camera *c = data;
    if (id != SPA_PARAM_Format || !param) return;
    struct spa_video_info_raw info;
    if (spa_format_video_raw_parse(param, &info) < 0 || !pw_camera_fourcc(info.format)) {
        pw_stream_set_error(c->stream, -ENOTSUP, "unsupported video format");
        return;
    }
    c->fourcc = pw_camera_fourcc(info.format), c->width = info.size.width, c->height = info.size.height;
    c->fps = info.framerate.denom ? info.framerate.num / info.framerate.denom : 0;
    /* buffers the provider can read: plain or memfd memory (a dmabuf may be tiled) */
    uint8_t buffer[256];
    struct spa_pod_builder b = SPA_POD_BUILDER_INIT(buffer, sizeof buffer);
    const struct spa_pod *params[] = {spa_pod_builder_add_object(&b, SPA_TYPE_OBJECT_ParamBuffers, SPA_PARAM_Buffers,
        SPA_PARAM_BUFFERS_dataType, SPA_POD_CHOICE_FLAGS_Int((1 << SPA_DATA_MemPtr) | (1 << SPA_DATA_MemFd)))};
    pw_stream_update_params(c->stream, params, 1);
    c->ready = 1;
    pw_thread_loop_signal(c->loop, false);
}

static void on_process(void *data)
{
    struct apklayer_camera *c = data;
    struct pw_buffer *b = pw_stream_dequeue_buffer(c->stream);
    if (!b) return;
    struct spa_buffer *buf = b->buffer;
    uint32_t planes = c->fourcc == APKLAYER_CAM_I420 ? 3 : c->fourcc == APKLAYER_CAM_NV12 ? 2 : 1;
    struct apklayer_camera_frame f = {.fourcc = c->fourcc, .width = c->width, .height = c->height, .num_planes = planes,
                                      .dmabuf_fd = {-1, -1, -1, -1}, .sequence = c->sequence++};
    struct timespec now;
    clock_gettime(CLOCK_MONOTONIC, &now);
    f.timestamp_ns = now.tv_sec * 1000000000LL + now.tv_nsec;  /* shortcut: the dequeue time, not the sensor's (SPA_META_Header pts) */
    int ok = buf->n_datas > 0 && buf->datas[0].data && buf->datas[0].chunk->size > 0;
    for (uint32_t p = 0; ok && p < planes; p++) {
        if (p < buf->n_datas) {  /* a data per plane */
            struct spa_data *d = &buf->datas[p];
            ok = d->data != NULL;
            f.data[p] = ok ? (const uint8_t *)d->data + d->chunk->offset : NULL;
            f.stride[p] = d->chunk->stride, f.size[p] = d->chunk->size;
        } else {  /* the planes one after the other in one data (V4L2 single-planar) */
            uint32_t prev_rows = p == 1 ? c->height : c->height / 2;
            f.stride[p] = c->fourcc == APKLAYER_CAM_NV12 ? f.stride[0] : f.stride[0] / 2;
            f.data[p] = (const uint8_t *)f.data[p - 1] + f.stride[p - 1] * prev_rows;
            f.size[p] = f.stride[p] * (c->height / 2);
        }
    }
    if (ok && f.stride[0] && c->cb.frame) c->cb.frame(c->cb.user, &f);
    pw_stream_queue_buffer(c->stream, b);
}

static const struct pw_stream_events stream_events = {
    PW_VERSION_STREAM_EVENTS, .state_changed = on_state, .param_changed = on_param_changed, .process = on_process,
};

struct apklayer_camera *apklayer_camera_open(const char *id, const struct apklayer_camera_callbacks *callbacks)
{
    struct apklayer_camera *c = calloc(1, sizeof *c);
    if (!c) return NULL;
    snprintf(c->id, sizeof c->id, "%s", id);
    c->cb = *callbacks;
    return c;
}

int apklayer_camera_stop(struct apklayer_camera *c)
{
    if (!c->loop) return 0;
    pw_thread_loop_lock(c->loop);
    if (c->stream) pw_stream_destroy(c->stream);
    c->stream = NULL;
    pw_thread_loop_unlock(c->loop);
    pw_thread_loop_stop(c->loop);
    pw_thread_loop_destroy(c->loop);
    c->loop = NULL;
    return 0;
}

void apklayer_camera_close(struct apklayer_camera *c)
{
    if (!c) return;
    apklayer_camera_stop(c);
    free(c);
}

int apklayer_camera_start(struct apklayer_camera *c, uint32_t width, uint32_t height, uint32_t fps, const uint32_t *fourccs)
{
    apklayer_camera_stop(c);
    pw_init(NULL, NULL);
    c->loop = pw_thread_loop_new("apklayer-camera", NULL);
    if (!c->loop) return -ENOMEM;
    struct pw_properties *props = pw_properties_new(PW_KEY_MEDIA_TYPE, "Video", PW_KEY_MEDIA_CATEGORY, "Capture", PW_KEY_MEDIA_ROLE, "Camera",
                                                    PW_KEY_TARGET_OBJECT, c->id, NULL);
    c->stream = pw_stream_new_simple(pw_thread_loop_get_loop(c->loop), "apklayer camera", props, &stream_events, c);
    if (!c->stream || pw_thread_loop_start(c->loop) < 0) { apklayer_camera_stop(c); return -EIO; }
    pw_thread_loop_lock(c->loop);
    uint8_t buffer[2048];
    struct spa_pod_builder b = SPA_POD_BUILDER_INIT(buffer, sizeof buffer);
    const struct spa_pod *params[4];
    uint32_t n = 0;
    for (const uint32_t *f = fourccs; *f && n < 4; f++) {  /* one EnumFormat per format, in the provider's order of preference */
        uint32_t format = spa_format_of(*f);
        if (format == SPA_VIDEO_FORMAT_UNKNOWN) continue;
        params[n++] = spa_pod_builder_add_object(&b, SPA_TYPE_OBJECT_Format, SPA_PARAM_EnumFormat,
            SPA_FORMAT_mediaType, SPA_POD_Id(SPA_MEDIA_TYPE_video), SPA_FORMAT_mediaSubtype, SPA_POD_Id(SPA_MEDIA_SUBTYPE_raw),
            SPA_FORMAT_VIDEO_format, SPA_POD_Id(format),
            SPA_FORMAT_VIDEO_size, SPA_POD_CHOICE_RANGE_Rectangle(&SPA_RECTANGLE(width, height), &SPA_RECTANGLE(16, 16), &SPA_RECTANGLE(8192, 8192)),
            SPA_FORMAT_VIDEO_framerate, SPA_POD_CHOICE_RANGE_Fraction(&SPA_FRACTION(fps, 1), &SPA_FRACTION(0, 1), &SPA_FRACTION(240, 1)));
    }
    c->ready = 0;
    int result = n ? pw_stream_connect(c->stream, PW_DIRECTION_INPUT, PW_ID_ANY, PW_STREAM_FLAG_AUTOCONNECT | PW_STREAM_FLAG_MAP_BUFFERS, params, n) : -EINVAL;
    struct timespec end;
    pw_thread_loop_get_time(c->loop, &end, 3000000000LL);
    while (result == 0 && !c->ready)
        if (pw_thread_loop_timed_wait_full(c->loop, &end) != 0) result = -ETIMEDOUT;
    if (result == 0 && c->ready < 0) result = c->ready;
    pw_thread_loop_unlock(c->loop);
    if (result < 0) { fprintf(stderr, "camera pipewire: %s: no stream (%s)\n", c->id, strerror(-result)); apklayer_camera_stop(c); }
    return result < 0 ? result : 0;
}

void apklayer_camera_format(const struct apklayer_camera *c, uint32_t *fourcc, uint32_t *width, uint32_t *height, uint32_t *fps)
{
    *fourcc = c->fourcc, *width = c->width, *height = c->height, *fps = c->fps;
}
