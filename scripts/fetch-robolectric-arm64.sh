#!/bin/sh
# fetch-robolectric-arm64: Robolectric 4.17's native runtime for Linux arm64, the FALLBACK flavor (PLAN P7; docs/THIRD_PARTY.md).
# A third-party build (github.com/LavishSonbhurra/robolectric-native-arm64) of AOSP android-16.0.0_r1, SDK 36. The download is
# checked against the pinned sha256 below; the file is stored in its own directory and only used when a native directory holds it
# as robolectric-nativeruntime-linux-aarch64.jar (bin/apklayer picks that flavor). Usage: fetch-robolectric-arm64.sh [stage-dir]
set -e
TAG=v4.17-16-robolectric-13921718-2
NAME=robolectric-nativeruntime-linux-aarch64-4.17-16-robolectric-13921718-2.jar
SHA256=bc04263c07078c3c12b2b023634446c4de23a9b23d4a760fe809c9ac97a6ed85
DIR="$HOME/.cache/apklayer/third-party/robolectric-native-arm64-$TAG"
mkdir -p "$DIR"
if [ ! -f "$DIR/$NAME" ]; then
    curl -fsSL -o "$DIR/$NAME.part" "https://github.com/LavishSonbhurra/robolectric-native-arm64/releases/download/$TAG/$NAME"
    mv "$DIR/$NAME.part" "$DIR/$NAME"
fi
got=$(sha256sum "$DIR/$NAME" 2>/dev/null | cut -d' ' -f1 || shasum -a 256 "$DIR/$NAME" | cut -d' ' -f1)
[ "$got" = "$SHA256" ] || { echo "sha256 mismatch for $NAME: $got" >&2; rm -f "$DIR/$NAME"; exit 1; }
if [ -n "${1:-}" ]; then mkdir -p "$1" && cp "$DIR/$NAME" "$1/robolectric-nativeruntime-linux-aarch64.jar"; fi
echo "$DIR/$NAME"
