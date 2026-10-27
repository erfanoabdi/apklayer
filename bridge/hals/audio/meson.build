pipewire = dependency('libpipewire-0.3', version: '>=1.0.4', required: false)
if pipewire.found()
  audio_inc = include_directories('include')
  libaudio = shared_library('apklayer-hal-audio', 'audio.c', 'capture.c',
    include_directories: audio_inc, dependencies: [pipewire, dependency('threads')], install: true)
  executable('audio-test', '../../../tests/hals/audio-test.c',
    include_directories: audio_inc, link_with: libaudio,
    dependencies: [dependency('threads'), meson.get_compiler('c').find_library('m')])
endif
