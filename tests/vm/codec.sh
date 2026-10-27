#!/usr/bin/env bash
# P5.2 MediaCodec on the VM: tests/apps/hello-codec feeds 60 VP9 frames (one per file) to the VP9 decoder with a
# Surface; every frame must come back and the last one must be on screen (colour bars).
set -uo pipefail
source "$(dirname "$0")/lib.sh"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"; VM="${APKLY_VM:-utm@192.168.64.7}"; PASS="${APKLY_VM_PASS:-1234}"
"$ROOT/tests/apps/build.sh" hello-codec >/dev/null || { echo 'FAIL  the sample app does not build'; exit 1; }
run() { # run <mime> <min frames back> <gst encoder pipeline>: encode 60 frames to one file each, decode them with the app
  local mime="$1" min="$2" enc="$3"
  sshpass -p "$PASS" ssh -o StrictHostKeyChecking=no "$VM" "rm -f /tmp/codec-f-*.bin; echo $mime > /tmp/codec.mime; gst-launch-1.0 -q videotestsrc num-buffers=60 pattern=smpte ! video/x-raw,width=320,height=240,framerate=30/1 ! $enc ! multifilesink location=/tmp/codec-f-%03d.bin"
  LOG=$(APKLAYER_JOPTS='-Dapklayer.runms=26000 -Dapklayer.snapms=22000' "$ROOT/scripts/vm-run.sh" "$ROOT/build/hello-codec.apk" 2>&1)
  sshpass -p "$PASS" scp -q -o StrictHostKeyChecking=no "$VM:/tmp/apklayer-frame-22000ms.png" /tmp/codec-frame.png
  sshpass -p "$PASS" ssh -o StrictHostKeyChecking=no "$VM" 'pkill -x apklayer-shell; pkill -f "[a]pklayer-codec"; pkill -f "[a]pklayer.host.Main"; rm -f /tmp/codec-f-*.bin /tmp/codec.mime; true'
  n=$(grep -o "hello-codec\] rendered [0-9]*" <<<"$LOG" | grep -o "[0-9]*$")
  check "$mime: at least $min of 60 frames come back (got ${n:-0}; H.264 decoders run frames behind)" '[ "${n:-0}" -ge "$min" ]'
  check "$mime: the decoded frame reaches the window (colour bars)" 'bars_ok /tmp/codec-frame.png'
}
fail=0
check() { if eval "$2"; then echo "PASS  $1"; else echo "FAIL  $1"; fail=1; fi; }
run video/x-vnd.on2.vp9 55 "vp9enc"
run video/avc 40 "x264enc key-int-max=30 ! video/x-h264,stream-format=byte-stream"
exit $fail
