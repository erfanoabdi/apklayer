#!/usr/bin/env bash
# P5.2 video on the VM: tests/apps/hello-video plays a generated colour-bars clip into a SurfaceView; the
# window snapshot must show the bars (very different colours at the left and right of the video area).
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"; VM="${APKLY_VM:-parallels@10.211.55.4}"; PASS="${APKLY_VM_PASS:-1234}"
"$ROOT/tests/apps/build.sh" hello-video >/dev/null || { echo 'FAIL  the sample app does not build'; exit 1; }
sshpass -p "$PASS" ssh -o StrictHostKeyChecking=no "$VM" 'gst-launch-1.0 -q videotestsrc num-buffers=300 pattern=smpte ! video/x-raw,width=320,height=240,framerate=30/1 ! vp8enc ! webmmux ! filesink location=/tmp/apklayer-test.webm'
LOG=$(APKLAYER_JOPTS='-Dapklayer.runms=26000 -Dapklayer.snapms=22000' "$ROOT/scripts/vm-run.sh" "$ROOT/build/hello-video.apk" 2>&1)
sshpass -p "$PASS" scp -q -o StrictHostKeyChecking=no "$VM:/tmp/apklayer-frame-22000ms.png" /tmp/video-frame.png
sshpass -p "$PASS" ssh -o StrictHostKeyChecking=no "$VM" 'pkill -x apklayer-shell; pkill -f "[a]pklayer-media"; pkill -f "[a]pklayer.host.Main"; rm -f /tmp/apklayer-test.webm; true'
fail=0
check() { if eval "$2"; then echo "PASS  $1"; else echo "FAIL  $1"; fail=1; fi; }
check "MediaPlayer prepares the clip (duration from the file)" 'grep -qE "hello-video\] prepared, duration=[1-9]" <<<"$LOG"'
check "video frames reach the window (colour bars, not blank)" 'python3 - <<E2
import struct, zlib, sys
d=open("/tmp/video-frame.png","rb").read(); i=8; idat=b""
while i<len(d):
    n,t=struct.unpack(">I4s",d[i:i+8]); b=d[i+8:i+8+n]
    if t==b"IHDR": w,h=struct.unpack(">II",b[:8])
    if t==b"IDAT": idat+=b
    i+=12+n
raw=zlib.decompress(idat); y=h//2
px=lambda x: raw[y*(w*4+1)+1+x*4:][:3]
a,b2=px(w//10),px(w-w//10)
sys.exit(0 if sum(abs(a[k]-b2[k]) for k in range(3))>150 else 1)
E2'
exit $fail
