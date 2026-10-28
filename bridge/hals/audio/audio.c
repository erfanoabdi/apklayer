/* PipeWire stream setup follows its playback tutorial and installed stream API.
 * The HAL worker appends PCM to a ring; the stream's process callback hands PipeWire one full buffer
 * (what it requested) per graph cycle. Queueing each write as its own buffer left every cycle with
 * a few hundred frames and the clock at a third of real time. No fabricated playback clock and no
 * helper process per track. */
#include <apklayer/audio.h>
#include <errno.h>
#include <pipewire/pipewire.h>
#include <pthread.h>
#include <spa/param/audio/format-utils.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

struct apklayer_audio {
    struct pw_thread_loop *loop;
    struct pw_stream *stream;
    enum pw_stream_state state;
    uint32_t rate, channels, format, stride;
    uint64_t written, position, pending;
    uint8_t *ring;
    size_t ring_cap, ring_head, ring_fill; /* bytes: not yet handed to PipeWire */
    bool running, active, drained, drain_requested;
};
static pthread_once_t initialized = PTHREAD_ONCE_INIT;
static void initialize(void) { pw_init(NULL, NULL); }
static void wake(void *opaque) {
    struct apklayer_audio *audio = opaque;
    pw_thread_loop_signal(audio->loop, false);
}
/* Runs in the loop thread, which holds the thread-loop lock: one buffer of what the graph asked for, from the ring. */
static void process(void *opaque) {
    struct apklayer_audio *audio = opaque;
    if (audio->ring_fill) {
        struct pw_buffer *buffer = pw_stream_dequeue_buffer(audio->stream);
        if (buffer && buffer->buffer->n_datas) {
            struct spa_data *data = &buffer->buffer->datas[0];
            size_t max = data->maxsize / audio->stride;
            size_t frames = buffer->requested ? buffer->requested : max;
            if (frames > max) frames = max;
            if (frames > audio->ring_fill / audio->stride) frames = audio->ring_fill / audio->stride;
            if (data->data && data->chunk && frames) {
                size_t bytes = frames * audio->stride, first = audio->ring_cap - audio->ring_head;
                if (first > bytes) first = bytes;
                memcpy(data->data, audio->ring + audio->ring_head, first);
                memcpy((char *)data->data + first, audio->ring, bytes - first);
                audio->ring_head = (audio->ring_head + bytes) % audio->ring_cap;
                audio->ring_fill -= bytes;
                data->chunk->offset = 0; data->chunk->stride = audio->stride; data->chunk->size = bytes;
                buffer->size = frames;
            } else if (data->chunk) {
                data->chunk->offset = 0; data->chunk->stride = audio->stride; data->chunk->size = 0;
                buffer->size = 0;
            }
            pw_stream_queue_buffer(audio->stream, buffer);
        }
    }
    wake(audio);
}
static void state_changed(void *opaque, enum pw_stream_state old, enum pw_stream_state state, const char *error) {
    (void)old;
    struct apklayer_audio *audio = opaque;
    audio->state = state;
    if (state == PW_STREAM_STATE_ERROR) fprintf(stderr, "[audio] PipeWire: %s\n", error ? error : "stream error");
    wake(audio);
}
static void drained(void *opaque) {
    struct apklayer_audio *audio = opaque;
    if (!audio->drain_requested) return;
    audio->drain_requested = false;
    audio->drained = true;
    audio->position = audio->written;
    audio->pending = 0;
    wake(audio);
}
static void format_changed(void *opaque, uint32_t id, const struct spa_pod *param) {
    if (id != SPA_PARAM_Format || !param) return;
    struct apklayer_audio *audio = opaque;
    struct spa_audio_info_raw info;
    if (spa_format_audio_raw_parse(param, &info) < 0 || info.rate != audio->rate ||
        info.channels != audio->channels || info.format != audio->format)
        pw_stream_set_error(audio->stream, -ENOTSUP, "unsupported negotiated PCM format");
    wake(audio);
}
static const struct pw_stream_events events = {
    PW_VERSION_STREAM_EVENTS, .state_changed = state_changed, .process = process, .drained = drained, .param_changed = format_changed,
};
static int wait_event(struct apklayer_audio *audio, const struct timespec *end) {
    if (audio->state == PW_STREAM_STATE_ERROR || audio->state == PW_STREAM_STATE_UNCONNECTED) return -EPIPE;
    return pw_thread_loop_timed_wait_full(audio->loop, end);
}
int apklayer_audio_open(const struct apklayer_audio_config *config, struct apklayer_audio **out) {
    if (!out) return -EINVAL;
    *out = NULL;
    if (!config || config->rate < 8000 || config->rate > 192000 ||
        (config->channels != 1 && config->channels != 2) ||
        (config->format != APKLAYER_AUDIO_S16 && config->format != APKLAYER_AUDIO_F32)) return -EINVAL;
    pthread_once(&initialized, initialize);
    struct apklayer_audio *audio = calloc(1, sizeof *audio);
    if (!audio) return -ENOMEM;
    audio->rate = config->rate;
    audio->channels = config->channels;
    audio->format = config->format == APKLAYER_AUDIO_S16 ? SPA_AUDIO_FORMAT_S16 : SPA_AUDIO_FORMAT_F32;
    audio->stride = config->channels * (config->format == APKLAYER_AUDIO_S16 ? 2 : 4);
    audio->active = true;
    audio->ring_cap = (config->rate / 10 > 4096 ? config->rate / 10 : 4096) * audio->stride;
    audio->ring = malloc(audio->ring_cap);
    if (!audio->ring) { free(audio); return -ENOMEM; }
    audio->loop = pw_thread_loop_new("apklayer-audio", NULL);
    if (!audio->loop) { free(audio->ring); free(audio); return -ENOMEM; }
    struct pw_properties *properties = pw_properties_new(PW_KEY_MEDIA_TYPE, "Audio",
        PW_KEY_MEDIA_CATEGORY, "Playback", PW_KEY_MEDIA_ROLE, "Music", PW_KEY_APP_NAME, "apklayer",
        PW_KEY_NODE_NAME, "apklayer.playback", NULL);
    if (config->target) pw_properties_set(properties, PW_KEY_TARGET_OBJECT, config->target);
    audio->stream = pw_stream_new_simple(pw_thread_loop_get_loop(audio->loop), "apklayer playback", properties, &events, audio);
    if (!audio->stream) { apklayer_audio_close(audio); return -EIO; }
    int result = pw_thread_loop_start(audio->loop);
    if (result < 0) { apklayer_audio_close(audio); return result; }
    audio->running = true;
    pw_thread_loop_lock(audio->loop);
    uint8_t buffer[1024];
    struct spa_pod_builder builder = SPA_POD_BUILDER_INIT(buffer, sizeof buffer);
    struct spa_audio_info_raw info = SPA_AUDIO_INFO_RAW_INIT(
        .format = config->format == APKLAYER_AUDIO_S16 ? SPA_AUDIO_FORMAT_S16 : SPA_AUDIO_FORMAT_F32,
        .rate = config->rate, .channels = config->channels);
    info.position[0] = config->channels == 1 ? SPA_AUDIO_CHANNEL_MONO : SPA_AUDIO_CHANNEL_FL;
    if (config->channels == 2) info.position[1] = SPA_AUDIO_CHANNEL_FR;
    const struct spa_pod *params[] = {spa_format_audio_raw_build(&builder, SPA_PARAM_EnumFormat, &info)};
    result = pw_stream_connect(audio->stream, PW_DIRECTION_OUTPUT, PW_ID_ANY,
        PW_STREAM_FLAG_AUTOCONNECT | PW_STREAM_FLAG_MAP_BUFFERS | PW_STREAM_FLAG_ASYNC | PW_STREAM_FLAG_EARLY_PROCESS, params, 1);
    struct timespec end;
    pw_thread_loop_get_time(audio->loop, &end, 2000000000);
    while (result >= 0 && audio->state < PW_STREAM_STATE_PAUSED) result = wait_event(audio, &end);
    pw_thread_loop_unlock(audio->loop);
    if (result < 0) { apklayer_audio_close(audio); return result; }
    *out = audio;
    return 0;
}
ssize_t apklayer_audio_write(struct apklayer_audio *audio, const void *pcm, size_t frames) {
    if (!audio || (!pcm && frames) || frames > SIZE_MAX / audio->stride) return -EINVAL;
    pw_thread_loop_lock(audio->loop);
    struct timespec end;
    pw_thread_loop_get_time(audio->loop, &end, 2000000000);
    size_t done = 0;
    int status = !audio->active ? -EAGAIN : audio->drain_requested ? -EBUSY : 0;
    while (done < frames && status >= 0) {
        if (audio->state == PW_STREAM_STATE_ERROR || audio->state == PW_STREAM_STATE_UNCONNECTED) { status = -EPIPE; break; }
        size_t space = audio->ring_cap - audio->ring_fill;
        if (space < audio->stride) { status = wait_event(audio, &end); continue; }
        size_t bytes = (frames - done) * audio->stride;
        if (bytes > space) bytes = space / audio->stride * audio->stride;
        size_t tail = (audio->ring_head + audio->ring_fill) % audio->ring_cap, first = audio->ring_cap - tail;
        if (first > bytes) first = bytes;
        memcpy(audio->ring + tail, (const char *)pcm + done * audio->stride, first);
        memcpy(audio->ring, (const char *)pcm + done * audio->stride + first, bytes - first);
        audio->ring_fill += bytes;
        done += bytes / audio->stride; audio->written += bytes / audio->stride; audio->drained = false;
    }
    pw_thread_loop_unlock(audio->loop);
    return done ? (ssize_t)done : status;
}
/* PipeWire stream.h documents queued + resampler buffered + extrapolated device
 * delay. Clamp to submitted frames and retain a monotonic presentation position. */
