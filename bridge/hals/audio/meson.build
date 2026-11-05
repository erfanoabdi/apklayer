pipewire = dependency('libpipewire-0.3', version: '>=1.0.4', required: false)
pulse = dependency('libpulse-simple', required: false)
audio_backend = get_option('audio_backend')
if audio_backend == 'auto'
  audio_backend = ''
  if pipewire.found()
    audio_backend = 'pipewire'
  elif pulse.found()
    audio_backend = 'pulse'
  endif
endif
audio_inc = include_directories('include')
libaudio = disabler()
if audio_backend == 'pipewire' and pipewire.found()
  libaudio = shared_library('apklayer-hal-audio', 'audio.c', 'capture.c',
    include_directories: audio_inc, dependencies: [pipewire, dependency('threads')], install: true, install_dir: native_dir)
elif audio_backend == 'pulse' and pulse.found()
  libaudio = shared_library('apklayer-hal-audio', 'pulse.c',
    include_directories: audio_inc, dependencies: [pulse, dependency('threads')], install: true, install_dir: native_dir)
endif
# auto with both libraries found: the PulseAudio one too, which the CLI loads instead when the session runs no PipeWire daemon (Ubuntu Touch;
# cli/halium.cpp audioLibrary)
if get_option('audio_backend') == 'auto' and audio_backend == 'pipewire' and pulse.found()
  shared_library('apklayer-hal-audio-pulse', 'pulse.c',
    include_directories: audio_inc, dependencies: [pulse, dependency('threads')], install: true, install_dir: native_dir)
endif
if libaudio.found()
  executable('audio-test', '../../../tests/hals/audio-test.c',
    include_directories: audio_inc, link_with: libaudio,
    dependencies: [dependency('threads'), meson.get_compiler('c').find_library('m')])
endif
