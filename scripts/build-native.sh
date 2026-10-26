#!/usr/bin/env bash
# build-native: P4. Builds bionic_translation (the bionic linker for Android .so files) and
# apklayer's JNI loader for x86_64 inside the OrbStack x86_64 VM (the app JVM's arch on the dev VM,
# D17) and copies the results to build/native/x86_64/. The Parallels VM gets them via vm-run.
# Needs ../bionic_translation (git clone https://gitlab.com/android_translation_layer/bionic_translation).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BT="${BIONIC_TRANSLATION:-$ROOT/../bionic_translation}"
OUT="$ROOT/build/native/x86_64"; mkdir -p "$OUT"
VMN="${ORB_VM:-ubuntu22-x86}"
(cd "$ROOT/.." && tar cf - "$(basename "$BT")") | orb -m "$VMN" bash -c 'rm -rf ~/bt && mkdir -p ~/bt && tar xf - -C ~/bt 2>/dev/null'
tar cf - -C "$ROOT/bridge" native | orb -m "$VMN" bash -c 'tar xf - -C ~/bt 2>/dev/null'
orb -m "$VMN" bash -s <<'R'
set -e
export PATH=$HOME/.local/bin:$PATH PKG_CONFIG_PATH=$HOME/local/lib/pkgconfig
cd ~/bt
cat > relr-compat.h <<EOF
#include <elf.h>
#ifndef DT_RELR
typedef Elf32_Word Elf32_Relr;
typedef Elf64_Xword Elf64_Relr;
#define DT_RELRSZ 35
#define DT_RELR 36
#define DT_RELRENT 37
#endif
EOF
cd "$(ls -d bionic_translation* | head -1)"
rm -rf b && CFLAGS="-include $HOME/bt/relr-compat.h" meson setup b -Dbuildtype=release >/dev/null
ninja -C b -k 0 >/dev/null 2>&1 || true   # libstdc++_bio needs a newer compiler and is not needed yet
test -e b/libdl_bio.so && test -e b/libc_bio.so && test -e b/libpthread_bio.so
JNI=$(dirname "$(dirname "$(readlink -f "$(which javac)")")")/include
[ -d "$JNI" ] || JNI=$(ls -d /usr/lib/jvm/*/include | head -1)
cc -shared -fPIC -O2 -I"$JNI" -I"$JNI/linux" -o b/libapklayer_native.so ~/bt/native/native_loader.c -Lb -ldl_bio -Wl,-rpath,'$ORIGIN'
mkdir -p ~/bt/out && find b -maxdepth 1 \( -type f -o -type l \) -name 'lib*.so*' -exec cp -L {} ~/bt/out/ \;
# libstdc++_bio: build with clang; __cxa_guard.cpp needs libc++'s <atomic>, so link without it (apps
# with C++ static initialisers will need it later)
if [ ! -e ~/bt/bionic_translation/b/libstdc++_bio.so.0 ]; then
  cd ~/bt/bionic_translation && rm -rf b2 && sed -i 's/--std=c++23/--std=c++2b/' meson.build
  CC=clang CXX=clang++ CFLAGS="-include $HOME/bt/relr-compat.h" meson setup b2 -Dbuildtype=release >/dev/null
  ninja -C b2 -k 0 >/dev/null 2>&1 || true
  clang++ -shared -fPIC -o b/libstdc++_bio.so.0 b2/libstdc++_bio.so.0.p/*.o -Wl,-soname,libstdc++_bio.so.0
  ln -sf libstdc++_bio.so.0 b/libstdc++_bio.so
fi
cd ~/bt/bionic_translation
mkdir -p ~/bt/out && find b -maxdepth 1 \( -type f -o -type l \) -name 'lib*.so*' -exec cp -L {} ~/bt/out/ \;
# name overrides: libc.so -> libc_bio.so.0 etc. (the linker reads cfg.d under $XDG_DATA_DIRS/bionic_translation)
mkdir -p ~/bt/out/share/bionic_translation/cfg.d
cp cfg.d/bionic_translation.cfg ~/bt/out/share/bionic_translation/cfg.d/
printf 'libm.so            libm.so.6\nlibdl.so           libdl_bio.so.0\n' >> ~/bt/out/share/bionic_translation/cfg.d/bionic_translation.cfg
# bundle the non-glibc shared libraries these need (the dev VM's x86_64 userspace is minimal)
cd ~/bt/out
for l in $(LD_LIBRARY_PATH=. ldd libapklayer_native.so libdl_bio.so libc_bio.so libpthread_bio.so | awk '/=>/ {print $3}' | sort -u); do
  case "$(basename "$l")" in libc.so.*|libm.so.*|libdl.so.*|libpthread.so.*|librt.so.*|ld-linux*) ;; *) [ -e "$(basename "$l")" ] || cp -L "$l" . ;; esac
done
R
orb -m "$VMN" bash -c 'cd ~/bt/out && tar cf - .' | tar xf - -C "$OUT"
ls "$OUT"
