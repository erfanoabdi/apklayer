#!/bin/sh
# stage-x86_64: everything an x86_64 machine needs from the user's OrbStack LineageOS tree (the x86_64 GSI build and apklayer's modules built for it), staged into one tar file:
#   android-x86_64/lib64   the Android libraries (the closure of deps/android-libs.list: system/lib64, the APEX and module directories, vendor/apklayer's modules)
#   android-root           the tree's files Android reads at fixed paths (scripts/collect-android-root.sh with the x86_64 product)
#   aosp/Robolectric_all.jar   the tree's Robolectric
#   bt-libs                bionic_translation (patched, scripts/build-bionic-translation.sh) built for x86_64 as a check and as a convenience
# and verified: every ELF is checked to be x86-64. Usage on the Mac: scripts/stage-x86_64.sh   (it re-runs itself inside the OrbStack machine "ubuntu22-x86").
# The tar file lands in build/x86_64/apklayer-x86_64-stage.tar (docs/X86_64.md says what to do with it on the PC).
set -e
here="$(cd "$(dirname "$0")" && pwd)"
if [ "$1" != --inside ]; then exec orb -m ubuntu22-x86 sh "/mnt/mac$here/stage-x86_64.sh" --inside; fi
repo="$(dirname "$here")"
stage="$HOME/apklayer-x86_64"
tree="$HOME/los24"
out="$tree/out/target/product/generic_x86_64"
mkdir -p "$stage"

echo "== libraries"
srcs="$out/system/lib64"
for d in "$out"/apex/*/lib64 "$out/system_ext/lib64" "$out/vendor/lib64"; do [ -d "$d" ] && srcs="$srcs:$d"; done
for d in $(ls -d "$tree"/out/soong/.intermediates/vendor/apklayer/*/*/android_x86_64_*shared "$tree"/out/soong/.intermediates/vendor/apklayer/*/*/*/android_x86_64_*shared 2>/dev/null); do srcs="$srcs:$d"; done
rm -rf "$stage/android-x86_64"; mkdir -p "$stage/android-x86_64/lib64"
ANDROID_LIBS_SRC="$srcs" sh "$here/collect-android-libs.sh" "$stage/android-x86_64/lib64" $(grep -v 'vktest\|vulkan.apklayer\|libapklayer_vulkan' "$repo/deps/android-libs.list") > "$stage/libs-copied.txt" 2> "$stage/libs-missing.txt"
echo "$(wc -l < "$stage/libs-copied.txt") libraries copied; missing (see $stage/libs-missing.txt): $(sed 's/.*: //' "$stage/libs-missing.txt" | sort -u | wc -l)"

echo "== Android root"
rm -rf "$stage/android-root"
APKLAYER_ORB=env ANDROID_PRODUCT=generic_x86_64 STAGE_DEST="$stage/android-root" sh "$here/collect-android-root.sh" > /dev/null

echo "== Robolectric"
mkdir -p "$stage/aosp" && cp "$tree/out/host/linux-x86/framework/Robolectric_all.jar" "$stage/aosp/"

echo "== bionic_translation"
if [ ! -d "$stage/bt" ]; then cp -r "$HOME/bt/bionic_translation" "$stage/bt" && (cd "$stage/bt" && rm -rf b b2 && git checkout meson.build); fi
sh "$here/build-bionic-translation.sh" "$stage/bt" > "$stage/bt-build.log" 2>&1 && echo "built, log $stage/bt-build.log"

echo "== check: every ELF is x86-64"
bad=0
for f in "$stage"/android-x86_64/lib64/*.so* "$stage"/bt/b/lib*_bio.so*; do
    [ -f "$f" ] || continue
    case "$(readelf -h "$f" 2>/dev/null | sed -n 's/.*Machine: *//p')" in *X86-64*) ;; *) echo "not x86-64: $f"; bad=$((bad + 1));; esac
done
echo "$bad foreign ELF files"

echo "== tar"
rm -rf "$stage/bt-libs"; mkdir -p "$stage/bt-libs" && cp -L "$stage"/bt/b/lib*_bio.so* "$stage/bt-libs/"
mkdir -p "$repo/build/x86_64"
tar cf "$repo/build/x86_64/apklayer-x86_64-stage.tar" -C "$stage" android-x86_64 android-root aosp bt-libs libs-copied.txt libs-missing.txt
ls -la "$repo/build/x86_64/apklayer-x86_64-stage.tar"
