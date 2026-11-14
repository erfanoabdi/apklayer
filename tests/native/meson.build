# bionic_translation regression tests (`meson test -C b --suite bionic`; tests/run native): each program dlopens libc_bio and checks one
# wrapper (mutex timedlock, cond destroy/clockwait, fortify read). The library is bionic_translation's (the subproject, Linux only).
if is_variable('c_bio')
  bt_dir = fs.parent(c_bio.full_path())
  native_dl = dependency('dl')
  native_threads = dependency('threads')
  foreach name : ['mutex-timedlock', 'cond-destroy', 'cond-clock', 'fortify-read', 'attr-copy', 'attr-stack', 'strerror-r', 'app-uid', 'std-streams']
    test(name, executable(name + '-test', name + '.c', dependencies: [native_dl, native_threads], c_args: ['-Wno-unused-parameter']),
      args: [c_bio], env: ['LD_LIBRARY_PATH=' + bt_dir], suite: 'bionic', timeout: 30)
  endforeach
  # a chaining bionic crash handler ends the process (0046); JVM_get_signal_action exported as libjsig's
  test('fault-chain', executable('fault-chain-test', 'fault-chain.c', dependencies: [native_dl], export_dynamic: true),
    args: [c_bio], env: ['LD_LIBRARY_PATH=' + bt_dir], suite: 'bionic', timeout: 30)
  # the dl* entries as namespace bypasses read them: first bl -> __loader_* (0049; androidnsbypass in FCL/Pojav)
  test('loader-entry', executable('loader-entry-test', 'loader-entry.c', dependencies: [native_dl]),
    args: [dl_bio], env: ['LD_LIBRARY_PATH=' + bt_dir], suite: 'bionic', timeout: 30)
  # PLT hooks as bytehook makes them, through dl_iterate_phdr and libdl's GOT (0054); the library has no libc, the shim linker loads it
  plt_hook_lib = shared_module('plt-hook-lib', 'plt-hook-lib.c', link_args: ['-nostdlib'], c_args: ['-fno-stack-protector'])
  test('plt-hook', executable('plt-hook-test', 'plt-hook.c', dependencies: [native_dl], export_dynamic: true),
    args: [dl_bio, 'libplt-hook-lib.so'], depends: plt_hook_lib, suite: 'bionic', timeout: 30,
    env: ['LD_LIBRARY_PATH=' + bt_dir, 'BIONIC_LD_LIBRARY_PATH=' + meson.current_build_dir()])
  # bionic code's path functions through the path layer (paths.c, as the shim installs it)
  test('etc-files', executable('etc-files-test', ['etc-files.c', '../../bridge/native/paths.c'], dependencies: [native_dl], c_args: ['-U_FILE_OFFSET_BITS']),
    args: [meson.project_source_root() / 'bridge/native/android-root'], suite: 'bionic', timeout: 30)
  test('bionic-paths', executable('bionic-paths-test', ['bionic-paths.c', '../../bridge/native/paths.c'], dependencies: [native_dl], c_args: ['-U_FILE_OFFSET_BITS']),
    args: [c_bio], env: ['LD_LIBRARY_PATH=' + bt_dir], suite: 'bionic', timeout: 30)
endif
# the path layer's LD_PRELOAD library (bridge/native/paths_preload.c): /storage mapped for the functions glibc does not route through open/stat
if is_variable('apklayer_preload')
  test('path-layer', executable('path-layer-test', 'path-layer.c'), env: ['LD_PRELOAD=' + apklayer_preload.full_path()], suite: 'bionic', timeout: 30)
  # needs its own cgroup scope, as proc-leaks: `systemd-run --user --scope meson test proc-host-tools`; skipped (77) where every process shares one
  test('proc-host-tools', executable('proc-host-tools-test', 'proc-host-tools.c', dependencies: [native_dl]), env: ['LD_PRELOAD=' + apklayer_preload.full_path()], suite: 'bionic', timeout: 30)
  test('apk-paths', executable('apk-paths-test', 'apk-paths.c'), env: ['LD_PRELOAD=' + apklayer_preload.full_path()], suite: 'bionic', timeout: 30)
  test('etc-hidden', executable('etc-hidden-test', 'etc-hidden.c', dependencies: [native_dl]), env: ['LD_PRELOAD=' + apklayer_preload.full_path()], suite: 'bionic', timeout: 30)
  test('dev-pts', executable('dev-pts-test', 'dev-pts.c', dependencies: [native_dl]), env: ['LD_PRELOAD=' + apklayer_preload.full_path()], suite: 'bionic', timeout: 30)
  test('proc-mounts', executable('proc-mounts-test', 'proc-mounts.c', dependencies: [native_dl]), env: ['LD_PRELOAD=' + apklayer_preload.full_path()], suite: 'bionic', timeout: 30)
  test('own-data-spelling', executable('own-data-spelling-test', 'own-data-spelling.c'), env: ['LD_PRELOAD=' + apklayer_preload.full_path()], suite: 'bionic', timeout: 30)
  # needs its own cgroup scope (the filter judges processes by it): `systemd-run --user --scope meson test proc-leaks`; skipped (77) where every process shares one
  test('proc-leaks', executable('proc-leaks-test', 'proc-leaks.c', dependencies: [native_dl]), env: ['LD_PRELOAD=' + apklayer_preload.full_path()], suite: 'bionic', timeout: 30)
