# The pinned Java artifacts of deps.lock (sha256-checked: tools/gen/fetch-dep.py), one target each, into <build>/deps/<path>.
# android-all/ (Robolectric's Maven framework) is not fetched: the framework is the prebuilts' (runtime/android-all).
fetch_dep = [python, files('../tools/gen/fetch-dep.py')]
deps_local = prebuilts != '' ? [prebuilts / 'common'] : []
deps_lock = []
foreach line : fs.read('deps.lock').split('\n')
  f = line.strip().split()
  if f.length() == 3 and not f[0].startswith('#') and not f[1].startswith('android-all/')
    deps_lock += [f]
  endif
endforeach
deps = {}
foreach f : deps_lock
  if not f[1].contains('/')
    # android-stub.jar: the Android API stubs on the host's class path, with the Android half (the deb's runtime bundle has its own)
    deps += {f[1]: custom_target(f[1], output: f[1], command: [fetch_dep, f[0], f[2], '@OUTPUT@', deps_local], build_by_default: true,
      install: get_option('android_runtime') and prebuilts != '', install_dir: native_dir)}
  endif
endforeach
subdir('libs')
