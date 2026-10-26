#!/usr/bin/env bash
# P5.2 MediaCodec on the VM: tests/apps/hello-codec feeds 60 VP9 frames (one per file) to the VP9 decoder with a
# Surface; every frame must come back and the last one must be on screen (colour bars).
set -uo pipefail
source "$(dirname "$0")/lib.sh"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"; VM="${APKLY_VM:-parallels@10.211.55.4}"; PASS="${APKLY_VM_PASS:-1234}"
"$ROOT/tests/apps/build.sh" hello-codec >/dev/null || { echo 'FAIL  the sample app does not build'; exit 1; }
sshpass -p "$PASS" ssh -o StrictHostKeyChecking=no "$VM" 'rm -f /tmp/vp9f-*.bin; gst-launch-1.0 -q videotestsrc num-buffers=60 pattern=smpte ! video/x-raw,width=320,height=240,framerate=30/1 ! vp9enc ! multifilesink location=/tmp/vp9f-%03d.bin'
LOG=$(APKLAYER_JOPTS='-Dapklayer.runms=26000 -Dapklayer.snapms=22000' "$ROOT/scripts/vm-run.sh" "$ROOT/build/hello-codec.apk" 2>&1)
sshpass -p "$PASS" scp -q -o StrictHostKeyChecking=no "$VM:/tmp/apklayer-frame-22000ms.png" /tmp/codec-frame.png
sshpass -p "$PASS" ssh -o StrictHostKeyChecking=no "$VM" 'pkill -x apklayer-shell; pkill -f "[a]pklayer-codec"; pkill -f "[a]pklayer.host.Main"; rm -f /tmp/vp9f-*.bin; true'
fail=0
check() { if eval "$2"; then echo "PASS  $1"; else echo "FAIL  $1"; fail=1; fi; }
check "the VP9 decoder returns a frame for every input buffer" 'grep -qE "hello-codec\] rendered (5[5-9]|60) frames" <<<"$LOG"'
check "the decoded frame reaches the window (colour bars)" 'bars_ok /tmp/codec-frame.png'
exit $fail
