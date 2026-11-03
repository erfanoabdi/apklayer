# bionic_translation regression tests (`meson test -C b --suite bionic`; tests/run native): each program dlopens libc_bio and checks one
# wrapper (mutex timedlock, cond destroy/clockwait, fortify read). The library is bionic_translation's (bt_dir of the top meson.build);
# without it the tests are not defined.
libc_bio = bt_dir / 'libc_bio.so.0'
if fs.exists(libc_bio)
  native_dl = dependency('dl')
  native_threads = dependency('threads')
  foreach name : ['mutex-timedlock', 'cond-destroy', 'cond-clock', 'fortify-read']
    test(name, executable(name + '-test', name + '.c', dependencies: [native_dl, native_threads], c_args: ['-Wno-unused-parameter']),
      args: [libc_bio], env: ['LD_LIBRARY_PATH=' + bt_dir], suite: 'bionic', timeout: 30)
  endforeach
endif
