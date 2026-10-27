/* Native host decoding: reuse the existing GStreamer backend, retaining raw
 * samples and dmabuf ownership instead of converting frames into RGBA files. */
#include <apklayer/codec.h>
#include <errno.h>
#include <gst/app/gstappsrc.h>
#include <gst/app/gstappsink.h>
#include <gst/allocators/gstdmabuf.h>
#include <gst/video/video.h>
#include <gst/video/video-info-dma.h>
#include <stdio.h>
#include <string.h>

struct apklayer_codec { GstElement *pipe; GstAppSrc *src; GstAppSink *sink; int failed; };
struct apklayer_codec_frame {
    GstSample *sample;
    GstVideoInfo video;
    GstVideoFrame mapping;
    gboolean mapped;
    struct apklayer_codec_frame_info info;
};

static GstCaps *input_caps(const char *mime)
{
    if (!mime) return NULL;
    const char *caps = NULL;
    if (!strcmp(mime, "video/x-vnd.on2.vp8")) caps = "video/x-vp8";
    if (!strcmp(mime, "video/x-vnd.on2.vp9")) caps = "video/x-vp9";
    if (!strcmp(mime, "video/avc")) caps = "video/x-h264,stream-format=byte-stream,alignment=au";
    if (!strcmp(mime, "video/hevc")) caps = "video/x-h265,stream-format=byte-stream,alignment=au";
    if (!strcmp(mime, "video/av01")) caps = "video/x-av1,stream-format=obu-stream,alignment=tu";
    return caps ? gst_caps_from_string(caps) : NULL;
}

int apklayer_codec_supports(const char *mime)
{
    gst_init(NULL, NULL);
    GstCaps *caps = input_caps(mime);
    if (!caps) return 0;
    GList *all = gst_element_factory_list_get_elements(GST_ELEMENT_FACTORY_TYPE_DECODER, GST_RANK_MARGINAL);
    GList *matches = gst_element_factory_list_filter(all, caps, GST_PAD_SINK, FALSE);
    int supported = matches != NULL;
    gst_plugin_feature_list_free(matches);
    gst_plugin_feature_list_free(all);
    gst_caps_unref(caps);
    return supported;
}

static int check_error(struct apklayer_codec *c)
{
    if (c->failed) return -EIO;
    GstBus *bus = gst_element_get_bus(c->pipe);
    GstMessage *msg = gst_bus_pop_filtered(bus, GST_MESSAGE_ERROR);
    gst_object_unref(bus);
    if (!msg) return 0;
    GError *error = NULL;
    gchar *detail = NULL;
    gst_message_parse_error(msg, &error, &detail);
    fprintf(stderr, "[codec] %s: %s\n", GST_OBJECT_NAME(msg->src), error->message);
    g_clear_error(&error);
    g_free(detail);
    gst_message_unref(msg);
    c->failed = 1;
    return -EIO;
}

int apklayer_codec_open(const struct apklayer_codec_config *cfg, struct apklayer_codec **out)
{
    if (!cfg || !out || !cfg->width || !cfg->height ||
        cfg->width > INT_MAX || cfg->height > INT_MAX ||
        (cfg->codec_data_size && !cfg->codec_data)) return -EINVAL;
    *out = NULL;
    if (!apklayer_codec_supports(cfg->mime)) return -ENOTSUP;
    struct apklayer_codec *c = g_new0(struct apklayer_codec, 1);
    GError *error = NULL;
    c->pipe = gst_parse_launch("appsrc name=input format=time ! decodebin ! "
        "appsink name=output sync=false max-buffers=8", &error);
    if (!c->pipe || error) {
        if (error) fprintf(stderr, "[codec] %s\n", error->message);
        g_clear_error(&error);
        apklayer_codec_close(c);
        return -ENOTSUP;
    }
    c->src = GST_APP_SRC(gst_bin_get_by_name(GST_BIN(c->pipe), "input"));
    c->sink = GST_APP_SINK(gst_bin_get_by_name(GST_BIN(c->pipe), "output"));
    GstCaps *caps = input_caps(cfg->mime);
    gst_caps_set_simple(caps, "width", G_TYPE_INT, (int)cfg->width,
                        "height", G_TYPE_INT, (int)cfg->height, NULL);
    if (cfg->codec_data_size) {
        GstBuffer *data = gst_buffer_new_allocate(NULL, cfg->codec_data_size, NULL);
        gst_buffer_fill(data, 0, cfg->codec_data, cfg->codec_data_size);
        gst_caps_set_simple(caps, "codec_data", GST_TYPE_BUFFER, data, NULL);
        gst_buffer_unref(data);
    }
    gst_app_src_set_caps(c->src, caps);
    gst_caps_unref(caps);
    gst_app_src_set_max_bytes(c->src, 8 * 1024 * 1024);
    caps = gst_caps_from_string("video/x-raw;video/x-raw(memory:DMABuf)");
    gst_app_sink_set_caps(c->sink, caps);
    gst_caps_unref(caps);
    if (gst_element_set_state(c->pipe, GST_STATE_PLAYING) == GST_STATE_CHANGE_FAILURE) {
        check_error(c);
        apklayer_codec_close(c);
        return -EIO;
    }
    *out = c;
    return 0;
}

