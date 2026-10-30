#!/bin/sh
# build-bionic-translation: a patched bionic_translation (scripts/patch-bionic-translation.sh) built on THIS machine, whatever its architecture.
# Usage: scripts/build-bionic-translation.sh <source dir (a clone of bionic_translation)> [build dir (default <source>/b)]
# Older toolchains (Ubuntu 22.04: gcc 11, libunwind 1.3, glibc 2.35) get the small compatibility shims in scripts/x86_64/ (RELR types, <stdatomic.h> for C++, libunwind version, -std=c++2b).
set -e
src="$(cd "$1" && pwd)"
build="${2:-$src/b}"
here="$(cd "$(dirname "$0")" && pwd)"
sh "$here/patch-bionic-translation.sh" "$src"
cflags="${CFLAGS:-}"
cxxflags="${CXXFLAGS:-}"
gcc_major="$(${CC:-cc} -dumpversion | cut -d. -f1)"
if [ "$gcc_major" -lt 13 ] 2>/dev/null; then sed -i 's/--std=c++23/--std=c++2b/' "$src/meson.build"; fi
if [ "$gcc_major" -lt 12 ] 2>/dev/null; then
    cxxflags="$cxxflags -I$here/x86_64/compat"
fi
if ! grep -q 'Elf64_Relr' /usr/include/elf.h 2>/dev/null; then cflags="$cflags -include $here/x86_64/relr-compat.h"; cxxflags="$cxxflags -include $here/x86_64/relr-compat.h"; fi
unwind="$(pkg-config --modversion libunwind 2>/dev/null || echo 0)"
case "$unwind" in 0|1.[0-7]*)
    sed -i "s/version : '>=1.8.0'/version : '>=1.3.0'/" "$src/meson.build"
    # libunwind before 1.8 has no unw_set_iterate_phdr_function (backtraces then show only the libraries the system loader knows)
    grep -q 'unw_set_iterate_phdr_function) unw_set' "$src/linker/linker.c" || sed -i 's|^\(\s*\)unw_set_iterate_phdr_function(\(.*\));|extern void unw_set_iterate_phdr_function() __attribute__((weak));\n\1if (unw_set_iterate_phdr_function) unw_set_iterate_phdr_function(\2);|' "$src/linker/linker.c";;
esac
rm -rf "$build"
CFLAGS="$cflags" CXXFLAGS="$cxxflags" meson setup "$build" "$src" >/dev/null
ninja -C "$build"
