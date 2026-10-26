#!/usr/bin/env bash
# P4 native-library test on the VM: an APK with native code (CPU-Z, from ~/TestApps) loads its
# real x86_64 .so through bionic_translation and starts. Needs build/native (scripts/build-native.sh).
# Usage: tests/vm/native.sh
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
APK="${1:-$HOME/TestApps/cpu_z.apk}"
[ -f "$APK" ] || { echo "SKIP  $APK not found"; exit 0; }
[ -d "$ROOT/build/native/x86_64" ] || { echo "FAIL  build/native/x86_64 missing: run scripts/build-native.sh"; exit 1; }
VM="${APKLY_VM:-parallels@10.211.55.4}"; PASS="${APKLY_VM_PASS:-1234}"
LOG="$(mktemp)"
pkill -f "scripts/vm-run" 2>/dev/null
sshpass -p "$PASS" ssh -o StrictHostKeyChecking=no "$VM" 'pkill -f apklayer.host; pkill -x apklayer-shell; true'
"$ROOT/scripts/vm-run.sh" "$APK" > "$LOG" 2>&1 &
timeout 240 bash -c "until grep -qE 'started .*depth=1|UnsatisfiedLink|Aborted' '$LOG'; do sleep 2; done"
fail=0
check() { if eval "$2"; then echo "PASS  $1"; else echo "FAIL  $1"; fail=1; fi; }
check "the app's native library loads through the bionic linker" 'grep -q "\[apklayer\] loaded lib.*\.so" "$LOG"'
check "the app starts its launcher activity after loading it" 'grep -q "started .*depth=1" "$LOG"'
check "no UnsatisfiedLinkError / JVM crash" '! grep -qE "UnsatisfiedLinkError|Aborted" "$LOG"'
if grep -q "Loaded libEGL_shim" "$LOG"; then # an app that draws with native GL (tests/native/hello-gl2): frames must reach the host
    check "native GL renders frames for the host (eglSwapBuffers -> \$APKLAYER_GLDIR)" 'sshpass -p "$PASS" ssh -o StrictHostKeyChecking=no "$VM" "ls /run/user/1000/apklayer/gl-*/*.rgba" >/dev/null 2>&1'
fi
sshpass -p "$PASS" ssh -o StrictHostKeyChecking=no "$VM" 'pkill -f apklayer.host; true'
[ $fail -ne 0 ] && { echo '--- log tail:'; grep -vE '^[0-9]+: \[' "$LOG" | tail -15 | cut -c1-200; }
exit $fail
