# The native runtime's host binaries, staged into the native dir by `tools/build native`. Built where bionic_translation is (bt_dir).
# shared_module: no SONAME and undefined symbols allowed (the JVM, LD_PRELOAD and bionic_translation load them by path; Android's libraries resolve the rest).
native_opts = ['optimization=2', 'warning_level=1', 'b_asneeded=false']
bt_link = ['-L' + bt_dir, '-ldl_bio', '-Wl,-rpath,' + bt_dir]
jni_dep = dependency('jni', required: false)
if jni_dep.found()
  # the JNI shim Robolectric loads (tools/build native puts it into native-runtime.jar and the framework jars)
  shared_module('android_runtime', ['runtime_shim.c', 'media_runtime.c', 'binder_runtime.c', 'app_native.c', 'native_loader.c',
      'android_platform.c', 'properties.c', 'paths.c', 'android_stubs.c'],
    dependencies: jni_dep, link_args: bt_link, override_options: native_opts)
endif
# the launcher for Android executables built as libraries (btload --main)
executable('btload', ['btload.c', 'android_platform.c', 'properties.c', 'paths.c', 'binder_compat.c', 'android_stubs.c'],
  export_dynamic: true, link_args: bt_link, override_options: native_opts)
# the LD_PRELOAD library (first in symbol lookup for every library of the process, first static TLS module): the path layer and bionic's TLS slots
shared_module('apklayer-preload', ['paths.c', 'paths_preload.c', 'bionic_tls.c'],
  link_args: ['-ldl'], override_options: native_opts)
executable('aprop', ['aprop.c', 'properties.c'], override_options: native_opts)
