#!/usr/bin/env bash
# Compile tree Robolectric overlays, then atomically update the runtime jar.
# An optional staging directory supplies a freshly built shim and JNI metadata.
set -euo pipefail
cd "$(dirname "$0")/.."
native="$(realpath "$1")"
staging="${2:-}"
work="$(mktemp -d "$native/.overlay.XXXXXX")"
trap 'rm -rf "$work"' EXIT
mkdir "$work/classes"
mapfile -t sources < <(find runtime/patches -name '*.java' -print | sort)
echo "Compiling ${#sources[@]} runtime overlay sources"
javac -nowarn -d "$work/classes" -cp "$native/aosp/Robolectric_all.jar:$native/android-all/*" "${sources[@]}"
if [ -n "$staging" ]; then
    cp -R "$staging/." "$work/classes/"
    jar cf "$work/runtime.jar" -C "$work/classes" .
else
    # Preserve the working shim when updating just Java patches.
    cp "$native/native-runtime.jar" "$work/runtime.jar"
    jar uf "$work/runtime.jar" -C "$work/classes" .
fi
jar tf "$work/runtime.jar" | grep -qx 'native/linux/aarch64/libandroid_runtime.so'
mv -f "$work/runtime.jar" "$native/native-runtime.jar"
echo "Published $native/native-runtime.jar"
