# the vibrator HAL's backends (include/apklayer/vibrator.h): libapklayer-vibrator-<backend>.so, one chosen per instance in providers.toml (or the
# best by probe() when it names none)
vibrator_inc = include_directories('include')
shared_library('apklayer-vibrator-fake', 'fake.c', include_directories: vibrator_inc, install: true, install_dir: native_dir)
if host_machine.system() == 'linux'
  shared_library('apklayer-vibrator-ff', 'ff.c', include_directories: vibrator_inc, install: true, install_dir: native_dir)
endif
if dependency('gio-2.0', required: false).found()
  shared_library('apklayer-vibrator-feedbackd', 'feedbackd.c', include_directories: vibrator_inc, dependencies: dependency('gio-2.0'),
                 install: true, install_dir: native_dir)
endif