endif

if is_variable('apklayer_preload')
  maps_test = executable('proc-maps-test', 'proc-maps.c')
  status_test = executable('proc-status-test', 'proc-status.c')
  views_test = executable('proc-views-test', 'proc-views.c', dependencies: dependency('threads'))
  foreach mode : ['app', 'host', 'service']
    maps_env = ['LD_PRELOAD=' + apklayer_preload.full_path()]
    if mode != 'service'
      maps_env += ['APKLAYER_OWN_DATA=hello.maps:10001,3003,9997,20001,50001:/tmp/hello.maps']
    endif
    if mode == 'host'
      maps_env += ['APKLAYER_PROC_VIEW=host']
    endif
    test('proc-maps-' + mode, maps_test, env: maps_env, suite: 'bionic', timeout: 30)
    test('proc-status-' + mode, status_test, env: maps_env, suite: 'bionic', timeout: 30)
    test('proc-views-' + mode, views_test, env: maps_env, suite: 'bionic', timeout: 30)
  endforeach
  test('proc-maps-java', find_program('java'), args: [meson.current_source_dir() / 'ArtMaps.java'],
    env: ['LD_PRELOAD=' + apklayer_preload.full_path(), 'APKLAYER_OWN_DATA=hello.maps:10001,3003,9997,20001,50001:/tmp/hello.maps'], suite: 'bionic', timeout: 30)
endif

if is_variable('apklayer_preload') and is_variable('dl_bio')
  module_fixture = shared_module('module-view-lib', 'module-view-lib.c',
    c_args: ['-fasynchronous-unwind-tables', '-fno-optimize-sibling-calls'], link_args: ['-Wl,--eh-frame-hdr'])
  module_test = executable('module-view-test', 'module-view.c', dependencies: [dependency('dl')])
  foreach mode : ['app', 'host', 'service']
    module_env = ['LD_PRELOAD=' + apklayer_preload.full_path(), 'LD_LIBRARY_PATH=' + fs.parent(dl_bio.full_path())]
    if mode != 'service'
      module_env += ['APKLAYER_OWN_DATA=hello.modules:10001:/tmp/hello.modules']
    endif
    if mode == 'host'
      module_env += ['APKLAYER_PROC_VIEW=host']
    endif
    test('module-view-' + mode, module_test, args: [dl_bio, module_fixture], env: module_env, suite: 'bionic', timeout: 30)
  endforeach
endif

if is_variable('c_bio')
  environment_fixture = shared_module('environment-lib', 'environment-lib.c', link_args: ['-nostdlib'], c_args: ['-fno-stack-protector'])
  environment_test = executable('environment-test', 'environment.c', dependencies: [dependency('dl')], export_dynamic: true)
  foreach mode : ['app', 'host', 'service']
    environment_env = ['LD_LIBRARY_PATH=' + fs.parent(c_bio.full_path()), 'BIONIC_LD_LIBRARY_PATH=' + meson.current_build_dir(),
      'HOME=/tmp/environment-host-home', 'JAVA_HOME=/host/jdk', 'ENV_HOST_ONLY=private']
    if mode != 'service'
      environment_env += ['APKLAYER_OWN_DATA=hello.environment:10001:/tmp/hello.environment']
    endif
    if mode == 'host'
      environment_env += ['APKLAYER_PROC_VIEW=host']
    endif
    test('environment-' + mode, environment_test, args: [c_bio, dl_bio, 'libenvironment-lib.so'],
      depends: environment_fixture, env: environment_env, suite: 'bionic', timeout: 30)
  endforeach
endif

if is_variable('apklayer_preload')
  test('guest-exec-environment', executable('guest-exec-environment-test',
    ['guest-exec-environment.c', '../../bridge/native/guest_exec_environment.c'], dependencies: [dependency('threads')]),
    suite: 'bionic', timeout: 30)
