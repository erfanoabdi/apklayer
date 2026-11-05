/* apklayer/audio.h over PulseAudio (libpulse-simple), for hosts that run PulseAudio and no PipeWire daemon (Ubuntu Touch, Halium phones).
 * Same contract as audio.c/capture.c: playback blocks in write (the server's buffer paces the HAL worker, kept at ~100 ms), the output
 * clock is frames written minus the server's reported latency; capture is a thread filling a ring of the last half second.
 * The audio domain's "pulse" backend (libapklayer-audio-pulse.so, probe.c): the exported functions are the same as the PipeWire one's. */
#include <apklayer/audio.h>
#include <errno.h>
#include <pthread.h>
#include <pulse/error.h>
#include <pulse/simple.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

static uint64_t now_ns(void) { struct timespec t; clock_gettime(CLOCK_MONOTONIC, &t); return (uint64_t)t.tv_sec * 1000000000ull + (uint64_t)t.tv_nsec; }
static pa_sample_spec spec_of(const struct apklayer_audio_config *c) {
    return (pa_sample_spec){.format = c->format == APKLAYER_AUDIO_F32 ? PA_SAMPLE_FLOAT32LE : PA_SAMPLE_S16LE, .rate = c->rate, .channels = (uint8_t)c->channels};
}
static uint32_t stride_of(const struct apklayer_audio_config *c) { return c->channels * (c->format == APKLAYER_AUDIO_F32 ? 4 : 2); }

struct apklayer_audio {
    pa_simple *s;
    uint32_t rate, stride;
    uint64_t written, position;
    pthread_mutex_t lock;
    bool active;
};

int apklayer_audio_open(const struct apklayer_audio_config *config, struct apklayer_audio **out) {
    if (!config || !out || !config->rate || !config->channels || config->channels > 32) return -EINVAL;
    *out = NULL;
    pa_sample_spec ss = spec_of(config);
    uint32_t stride = stride_of(config);
    pa_buffer_attr attr = {.maxlength = (uint32_t)-1, .tlength = config->rate / 10 * stride, .prebuf = (uint32_t)-1, .minreq = (uint32_t)-1, .fragsize = (uint32_t)-1};
    int error = 0;
    pa_simple *s = pa_simple_new(NULL, "apklayer", PA_STREAM_PLAYBACK, config->target, "playback", &ss, NULL, &attr, &error);
    if (!s) { fprintf(stderr, "audio pulse: open: %s\n", pa_strerror(error)); return -EIO; }
    struct apklayer_audio *a = calloc(1, sizeof *a);
    if (!a) { pa_simple_free(s); return -ENOMEM; }
    a->s = s; a->rate = config->rate; a->stride = stride; a->active = true;
    pthread_mutex_init(&a->lock, NULL);
    *out = a;
    return 0;
}

ssize_t apklayer_audio_write(struct apklayer_audio *a, const void *pcm, size_t frames) {
    if (!a || (!pcm && frames)) return -EINVAL;
    if (!a->active) return -EAGAIN;
    int error = 0;
    if (frames && pa_simple_write(a->s, pcm, frames * a->stride, &error) < 0) { fprintf(stderr, "audio pulse: write: %s\n", pa_strerror(error)); return -EPIPE; }
    pthread_mutex_lock(&a->lock);
    a->written += frames;
    pthread_mutex_unlock(&a->lock);
    return (ssize_t)frames;
}

int apklayer_audio_position(struct apklayer_audio *a, struct apklayer_audio_position *out) {
    if (!a || !out) return -EINVAL;
    int error = 0;
    pa_usec_t latency = pa_simple_get_latency(a->s, &error);
    uint64_t pending = error ? 0 : (uint64_t)latency * a->rate / 1000000;
    pthread_mutex_lock(&a->lock);
    if (pending > a->written) pending = a->written;
    uint64_t position = a->written - pending;
    if (position > a->position) a->position = position;
    *out = (struct apklayer_audio_position){a->position, now_ns(), a->written - a->position};
    pthread_mutex_unlock(&a->lock);
    return 0;
}

int apklayer_audio_pause(struct apklayer_audio *a) { if (!a) return -EINVAL; a->active = false; return 0; }
int apklayer_audio_start(struct apklayer_audio *a) { if (!a) return -EINVAL; a->active = true; return 0; }

int apklayer_audio_flush(struct apklayer_audio *a) {
    if (!a) return -EINVAL;
    int error = 0;
    if (pa_simple_flush(a->s, &error) < 0) return -EIO;
    pthread_mutex_lock(&a->lock);
    a->written = a->position = 0;
    pthread_mutex_unlock(&a->lock);
    return 0;
}

int apklayer_audio_drain(struct apklayer_audio *a) {
    if (!a) return -EINVAL;
    if (!a->active) return -EAGAIN;
    int error = 0;
    return pa_simple_drain(a->s, &error) < 0 ? -EIO : 0;
}

void apklayer_audio_close(struct apklayer_audio *a) {
    if (!a) return;
    pa_simple_free(a->s);
    free(a);
}

/* capture */
struct apklayer_capture {
    pa_simple *s;
    pthread_t thread;
    pthread_mutex_t lock;
    pthread_cond_t cond;
    uint8_t *ring;
    size_t cap, head, fill;  /* bytes */
    uint32_t stride;
    uint64_t read_frames;
    bool stop, paused;
};

