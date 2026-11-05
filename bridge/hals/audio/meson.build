# The audio domain's backends (include/apklayer/audio.h): libapklayer-audio-<backend>.so, the host library of the Android audio HAL, loaded into
# every Android process through APKLAYER_HOST_LIBS. Which one an instance uses is a hardware provider choice (cli/providers.cpp: `apklayer
# providers set audio <backend>`, else the best whose probe answers: PipeWire on a desktop, PulseAudio on Ubuntu Touch).
pipewire = dependency('libpipewire-0.3', version: '>=1.0.4', required: false)
pulse = dependency('libpulse-simple', required: false)
audio_backend = get_option('audio_backend')
audio_inc = include_directories('include', '../include')
audio_libs = []  # the first one built: the HAL tests link it
if audio_backend in ['auto', 'pipewire'] and pipewire.found()
  audio_libs += shared_library('apklayer-audio-pipewire', 'audio.c', 'capture.c', 'probe.c',
    include_directories: audio_inc, dependencies: [pipewire, dependency('threads')], install: true, install_dir: native_dir)
endif
if audio_backend in ['auto', 'pulse'] and pulse.found()
  audio_libs += shared_library('apklayer-audio-pulse', 'pulse.c', 'probe.c', c_args: '-DAPKLAYER_AUDIO_PULSE',
    include_directories: audio_inc, dependencies: [pulse, dependency('threads')], install: true, install_dir: native_dir)
endif
if audio_libs.length() > 0
  executable('audio-test', '../../../tests/hals/audio-test.c',
    include_directories: audio_inc, link_with: audio_libs[0],
    dependencies: [dependency('threads'), meson.get_compiler('c').find_library('m')])
endif
