/* Native PipeWire playback for the Android audio HAL. Plain C across the ABI. */
#ifndef APKLAYER_AUDIO_H
#define APKLAYER_AUDIO_H
#include <stddef.h>
#include <stdint.h>
#include <sys/types.h>
#ifdef __cplusplus
extern "C" {
#endif
struct apklayer_audio;
enum apklayer_audio_format { APKLAYER_AUDIO_S16, APKLAYER_AUDIO_F32 };
struct apklayer_audio_config {
    uint32_t rate, channels;
    enum apklayer_audio_format format;
    const char *target; /* optional PipeWire target.object; NULL uses host routing */
};
struct apklayer_audio_position {
    uint64_t frames, timestamp_ns, pending_frames;
};
/* Output clock is derived from PipeWire's queued/resampler/device-delay report.
 * Calls are serialized by the owning HAL worker. close joins all callbacks. */
int apklayer_audio_open(const struct apklayer_audio_config *, struct apklayer_audio **);
ssize_t apklayer_audio_write(struct apklayer_audio *, const void *pcm, size_t frames);
int apklayer_audio_position(struct apklayer_audio *, struct apklayer_audio_position *);
int apklayer_audio_pause(struct apklayer_audio *);
int apklayer_audio_start(struct apklayer_audio *);
int apklayer_audio_flush(struct apklayer_audio *); /* discard and reset frame epoch */
int apklayer_audio_drain(struct apklayer_audio *);
void apklayer_audio_close(struct apklayer_audio *);
/* Capture: the same plain-C shape for a PipeWire input stream (the host's microphone, or a sink's monitor when config.target names a sink).
 * PCM is formatted as config says (S16 or F32); the stream keeps a ring of the last half second. position.frames counts frames read. */
struct apklayer_capture;
int apklayer_capture_open(const struct apklayer_audio_config *, struct apklayer_capture **);
/* Blocks until `frames` frames are read, or timeout_ns passes (then the frames read so far are returned, 0 if none: -EAGAIN). */
ssize_t apklayer_capture_read(struct apklayer_capture *, void *pcm, size_t frames, uint64_t timeout_ns);
int apklayer_capture_position(struct apklayer_capture *, struct apklayer_audio_position *);
int apklayer_capture_pause(struct apklayer_capture *);
int apklayer_capture_start(struct apklayer_capture *);
int apklayer_capture_flush(struct apklayer_capture *); /* drop what is buffered */
void apklayer_capture_close(struct apklayer_capture *);
#ifdef __cplusplus
}
#endif
#endif
