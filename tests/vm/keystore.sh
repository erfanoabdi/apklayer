#!/bin/bash
# Android Keystore: AES-GCM, HMAC, RSA and EC keys through KeyGenParameterSpec, then a second launch that uses what the first one stored.
# On the VM, in the group's instance (tests/run keystore): APKLAYER_INSTANCE, APKLY_STATE (its logs) and the test apps installed by the runner.
: "${APKLY_STATE:=${XDG_STATE_HOME:-$HOME/.local/state}/apklayer/${APKLAYER_INSTANCE:-default}}"
out=$APKLY_STATE/ks
bin/apklayer stop >/dev/null 2>&1
for phase in first second; do
    APKLAYER_JOPTS="-Dhello.ks=$phase -Dapklayer.runms=8000" bin/apklayer run hello.keystore > $out-$phase.out 2>&1 || true
    grep -a "\[ks\]" $out-$phase.out
    grep -aq "\[ks\] PASS $phase" $out-$phase.out || { echo "FAIL $phase"; grep -a "Exception\|Error" $out-$phase.out | head -5; bin/apklayer stop >/dev/null 2>&1; exit 1; }
done
bin/apklayer stop >/dev/null 2>&1
echo "PASS keystore"
