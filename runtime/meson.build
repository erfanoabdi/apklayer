# The runtime's Java side against the prebuilts' Robolectric and framework (common/ of the prebuilts branch), into the native dir:
# host classes, the Robolectric overlay with the JNI shim (native-runtime.jar), the java.base patch; with android_runtime also the Android half
# (android-all/: the framework jar with the shim, android-all-instrumented/: the same instrumented once, android-root/, the Robolectric jars).
pc = prebuilts / 'common'
robolectric = pc / 'Robolectric_all.jar'
sim_classes = pc / 'sim-classes'
framework_name = 'android-all-16-robolectric-13921718.jar'
framework = pc / framework_name
foreach f : [robolectric, framework]
  if not fs.is_file(f)
    error('-Dprebuilts=' + prebuilts + ' has no ' + f + ' (a joined checkout of the prebuilts branch: sh <prebuilts>/join-parts.sh)')
  endif
endforeach
java_files = 'cd "$MESON_SOURCE_ROOT/$MESON_SUBDIR" && find "$1" -type f ! -name "._*" | sort'
overlay_sources = files(run_command('sh', '-c', java_files, 'sh', 'patches', check: true).stdout().strip().split('\n'))
robolectric_patches = files(run_command('sh', '-c', java_files, 'sh', '../patches/robolectric', check: true).stdout().strip().split('\n'))
simulator = deps['libs/simulator-4.17.jar']
shim_entries = ['native/linux/x86_64/libandroid_runtime.so=@INPUT1@', 'native/linux/aarch64/libandroid_runtime.so=@INPUT1@']

# the host (runtime/host): -sourcepath runtime/patches -implicit:none checks the overlay classes it calls (WindowOrder) from their sources; they
# come from native-runtime.jar at run time. The tree's simulator predates the FrameListener split: those two classes come from the pinned simulator.
host_jar = custom_target('host-classes.jar',
  input: files(run_command('sh', '-c', java_files, 'sh', 'host', check: true).stdout().strip().split('\n')) + [simulator, dex2jvm_jar],
  depend_files: overlay_sources, output: 'host-classes.jar',
  command: [javac_jar, '@OUTPUT@', '@PRIVATE_DIR@', '--cp', ':'.join([sim_classes, robolectric, framework, dex2jvm_jar.full_path()]),
    '--extract', simulator, 'org/robolectric/simulator/FrameListener.class', 'org/robolectric/simulator/ScreenUpdateHandler.class',
    '--resources', meson.current_source_dir() / 'host/resources',
    '--javac=--add-exports=java.base/sun.net.util=ALL-UNNAMED', '--javac=--add-exports=java.base/sun.security.tools.keytool=ALL-UNNAMED',
    '--javac=--add-exports=java.base/sun.security.x509=ALL-UNNAMED', '--javac=-sourcepath', '--javac=' + meson.current_source_dir() / 'patches',
    '--javac=-implicit:none', meson.current_source_dir() / 'host/java'],
  install: true, install_dir: native_dir / 'aosp')
host_cp = host_jar.full_path() + ':' + robolectric

# the tree's Robolectric files patches/robolectric changes (external/robolectric at its REVISION, the prebuilts' robolectric-src) and runtime/patches
overlay_jar = custom_target('overlay.jar', input: overlay_sources, depend_files: robolectric_patches, output: 'overlay.jar',
  command: [javac_jar, '@OUTPUT@', '@PRIVATE_DIR@', '--cp', robolectric + ':' + framework, '--patched', pc / 'robolectric-src', 'robolectric',
    meson.current_source_dir() / 'patches'])

# java.base members Android's native libraries look up (apklayer.host.JdkPatch): the JVM runs with --patch-module java.base=<native>/jdk-patch
custom_target('jdk-patch', input: host_jar, output: 'jdk-patch',
  command: ['sh', '-c', 'rm -rf "$1" && exec "$2" -cp "$3" apklayer.host.JdkPatch "$1"', 'sh', '@OUTPUT@', java, host_cp],
  install: true, install_dir: native_dir)

# @CriticalNative methods (apklayer.host.CriticalNatives) of the framework's javac output (the prebuilts' javac-shards), plus the ones of classes
# the shards lack (bridge/native/critical-natives-extra.txt)
shards = run_command('sh', '-c', 'ls "$1" | grep -E "^framework\\.jar[0-9]+$" | sed "s|^|$1/|"', 'sh', pc / 'javac-shards', check: true).stdout().split()
if shards.length() == 0
  error(pc / 'javac-shards has no framework.jar<N> (the framework\'s javac output, tools/stage)')
endif
critical_list = custom_target('critical-natives.list', input: host_jar, depend_files: shards, output: 'critical-natives.list',
  command: [java, '-cp', host_cp, 'apklayer.host.CriticalNatives', '@OUTPUT@', shards])
critical = custom_target('critical-natives.txt', input: [critical_list, '../bridge/native/critical-natives-extra.txt'], output: 'critical-natives.txt',
  command: ['sh', '-c', 'cat "$1" "$2" | awk \'!seen[$0]++\' > "$3"', 'sh', '@INPUT0@', '@INPUT1@', '@OUTPUT@'])

# apklayer patch (docs/PATCHES.md): Robolectric's loader refuses Linux aarch64; the patched class wins over Robolectric_all.jar's
custom_target('native-runtime.jar', input: [overlay_jar, shim, critical], output: 'native-runtime.jar',
  command: [jar_put, '@INPUT0@', '@OUTPUT@', shim_entries, 'critical-natives.txt=@INPUT2@'], install: true, install_dir: native_dir)

if get_option('android_runtime')
  subdir('android-all')
  subdir('android-all-instrumented')
  install_symlink('Robolectric_all.jar', pointing_to: robolectric, install_dir: native_dir / 'aosp')
  install_symlink('sim-classes', pointing_to: sim_classes, install_dir: native_dir / 'aosp')
  install_symlink('android-libs', pointing_to: android_libs, install_dir: native_dir)
  # the files Android processes read at fixed paths: the prebuilts' and apklayer's own (bridge/native/android-root, which wins). Copied as cp -a
  # does: Meson's install_subdir fails on the tree's links (data/media/0, links to the libraries)
  meson.add_install_script('sh', '-c', 'd="$MESON_INSTALL_DESTDIR_PREFIX/$1" && mkdir -p "$d" && cp -au --remove-destination "$2/." "$d/" && ' +
    'cp -r --remove-destination "$3/." "$d/"', 'sh', fs.relative_to(native_dir / 'android-root', get_option('prefix')),
    prebuilts / cpu / 'android-root', meson.project_source_root() / 'bridge/native/android-root')
  runtime_version = fs.read('../tools/release/versions').split('runtime=')[1].strip()
  configure_file(output: 'runtime.json', command: [python, '-c', 'import json, sys; json.dump({"version": sys.argv[1], "cpu": sys.argv[2], "built": True}, open(sys.argv[3], "w"))',
    runtime_version, cpu, '@OUTPUT@'], install: true, install_dir: native_dir)
endif
