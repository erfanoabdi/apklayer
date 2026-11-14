# The native runtime's host binaries, installed into the native dir (native_dir). Linked against bionic_translation (bt, the subproject).
# shared_module: no SONAME and undefined symbols allowed (the JVM, LD_PRELOAD and bionic_translation load them by path; Android's libraries resolve the rest).
native_opts = ['optimization=2', 'warning_level=1', 'b_asneeded=false']
# meson defines _FILE_OFFSET_BITS=64: glibc would then rename the path layer's open/stat/... interposers to their *64 twins, which it also defines
native_cargs = ['-U_FILE_OFFSET_BITS']
dl_bio = bt.get_variable('dl_bio')
c_bio = bt.get_variable('c_bio')
jni_dep = dependency('jni', required: false)
if jni_dep.found()
  # the JNI shim Robolectric loads (runtime/meson.build puts it into native-runtime.jar and the framework jars)
  shim = shared_module('android_runtime', ['runtime_shim.c', 'media_runtime.c', 'binder_runtime.c', 'app_native.c', 'environment_runtime.c', 'jni_system_route.c', 'proc_title.c', 'native_loader.c', 'native_bridge.c', 'spare_target.c', 'private_stdio.c',
      'android_platform.c', 'properties.c', 'paths.c', 'android_stubs.c'],
    dependencies: jni_dep, link_with: dl_bio, c_args: native_cargs, override_options: native_opts)
endif
# the launcher for Android executables built as libraries (btload --main)
executable('btload', ['bionic_tls.c', 'btload.c', 'android_platform.c', 'properties.c', 'paths.c', 'binder_compat.c', 'android_stubs.c'],
  export_dynamic: true, link_with: dl_bio, dependencies: dependency('threads'),  # threads: libpthread before glibc 2.34 (pthread_getcpuclockid)
  c_args: native_cargs, override_options: native_opts, install: true, install_dir: native_dir / 'bin')
# the LD_PRELOAD library (first in symbol lookup for every library of the process, first static TLS module): the path layer and bionic's TLS slots
# and the userspace binder's route (binder_route.c: bionic_translation's fd hooks find it here, one copy per process)
apklayer_preload = shared_module('apklayer-preload', ['paths.c', 'paths_preload.c', 'proc_view.c', 'proc_task.c', 'exec_preload.c', 'guest_exec_environment.c', 'bionic_tls.c', 'binder_route.c', 'broker.c', '../binderd/wire.c'],
  link_args: ['-ldl', '-lpthread'], c_args: native_cargs, override_options: native_opts, install: true, install_dir: native_dir)
# the host's libGLESv2 with extensions hidden from Android code (APKLAYER_GL_HIDE; cli/env.cpp maps libGLESv2.so here only then)
gles_filter_glesv2 = dependency('glesv2', required: false)
if gles_filter_glesv2.found()
  shared_module('apklayer_gles', 'gles_filter.c', dependencies: gles_filter_glesv2, link_args: ['-Wl,--no-as-needed', '-lGLESv2', '-Wl,--as-needed', '-ldl', '-lpthread'],
    name_prefix: 'lib', c_args: native_cargs, override_options: native_opts, install: true, install_dir: native_dir)
endif
executable('aprop', ['aprop.c', 'properties.c'], c_args: native_cargs, override_options: native_opts, install: true, install_dir: native_dir / 'bin')
install_data('getprop', install_dir: native_dir / 'bin', install_mode: 'rwxr-xr-x')  # Android's getprop for the apps that run it

# bionic_translation's libraries: <pkg_lib>/bionic_translation/b (the CLI's APKLAYER_BT), and the native dir's names for them (its
# android-system-stubs, and the libraries that look there: a stale copy made the JVM spin in the property reader)
bt_libs = {'libdl_bio.so.0.0.1': ['libdl_bio.so.0', 'libdl_bio.so'], 'libc_bio.so.0': ['libc_bio.so'], 'libpthread_bio.so.0': ['libpthread_bio.so'],
  'libstdc++_bio.so.0': ['libstdc++_bio.so']}
custom_target('bt-libs', input: [dl_bio, c_bio, bt.get_variable('pthread_bio'), bt.get_variable('stdcpp_bio')], output: bt_libs.keys(),
  command: ['cp', '@INPUT@', '@OUTDIR@'], install: true, install_dir: pkg_lib / 'bionic_translation/b', build_by_default: true)
foreach file, names : bt_libs
  foreach name : names
    install_symlink(name, pointing_to: file, install_dir: pkg_lib / 'bionic_translation/b')
  endforeach
  foreach name : [file] + names
    install_symlink(name, pointing_to: '../bionic_translation/b' / name, install_dir: native_dir)
  endforeach
endforeach
# (paths into the subproject as strings: Meson does not let a project take a subproject's files; its cfg changes only with the pinned revision)
bt_cfg = bt_src / 'cfg.d/bionic_translation.cfg'
custom_target('bt-cfg.d', output: 'cfg.d', command: ['sh', '-c', 'mkdir -p "$1" && cp "$2" "$1/"', 'sh', '@OUTPUT@', bt_cfg],
  install: true, install_dir: pkg_lib / 'bionic_translation', build_by_default: true)
# what apps that dlopen system libraries by name find on the LD_LIBRARY_PATH they read (HostLibraryPath.java)
foreach f : ['libc', 'libdl', 'libstdc++']
  install_symlink(f + '.so', pointing_to: '../' + f + '_bio.so', install_dir: native_dir / 'android-system-stubs')
endforeach
libm = run_command(cc.cmd_array(), '-print-file-name=libm.so.6', check: true).stdout().strip()
install_symlink('libm.so', pointing_to: run_command('realpath', libm, check: true).stdout().strip(), install_dir: native_dir / 'android-system-stubs')

# bionic_translation reads every file of <native>/share/bionic_translation/cfg.d and takes the first line naming a library: ours, the mappings
# that need a staged library (tools/gen/bt-cfg.py looks at android_libs), then upstream's
if android_libs != ''
  checked = []
  foreach lib : ['libnativedisplay.so', 'libapklayer_egl.so', 'libapklayer_vulkan.so', 'vulkan.apklayer.so', 'libOpenSLES.so']
    if fs.exists(android_libs / lib)
      checked += android_libs / lib
    endif
  endforeach
  custom_target('bionic_translation.cfg', input: 'bionic_translation.cfg',
    output: 'bionic_translation.cfg', depend_files: checked, command: [python, files('../../tools/gen/bt-cfg.py'), '@INPUT@', bt_cfg, android_libs, '@OUTPUT@'],
    install: true, install_dir: native_dir / 'share/bionic_translation/cfg.d')
endif
