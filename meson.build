project('apklayer', ['c', 'java'],
  version: '0.1',
  license: 'AGPL-3.0-or-later',
  meson_version: '>=1.3',
  default_options: ['warning_level=2', 'buildtype=release'])

# Pinned Java dependencies: deps/deps.lock, materialised by scripts/fetch-deps.sh.
deps_dir = get_option('deps')
if deps_dir == ''
  deps_dir = run_command('sh', '-c', 'echo "$HOME/.cache/apklayer/deps"', check: true).stdout().strip()
endif
robolectric_cp = deps_dir + '/libs/*:' + deps_dir + '/android-all/*'
# One javac call per jar (scripts/javac-jar.sh): Meson's jar() compiles per file and resolves
# siblings by package path, which dex2jvm's flat upstream layout doesn't follow.
find_program('javac')
find_program('jar')
javac_jar = find_program('scripts/javac-jar.sh')

subdir('runtime/dex2jvm')
subdir('runtime/host')
subdir('bridge/shell')
subdir('bridge/hals/graphics')

subdir('bridge/hals/media')

subdir('bridge/hals/audio')
