project('apklayer', ['c', 'cpp', 'java'],
  version: '0.1',
  license: 'AGPL-3.0-or-later',
  meson_version: '>=1.3',
  default_options: ['warning_level=2', 'buildtype=release', 'cpp_std=c++17'])

# Everything apklayer needs to run, built and staged by `meson setup b -Dprefix=<dir> && ninja -C b && meson install -C b --skip-subprojects`
# (docs/INSTALL.md): <prefix>/lib/apklayer mirrors this build dir (cli/, bridge/shell/, runtime/dex2jvm/, bionic_translation/) with the runtime's
# native dir under it (native/), <prefix>/share/apklayer holds what the CLI reads from the source tree. The deb is the same install (debian/rules).
fs = import('fs')
python = find_program('python3')
java = find_program('java')
find_program('javac')
find_program('jar')
javac_jar = [python, files('tools/gen/javac-jar.py')]
jar_put = [python, files('tools/gen/jar-put.py')]
pkg_lib = get_option('prefix') / 'lib/apklayer'
native_dir = pkg_lib / 'native'
share_dir = get_option('prefix') / get_option('datadir') / 'apklayer'
plugin_dir = get_option('prefix') / get_option('libdir') / 'apklayer/plugins'  # third-party hardware plugins (docs/PLUGINS.md)

# the prebuilts branch (docs/INSTALL.md): the Android framework, Robolectric and Android's libraries the runtime is built against
prebuilts = get_option('prebuilts')
if prebuilts == ''
  prebuilts = run_command('sh', '-c', 'printf %s "${APKLAYER_PREBUILTS:-}"', check: true).stdout()
endif
cpu = host_machine.cpu_family() == 'aarch64' ? 'arm64' : host_machine.cpu_family()
android_libs = get_option('android_libs')
if android_libs == '' and prebuilts != ''
  android_libs = prebuilts / cpu / 'android-libs/lib64'
endif

subdir('deps')
subdir('runtime/dex2jvm')
subdir('bridge/shell')
subdir('cli')
subdir('bridge/hals/graphics')
subdir('bridge/hals/media')
subdir('bridge/hals/audio')
# the camera provider's backends (docs/PLAN.md "Plan: hardware providers")
subdir('bridge/hals/camera')
# the hardware providers' backends (sensors, GNSS, vibrator: docs/PLAN.md "Plan: hardware providers")
subdir('bridge/hals/sensors')
subdir('bridge/hals/gnss')
subdir('bridge/hals/vibrator')
# the hardware plugin API for out-of-tree plugins (docs/PLUGINS.md; the apklayer-dev package): its headers and apklayer-plugin.pc
install_headers('bridge/hals/include/apklayer/plugin.h', 'bridge/hals/sensors/include/apklayer/sensors.h', 'bridge/hals/gnss/include/apklayer/gnss.h',
  'bridge/hals/vibrator/include/apklayer/vibrator.h', 'bridge/hals/camera/include/apklayer/camera.h', subdir: 'apklayer')
import('pkgconfig').generate(name: 'apklayer-plugin', filebase: 'apklayer-plugin', version: version,
  description: 'apklayer hardware plugin API (sensors, GNSS, vibrator, camera)', variables: {'plugindir': '${libdir}/apklayer/plugins'})