static int position_locked(struct apklayer_audio *audio, struct apklayer_audio_position *out) {
    uint64_t now = pw_stream_get_nsec(audio->stream);
    if (audio->active && !audio->drained) {
        struct pw_time time = {0};
        int result = pw_stream_get_time_n(audio->stream, &time, sizeof time);
        if (result == -EIO) result = 0;  // PipeWire 1.6: no timing before the stream runs (the zeroed time takes the branch below)
        if (result < 0) return result;
        if (audio->state != PW_STREAM_STATE_STREAMING || time.now <= 0 || !time.rate.num || !time.rate.denom) {
            audio->pending = audio->written - audio->position;
            *out = (struct apklayer_audio_position){audio->position, now, audio->pending};
            return 0;
        }
        long double delay = time.rate.denom ? (long double)time.delay * time.rate.num / time.rate.denom : 0;
        if (audio->state == PW_STREAM_STATE_STREAMING && now > (uint64_t)time.now) delay -= (long double)(now - time.now) / SPA_NSEC_PER_SEC;
        uint64_t pending = audio->ring_fill / audio->stride + time.queued + time.buffered + (delay > 0 ? delay * audio->rate : 0);
        audio->pending = pending;
        uint64_t position = audio->written > pending ? audio->written - pending : 0;
        if (position > audio->position) audio->position = position;
    }
    *out = (struct apklayer_audio_position){audio->position, now, audio->pending};
    return 0;
}
int apklayer_audio_position(struct apklayer_audio *audio, struct apklayer_audio_position *out) {
    if (!audio || !out) return -EINVAL;
    pw_thread_loop_lock(audio->loop);
    int result = position_locked(audio, out);
    pw_thread_loop_unlock(audio->loop);
    return result;
}
int apklayer_audio_pause(struct apklayer_audio *audio) {
    if (!audio) return -EINVAL;
    pw_thread_loop_lock(audio->loop);
    struct apklayer_audio_position position;
    int result = position_locked(audio, &position);
    if (result >= 0) result = pw_stream_set_active(audio->stream, false);
    if (result >= 0) audio->active = false;
    pw_thread_loop_unlock(audio->loop);
    return result;
}
int apklayer_audio_start(struct apklayer_audio *audio) {
    if (!audio) return -EINVAL;
    pw_thread_loop_lock(audio->loop);
    int result = pw_stream_set_active(audio->stream, true);
    if (result >= 0) audio->active = true;
    pw_thread_loop_unlock(audio->loop);
    return result;
}
int apklayer_audio_flush(struct apklayer_audio *audio) {
    if (!audio) return -EINVAL;
    pw_thread_loop_lock(audio->loop);
    int result = pw_stream_flush(audio->stream, false);
    if (result >= 0) { audio->ring_head = audio->ring_fill = 0; audio->written = audio->position = audio->pending = 0; audio->drained = audio->drain_requested = false; }
    pw_thread_loop_unlock(audio->loop);
    return result;
}
int apklayer_audio_drain(struct apklayer_audio *audio) {
    if (!audio) return -EINVAL;
    pw_thread_loop_lock(audio->loop);
    struct timespec end;
    pw_thread_loop_get_time(audio->loop, &end, 2000000000);
    int result = !audio->active ? -EAGAIN : 0;
    while (result >= 0 && audio->ring_fill) result = wait_event(audio, &end);
    if (result >= 0 && audio->written && !audio->drained) {
        audio->drain_requested = true;
        result = pw_stream_flush(audio->stream, true);
        if (result < 0) audio->drain_requested = false;
    }
    while (result >= 0 && audio->written && !audio->drained) result = wait_event(audio, &end);
    pw_thread_loop_unlock(audio->loop);
    return result;
}
void apklayer_audio_close(struct apklayer_audio *audio) {
    if (!audio) return;
    if (audio->running) pw_thread_loop_lock(audio->loop);
    if (audio->stream) { pw_stream_disconnect(audio->stream); pw_stream_destroy(audio->stream); }
    if (audio->running) { pw_thread_loop_unlock(audio->loop); pw_thread_loop_stop(audio->loop); }
    pw_thread_loop_destroy(audio->loop);
    free(audio->ring);
    free(audio);
}
