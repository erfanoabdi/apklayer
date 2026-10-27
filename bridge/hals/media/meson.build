# Native raw video/audio samples retain ownership; video dmabufs keep their allocation.
gst_app = dependency('gstreamer-app-1.0', version: '>=1.24', required: false)
gst_video = dependency('gstreamer-video-1.0', version: '>=1.24', required: false)
gst_audio = dependency('gstreamer-audio-1.0', version: '>=1.24', required: false)
gst_alloc = dependency('gstreamer-allocators-1.0', version: '>=1.24', required: false)
if gst_app.found() and gst_video.found() and gst_alloc.found() and gst_audio.found()
  codec_inc = include_directories('include')
  libcodec = shared_library('apklayer-hal-media', 'codec.c',
    include_directories: codec_inc, dependencies: [gst_app, gst_video, gst_alloc, gst_audio], install: true)
  executable('codec-test', '../../../tests/hals/codec-test.c',
    include_directories: codec_inc, dependencies: [gst_app, gst_video], link_with: libcodec)
  executable('codec-reorder-test', '../../../tests/hals/codec-reorder-test.c',
    include_directories: codec_inc, dependencies: [gst_app, gst_video], link_with: libcodec)
  executable('codec-audio-test', '../../../tests/hals/codec-audio-test.c',
    include_directories: codec_inc, dependencies: [gst_app, gst_audio, meson.get_compiler('c').find_library('m')], link_with: libcodec)
endif
