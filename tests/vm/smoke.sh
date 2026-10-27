#!/usr/bin/env bash
# Smoke test of one APK on the VM: runs it for a while, takes a window snapshot, prints the lines that matter
# (activities started, uncaught exceptions) and closes everything. Usage: smoke.sh <app.apk> [run seconds, default 25]
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"; VM="${APKLY_VM:-utm@192.168.64.7}"; PASS="${APKLY_VM_PASS:-1234}"
APK="$1"; SECS="${2:-25}"; NAME="$(basename "$APK" .apk)"
LOG=$(APKLAYER_JOPTS="-Dapklayer.runms=$((SECS * 1000)) -Dapklayer.snapms=$(((SECS - 3) * 1000))" "$ROOT/scripts/vm-run.sh" "$APK" 2>&1)
sshpass -p "$PASS" scp -q -o StrictHostKeyChecking=no "$VM:/tmp/apklayer-frame-$(((SECS - 3) * 1000))ms.png" "/tmp/smoke-$NAME.png" 2>/dev/null
sshpass -p "$PASS" ssh -o StrictHostKeyChecking=no "$VM" 'pkill -x apklayer-shell; pkill -f "[a]pklayer-webview"; pkill -f "[a]pklayer-media"; pkill -f "[a]pklayer.host.Main"; true'
grep -aE "\[apklayer\] (started|cannot|exit)|^[a-zA-Z.]*(Exception|Error)|^\s+at .*(com|org)\.[a-z]+" <<<"$LOG" | grep -av "Unsafe\]" | head -${LINES_MAX:-14} | cut -c1-200
echo "snapshot: /tmp/smoke-$NAME.png"