endif

if is_variable('apklayer_preload') and is_variable('c_bio')
  proc_environment_test = executable('proc-environment-test', 'proc-environment.c', dependencies: [dependency('dl')])
  foreach mode : ['app', 'host', 'service']
    proc_environment_env = ['LD_PRELOAD=' + apklayer_preload.full_path(), 'LD_LIBRARY_PATH=' + fs.parent(c_bio.full_path()),
      'ENV_HOST_ONLY=private']
    if mode != 'service'
      proc_environment_env += ['APKLAYER_OWN_DATA=hello.environment:10001:/tmp/hello.environment']
    endif
    if mode == 'host'
      proc_environment_env += ['APKLAYER_PROC_VIEW=host']
    endif
    test('proc-environment-' + mode, proc_environment_test, args: [c_bio], env: proc_environment_env, suite: 'bionic', timeout: 30)
  endforeach
endif

if is_variable('c_bio') and is_variable('apklayer_preload')
  bionic_status_test = executable('bionic-status-test', 'bionic-status.c', dependencies: [dependency('dl')])
  foreach mode : ['app', 'service']
    bionic_status_env = ['LD_PRELOAD=' + apklayer_preload.full_path(), 'LD_LIBRARY_PATH=' + fs.parent(c_bio.full_path())]
    if mode == 'app'
      bionic_status_env += ['APKLAYER_OWN_DATA=hello.status:10001:/tmp/hello.status']
    endif
    test('bionic-status-' + mode, bionic_status_test, args: [c_bio], env: bionic_status_env, suite: 'bionic', timeout: 30)
  endforeach
  test('guest-exec', executable('guest-exec-test', 'guest-exec.c', dependencies: [dependency('dl')]), args: [c_bio],
    env: ['LD_PRELOAD=' + apklayer_preload.full_path(), 'LD_LIBRARY_PATH=' + fs.parent(c_bio.full_path()),
      'APKLAYER_OWN_DATA=hello.exec:10001:/tmp/hello.exec', 'ENV_HOST_ONLY=private', 'PATH=/host-path-must-not-be-searched'],
    suite: 'bionic', timeout: 30)
  # guest system, popen/pclose/fclose and posix_spawn[p] (0069): /system/bin/sh, the guest environment and PATH, bionic's spawn objects
  test('guest-spawn', executable('guest-spawn-test', 'guest-spawn.c', dependencies: [dependency('dl'), dependency('threads')]), args: [c_bio],
    env: ['LD_PRELOAD=' + apklayer_preload.full_path(), 'LD_LIBRARY_PATH=' + fs.parent(c_bio.full_path()),
      'APKLAYER_OWN_DATA=hello.spawn:10001:/tmp/hello.spawn', 'ENV_HOST_ONLY=private', 'PATH=/host-path-must-not-be-searched'],
    suite: 'bionic', timeout: 30)
endif

# guest code on native arm64 (bionic_translation 0073/0074): raw `svc` syscalls answered through the process views, and the app namespace's
# dlopen/dladdr/dl_iterate_phdr (the library has no libc; the bionic linker loads it in the app namespace). Skipped (77) off arm64.
if is_variable('apklayer_preload') and is_variable('dl_bio')
  raw_syscall_lib = shared_module('raw-syscall-lib', 'raw-syscall-lib.c', link_args: ['-nostdlib'], c_args: ['-fno-stack-protector'])
  raw_syscall_test = executable('raw-syscall-test', 'raw-syscall.c', dependencies: [dependency('dl'), dependency('threads')])
  foreach mode : ['app', 'off']
    raw_env = ['LD_PRELOAD=' + apklayer_preload.full_path(), 'LD_LIBRARY_PATH=' + fs.parent(dl_bio.full_path()),
      'BIONIC_LD_LIBRARY_PATH=' + meson.current_build_dir(), 'APKLAYER_ANDROID_ROOT=' + meson.project_source_root() / 'bridge/native/android-root',
      'APKLAYER_OWN_DATA=hello.raw:10001,3003,9997,20001,50001:/tmp/hello.raw']
    if mode == 'off'
      raw_env += ['APKLAYER_RAW_SYSCALL_VIEW=off']
    endif
    test('raw-syscall-' + mode, raw_syscall_test, args: [dl_bio, 'libraw-syscall-lib.so', mode], depends: raw_syscall_lib, env: raw_env, suite: 'bionic', timeout: 30)
  endforeach
endif
