#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/../.."
VM="${APKLY_VM:-parallels@10.211.55.4}"
PASS="${APKLY_VM_PASS:-1234}"
tests/apps/build.sh hello-native-surface
sshpass -p "$PASS" scp build/hello-native-surface.apk "$VM:/tmp/hello-native-surface.apk"
COPYFILE_DISABLE=1 tar cf - tests/vm/native-surface.py tests/vm/uinput-keys.c scripts/arm64-stack.sh bridge/native/apklayer.prop bridge/native/android-root/vendor/etc/media_codecs.xml bridge/native/android-root/system/lib64/extractors |
    sshpass -p "$PASS" ssh "$VM" 'set -e; tar xf - -C ~/apklayer-m1; mkdir -p ~/arm64-native/android-root/vendor/etc ~/arm64-native/android-root/system/lib64; cp -a ~/apklayer-m1/bridge/native/android-root/system/lib64/extractors ~/arm64-native/android-root/system/lib64/; cp ~/apklayer-m1/bridge/native/android-root/vendor/etc/media_codecs.xml ~/arm64-native/android-root/vendor/etc/'
sshpass -p "$PASS" ssh "$VM" 'set -e; cd ~/apklayer-m1; export XDG_RUNTIME_DIR=/run/user/1000 APKLAYER_NATIVE="$HOME/arm64-native"; trap '\''bin/apklayer stop >/dev/null 2>&1; pkill -9 -x tail || true'\'' EXIT; bin/apklayer install /tmp/hello-native-surface.apk > /tmp/apklayer-codec-install.log 2>&1; grep -a installed /tmp/apklayer-codec-install.log'
python3 tests/vm/native-surface-driver.py "$VM" "$PASS"
