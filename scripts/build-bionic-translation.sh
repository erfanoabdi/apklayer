#!/bin/sh
# build-bionic-translation: bionic_translation with apklayer's patches (patches/bionic_translation, `patches/apply`) built on THIS machine, whatever its architecture.
# Usage: scripts/build-bionic-translation.sh <source dir (a clone of bionic_translation at patches/bionic_translation/REVISION)> [build dir (default <source>/b)]
# Older toolchains (Ubuntu 22.04: gcc 11, libunwind 1.3, glibc 2.35) also get patches/bionic_translation/compat/ (-std=c++2b, libunwind version, RELR types, <stdatomic.h> for C++).
set -e
src="$(cd "$1" && pwd)"
build="${2:-$src/b}"
here="$(cd "$(dirname "$0")" && pwd)"
patches="$here/../patches"
compat="$patches/bionic_translation/compat"
"$patches/apply" bionic_translation "$src"
cflags="${CFLAGS:-}"
cxxflags="${CXXFLAGS:-}"
gcc_major="$(${CC:-cc} -dumpversion | cut -d. -f1)"
if [ "$gcc_major" -lt 13 ] 2>/dev/null; then "$patches/apply" "$compat/gcc-before-13-cxx2b.patch" "$src"; fi
if [ "$gcc_major" -lt 12 ] 2>/dev/null; then
    cxxflags="$cxxflags -I$compat/include"
fi
if ! grep -q 'Elf64_Relr' /usr/include/elf.h 2>/dev/null; then cflags="$cflags -include $compat/include/relr-compat.h"; cxxflags="$cxxflags -include $compat/include/relr-compat.h"; fi
unwind="$(pkg-config --modversion libunwind 2>/dev/null || echo 0)"
case "$unwind" in 0|1.[0-7]*) # one call each: a copy may have meson.build reset (scripts/stage-x86_64.sh) and keep linker.c
    "$patches/apply" "$compat/libunwind-before-1.8-meson.patch" "$src"
    "$patches/apply" "$compat/libunwind-before-1.8-weak-phdr.patch" "$src";;
esac
rm -rf "$build"
CFLAGS="$cflags" CXXFLAGS="$cxxflags" meson setup "$build" "$src" >/dev/null
ninja -C "$build"
