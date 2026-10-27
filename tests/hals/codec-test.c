/* Real libvpx encoding/decoding, PTS/EOS and retained frame lifetime. */
#include <apklayer/codec.h>
#include <assert.h>
#include <errno.h>
#include <gst/app/gstappsink.h>
#include <gst/gst.h>
#include <stdio.h>

static void le(FILE *out, uint64_t value, unsigned bytes)
{
    for (unsigned n = 0; n < bytes; n++) fputc(value >> (n * 8), out);
}

static void decode(unsigned width, unsigned height, const char *fixture, int vp9)
{
    struct apklayer_codec_config cfg = { .mime = vp9 ? "video/x-vnd.on2.vp9" : "video/x-vnd.on2.vp8", .width = width, .height = height };
    struct apklayer_codec *codec = NULL;
    assert(apklayer_codec_open(&cfg, &codec) == 0);
    struct apklayer_codec_frame *empty = NULL;
    assert(apklayer_codec_pull(codec, 0, &empty) == -EAGAIN && !empty);
    char *desc = g_strdup_printf("videotestsrc num-buffers=3 pattern=red ! "
        "video/x-raw,format=I420,width=%u,height=%u,framerate=30/1 ! %s deadline=1 ! "
        "appsink name=encoded sync=false", width, height, vp9 ? "vp9enc" : "vp8enc");
    GError *error = NULL;
    GstElement *encoder = gst_parse_launch(desc, &error);
    g_free(desc);
    assert(encoder && !error);
    GstAppSink *encoded = GST_APP_SINK(gst_bin_get_by_name(GST_BIN(encoder), "encoded"));
    assert(gst_element_set_state(encoder, GST_STATE_PLAYING) != GST_STATE_CHANGE_FAILURE);
    FILE *ivf = fixture ? fopen(fixture, "wb") : NULL;
    assert(!fixture || ivf);
    if (ivf) {
        fwrite("DKIF", 1, 4, ivf); le(ivf, 0, 2); le(ivf, 32, 2);
        fwrite(vp9 ? "VP90" : "VP80", 1, 4, ivf); le(ivf, width, 2); le(ivf, height, 2);
        le(ivf, 30, 4); le(ivf, 1, 4); le(ivf, 3, 4); le(ivf, 0, 4);
    }
    int64_t pts[3];
    for (unsigned n = 0; n < 3; n++) {
        GstSample *sample = gst_app_sink_try_pull_sample(encoded, GST_SECOND);
        assert(sample);
        GstBuffer *buffer = gst_sample_get_buffer(sample);
        GstMapInfo map;
        assert(gst_buffer_map(buffer, &map, GST_MAP_READ));
        pts[n] = GST_BUFFER_PTS(buffer);
        if (ivf) {
            le(ivf, map.size, 4); le(ivf, n, 8);
            assert(fwrite(map.data, 1, map.size, ivf) == map.size);
        }
        assert(apklayer_codec_queue(codec, map.data, map.size, pts[n]) == 0);
        gst_buffer_unmap(buffer, &map);
        gst_sample_unref(sample);
    }
    if (ivf) assert(fclose(ivf) == 0);
    assert(apklayer_codec_end(codec) == 0);
    struct apklayer_codec_frame *retained = NULL;
    for (unsigned n = 0; n < 3; n++) {
        struct apklayer_codec_frame *frame = NULL;
        int result = apklayer_codec_pull(codec, GST_SECOND, &frame);
        if (result) fprintf(stderr, "pull frame %u: %d\n", n, result);
        assert(result == 0);
        struct apklayer_codec_frame_info info;
        apklayer_codec_frame_describe(frame, &info);
        assert(info.width == width && info.height == height && info.planes == 3);
        assert(!strcmp(info.format, "I420") && info.pts_ns == pts[n]);
        const void *planes[4];
        assert(apklayer_codec_frame_map(frame, planes) == 0);
        unsigned y = *(const unsigned char *)planes[0];
        unsigned u = *(const unsigned char *)planes[1];
        unsigned v = *(const unsigned char *)planes[2];
        assert(y > 50 && y < 110 && u < 130 && v > 180);
        printf("PASS VP%d %ux%u frame %u pts=%ld YUV=%u,%u,%u\n", vp9 ? 9 : 8, width, height, n, (long)info.pts_ns, y, u, v);
        if (!n) retained = frame; else apklayer_codec_frame_release(frame);
    }
    struct apklayer_codec_frame *none = NULL;
    assert(apklayer_codec_pull(codec, GST_SECOND, &none) == -ENODATA && !none);
    apklayer_codec_close(codec);
    /* Samples stay owned even after stopping/releasing the decoder. */
    const void *planes[4];
    assert(apklayer_codec_frame_map(retained, planes) == 0);
    assert(*(const unsigned char *)planes[2] > 180);
    apklayer_codec_frame_release(retained);
    gst_element_set_state(encoder, GST_STATE_NULL);
    gst_object_unref(encoded);
    gst_object_unref(encoder);
    puts("PASS EOS and frame ownership after decoder teardown");
}

int main(int argc, char **argv)
{
    gst_init(NULL, NULL);
    assert(!apklayer_codec_supports("video/not-a-codec"));
    assert(apklayer_codec_supports("video/x-vnd.on2.vp8"));
    struct apklayer_codec *bad = NULL;
    struct apklayer_codec_config cfg = { .mime = "video/not-a-codec", .width = 64, .height = 48 };
    assert(apklayer_codec_open(&cfg, &bad) == -ENOTSUP && !bad);
    decode(64, 48, argc >= 2 ? argv[1] : NULL, 0);
    decode(80, 60, NULL, 0);
    decode(64, 48, argc >= 3 ? argv[2] : NULL, 1);
    decode(80, 60, NULL, 1);
    return 0;
}
