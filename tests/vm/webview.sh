#!/usr/bin/env bash
# P5.1 WebView on the VM: tests/apps/hello-webview loads inline HTML in the engine process, gets onPageFinished,
# evaluateJavascript("6 * 7") answers 42, and the engine's green page is composed into the window frame.
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"; VM="${APKLY_VM:-parallels@10.211.55.4}"; PASS="${APKLY_VM_PASS:-1234}"
"$ROOT/tests/apps/build.sh" hello-webview >/dev/null
LOG=$(APKLAYER_JOPTS='-Dapklayer.runms=24000 -Dapklayer.snapms=20000' "$ROOT/scripts/vm-run.sh" "$ROOT/build/hello-webview.apk" 2>&1)
sshpass -p "$PASS" scp -q -o StrictHostKeyChecking=no "$VM:/tmp/apklayer-frame-20000ms.png" /tmp/webview-frame.png
sshpass -p "$PASS" ssh -o StrictHostKeyChecking=no "$VM" 'pkill -x apklayer-shell; pkill -f "[a]pklayer-webview"; pkill -f "[a]pklayer.host.Main"; true'
fail=0
check() { if eval "$2"; then echo "PASS  $1"; else echo "FAIL  $1"; fail=1; fi; }
check "WebViewClient.onPageFinished is called" 'grep -q "hello-webview\] page finished" <<<"$LOG"'
check "evaluateJavascript returns the page engine's answer" 'grep -q "6\*7 = 42" <<<"$LOG"'
check "the page is composed into the window (green body pixel)" 'python3 - <<E2
import struct, zlib, sys
d=open("/tmp/webview-frame.png","rb").read(); i=8; idat=b""
while i<len(d):
    n,t=struct.unpack(">I4s",d[i:i+8]); b=d[i+8:i+8+n]
    if t==b"IHDR": w,h=struct.unpack(">II",b[:8])
    if t==b"IDAT": idat+=b
    i+=12+n
raw=zlib.decompress(idat); y=400; x=240; px=raw[y*(w*4+1)+1+x*4:][:3]
sys.exit(0 if px[1]>px[0]+50 and px[1]>px[2]+20 else 1)
E2'
exit $fail
