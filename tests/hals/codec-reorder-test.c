/* Actual H.264 B-frame decode: coded-order input and presentation-order output. */
#include <apklayer/codec.h>
#include <assert.h>
#include <errno.h>
#include <gst/app/gstappsink.h>
#include <gst/gst.h>
#include <stdio.h>
#include <stdlib.h>

static void ready(void *opaque) { g_atomic_int_inc((gint *)opaque); }
static void le(FILE *out, uint64_t value, unsigned bytes) {
    for (unsigned n = 0; n < bytes; n++) fputc(value >> (n * 8), out);
}
static int compare(const void *a, const void *b) {
    int64_t x = *(const int64_t *)a, y = *(const int64_t *)b;
    return (x > y) - (x < y);
}
int main(int argc, char **argv) {
    gst_init(NULL, NULL);
    struct apklayer_codec_config cfg = {.mime="video/avc", .width=64, .height=48};
    struct apklayer_codec *codec = NULL;
    assert(apklayer_codec_open(&cfg, &codec) == 0);
    gint notifications = 0;
    assert(apklayer_codec_set_notify(codec, ready, &notifications) == 0);
    GError *error = NULL;
    GstElement *encoder = gst_parse_launch("videotestsrc num-buffers=12 pattern=red ! "
        "video/x-raw,format=I420,width=64,height=48,framerate=30/1 ! "
        "x264enc bframes=2 b-adapt=false key-int-max=30 speed-preset=ultrafast byte-stream=true ! "
        "video/x-h264,stream-format=byte-stream,alignment=au ! appsink name=encoded sync=false", &error);
    assert(encoder && !error);
    GstAppSink *sink = GST_APP_SINK(gst_bin_get_by_name(GST_BIN(encoder), "encoded"));
    assert(gst_element_set_state(encoder, GST_STATE_PLAYING) != GST_STATE_CHANGE_FAILURE);
    FILE *fixture = argc == 2 ? fopen(argv[1], "wb") : NULL;
    assert(argc != 2 || fixture);
    if (fixture) { fwrite("A264", 1, 4, fixture); le(fixture, 64, 2); le(fixture, 48, 2); le(fixture, 12, 4); }
    int64_t timestamps[12];
    int reordered = 0;
    for (unsigned n = 0; n < 12; n++) {
        GstSample *sample = gst_app_sink_try_pull_sample(sink, GST_SECOND);
        assert(sample);
        GstBuffer *buffer = gst_sample_get_buffer(sample);
        GstMapInfo map;
        assert(gst_buffer_map(buffer, &map, GST_MAP_READ));
        timestamps[n] = GST_BUFFER_PTS(buffer);
        if (n && timestamps[n] < timestamps[n - 1]) reordered = 1;
        if (fixture) {
            le(fixture, map.size, 4); le(fixture, timestamps[n] / 1000, 8);
            assert(fwrite(map.data, 1, map.size, fixture) == map.size);
        }
        assert(apklayer_codec_queue(codec, map.data, map.size, timestamps[n]) == 0);
        printf("H264 queued %u pts=%ld\n", n, (long)timestamps[n]);
        gst_buffer_unmap(buffer, &map);
        gst_sample_unref(sample);
    }
    assert(reordered);
    if (fixture) assert(fclose(fixture) == 0);
    assert(apklayer_codec_end(codec) == 0);
    qsort(timestamps, 12, sizeof timestamps[0], compare);
    for (unsigned n = 0; n < 12; n++) {
        struct apklayer_codec_frame *frame = NULL;
        int result = apklayer_codec_pull(codec, GST_SECOND, &frame);
        if (result) fprintf(stderr, "H264 output %u: %d\n", n, result);
        assert(result == 0);
        struct apklayer_codec_frame_info info;
        apklayer_codec_frame_describe(frame, &info);
        assert(info.width == 64 && info.height == 48 && info.pts_ns == timestamps[n]);
        const void *planes[4];
        assert(apklayer_codec_frame_map(frame, planes) == 0 && info.planes == 3);
        unsigned y = *(const unsigned char *)planes[0], u = *(const unsigned char *)planes[1], v = *(const unsigned char *)planes[2];
        assert(y > 50 && y < 110 && u < 130 && v > 180);
        printf("PASS H264 output %u pts=%ld YUV=%u,%u,%u\n", n, (long)info.pts_ns, y, u, v);
        apklayer_codec_frame_release(frame);
    }
    struct apklayer_codec_frame *none = NULL;
    assert(apklayer_codec_pull(codec, GST_SECOND, &none) == -ENODATA && !none);
    assert(g_atomic_int_get(&notifications) >= 12);
    apklayer_codec_close(codec);
    gst_element_set_state(encoder, GST_STATE_NULL);
    gst_object_unref(sink);
    gst_object_unref(encoder);
    puts("PASS H264 reordered timestamps, delayed EOS and output notifications");
    return 0;
}
