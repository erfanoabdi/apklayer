#!/bin/sh
# arm32: an armeabi-v7a-only app (hello-arm32) runs in the 32-bit app process: the install recorded the route, the JVM is the armhf one
# (os.arch arm) and the library answers from 32-bit ARM code (arithmetic, a call into Java, a native thread calling Java, its own fault handler).
set -eu
out=$(mktemp)
trap 'rm -f "$out"; bin/apklayer stop >/dev/null 2>&1 || true' EXIT
bin/apklayer info hello.arm32 | grep -qx "nativeRoute	arm32"
APKLAYER_JOPTS=-Dapklayer.runms=8000 bin/apklayer run hello.arm32 > "$out" 2>&1 || true
grep -E '\[hello-arm32\]' "$out"
grep -q '\[hello-arm32\] PASS' "$out"
