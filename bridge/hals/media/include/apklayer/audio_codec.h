/* Compressed host audio -> owned interleaved PCM samples; no Android/GStreamer ABI. */
#ifndef APKLAYER_AUDIO_CODEC_H
#define APKLAYER_AUDIO_CODEC_H
#include <apklayer/codec.h>
#ifdef __cplusplus
extern "C" {
#endif
struct apklayer_audio_codec_config {
    const char *mime;
    uint32_t sample_rate, channels;
    const void *codec_data; /* AAC AudioSpecificConfig; raw AAC access units */
    size_t codec_data_size;
};
struct apklayer_audio_codec_frame_info {
    uint32_t sample_rate, channels;
    uint64_t frames, bytes;
    int64_t pts_ns;
    char format[16]; /* S16LE, interleaved; original decoded sample rate/channels */
};
int apklayer_audio_codec_open(const struct apklayer_audio_codec_config *, struct apklayer_codec **);
/* Use existing queue/end/pull/notify/release/close functions for the same decoder. */
int apklayer_audio_codec_frame_describe(const struct apklayer_codec_frame *, struct apklayer_audio_codec_frame_info *);
int apklayer_audio_codec_frame_map(struct apklayer_codec_frame *, const void **data, size_t *size);
#ifdef __cplusplus
}
#endif
#endif
