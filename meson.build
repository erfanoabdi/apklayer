project('apklayer', ['c', 'cpp', 'java'],
  version: '0.4.8',
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

# bionic_translation's C11 atomics for C++ with gcc before 12 (patches/bionic_translation/compat/include), a global argument: a subproject's
# default_options cpp_args (and buildtype) take effect only from Meson 1.8 on (Sailfish OS 5.0 has 1.4); so before any target
cc = meson.get_compiler('c')
if host_machine.system() == 'linux' and cc.version().version_compare('<12')
  add_global_arguments('-I' + meson.project_source_root() / 'patches/bionic_translation/compat/include', language: 'cpp')
endif
# the binder uapi header (bridge/compat/include) where the kernel headers predate it (Sailfish OS 5.0: 3.18)
if host_machine.system() == 'linux' and not cc.has_header('linux/android/binder.h')
  add_project_arguments('-I' + meson.project_source_root() / 'bridge/compat/include', language: ['c', 'cpp'])
endif
# dlopen and friends live in libdl apart from libc before glibc 2.34 (Sailfish OS 5.0: 2.30); built into libc after, then this adds nothing
add_project_dependencies(dependency('dl'), language: ['c', 'cpp'])

# a cross build is the 32-bit app process's (tools/armhf/build.sh): only the native parts an app JVM loads; CLI, shell and Java come from the 64-bit build
version = get_option('package_version') != '' ? get_option('package_version') : meson.project_version()  # the deb passes its own (debian/rules)
arm32_only = meson.is_cross_build()
if not arm32_only
  subdir('deps')
  subdir('runtime/dex2jvm')
  subdir('bridge/shell')
  subdir('cli')
endif
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
  # patch resets it to that revision first, below), plus patches/bionic_translation/compat where the toolchain is older
  bt_src = meson.project_source_root() / 'subprojects/bionic_translation'
  if not fs.is_dir(bt_src)
    run_command(find_program('meson'), 'subprojects', 'download', '--sourcedir', meson.project_source_root(), 'bionic_translation', check: true)
  endif
  apply = find_program('patches/apply')
  compat = meson.project_source_root() / 'patches/bionic_translation/compat'
  foreach p : run_command('sh', '-c', 'cd "$MESON_SOURCE_ROOT" && ls patches/bionic_translation/*.patch patches/bionic_translation/compat/*.patch', check: true).stdout().split()
    fs.read(p)  # a changed or new patch reconfigures
  endforeach
  applied = run_command(apply, 'bionic_translation', bt_src, check: false)
  if applied.returncode() != 0  # a patch edited since this tree was patched: the pinned revision again, then the whole series
    run_command(find_program('meson'), 'subprojects', 'update', '--reset', '--sourcedir', meson.project_source_root(), 'bionic_translation', check: true)
    applied = run_command(apply, 'bionic_translation', bt_src, check: true)
  endif
  message(applied.stdout().strip())
  if cc.version().version_compare('<11')  # Sailfish OS 5.0: gcc 10 (one series: the second patch is made on the first)
    run_command(apply, compat / 'gcc-before-13-cxx2b.patch', compat / 'gcc-before-11-cxx20.patch', bt_src, check: true)
  elif cc.version().version_compare('<13')  # Ubuntu 22.04: gcc 11
    run_command(apply, compat / 'gcc-before-13-cxx2b.patch', bt_src, check: true)
  endif
  if not cc.has_header_symbol('elf.h', 'Elf64_Relr')  # glibc before 2.36
    run_command(apply, compat / 'glibc-before-2.36-relr.patch', bt_src, check: true)
  endif
  if not cc.has_function('strlcpy')  # glibc before 2.38 (Sailfish OS 5.0: 2.30): libc functions Android's libraries use
    run_command(apply, compat / 'glibc-before-2.38-libc.patch', bt_src, check: true)
  endif
  unwind = dependency('libunwind', required: false)
  if not unwind.found() or unwind.version().version_compare('<1.8')  # one call each: a copy may have meson.build reset and keep linker.c
    run_command(apply, compat / 'libunwind-before-1.8-meson.patch', bt_src, check: true)
    run_command(apply, compat / 'libunwind-before-1.8-weak-phdr.patch', bt_src, check: true)
  endif
  # optimized with its debug info kept (upstream builds it with Meson's default, -O0): every proxied libc call, mutex and condition variable of
  # translated code goes through it
  bt = subproject('bionic_translation', default_options: ['buildtype=debugoptimized', 'warning_level=1'])
  subdir('bridge/binderd')  # the userspace binder's daemon (docs/BINDER.md)
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
install_data('tools/sysapps.list', 'tools/release/setup-runtime.sh', 'tools/release/jdk-image.sh', install_dir: share_dir / 'tools')
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
if not arm32_only
  install_symlink('apklayer', pointing_to: '../lib/apklayer/cli/apklayer', install_dir: get_option('bindir'))
endif
install_data('data/apklayer-manager.desktop', install_dir: get_option('datadir') / 'applications')
# the Apklayer submenu of the desktop's app menu (as Waydroid's): apps carry Categories=X-Apklayer-App; the CLI writes a per-user copy too
install_data('data/apklayer.menu', install_dir: get_option('sysconfdir') / 'xdg/menus/applications-merged')
install_data('data/apklayer.directory', install_dir: get_option('datadir') / 'desktop-directories')
# Ubuntu Touch: 'Open with' / share of a file to apklayer. Lomiri Content Hub reads its peers from /usr/share/lomiri-content-hub/peers, each named
# by the receiving app's id: the hidden apklayer-share app, apklayer-manager --share. Unused elsewhere.
install_data('data/apklayer-share.desktop', install_dir: get_option('datadir') / 'applications')
install_data('data/content-hub-peer.json', rename: 'apklayer-share', install_dir: get_option('datadir') / 'lomiri-content-hub/peers')
install_data('data/apklayer.svg', install_dir: get_option('datadir') / 'icons/hicolor/scalable/apps')
install_data('data/apklayer-256.png', rename: 'apklayer.png', install_dir: get_option('datadir') / 'icons/hicolor/256x256/apps')
