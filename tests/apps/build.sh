#!/usr/bin/env bash
# Builds a sample APK from tests/apps/<name>/ (AndroidManifest.xml, src/**/*.java, optional *.c -> libapp.so for x86_64)
# with the local Android SDK + NDK. Usage: build.sh <name> [out.apk]  (default build/<name>.apk)
set -euo pipefail
NAME="$1"; cd "$(dirname "$0")/$NAME"
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
NDK=$(ls -d "$SDK"/ndk/* | tail -1); BT=$(ls -d "$SDK"/build-tools/* | tail -1); AJ="$SDK/platforms/android-36.1/android.jar"
OUT="${2:-$PWD/../../../build/$NAME.apk}"; W=$(mktemp -d)
trap 'rm -rf "$W"' EXIT
mkdir -p "$W/cls" "$(dirname "$OUT")"
LIBS=""
if ls *.c >/dev/null 2>&1; then
  mkdir -p "$W/lib/x86_64"
  "$(ls "$NDK"/toolchains/llvm/prebuilt/*/bin/x86_64-linux-android26-clang)" -shared -fPIC -O2 -o "$W/lib/x86_64/libhello.so" *.c -lEGL -lGLESv2 -landroid -llog
  LIBS="lib/x86_64/libhello.so"
fi
javac -nowarn -d "$W/cls" -cp "$AJ" $(find src -name '*.java')
"$BT"/d8 --min-api 26 --lib "$AJ" --output "$W" $(find "$W/cls" -name '*.class')
ASSETS=()
[ ! -d assets ] || ASSETS=(-A "$PWD/assets")
"$BT"/aapt2 link ${ASSETS[@]+"${ASSETS[@]}"} -o "$W/base.apk" -I "$AJ" --manifest AndroidManifest.xml
(cd "$W" && zip -q base.apk classes.dex $LIBS) && cp "$W/base.apk" "$OUT"
echo "$OUT"
