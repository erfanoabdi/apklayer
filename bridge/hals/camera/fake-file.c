/* The fake camera's file source: a still image (repeated) or a video file (looped), decoded and scaled by GStreamer to the I420 size and
 * rate the provider asked for, paced by the pipeline clock. */
#include "fake.h"

#include <errno.h>
#include <stdio.h>
#include <string.h>
#include <strings.h>
#include <time.h>

#include <gst/app/gstappsink.h>
#include <gst/gst.h>
#include <gst/video/video.h>

static int is_image(const char *path)
{
    static const char *const ext[] = {".png", ".jpg", ".jpeg", ".bmp", ".gif", ".webp", ".ppm", NULL};
    const char *dot = strrchr(path, '.');
    for (int i = 0; dot && ext[i]; i++)
        if (!strcasecmp(dot, ext[i])) return 1;
    return 0;
}

static GstElement *make_pipeline(const char *path, uint32_t w, uint32_t h, uint32_t fps)
{
    char desc[512];
    snprintf(desc, sizeof desc,
             "filesrc name=src ! decodebin ! videoconvert ! videoscale ! %s videorate ! "
             "video/x-raw,format=I420,width=%u,height=%u,framerate=%u/1,pixel-aspect-ratio=1/1 ! appsink name=sink sync=true max-buffers=2 drop=true",
             is_image(path) ? "imagefreeze !" : "", w, h, fps);
    GError *error = NULL;
    GstElement *pipeline = gst_parse_launch(desc, &error);
    if (!pipeline) {
        fprintf(stderr, "camera fake: %s\n", error ? error->message : "no pipeline");
        g_clear_error(&error);
        return NULL;
    }
    GstElement *src = gst_bin_get_by_name(GST_BIN(pipeline), "src");
    g_object_set(src, "location", path, NULL);
    gst_object_unref(src);
    return pipeline;
}

void *fake_file_thread(void *arg)
{
    struct apklayer_camera *c = arg;
    gst_init(NULL, NULL);
    uint32_t w = fake_width(c), h = fake_height(c);
    GstElement *pipeline = make_pipeline(fake_source(), w, h, fake_fps(c));
    GstAppSink *sink = pipeline ? GST_APP_SINK(gst_bin_get_by_name(GST_BIN(pipeline), "sink")) : NULL;
    GstSample *sample = NULL;
    if (sink && gst_element_set_state(pipeline, GST_STATE_PLAYING) != GST_STATE_CHANGE_FAILURE)
        sample = gst_app_sink_try_pull_sample(sink, 5 * GST_SECOND);  // the first frame: the file decodes
    if (!sample) fprintf(stderr, "camera fake: no frame from %s\n", fake_source());
    fake_report_ready(c, sample ? 1 : -EIO);
    for (uint32_t seq = 0; sample && fake_running(c); seq++) {
        GstVideoInfo info;
        GstVideoFrame frame;
        if (gst_video_info_from_caps(&info, gst_sample_get_caps(sample)) &&
            gst_video_frame_map(&frame, &info, gst_sample_get_buffer(sample), GST_MAP_READ)) {
            const uint8_t *planes[3];
            uint32_t strides[3];
            for (int p = 0; p < 3; p++) planes[p] = GST_VIDEO_FRAME_PLANE_DATA(&frame, p), strides[p] = GST_VIDEO_FRAME_PLANE_STRIDE(&frame, p);
            struct timespec now;
            clock_gettime(CLOCK_MONOTONIC, &now);
            fake_deliver(c, planes, strides, w, h, now.tv_sec * 1000000000LL + now.tv_nsec, seq);
            gst_video_frame_unmap(&frame);
        }
        gst_sample_unref(sample);
        sample = NULL;
        while (!sample && fake_running(c)) {
            sample = gst_app_sink_try_pull_sample(sink, GST_SECOND / 10);
            if (!sample && gst_app_sink_is_eos(sink))  // the end of a video: from the start again
                gst_element_seek_simple(pipeline, GST_FORMAT_TIME, GST_SEEK_FLAG_FLUSH | GST_SEEK_FLAG_KEY_UNIT, 0);
        }
    }
    if (sample) gst_sample_unref(sample);
    if (sink) gst_object_unref(sink);
    if (pipeline) {
        gst_element_set_state(pipeline, GST_STATE_NULL);
        gst_object_unref(pipeline);
    }
    return NULL;
}
