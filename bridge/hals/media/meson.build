# Native decoder samples stay YUV; dmabufs retain their original allocation.
gst_app = dependency('gstreamer-app-1.0', version: '>=1.24', required: false)
gst_video = dependency('gstreamer-video-1.0', version: '>=1.24', required: false)
gst_alloc = dependency('gstreamer-allocators-1.0', version: '>=1.24', required: false)
if gst_app.found() and gst_video.found() and gst_alloc.found()
  codec_inc = include_directories('include')
  libcodec = shared_library('apklayer-hal-media', 'codec.c',
    include_directories: codec_inc, dependencies: [gst_app, gst_video, gst_alloc], install: true)
  executable('codec-test', '../../../tests/hals/codec-test.c',
    include_directories: codec_inc, dependencies: [gst_app, gst_video], link_with: libcodec)
endif
