#!/usr/bin/env bash
# Rebuild the JNI shim (bridge/native/*.c) and install it everywhere Robolectric may load it from: native-runtime.jar (both arch folders) and the
# framework jars' own native/ folders (it loads from there first). The quick path after editing runtime_shim.c, media_runtime.c, android_platform.c...
# Usage on the VM, from the repo root: scripts/install-shim.sh ~/arm64-native
set -euo pipefail
cd "$(dirname "$0")/.."
native="$(realpath "$1")"
JH="$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")"
stage="$(mktemp -d)"
trap 'rm -rf "$stage"' EXIT
(cd "$stage" && unzip -q -o "$native/native-runtime.jar" -x "*.class")  # not the old overlay classes: they would overwrite the freshly compiled ones
# CursorWindow's @CriticalNative methods are not in the generated list (build-native-arm64.sh); without the adapter their arguments shift by JNIEnv/jclass
grep -q '^android.database.CursorWindow ' "$stage/critical-natives.txt" || cat >> "$stage/critical-natives.txt" <<'LIST'
android.database.CursorWindow nativeClear(J)V
android.database.CursorWindow nativeGetNumRows(J)I
android.database.CursorWindow nativeSetNumColumns(JI)Z
android.database.CursorWindow nativeFreeLastRow(J)V
android.database.CursorWindow nativeGetType(JII)I
android.database.CursorWindow nativePutLong(JJII)Z
android.database.CursorWindow nativePutDouble(JDII)Z
android.database.CursorWindow nativePutNull(JII)Z
LIST
cc -shared -fPIC -O2 -Wall -I"$JH/include" -I"$JH/include/linux" -o "$stage/native/linux/aarch64/libandroid_runtime.so" \
    bridge/native/runtime_shim.c bridge/native/media_runtime.c bridge/native/binder_runtime.c bridge/native/app_native.c bridge/native/native_loader.c bridge/native/android_platform.c bridge/native/properties.c bridge/native/paths.c bridge/native/android_stubs.c \
    -L"$HOME/bt-arm/bionic_translation/b" -ldl_bio -Wl,-rpath,"$HOME/bt-arm/bionic_translation/b"
cp "$stage/native/linux/aarch64/libandroid_runtime.so" "$stage/native/linux/x86_64/libandroid_runtime.so"
scripts/build-runtime-overlay.sh "$native" "$stage"
(cd "$stage" && for j in "$native"/aosp/android-all/*.jar "$native"/android-all/*.jar; do zip -q "$j" native/linux/x86_64/libandroid_runtime.so native/linux/aarch64/libandroid_runtime.so; done)
echo "shim installed in the runtime jar and the framework jars"

# apklayer patch: discard the obsolete whole-library SurfaceTexture stubs.
[ -f "$HOME/android-arm64/lib64/libnativedisplay.so" ]
sed -i '/^libnativedisplay.so[[:space:]]/d' "$native/share/bionic_translation/cfg.d/bionic_translation.cfg"
