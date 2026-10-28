#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/../.."
. tests/vm/config.sh
tests/apps/build.sh hello-native-codec
sshpass -p "$PASS" scp build/hello-native-codec.apk "$VM:/tmp/hello-native-codec.apk"
COPYFILE_DISABLE=1 tar cf - tests/vm/native-media.py tests/vm/uinput-keys.c bridge/native/apklayer.prop bridge/native/android-root/vendor/etc/media_codecs.xml bridge/native/android-root/system/lib64/extractors |
    sshpass -p "$PASS" ssh "$VM" 'set -e; tar xf - -C ~/apklayer; mkdir -p ~/arm64-native/android-root/vendor/etc ~/arm64-native/android-root/system/lib64; cp -a ~/apklayer/bridge/native/android-root/system/lib64/extractors ~/arm64-native/android-root/system/lib64/; cp ~/apklayer/bridge/native/android-root/vendor/etc/media_codecs.xml ~/arm64-native/android-root/vendor/etc/'
sshpass -p "$PASS" ssh "$VM" 'set -e; cd ~/apklayer; export XDG_RUNTIME_DIR=/run/user/1000 APKLAYER_NATIVE="$HOME/arm64-native"; trap '\''bin/apklayer stop >/dev/null 2>&1; pkill -9 -x tail || true'\'' EXIT; bin/apklayer install /tmp/hello-native-codec.apk > /tmp/apklayer-codec-install.log 2>&1; grep -a installed /tmp/apklayer-codec-install.log'
python3 tests/vm/native-media-driver.py "$VM" "$PASS"
