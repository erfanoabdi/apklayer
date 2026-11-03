# bionic_translation regression tests (`meson test -C b --suite bionic`; tests/run native): each program dlopens libc_bio and checks one
# wrapper (mutex timedlock, cond destroy/clockwait, fortify read). The library comes from APKLAYER_BT (default ~/bt-arm/bionic_translation/b);
# without it the tests are not defined.
bt = run_command('sh', '-c', 'echo "${APKLAYER_BT:-$HOME/bt-arm/bionic_translation/b}"', check: true).stdout().strip()
libc_bio = bt / 'libc_bio.so.0'
if import('fs').exists(libc_bio)
  native_dl = dependency('dl')
  native_threads = dependency('threads')
  foreach name : ['mutex-timedlock', 'cond-destroy', 'cond-clock', 'fortify-read']
    test(name, executable(name + '-test', name + '.c', dependencies: [native_dl, native_threads], c_args: ['-Wno-unused-parameter']),
      args: [libc_bio], env: ['LD_LIBRARY_PATH=' + bt], suite: 'bionic', timeout: 30)
  endforeach
endif
