# DEX -> JVM converter: upstream kksimp/dex2jvm (Apache-2.0, commit in .dex2jvm-commit) plus apklayer's own HostDefaults.java.
dex2jvm_jar = custom_target('dex2jvm.jar',
  input: files(run_command('sh', '-c', 'cd "$MESON_SOURCE_ROOT/$MESON_SUBDIR" && find . -name "*.java" ! -name "._*" | sort', check: true).stdout().strip().split('\n')),
  output: 'dex2jvm.jar',
  command: [javac_jar, '@OUTPUT@', '@PRIVATE_DIR@', '-', 'io.github.kksimp.dex2jvm.Main', '@INPUT@'],
  build_by_default: true)
