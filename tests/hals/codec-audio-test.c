/* Real AAC encode/decode, original timestamps, PCM frequency and retained samples. */
#include <apklayer/audio_codec.h>
#include <assert.h>
#include <errno.h>
#include <gst/app/gstappsink.h>
#include <math.h>
#include <stdio.h>
#include <string.h>
static void ready(void *opaque) { g_atomic_int_inc((gint *)opaque); }
static double energy(const int16_t *pcm, size_t frames, unsigned channels, unsigned channel, double hz) {
    double real = 0, imaginary = 0;
    for (size_t n = 0; n < frames; n++) {
        double phase = 2 * G_PI * hz * n / 48000;
        real += pcm[n * channels + channel] * cos(phase);
        imaginary += pcm[n * channels + channel] * sin(phase);
    }
    return real * real + imaginary * imaginary;
}
static void decode(unsigned channels) {
    char *description = g_strdup_printf("audiotestsrc num-buffers=48 samplesperbuffer=1024 freq=440 volume=0.2 ! "
        "audio/x-raw,rate=48000,channels=%u ! audioconvert ! avenc_aac bitrate=128000 ! appsink name=encoded sync=false", channels);
    GError *error = NULL;
    GstElement *encoder = gst_parse_launch(description, &error); g_free(description);
    assert(encoder && !error);
    GstAppSink *encoded = GST_APP_SINK(gst_bin_get_by_name(GST_BIN(encoder), "encoded"));
    assert(gst_element_set_state(encoder, GST_STATE_PLAYING) != GST_STATE_CHANGE_FAILURE);
    struct apklayer_codec *decoder = NULL;
    gint notifications = 0;
    unsigned packets = 0;
    for (; packets < 128; packets++) {
        GstSample *sample = gst_app_sink_try_pull_sample(encoded, GST_SECOND);
        if (!sample) { assert(gst_app_sink_is_eos(encoded)); break; }
        if (!decoder) {
            GstCaps *caps = gst_sample_get_caps(sample);
            const GValue *value = gst_structure_get_value(gst_caps_get_structure(caps, 0), "codec_data");
            assert(value && GST_VALUE_HOLDS_BUFFER(value));
            GstBuffer *data = gst_value_get_buffer(value); GstMapInfo config;
            assert(gst_buffer_map(data, &config, GST_MAP_READ));
            struct apklayer_audio_codec_config cfg = {"audio/mp4a-latm", 48000, channels, config.data, config.size};
            assert(apklayer_audio_codec_open(&cfg, &decoder) == 0);
            gst_buffer_unmap(data, &config);
            assert(apklayer_codec_set_notify(decoder, ready, &notifications) == 0);
        }
        GstBuffer *buffer = gst_sample_get_buffer(sample); GstMapInfo map;
        assert(gst_buffer_map(buffer, &map, GST_MAP_READ));
        int64_t pts = GST_BUFFER_PTS_IS_VALID(buffer) ? (int64_t)GST_BUFFER_PTS(buffer) : -1;
        assert(apklayer_codec_queue(decoder, map.data, map.size, pts) == 0);
        gst_buffer_unmap(buffer, &map); gst_sample_unref(sample);
    }
    assert(packets >= 48 && packets < 128 && decoder);
    assert(apklayer_codec_end(decoder) == 0);
    GByteArray *pcm = g_byte_array_new();
    struct apklayer_codec_frame *retained = NULL;
    int64_t last = -1;
    unsigned outputs = 0;
    for (; outputs < 128; outputs++) {
        struct apklayer_codec_frame *frame = NULL;
        int result = apklayer_codec_pull(decoder, GST_SECOND, &frame);
        if (result == -ENODATA) break;
        assert(result == 0 && frame);
        struct apklayer_audio_codec_frame_info info;
        assert(apklayer_audio_codec_frame_describe(frame, &info) == 0);
        assert(info.sample_rate == 48000 && info.channels == channels && !strcmp(info.format, "S16LE"));
        assert(info.frames > 0 && info.bytes == info.frames * channels * 2);
        assert(info.pts_ns >= last); last = info.pts_ns;
        const void *data; size_t size;
        assert(apklayer_audio_codec_frame_map(frame, &data, &size) == 0 && size == info.bytes);
        g_byte_array_append(pcm, data, size);
        const void *planes[4];
        assert(apklayer_codec_frame_map(frame, planes) == -EINVAL);
        if (!retained) retained = frame; else apklayer_codec_frame_release(frame);
    }
    assert(outputs >= 48 && outputs < 128 && g_atomic_int_get(&notifications) >= (int)outputs);
    size_t frames = pcm->len / (channels * 2);
    assert(frames >= 48000 && frames <= 52000);
    for (unsigned channel = 0; channel < channels; channel++)
        assert(energy((const int16_t *)pcm->data, frames, channels, channel, 440) >
               100 * energy((const int16_t *)pcm->data, frames, channels, channel, 1200));
    assert(apklayer_codec_set_notify(decoder, NULL, NULL) == 0);
    apklayer_codec_close(decoder);
    const void *data; size_t size;
    assert(apklayer_audio_codec_frame_map(retained, &data, &size) == 0 && size > 0);
    assert(!memcmp(data, pcm->data, size));
    apklayer_codec_frame_release(retained); g_byte_array_unref(pcm);
    gst_element_set_state(encoder, GST_STATE_NULL); gst_object_unref(encoded); gst_object_unref(encoder);
    printf("PASS native AAC %uch: %u packets/%u PCM samplesets/%zu frames, 440Hz, PTS/EOS and retained ownership\n", channels, packets, outputs, frames);
    fflush(stdout);
}
int main(void) {
    gst_init(NULL, NULL);
    assert(apklayer_codec_supports("audio/mp4a-latm"));
    struct apklayer_codec *bad = NULL;
    struct apklayer_audio_codec_config cfg = {"audio/mp4a-latm", 48000, 2, NULL, 0};
    assert(apklayer_audio_codec_open(&cfg, &bad) == -EINVAL && !bad);
    decode(2); decode(1);
    return 0;
}
