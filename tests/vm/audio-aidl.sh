#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/../.."
VM="${APKLY_VM:-utm@192.168.64.7}"
PASS="${APKLY_VM_PASS:-1234}"
COPYFILE_DISABLE=1 tar cf - aosp/hals/audio/manifest.xml aosp/hals/audio/audio_policy_configuration.xml scripts/arm64-stack.sh scripts/native-env.sh tests/vm/audio-host.py |
    sshpass -p "$PASS" ssh "$VM" 'tar xf - -C ~/apklayer-m1'
sshpass -p "$PASS" ssh "$VM" bash -s <<'REMOTE'
set -euo pipefail
cd ~/apklayer-m1
export XDG_RUNTIME_DIR=/run/user/1000 APKLAYER_NATIVE="$HOME/arm64-native"
export APKLAYER_HOST_LIBS="$APKLAYER_NATIVE/libapklayer-hal-graphics.so:$APKLAYER_NATIVE/libapklayer-hal-audio.so"
cp b/bridge/hals/audio/libapklayer-hal-audio.so "$APKLAYER_NATIVE/"
trap 'bin/apklayer stop >/dev/null 2>&1; pkill -9 -x tail || true' EXIT
python3 -u tests/vm/audio-host.py aidl
if ps -eo args | grep -E '[a]pklayer.host.Main|[b]tload|[a]pklayer-shell' | grep -v grep; then exit 1; fi
REMOTE
