# the gnss HAL's backends (include/apklayer/gnss.h): libapklayer-gnss-<backend>.so, one chosen per instance in providers.toml
libgnss_fake = shared_library('apklayer-gnss-fake', 'fake.c', include_directories: include_directories('include'), install: true, install_dir: native_dir)
test('providers-fake', executable('providers-fake-test', '../../../tests/hals/providers-fake-test.c',
  include_directories: include_directories('include', '../sensors/include'), link_with: [libgnss_fake, libsensors_fake]))
# the D-Bus backends (session.c: their thread and sessions): GeoClue 2 (desktops), GeoClue 1 (Sailfish OS), lomiri-location-service (Ubuntu Touch)
gio = dependency('gio-2.0', required: false)
if gio.found()
  foreach backend : ['geoclue', 'geoclue1', 'lomiri']
    shared_library('apklayer-gnss-' + backend, backend + '.c', 'session.c', include_directories: include_directories('include'),
      dependencies: [gio, dependency('threads')], install: true, install_dir: native_dir)
  endforeach
  executable('gnss-backend-fixes', '../../../tests/hals/gnss-backend-fixes.c', include_directories: include_directories('include'))
endif
