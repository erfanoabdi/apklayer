# apklayer.host (app host on Robolectric's simulator) and apklayer.tools (build-time helpers).
host_jar = custom_target('apklayer-host.jar',
  input: files(run_command('sh', '-c', 'cd "$MESON_SOURCE_ROOT/$MESON_SUBDIR" && find java -name "*.java" ! -name "._*" | sort', check: true).stdout().strip().split('\n')),
  output: 'apklayer-host.jar',
  command: [javac_jar, '@OUTPUT@', '@PRIVATE_DIR@', robolectric_cp, 'apklayer.host.Main', '@INPUT@'],
  build_by_default: true)
