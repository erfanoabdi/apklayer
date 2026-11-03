# apklayer-manager: its own Meson project, so the main build never needs Slint. Build:
#   manager/fetch-slint.sh && meson setup manager/b manager && ninja -C manager/b
#   APKLAYER_INSTANCE=<not default> meson test -C manager/b   (the backend against the real CLI, installs tests/uibench/uibench.apk)
project('apklayer-manager', 'cpp',
  version: '0.1',
  license: 'AGPL-3.0-or-later',
  meson_version: '>=1.3',
  default_options: ['warning_level=2', 'buildtype=release', 'cpp_std=c++20'])

slint = get_option('slint')
if slint == ''
  slint = run_command('sh', '-c', 'printf %s "$HOME/.cache/apklayer/slint"', check: true).stdout()
endif
slint_compiler = find_program(slint / 'bin/slint-compiler')
# the prebuilt Slint C++ package (no pkg-config file; its CMake config is not needed for one library)
slint_dep = declare_dependency(
  compile_args: ['-isystem', slint / 'cpp/include/slint'],
  link_args: ['-L' + slint / 'cpp/lib', '-lslint_cpp', '-Wl,-rpath,' + slint / 'cpp/lib'])

ui = custom_target('app.h',
  input: 'ui/app.slint',
  output: 'app.h',
  depfile: 'app.d',
  command: [slint_compiler, '--style', 'material', '-L', 'material=' + slint / 'material/material.slint', '--embed-resources', 'embed-files',
            '--depfile', '@DEPFILE@', '-o', '@OUTPUT@', '@INPUT@'])

backend = static_library('backend', 'src/backend.cpp')
executable('apklayer-manager', 'src/main.cpp', ui,
  dependencies: [slint_dep, dependency('libpng'), dependency('threads')],
  link_with: backend,
  install: true)

backend_test = executable('backend-test', 'tests/backend_test.cpp', link_with: backend, dependencies: dependency('threads'))
test('backend', backend_test, args: [meson.current_source_dir() / '../tests/uibench/uibench.apk'], timeout: 600)
