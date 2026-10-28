#!/bin/bash
# The original WebView (Chromium provider APK) in an app: provider class and native library loaded through bionic_translation, a page loaded, JavaScript run.
set -euo pipefail
cd "$(dirname "$0")/../.."
. tests/vm/config.sh
tests/apps/build.sh hello-wvprobe > /dev/null
sshpass -p "$PASS" scp build/hello-wvprobe.apk "$VM:/tmp/"
COPYFILE_DISABLE=1 tar cf - tests/vm/webview.py bin/apklayer scripts | sshpass -p "$PASS" ssh "$VM" 'tar xf - -C ~/apklayer'
sshpass -p "$PASS" ssh "$VM" 'set -e; cd ~/apklayer; export XDG_RUNTIME_DIR=/run/user/1000 APKLAYER_NATIVE="$HOME/arm64-native"; bin/apklayer install /tmp/hello-wvprobe.apk 2>&1 | grep -a installed'
sshpass -p "$PASS" ssh "$VM" "cd ~/apklayer; WV_VERBOSE=\"${WV_VERBOSE:-}\" WV_JOPTS=\"${WV_JOPTS:-}\" WV_RTDEBUG=\"${WV_RTDEBUG:-}\" WV_DUMP=\"${WV_DUMP:-}\" WV_KEEP=\"${WV_KEEP:-}\" python3 -u tests/vm/webview.py ${1:-probe}" 2>&1 | python3 -u tests/vm/webview-capture.py
