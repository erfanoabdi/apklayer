#!/bin/sh
# native-bridge: an arm64-only app (hello-arm64) on an x86_64 host runs its JNI library through the native bridge (Digitalis):
# the install records the route, the library answers from aarch64 code (arithmetic, a call into Java, a native thread calling Java).
set -eu
out=$(mktemp)
trap 'rm -f "$out"; bin/apklayer stop >/dev/null 2>&1 || true' EXIT
grep -qx 'nativeRoute=bridge-arm64' "$(bin/apklayer info hello.arm64 2>/dev/null | sed -n 's/^dir: //p')/info.properties" 2>/dev/null \
  || grep -rqx 'nativeRoute=bridge-arm64' "${XDG_DATA_HOME:-$HOME/.local/share}"/apklayer/*apps*/hello.arm64/info.properties
APKLAYER_JOPTS=-Dapklayer.runms=8000 bin/apklayer run hello.arm64 > "$out" 2>&1 || true
grep '\[hello-arm64\]' "$out"
grep -q '\[hello-arm64\] PASS' "$out"