static void *capture_thread(void *opaque) {
    struct apklayer_capture *c = opaque;
    size_t chunk = 1024 * c->stride;
    uint8_t *buffer = malloc(chunk);
    int error = 0;
    while (buffer && !c->stop) {
        if (pa_simple_read(c->s, buffer, chunk, &error) < 0) break;
        pthread_mutex_lock(&c->lock);
        if (!c->paused) {
            if (c->fill + chunk > c->cap) { size_t drop = c->fill + chunk - c->cap; c->head = (c->head + drop) % c->cap; c->fill -= drop; }  /* the oldest goes */
            size_t tail = (c->head + c->fill) % c->cap, first = c->cap - tail < chunk ? c->cap - tail : chunk;
            memcpy(c->ring + tail, buffer, first);
            memcpy(c->ring, buffer + first, chunk - first);
            c->fill += chunk;
            pthread_cond_broadcast(&c->cond);
        }
        pthread_mutex_unlock(&c->lock);
    }
    free(buffer);
    return NULL;
}

int apklayer_capture_open(const struct apklayer_audio_config *config, struct apklayer_capture **out) {
    if (!config || !out || !config->rate || !config->channels || config->channels > 32) return -EINVAL;
    *out = NULL;
    pa_sample_spec ss = spec_of(config);
    uint32_t stride = stride_of(config);
    pa_buffer_attr attr = {.maxlength = (uint32_t)-1, .tlength = (uint32_t)-1, .prebuf = (uint32_t)-1, .minreq = (uint32_t)-1, .fragsize = 1024 * stride};
    int error = 0;
    pa_simple *s = pa_simple_new(NULL, "apklayer", PA_STREAM_RECORD, config->target, "capture", &ss, NULL, &attr, &error);
    if (!s) { fprintf(stderr, "audio pulse: capture open: %s\n", pa_strerror(error)); return -EIO; }
    struct apklayer_capture *c = calloc(1, sizeof *c);
    if (!c) { pa_simple_free(s); return -ENOMEM; }
    c->s = s; c->stride = stride;
    c->cap = (config->rate / 2 + 1024) * stride;
    c->ring = malloc(c->cap);
    pthread_mutex_init(&c->lock, NULL);
    pthread_cond_init(&c->cond, NULL);
    if (!c->ring || pthread_create(&c->thread, NULL, capture_thread, c)) { free(c->ring); pa_simple_free(s); free(c); return -ENOMEM; }
    *out = c;
    return 0;
}

ssize_t apklayer_capture_read(struct apklayer_capture *c, void *pcm, size_t frames, uint64_t timeout_ns) {
    if (!c || (!pcm && frames)) return -EINVAL;
    struct timespec end;
    clock_gettime(CLOCK_REALTIME, &end);
    uint64_t t = (uint64_t)end.tv_nsec + timeout_ns % 1000000000ull;
    end.tv_sec += (time_t)(timeout_ns / 1000000000ull + t / 1000000000ull);
    end.tv_nsec = (long)(t % 1000000000ull);
    size_t want = frames * c->stride, done = 0;
    pthread_mutex_lock(&c->lock);
    while (done < want) {
        if (c->fill) {
            size_t n = want - done < c->fill ? want - done : c->fill, first = c->cap - c->head < n ? c->cap - c->head : n;
            memcpy((uint8_t *)pcm + done, c->ring + c->head, first);
            memcpy((uint8_t *)pcm + done + first, c->ring, n - first);
            c->head = (c->head + n) % c->cap; c->fill -= n; done += n;
            continue;
        }
        if (pthread_cond_timedwait(&c->cond, &c->lock, &end)) break;
    }
    c->read_frames += done / c->stride;
    pthread_mutex_unlock(&c->lock);
    return done ? (ssize_t)(done / c->stride) : -EAGAIN;
}

int apklayer_capture_position(struct apklayer_capture *c, struct apklayer_audio_position *out) {
    if (!c || !out) return -EINVAL;
    pthread_mutex_lock(&c->lock);
    *out = (struct apklayer_audio_position){c->read_frames, now_ns(), c->fill / c->stride};
    pthread_mutex_unlock(&c->lock);
    return 0;
}
int apklayer_capture_pause(struct apklayer_capture *c) { if (!c) return -EINVAL; pthread_mutex_lock(&c->lock); c->paused = true; pthread_mutex_unlock(&c->lock); return 0; }
int apklayer_capture_start(struct apklayer_capture *c) { if (!c) return -EINVAL; pthread_mutex_lock(&c->lock); c->paused = false; pthread_mutex_unlock(&c->lock); return 0; }
int apklayer_capture_flush(struct apklayer_capture *c) { if (!c) return -EINVAL; pthread_mutex_lock(&c->lock); c->head = c->fill = 0; pthread_mutex_unlock(&c->lock); return 0; }
void apklayer_capture_close(struct apklayer_capture *c) {
    if (!c) return;
    c->stop = true;
    pthread_join(c->thread, NULL);  /* the reader returns within a fragment (the server keeps delivering) */
    pa_simple_free(c->s);
    free(c->ring);
    free(c);
}
