# Our own versions of device libraries that need a service the desktop does not have: the source's functions, plus (ndk_libs) weak
# do-nothing stubs for every other export of the real staged library. Mapped by bridge/native/bionic_translation.cfg.
shared_module('camera2ndk', 'libcamera2ndk.c', c_args: native_cargs, override_options: native_opts)  # no camera HAL: the camera NDK reports errors
ndk_libs = {'selinux': 'apklayer_selinux'}  # still needed by the libraries that link it (the service manager no longer uses it)
foreach real, ours : ndk_libs
  lib = android_libs / 'lib' + real + '.so'
  if fs.exists(lib)
    stubs = custom_target('lib' + real + '-stubs', input: lib, output: 'lib' + real + '-stubs.c',
      command: [find_program('../../tools/gen/ndk-stubs.sh'), '@INPUT@', '@OUTPUT@'])
    shared_module(ours, ['lib' + real + '.c', stubs], c_args: native_cargs, override_options: native_opts)
  endif
endforeach
