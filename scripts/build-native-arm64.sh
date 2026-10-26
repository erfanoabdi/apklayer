#!/bin/sh
# build-native-arm64: stage the arm64 native runtime (PLAN P7) on the arm64 VM from the tree's build outputs.
# Usage (on the VM, from the repo root): scripts/build-native-arm64.sh <native-dir> <javac-shards-dir> <host-classes-dir>
#   native-dir:   has aosp/android-all/*.jar (the tree's framework jar) and receives native-runtime.jar
#   javac-shards: ~/los24/out/soong/.intermediates/frameworks/base/framework-minus-apex/android_common/javac (copied here)
#   the shim comes from bridge/native/runtime_shim.c + android_platform.c + android_stubs.c (see scripts/android-stubs.py)
set -e
native="$1" shards="$2" classes="$3" tmp="$(mktemp -d)"
JH="$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")"
cc -shared -fPIC -O2 -Wall -I"$JH/include" -I"$JH/include/linux" -o "$tmp/libandroid_runtime.so" \
    bridge/native/runtime_shim.c bridge/native/android_platform.c bridge/native/android_stubs.c \
    -L"$HOME/bt-arm/bionic_translation/b" -ldl_bio -Wl,-rpath,"$HOME/bt-arm/bionic_translation/b"
java -cp "$classes:$(ls "$native"/aosp/*.jar | tr '\n' ':')" apklayer.host.CriticalNatives "$tmp/critical-natives.txt" $(ls "$shards" | grep -E "^framework\.jar[0-9]+$" | sed "s|^|$shards/|")
mkdir -p "$tmp/jar/native/linux/x86_64" && cp "$tmp/libandroid_runtime.so" "$tmp/jar/native/linux/x86_64/" && cp "$tmp/critical-natives.txt" "$tmp/jar/"
rm -f "$native/native-runtime.jar" && (cd "$tmp/jar" && jar cf "$native/native-runtime.jar" .)
# Robolectric loads the library from the framework jar's own native/ first: keep the same copy there
(cd "$tmp/jar" && zip -q "$native"/aosp/android-all/android-all-current-robolectric-r0.jar native/linux/x86_64/libandroid_runtime.so)
rm -rf "$tmp"
