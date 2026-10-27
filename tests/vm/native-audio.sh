#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/../.."
VM="${APKLY_VM:-utm@192.168.64.7}"
PASS="${APKLY_VM_PASS:-1234}"
tests/apps/build.sh hello-native-audio
sshpass -p "$PASS" scp build/hello-native-audio.apk "$VM:/tmp/hello-native-audio.apk"
COPYFILE_DISABLE=1 tar cf - tests/vm/native-audio.py tests/vm/audio_capture.py tests/vm/uinput-keys.c aosp/hals/audio/manifest.xml aosp/hals/audio/effects-manifest.xml aosp/hals/audio/audio_policy_configuration.xml aosp/hals/audio/audio_effects_config.xml scripts/arm64-stack.sh bridge/native/apklayer.prop |
    sshpass -p "$PASS" ssh "$VM" 'tar xf - -C ~/apklayer-m1'
sshpass -p "$PASS" ssh "$VM" 'set -e; cd ~/apklayer-m1; export XDG_RUNTIME_DIR=/run/user/1000 APKLAYER_NATIVE="$HOME/arm64-native"; trap '\''bin/apklayer stop >/dev/null 2>&1; pkill -9 -x tail || true'\'' EXIT; bin/apklayer install /tmp/hello-native-audio.apk > /tmp/apklayer-audio-install.log 2>&1; grep -a installed /tmp/apklayer-audio-install.log'
python3 tests/vm/native-audio-driver.py "$VM" "$PASS" "${1:-}"
