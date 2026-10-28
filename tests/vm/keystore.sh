#!/bin/bash
# Android Keystore: AES-GCM, HMAC, RSA and EC keys through KeyGenParameterSpec, then a second launch that uses what the first one stored.
set -euo pipefail
cd "$(dirname "$0")/../.."
. tests/vm/config.sh
tests/apps/build.sh hello-keystore > /dev/null
sshpass -p "$PASS" scp -q build/hello-keystore.apk "$VM:hello-keystore.apk"
sshpass -p "$PASS" ssh "$VM" 'cd ~/apklayer; export XDG_RUNTIME_DIR=/run/user/1000 WAYLAND_DISPLAY=wayland-0
b/cli/apklayer stop >/dev/null 2>&1
b/cli/apklayer install ~/hello-keystore.apk | grep -a installed
for phase in first second; do
    APKLAYER_JOPTS="-Dhello.ks=$phase -Dapklayer.runms=8000" b/cli/apklayer run hello.keystore > /tmp/ks-$phase.out 2>&1 || true
    grep -a "\[ks\]" /tmp/ks-$phase.out
    grep -aq "\[ks\] PASS $phase" /tmp/ks-$phase.out || { echo "FAIL $phase"; grep -a "Exception\|Error" /tmp/ks-$phase.out | head -5; b/cli/apklayer stop >/dev/null 2>&1; exit 1; }
done
b/cli/apklayer stop >/dev/null 2>&1
echo "PASS keystore"'
