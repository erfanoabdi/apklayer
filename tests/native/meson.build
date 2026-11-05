# bionic_translation regression tests (`meson test -C b --suite bionic`; tests/run native): each program dlopens libc_bio and checks one
# wrapper (mutex timedlock, cond destroy/clockwait, fortify read). The library is bionic_translation's (the subproject, Linux only).
if is_variable('c_bio')
  bt_dir = fs.parent(c_bio.full_path())
  native_dl = dependency('dl')
  native_threads = dependency('threads')
  foreach name : ['mutex-timedlock', 'cond-destroy', 'cond-clock', 'fortify-read', 'attr-copy']
    test(name, executable(name + '-test', name + '.c', dependencies: [native_dl, native_threads], c_args: ['-Wno-unused-parameter']),
      args: [c_bio], env: ['LD_LIBRARY_PATH=' + bt_dir], suite: 'bionic', timeout: 30)
  endforeach
  # bionic code's path functions through the path layer (paths.c, as the shim installs it)
  test('bionic-paths', executable('bionic-paths-test', ['bionic-paths.c', '../../bridge/native/paths.c'], dependencies: [native_dl], c_args: ['-U_FILE_OFFSET_BITS']),
    args: [c_bio], env: ['LD_LIBRARY_PATH=' + bt_dir], suite: 'bionic', timeout: 30)
endif
# the path layer's LD_PRELOAD library (bridge/native/paths_preload.c): /storage mapped for the functions glibc does not route through open/stat
if is_variable('apklayer_preload')
  test('path-layer', executable('path-layer-test', 'path-layer.c'), env: ['LD_PRELOAD=' + apklayer_preload.full_path()], suite: 'bionic', timeout: 30)
endif
