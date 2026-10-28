project('apklayer', ['c', 'cpp', 'java'],
  version: '0.1',
  license: 'AGPL-3.0-or-later',
  meson_version: '>=1.3',
  default_options: ['warning_level=2', 'buildtype=release', 'cpp_std=c++17'])

# One javac call per jar (scripts/javac-jar.sh): Meson's jar() compiles per file and resolves
# siblings by package path, which dex2jvm's flat upstream layout doesn't follow.
find_program('javac')
find_program('jar')
javac_jar = find_program('scripts/javac-jar.sh')

subdir('runtime/dex2jvm')
subdir('bridge/shell')
subdir('cli')
subdir('bridge/hals/graphics')

subdir('bridge/hals/media')

subdir('bridge/hals/audio')
