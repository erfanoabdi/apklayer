#!/usr/bin/env bash
# Builds hello-gl2.apk (x86_64 native lib) with the local Android SDK + NDK -> $1 (default build/hello-gl2.apk).
set -euo pipefail
cd "$(dirname "$0")"
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
NDK=$(ls -d "$SDK"/ndk/* | tail -1); BT=$(ls -d "$SDK"/build-tools/* | tail -1)
OUT="${1:-$PWD/../../../build/hello-gl2.apk}"; W=$(mktemp -d)
CLANG=$(ls "$NDK"/toolchains/llvm/prebuilt/*/bin/x86_64-linux-android26-clang)
mkdir -p "$W/lib/x86_64" "$W/cls" "$(dirname "$OUT")"
"$CLANG" -shared -fPIC -O2 -o "$W/lib/x86_64/libhello.so" hello.c -lEGL -lGLESv2 -landroid -llog
javac -nowarn -d "$W/cls" -cp "$SDK/platforms/android-36.1/android.jar" src/hello/gl2/Main.java
"$BT"/d8 --min-api 26 --lib "$SDK/platforms/android-36.1/android.jar" --output "$W" "$W"/cls/hello/gl2/*.class
"$BT"/aapt2 link -o "$W/base.apk" -I "$SDK/platforms/android-36.1/android.jar" --manifest AndroidManifest.xml
(cd "$W" && zip -q base.apk classes.dex lib/x86_64/libhello.so) && cp "$W/base.apk" "$OUT"
echo "$OUT"
