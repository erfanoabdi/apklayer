#!/bin/sh
# native-bridge: an arm64-only app (hello-arm64) on an x86_64 host runs its JNI library through the native bridge (Digitalis):
# the install recorded the route, the library answers from aarch64 code (arithmetic, a call into Java, a native thread calling Java).
set -eu
out=$(mktemp)
trap 'rm -f "$out"; bin/apklayer stop >/dev/null 2>&1 || true' EXIT
route=bridge-arm64; [ "$(uname -m)" != aarch64 ] || route=native  # on an arm64 host the library is native
bin/apklayer info hello.arm64 | grep -qx "nativeRoute	$route"
APKLAYER_JOPTS=-Dapklayer.runms=8000 bin/apklayer run hello.arm64 > "$out" 2>&1 || true
grep -E '\[hello-arm64\]|native bridge' "$out"
grep -q '\[hello-arm64\] PASS' "$out"