if host_machine.system() == 'linux'
  # bionic_translation at its pinned revision (subprojects/bionic_translation.wrap) with our series applied (patches/apply, idempotent; an edited
  # patch: `meson subprojects update --reset bionic_translation`), plus patches/bionic_translation/compat where the toolchain is older
  bt_src = meson.project_source_root() / 'subprojects/bionic_translation'
  if not fs.is_dir(bt_src)
    run_command(find_program('meson'), 'subprojects', 'download', '--sourcedir', meson.project_source_root(), 'bionic_translation', check: true)
  endif
  apply = find_program('patches/apply')
  compat = meson.project_source_root() / 'patches/bionic_translation/compat'
  foreach p : run_command('sh', '-c', 'cd "$MESON_SOURCE_ROOT" && ls patches/bionic_translation/*.patch patches/bionic_translation/compat/*.patch', check: true).stdout().split()
    fs.read(p)  # a changed or new patch reconfigures
  endforeach
  message(run_command(apply, 'bionic_translation', bt_src, check: true).stdout().strip())
  cc = meson.get_compiler('c')
  bt_cargs = []
  bt_cppargs = []
  if cc.version().version_compare('<13')  # Ubuntu 22.04: gcc 11
    run_command(apply, compat / 'gcc-before-13-cxx2b.patch', bt_src, check: true)
  endif
  if cc.version().version_compare('<12')
    bt_cppargs += '-I' + compat / 'include'
  endif
  if not cc.has_header_symbol('elf.h', 'Elf64_Relr')  # glibc before 2.36
    bt_cargs += ['-include', compat / 'include/relr-compat.h']
    bt_cppargs += ['-include', compat / 'include/relr-compat.h']
  endif
  unwind = dependency('libunwind', required: false)
  if not unwind.found() or unwind.version().version_compare('<1.8')  # one call each: a copy may have meson.build reset and keep linker.c
    run_command(apply, compat / 'libunwind-before-1.8-meson.patch', bt_src, check: true)
    run_command(apply, compat / 'libunwind-before-1.8-weak-phdr.patch', bt_src, check: true)
  endif
  # built as upstream builds it on its own (debug, no optimization): the flags it was tested with
  bt = subproject('bionic_translation', default_options: ['buildtype=debug', 'warning_level=1', 'c_args=' + ' '.join(bt_cargs),
    'cpp_args=' + ' '.join(bt_cppargs)])
  subdir('bridge/native')  # the native runtime: JNI shim, btload, the path layer, bionic_translation's library mapping
  subdir('bridge/ndk')
  subdir('bridge/hals/graphics/gles1')  # OpenGL ES 1 for hosts without it (-Dgfxstream)
  if prebuilts != ''
    subdir('runtime')  # the Java side: host classes, Robolectric overlay, native-runtime.jar, the framework jars
  else
    warning('no -Dprebuilts (nor $APKLAYER_PREBUILTS): the Java runtime and the framework jars are not built (docs/INSTALL.md)')
  endif
endif

# bionic_translation regression tests (meson test --suite bionic)
subdir('tests/native')

# what the CLI reads from its root (the source tree, or <prefix>/share/apklayer installed: cli/layout.cpp)
install_data('tools/release/versions', install_dir: share_dir)
install_data('tools/sysapps.list', 'tools/release/setup-runtime.sh', install_dir: share_dir / 'tools')
install_subdir('tools/host-setup', install_dir: share_dir / 'tools')
install_data('runtime/host/jvm.args', install_dir: share_dir / 'runtime/host')
install_data('bridge/native/apklayer.prop', 'bridge/native/bionic_translation.cfg', install_dir: share_dir / 'bridge/native')
# (its links to the libraries are copied as links: Meson's install_subdir fails on a link whose target is missing when it installs again)
meson.add_install_script('sh', '-c', 'mkdir -p "$MESON_INSTALL_DESTDIR_PREFIX/$1" && cp -r --remove-destination "$2" "$MESON_INSTALL_DESTDIR_PREFIX/$1/"', 'sh',
  fs.relative_to(share_dir / 'bridge/native', get_option('prefix')), meson.project_source_root() / 'bridge/native/android-root')
install_subdir('bridge/portal', install_dir: share_dir / 'bridge', exclude_directories: '__pycache__')
install_data(files(run_command('sh', '-c', 'cd "$MESON_SOURCE_ROOT" && find aosp/hals -name "*.xml" | sort', check: true).stdout().strip().split('\n')),
  preserve_path: true, install_dir: share_dir)
install_symlink('portal', pointing_to: '../../../share/apklayer/bridge/portal', install_dir: pkg_lib / 'bridge')  # helpers above the CLI (HostFilePortal)
install_symlink('apklayer', pointing_to: '../lib/apklayer/cli/apklayer', install_dir: get_option('bindir'))
install_data('data/apklayer-manager.desktop', install_dir: get_option('datadir') / 'applications')
install_data('data/apklayer.svg', install_dir: get_option('datadir') / 'icons/hicolor/scalable/apps')
install_data('data/apklayer-256.png', rename: 'apklayer.png', install_dir: get_option('datadir') / 'icons/hicolor/256x256/apps')
install_data('data/modules-load.conf', rename: 'apklayer.conf', install_dir: 'lib/modules-load.d')  # binder_linux at boot
