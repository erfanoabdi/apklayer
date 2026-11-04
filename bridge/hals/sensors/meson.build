# the sensors HAL's backends (include/apklayer/sensors.h): libapklayer-sensors-<backend>.so, one chosen per instance in providers.toml (or the
# best by probe() when it names none)
sensors_inc = include_directories('include')
libsensors_fake = shared_library('apklayer-sensors-fake', 'fake.c', include_directories: sensors_inc, install: true, install_dir: native_dir)
gio_dep = dependency('gio-2.0', required: false)
if gio_dep.found()  # the D-Bus daemons' backends
  shared_library('apklayer-sensors-iio', 'iio.c', include_directories: sensors_inc, dependencies: gio_dep, install: true, install_dir: native_dir)
  shared_library('apklayer-sensors-sensorfw', 'sensorfw.c', include_directories: sensors_inc, dependencies: [gio_dep, meson.get_compiler('c').find_library('m')],
                 install: true, install_dir: native_dir)
endif
