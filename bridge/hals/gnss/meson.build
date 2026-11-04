# the gnss HAL's backends (include/apklayer/gnss.h): libapklayer-gnss-<backend>.so, one chosen per instance in providers.toml
libgnss_fake = shared_library('apklayer-gnss-fake', 'fake.c', include_directories: include_directories('include'), install: true, install_dir: native_dir)
test('providers-fake', executable('providers-fake-test', '../../../tests/hals/providers-fake-test.c',
  include_directories: include_directories('include', '../sensors/include'), link_with: [libgnss_fake, libsensors_fake]))
gio = dependency('gio-2.0', required: false)
if gio.found()
  shared_library('apklayer-gnss-geoclue', 'geoclue.c', include_directories: include_directories('include'),
    dependencies: [gio, dependency('threads')], install: true, install_dir: native_dir)
endif
