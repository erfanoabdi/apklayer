/* Host decoder API for the Codec2 frontend: compressed access units in,
 * reference-counted YUV samples out. No Android or GStreamer types cross it. */
#ifndef APKLAYER_CODEC_H
#define APKLAYER_CODEC_H
#include <stddef.h>
#include <stdint.h>
#ifdef __cplusplus
extern "C" {
#endif
struct apklayer_codec;
struct apklayer_codec_frame;
struct apklayer_codec_config {
    const char *mime;
    uint32_t width, height;
    const void *codec_data;
    size_t codec_data_size; /* avc/hvc input uses Annex B; queue SPS/PPS as access units */
};
struct apklayer_codec_frame_info {
    uint32_t width, height, planes;
    char format[32]; /* GStreamer raw YUV format, e.g. I420 or NV12 */
    int64_t pts_ns; /* -1 if unknown */
    uint32_t fourcc;
    uint64_t modifier;
    int fd[4]; /* borrowed until release; -1 for host memory */
    int32_t stride[4];
    uint64_t offset[4]; /* byte offsets in fd[] for dmabufs */
};
/* Availability reflects the installed decoder registry; 0 means unsupported. */
int apklayer_codec_supports(const char *mime);
int apklayer_codec_open(const struct apklayer_codec_config *, struct apklayer_codec **);
/* Install once before queueing input. Streaming-thread notifications signal
 * sample/EOS/error availability; schedule work and return promptly, never pull
 * or close from the callback. Passing NULL disables it. close joins callbacks. */
int apklayer_codec_set_notify(struct apklayer_codec *, void (*ready)(void *), void *opaque);
/* Copies compressed input only. Timestamps are nanoseconds; -1 means unknown.
 * -EAGAIN means the bounded input queue is full: drain output before retrying. */
int apklayer_codec_queue(struct apklayer_codec *, const void *, size_t, int64_t pts_ns);
int apklayer_codec_end(struct apklayer_codec *);
/* 0 returns an owned frame; -EAGAIN: timeout; -ENODATA: drained EOS; other -errno: failure.
 * Decoding runs on GStreamer's streaming threads. No RGBA conversion/scaling. */
int apklayer_codec_pull(struct apklayer_codec *, uint64_t timeout_ns, struct apklayer_codec_frame **);
void apklayer_codec_frame_describe(const struct apklayer_codec_frame *, struct apklayer_codec_frame_info *);
/* Software fallback: maps linear YUV planes until release; dmabuf import needs no map. */
int apklayer_codec_frame_map(struct apklayer_codec_frame *, const void *planes[4]);
void apklayer_codec_frame_release(struct apklayer_codec_frame *);
void apklayer_codec_close(struct apklayer_codec *);
#ifdef __cplusplus
}
#endif
#endif
