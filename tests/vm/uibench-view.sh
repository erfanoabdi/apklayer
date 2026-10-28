#!/bin/bash
# Opens one UiBench activity directly (the intent file of HostApps), waits for it to draw, captures the screen and reports exceptions/crashes from the log, then closes everything.
# Usage: tests/vm/uibench-view.sh ScrollableWebViewActivity|GlTextureViewActivity [seconds]
set -euo pipefail
cd "$(dirname "$0")/../.."
ACT="${1:?activity class name}"; SECS="${2:-12}"
VM="${APKLY_VM:-utm@192.168.64.7}"; PASS="${APKLY_VM_PASS:-1234}"
sshpass -p "$PASS" ssh "$VM" "pkill -f '[a]pklayer.host.Main'; pkill -x apklayer-shell; cd ~/apklayer-m1 && bin/apklayer stop >/dev/null 2>&1
printf 'component=com.android.test.uibench.$ACT\n' > /tmp/uibench-intent.properties
export XDG_RUNTIME_DIR=/run/user/1000 WAYLAND_DISPLAY=wayland-0 APKLAYER_NATIVE=\$HOME/arm64-native APKLAYER_LOGCAT=1 APKLAYER_JOPTS='-Dapklayer.realbinder=true -Dapklayer.instancehub=true' APKLAYER_INTENT_FILE=/tmp/uibench-intent.properties
setsid nohup bin/apklayer run com.android.test.uibench > /tmp/uibench-view.log 2>&1 < /dev/null &
echo launched"
python3 - "$SECS" <<'PY'
import subprocess, sys
sys.path.insert(0, 'tests/vm')
from vmcapture import capture
import time
time.sleep(int(sys.argv[1]))
capture('/tmp/apklayer-uibench.png')
PY
sshpass -p "$PASS" ssh "$VM" "grep -a 'FATAL\|Exception\|SIGSEGV\|Problematic\|started com.android' /tmp/uibench-view.log | grep -v '^\s*at ' | head -8 | cut -c1-200; pkill -f '[a]pklayer.host.Main'; pkill -x apklayer-shell; cd ~/apklayer-m1 && bin/apklayer stop >/dev/null 2>&1; ps -eo pid,args | grep -cE '[a]pklayer.host.Main' || true"
echo "screenshot: /tmp/apklayer-uibench.png"
