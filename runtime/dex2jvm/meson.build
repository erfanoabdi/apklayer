# DEX -> JVM converter (from Mac.apk, AGPL-3.0, unmodified).
dex2jvm_jar = custom_target('dex2jvm.jar',
  input: files(run_command('sh', '-c', 'cd "$MESON_SOURCE_ROOT/$MESON_SUBDIR" && find . -name "*.java" ! -name "._*" | sort', check: true).stdout().strip().split('\n')),
  output: 'dex2jvm.jar',
  command: [javac_jar, '@OUTPUT@', '@PRIVATE_DIR@', '-', 'com.macapk.dex.Main', '@INPUT@'],
  build_by_default: true)
