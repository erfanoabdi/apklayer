#!/bin/sh
# native-bridge: an arm64-only app (hello-arm64) on an x86_64 host runs its JNI library through the native bridge (Digitalis):
# the install recorded the route, the library answers from aarch64 code (arithmetic, a call into Java, a native thread calling Java).
# Again with the runtime reached through a symlink (the deb's runtime/current): the guest linker still finds /system/bin/arm64/app_process64's
# ld.config section (its realpath is the kernel's spelling of the Android root).
set -eu
out=$(mktemp)
link=$(mktemp -d)
trap 'rm -rf "$out" "$link"; bin/apklayer stop >/dev/null 2>&1 || true' EXIT
route=bridge-arm64; [ "$(uname -m)" != aarch64 ] || route=native  # on an arm64 host the library is native
bin/apklayer info hello.arm64 | grep -qx "nativeRoute	$route"
APKLAYER_JOPTS=-Dapklayer.runms=8000 bin/apklayer run hello.arm64 > "$out" 2>&1 || true
grep -E '\[hello-arm64\]|native bridge|CANNOT LINK' "$out"
grep -q '\[hello-arm64\] PASS' "$out"
! grep -q 'abi consistent MIXED' "$out"
[ -n "${APKLAYER_NATIVE:-}" ] || exit 0
ln -s "$(readlink -f "$APKLAYER_NATIVE")" "$link/current"
bin/apklayer stop hello.arm64 >/dev/null 2>&1 || true
APKLAYER_NATIVE="$link/current" APKLAYER_JOPTS=-Dapklayer.runms=8000 bin/apklayer run hello.arm64 > "$out" 2>&1 || true
grep -E '\[hello-arm64\] PASS|CANNOT LINK' "$out" | sed 's/^/symlinked runtime: /'
grep -q '\[hello-arm64\] PASS' "$out" && ! grep -q 'CANNOT LINK' "$out"
