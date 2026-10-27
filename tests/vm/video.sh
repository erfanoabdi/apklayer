#!/usr/bin/env bash
# P5.2 video on the VM: tests/apps/hello-video plays a generated colour-bars clip into a SurfaceView; the
# window snapshot must show the bars (very different colours at the left and right of the video area).
set -uo pipefail
source "$(dirname "$0")/lib.sh"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"; VM="${APKLY_VM:-utm@192.168.64.7}"; PASS="${APKLY_VM_PASS:-1234}"
"$ROOT/tests/apps/build.sh" hello-video >/dev/null || { echo 'FAIL  the sample app does not build'; exit 1; }
sshpass -p "$PASS" ssh -o StrictHostKeyChecking=no "$VM" 'gst-launch-1.0 -q videotestsrc num-buffers=300 pattern=smpte ! video/x-raw,width=320,height=240,framerate=30/1 ! vp8enc ! webmmux ! filesink location=/tmp/apklayer-test.webm'
LOG=$(APKLAYER_JOPTS='-Dapklayer.runms=26000 -Dapklayer.snapms=22000' "$ROOT/scripts/vm-run.sh" "$ROOT/build/hello-video.apk" 2>&1)
sshpass -p "$PASS" scp -q -o StrictHostKeyChecking=no "$VM:/tmp/apklayer-frame-22000ms.png" /tmp/video-frame.png
sshpass -p "$PASS" ssh -o StrictHostKeyChecking=no "$VM" 'pkill -x apklayer-shell; pkill -f "[a]pklayer-media"; pkill -f "[a]pklayer.host.Main"; rm -f /tmp/apklayer-test.webm; true'
fail=0
check() { if eval "$2"; then echo "PASS  $1"; else echo "FAIL  $1"; fail=1; fi; }
check "MediaPlayer prepares the clip (duration from the file)" 'grep -qE "hello-video\] prepared, duration=[1-9]" <<<"$LOG"'
check "video frames reach the window (colour bars, not blank)" 'bars_ok /tmp/video-frame.png'
exit $fail
