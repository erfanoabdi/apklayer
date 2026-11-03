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
# the tree's Robolectric files patches/robolectric changes (external/robolectric at its REVISION, staged once from the tree:
# orb -m ubuntu22-x86 bash -c "cd ~/los24/external/robolectric && tar cf - $(sed -n 's|^+++ b/||p' patches/robolectric/*.patch | sort -u | tr '\n' ' ')" | ssh VM 'mkdir -p <native>/aosp/robolectric-src && tar xf - -C <native>/aosp/robolectric-src')
[ -d "$native/aosp/robolectric-src" ] || { echo "build-runtime-overlay: stage $native/aosp/robolectric-src first (see this script)" >&2; exit 1; }
cp -R "$native/aosp/robolectric-src" "$work/src"
patches/apply robolectric "$work/src" >/dev/null
mapfile -t sources < <(find runtime/patches "$work/src" -name '*.java' ! -name '._*' -print | sort)
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
