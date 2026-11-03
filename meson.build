project('apklayer', ['c', 'cpp', 'java'],
  version: '0.1',
  license: 'AGPL-3.0-or-later',
  meson_version: '>=1.3',
  default_options: ['warning_level=2', 'buildtype=release', 'cpp_std=c++17'])

# One javac call per jar (tools/javac-jar.sh): Meson's jar() compiles per file and resolves
# siblings by package path, which dex2jvm's flat upstream layout doesn't follow.
find_program('javac')
find_program('jar')
javac_jar = find_program('tools/javac-jar.sh')

subdir('runtime/dex2jvm')
subdir('bridge/shell')
subdir('cli')
subdir('bridge/hals/graphics')

subdir('bridge/hals/media')

subdir('bridge/hals/audio')

# The native runtime (bridge/native, bridge/ndk): built where bionic_translation (tools/build bt) and the staged Android libraries are.
fs = import('fs')
bt_dir = get_option('bionic_translation')
if bt_dir == ''
  bt_dir = run_command('sh', '-c', 'printf %s "${APKLAYER_BT:-$HOME/bt-arm/bionic_translation/b}"', check: true).stdout()
endif
android_libs = get_option('android_libs')
if android_libs == ''
  android_libs = run_command('sh', '-c', 'printf %s "${APKLAYER_ANDROID_LIBS:-$HOME/android-arm64/lib64}"', check: true).stdout()
endif
if host_machine.system() == 'linux' and fs.exists(bt_dir / 'libdl_bio.so')
  subdir('bridge/native')
  subdir('bridge/ndk')
endif

# bionic_translation regression tests (meson test --suite bionic)
subdir('tests/native')
