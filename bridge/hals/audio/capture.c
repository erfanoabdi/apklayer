/* PipeWire capture for the Android audio HAL's input streams: the process callback moves each captured buffer into a ring, reads take from it.
 * Same shape as audio.c (playback); the input stream is the host's default source, or a sink's monitor when the config names a sink. */
#include <apklayer/audio.h>
#include <errno.h>
#include <pipewire/pipewire.h>
#include <pthread.h>
#include <spa/param/audio/format-utils.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

struct apklayer_capture {
    struct pw_thread_loop *loop;
    struct pw_stream *stream;
    enum pw_stream_state state;
    uint32_t rate, channels, format, stride;
    uint64_t read_frames;
    uint8_t *ring;
    size_t ring_cap, ring_head, ring_fill; /* bytes */
    bool running, active;
};
static pthread_once_t capture_initialized = PTHREAD_ONCE_INIT;
static void capture_init(void) { pw_init(NULL, NULL); }
static void capture_wake(struct apklayer_capture *c) { pw_thread_loop_signal(c->loop, false); }
static void capture_state(void *opaque, enum pw_stream_state old, enum pw_stream_state state, const char *error) {
    (void)old;
    struct apklayer_capture *c = opaque;
    c->state = state;
    if (state == PW_STREAM_STATE_ERROR) fprintf(stderr, "[audio] PipeWire capture: %s\n", error ? error : "stream error");
    capture_wake(c);
}
/* Loop thread (lock held): the newest half second stays, older frames are dropped when nobody reads. */
static void capture_process(void *opaque) {
    struct apklayer_capture *c = opaque;
    struct pw_buffer *buffer = pw_stream_dequeue_buffer(c->stream);
    if (!buffer) return;
    struct spa_data *data = &buffer->buffer->datas[0];
    if (data->data && data->chunk) {
        const uint8_t *src = (const uint8_t *)data->data + data->chunk->offset;
        size_t bytes = data->chunk->size / c->stride * c->stride;
        if (bytes > c->ring_cap) { src += bytes - c->ring_cap; bytes = c->ring_cap; }
        if (c->ring_fill + bytes > c->ring_cap) { /* drop the oldest */
            size_t drop = c->ring_fill + bytes - c->ring_cap;
            c->ring_head = (c->ring_head + drop) % c->ring_cap;
            c->ring_fill -= drop;
        }
        size_t tail = (c->ring_head + c->ring_fill) % c->ring_cap, first = c->ring_cap - tail;
        if (first > bytes) first = bytes;
        memcpy(c->ring + tail, src, first);
        memcpy(c->ring, src + first, bytes - first);
        c->ring_fill += bytes;
    }
    pw_stream_queue_buffer(c->stream, buffer);
    capture_wake(c);
}
static void capture_format(void *opaque, uint32_t id, const struct spa_pod *param) {
    if (id != SPA_PARAM_Format || !param) return;
    struct apklayer_capture *c = opaque;
    struct spa_audio_info_raw info;
    if (spa_format_audio_raw_parse(param, &info) < 0 || info.rate != c->rate || info.channels != c->channels || info.format != c->format)
        pw_stream_set_error(c->stream, -ENOTSUP, "unsupported negotiated PCM format");
    capture_wake(c);
}
static const struct pw_stream_events capture_events = {
    PW_VERSION_STREAM_EVENTS, .state_changed = capture_state, .process = capture_process, .param_changed = capture_format,
};
int apklayer_capture_open(const struct apklayer_audio_config *config, struct apklayer_capture **out) {
    if (!out) return -EINVAL;
    *out = NULL;
    if (!config || config->rate < 8000 || config->rate > 192000 || (config->channels != 1 && config->channels != 2) ||
        (config->format != APKLAYER_AUDIO_S16 && config->format != APKLAYER_AUDIO_F32)) return -EINVAL;
    pthread_once(&capture_initialized, capture_init);
    struct apklayer_capture *c = calloc(1, sizeof *c);
    if (!c) return -ENOMEM;
    c->rate = config->rate;
    c->channels = config->channels;
    c->format = config->format == APKLAYER_AUDIO_S16 ? SPA_AUDIO_FORMAT_S16 : SPA_AUDIO_FORMAT_F32;
    c->stride = config->channels * (config->format == APKLAYER_AUDIO_S16 ? 2 : 4);
    c->active = true;
    c->ring_cap = (size_t)(config->rate / 2) * c->stride;
    c->ring = malloc(c->ring_cap);
    c->loop = pw_thread_loop_new("apklayer-capture", NULL);
    if (!c->ring || !c->loop) { free(c->ring); if (c->loop) pw_thread_loop_destroy(c->loop); free(c); return -ENOMEM; }
    struct pw_properties *properties = pw_properties_new(PW_KEY_MEDIA_TYPE, "Audio", PW_KEY_MEDIA_CATEGORY, "Capture",
        PW_KEY_MEDIA_ROLE, "Communication", PW_KEY_APP_NAME, "apklayer", PW_KEY_NODE_NAME, "apklayer.capture", NULL);
    if (config->target) { /* a sink's name: its monitor (what the machine plays); a source's: that device */
        pw_properties_set(properties, PW_KEY_TARGET_OBJECT, config->target);
        pw_properties_set(properties, PW_KEY_STREAM_CAPTURE_SINK, "true");
    }
    c->stream = pw_stream_new_simple(pw_thread_loop_get_loop(c->loop), "apklayer capture", properties, &capture_events, c);
    if (!c->stream) { apklayer_capture_close(c); return -EIO; }
    int result = pw_thread_loop_start(c->loop);
    if (result < 0) { apklayer_capture_close(c); return result; }
    c->running = true;
    pw_thread_loop_lock(c->loop);
    uint8_t buffer[1024];
    struct spa_pod_builder builder = SPA_POD_BUILDER_INIT(buffer, sizeof buffer);
    struct spa_audio_info_raw info = SPA_AUDIO_INFO_RAW_INIT(.format = c->format, .rate = config->rate, .channels = config->channels);
    info.position[0] = config->channels == 1 ? SPA_AUDIO_CHANNEL_MONO : SPA_AUDIO_CHANNEL_FL;
    if (config->channels == 2) info.position[1] = SPA_AUDIO_CHANNEL_FR;
    const struct spa_pod *params[] = {spa_format_audio_raw_build(&builder, SPA_PARAM_EnumFormat, &info)};
    result = pw_stream_connect(c->stream, PW_DIRECTION_INPUT, PW_ID_ANY,
        PW_STREAM_FLAG_AUTOCONNECT | PW_STREAM_FLAG_MAP_BUFFERS | PW_STREAM_FLAG_ASYNC, params, 1);
    struct timespec end;
    pw_thread_loop_get_time(c->loop, &end, 2000000000);
    while (result >= 0 && c->state < PW_STREAM_STATE_PAUSED) {
        if (c->state == PW_STREAM_STATE_ERROR) { result = -EIO; break; }
        result = pw_thread_loop_timed_wait_full(c->loop, &end);
    }
    pw_thread_loop_unlock(c->loop);
    if (result < 0) { apklayer_capture_close(c); return result; }
    *out = c;
    return 0;
}
ssize_t apklayer_capture_read(struct apklayer_capture *c, void *pcm, size_t frames, uint64_t timeout_ns) {
    if (!c || (!pcm && frames) || frames > SIZE_MAX / c->stride) return -EINVAL;
    pw_thread_loop_lock(c->loop);
    struct timespec end;
    pw_thread_loop_get_time(c->loop, &end, (int64_t)timeout_ns);
    size_t done = 0;
    int status = !c->active ? -EAGAIN : 0;
    while (done < frames && status >= 0) {
        if (c->state == PW_STREAM_STATE_ERROR || c->state == PW_STREAM_STATE_UNCONNECTED) { status = -EPIPE; break; }
        size_t have = c->ring_fill / c->stride;
        if (!have) {
            status = pw_thread_loop_timed_wait_full(c->loop, &end);
            if (status == -ETIMEDOUT) { status = 0; break; }
            continue;
        }
        size_t take = frames - done < have ? frames - done : have, bytes = take * c->stride, first = c->ring_cap - c->ring_head;
        if (first > bytes) first = bytes;
        memcpy((char *)pcm + done * c->stride, c->ring + c->ring_head, first);
        memcpy((char *)pcm + done * c->stride + first, c->ring, bytes - first);
        c->ring_head = (c->ring_head + bytes) % c->ring_cap;
        c->ring_fill -= bytes;
        done += take;
        c->read_frames += take;
    }
    pw_thread_loop_unlock(c->loop);
    return done ? (ssize_t)done : status < 0 ? status : -EAGAIN;
}
int apklayer_capture_position(struct apklayer_capture *c, struct apklayer_audio_position *out) {
    if (!c || !out) return -EINVAL;
    pw_thread_loop_lock(c->loop);
    *out = (struct apklayer_audio_position){c->read_frames, pw_stream_get_nsec(c->stream), c->ring_fill / c->stride};
    pw_thread_loop_unlock(c->loop);
    return 0;
}
int apklayer_capture_pause(struct apklayer_capture *c) {
    if (!c) return -EINVAL;
    pw_thread_loop_lock(c->loop);
    int result = pw_stream_set_active(c->stream, false);
    if (result >= 0) c->active = false;
    pw_thread_loop_unlock(c->loop);
    return result;
}
int apklayer_capture_start(struct apklayer_capture *c) {
    if (!c) return -EINVAL;
    pw_thread_loop_lock(c->loop);
    int result = pw_stream_set_active(c->stream, true);
    if (result >= 0) c->active = true;
    pw_thread_loop_unlock(c->loop);
    return result;
}
int apklayer_capture_flush(struct apklayer_capture *c) {
    if (!c) return -EINVAL;
    pw_thread_loop_lock(c->loop);
    c->ring_head = c->ring_fill = 0;
    pw_thread_loop_unlock(c->loop);
    return 0;
}
void apklayer_capture_close(struct apklayer_capture *c) {
    if (!c) return;
    if (c->running) pw_thread_loop_lock(c->loop);
    if (c->stream) { pw_stream_disconnect(c->stream); pw_stream_destroy(c->stream); }
    if (c->running) { pw_thread_loop_unlock(c->loop); pw_thread_loop_stop(c->loop); }
    pw_thread_loop_destroy(c->loop);
    free(c->ring);
    free(c);
}
