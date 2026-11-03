#!/bin/bash
# Android Keystore: AES-GCM, HMAC, RSA and EC keys through KeyGenParameterSpec, then another app (hello.keystoreother) that must not see them or
# replace them with its own key of the same alias (keystore2 namespaces by app uid: libbinder patch 0005), then a second launch that uses what the first stored.
set -euo pipefail
cd "$(dirname "$0")/../.."
. tests/vm/config.sh
tests/apps/build.sh hello-keystore > /dev/null
tests/apps/build.sh hello-keystore-other > /dev/null
sshpass -p "$PASS" scp -q build/hello-keystore.apk build/hello-keystore-other.apk "$VM:"
sshpass -p "$PASS" ssh "$VM" 'cd ~/apklayer; export XDG_RUNTIME_DIR=/run/user/1000 WAYLAND_DISPLAY=wayland-0
b/cli/apklayer stop >/dev/null 2>&1
for a in hello-keystore hello-keystore-other; do b/cli/apklayer install ~/$a.apk | grep -a installed; done
for phase in first other second; do
    pkg=hello.keystore; [ $phase = other ] && pkg=hello.keystoreother
    APKLAYER_JOPTS="-Dhello.ks=$phase -Dapklayer.runms=8000" b/cli/apklayer run $pkg > /tmp/ks-$phase.out 2>&1 || true
    grep -a "\[ks\]" /tmp/ks-$phase.out
    grep -aq "\[ks\] PASS $phase" /tmp/ks-$phase.out || { echo "FAIL $phase"; grep -a "Exception\|Error" /tmp/ks-$phase.out | head -5; b/cli/apklayer stop >/dev/null 2>&1; exit 1; }
done
b/cli/apklayer stop >/dev/null 2>&1
echo "PASS keystore"'
