# the camera HAL's backends (include/apklayer/camera.h): libapklayer-camera-<backend>.so, loaded into the camera provider (aosp/hals/camera)
camera_inc = include_directories('include', '../include')
if gst_app.found() and gst_video.found()
  libcamera_fake = shared_library('apklayer-camera-fake', 'fake.c', 'fake-file.c', include_directories: camera_inc,
    dependencies: [gst_app, gst_video, dependency('threads')], install: true, install_dir: native_dir)
  test('camera-fake', executable('camera-fake-test', '../../../tests/hals/camera-fake-test.c',
    include_directories: camera_inc, link_with: libcamera_fake), timeout: 30)
endif
if pipewire.found()
  libcamera_pw = shared_library('apklayer-camera-pipewire', 'pipewire.c', 'pipewire-list.c', include_directories: camera_inc, dependencies: pipewire, install: true, install_dir: native_dir)
  executable('camera-pipewire-test', '../../../tests/hals/camera-pipewire-test.c', include_directories: camera_inc, link_with: libcamera_pw)
endif