int apklayer_codec_queue(struct apklayer_codec *c, const void *data, size_t size, int64_t pts)
{
    if (!c || !data || !size || size > 8 * 1024 * 1024) return -EINVAL;
    if (check_error(c)) return -EIO;
    if (gst_app_src_get_current_level_bytes(c->src) + size > 8 * 1024 * 1024) return -EAGAIN;
    GstBuffer *b = gst_buffer_new_allocate(NULL, size, NULL);
    if (!b) return -ENOMEM;
    gst_buffer_fill(b, 0, data, size);
    GST_BUFFER_PTS(b) = pts < 0 ? GST_CLOCK_TIME_NONE : (GstClockTime)pts;
    GstFlowReturn r = gst_app_src_push_buffer(c->src, b);
    return r == GST_FLOW_OK ? 0 : r == GST_FLOW_EOS ? -ENODATA : -EIO;
}

int apklayer_codec_end(struct apklayer_codec *c)
{
    if (!c) return -EINVAL;
    return gst_app_src_end_of_stream(c->src) == GST_FLOW_OK ? 0 : -EIO;
}

int apklayer_codec_pull(struct apklayer_codec *c, uint64_t wait, struct apklayer_codec_frame **out)
{
    if (!c || !out) return -EINVAL;
    *out = NULL;
    GstSample *sample = gst_app_sink_try_pull_sample(c->sink, wait);
    if (!sample) {
        if (check_error(c)) return -EIO;
        return gst_app_sink_is_eos(c->sink) ? -ENODATA : -EAGAIN;
    }
    struct apklayer_codec_frame *f = g_new0(struct apklayer_codec_frame, 1);
    f->sample = sample;
    GstCaps *caps = gst_sample_get_caps(sample);
    GstVideoInfoDmaDrm drm;
    gst_video_info_dma_drm_init(&drm);
    gboolean valid;
    if (gst_video_is_dma_drm_caps(caps)) {
        valid = gst_video_info_dma_drm_from_caps(&drm, caps) &&
                gst_video_info_dma_drm_to_video_info(&drm, &f->video);
    } else {
        valid = gst_video_info_from_caps(&f->video, caps) &&
                gst_video_info_dma_drm_from_video_info(&drm, &f->video, 0);
    }
    if (!valid) {
        apklayer_codec_frame_release(f);
        return -ENOTSUP;
    }
    struct apklayer_codec_frame_info *i = &f->info;
    i->width = GST_VIDEO_INFO_WIDTH(&f->video);
    i->height = GST_VIDEO_INFO_HEIGHT(&f->video);
    i->planes = GST_VIDEO_INFO_N_PLANES(&f->video);
    i->fourcc = drm.drm_fourcc;
    i->modifier = drm.drm_modifier;
    g_strlcpy(i->format, gst_video_format_to_string(GST_VIDEO_INFO_FORMAT(&f->video)), sizeof i->format);
    GstBuffer *buffer = gst_sample_get_buffer(sample);
    i->pts_ns = GST_BUFFER_PTS_IS_VALID(buffer) ? (int64_t)GST_BUFFER_PTS(buffer) : -1;
    GstVideoMeta *meta = gst_buffer_get_video_meta(buffer);
    for (unsigned p = 0; p < 4; p++) i->fd[p] = -1;
    for (unsigned p = 0; p < i->planes; p++) {
        gsize offset = meta ? meta->offset[p] : f->video.offset[p];
        i->stride[p] = meta ? meta->stride[p] : f->video.stride[p];
        i->offset[p] = offset;
        guint index, length;
        gsize skip;
        if (gst_buffer_find_memory(buffer, offset, 1, &index, &length, &skip)) {
            GstMemory *memory = gst_buffer_peek_memory(buffer, index);
            if (gst_is_dmabuf_memory(memory)) {
                gsize start;
                gst_memory_get_sizes(memory, &start, NULL);
                i->fd[p] = gst_dmabuf_memory_get_fd(memory);
                i->offset[p] = start + skip;
            }
        }
    }
    *out = f;
    return 0;
}

void apklayer_codec_frame_describe(const struct apklayer_codec_frame *f, struct apklayer_codec_frame_info *out)
{ if (f && out) *out = f->info; }

int apklayer_codec_frame_map(struct apklayer_codec_frame *f, const void *planes[4])
{
    if (!f || !planes) return -EINVAL;
    if (f->info.modifier != 0) return -ENOTSUP;
    if (!f->mapped) {
        if (!gst_video_frame_map(&f->mapping, &f->video, gst_sample_get_buffer(f->sample), GST_MAP_READ)) return -EIO;
        f->mapped = TRUE;
    }
    for (unsigned p = 0; p < 4; p++) planes[p] = p < f->info.planes ? GST_VIDEO_FRAME_PLANE_DATA(&f->mapping, p) : NULL;
    return 0;
}

void apklayer_codec_frame_release(struct apklayer_codec_frame *f)
{
    if (!f) return;
    if (f->mapped) gst_video_frame_unmap(&f->mapping);
    gst_sample_unref(f->sample);
    g_free(f);
}

void apklayer_codec_close(struct apklayer_codec *c)
{
    if (!c) return;
    if (c->pipe) gst_element_set_state(c->pipe, GST_STATE_NULL);
    if (c->src) gst_object_unref(c->src);
    if (c->sink) gst_object_unref(c->sink);
    if (c->pipe) gst_object_unref(c->pipe);
    g_free(c);
}
