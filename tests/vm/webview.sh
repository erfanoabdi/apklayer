#!/usr/bin/env bash
# P5.1 WebView on the VM: tests/apps/hello-webview loads inline HTML in the engine process, gets onPageFinished,
# evaluateJavascript("6 * 7") answers 42, and the engine's green page is composed into the window frame.
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"; VM="${APKLY_VM:-parallels@10.211.55.4}"; PASS="${APKLY_VM_PASS:-1234}"
"$ROOT/tests/apps/build.sh" hello-webview >/dev/null
LOG=$(APKLAYER_JOPTS='-Dapklayer.runms=31000 -Dapklayer.snapms=14000,20000 -Dapklayer.script=16000:click:240,340+18000:key:29,97+19000:key:30,98' "$ROOT/scripts/vm-run.sh" "$ROOT/build/hello-webview.apk" 2>&1)
for t in 14000 20000; do sshpass -p "$PASS" scp -q -o StrictHostKeyChecking=no "$VM:/tmp/apklayer-frame-${t}ms.png" /tmp/webview-frame-$t.png; done
sshpass -p "$PASS" ssh -o StrictHostKeyChecking=no "$VM" 'pkill -x apklayer-shell; pkill -f "[a]pklayer-webview"; pkill -f "[a]pklayer.host.Main"; true'
fail=0
check() { if eval "$2"; then echo "PASS  $1"; else echo "FAIL  $1"; fail=1; fi; }
check "WebViewClient.onPageFinished is called" 'grep -q "hello-webview\] page finished" <<<"$LOG"'
check "onPageStarted is called" 'grep -q "hello-webview\] page started" <<<"$LOG"'
check "the page calls an addJavascriptInterface object and gets its return value" 'grep -q "js says hi web" <<<"$LOG"'
check "a link click asks shouldOverrideUrlLoading" 'grep -q "hello-webview\] link https://example.org/next" <<<"$LOG"'
check "typing reaches the focused page element (input=ab)" 'grep -q "js says input=ab" <<<"$LOG"'
check "the engine reports history to canGoBack()" 'grep -q "canGoBack=true" <<<"$LOG"'
check "evaluateJavascript returns the page engine's answer" 'grep -q "6\*7 = 42" <<<"$LOG"'
pixel() { python3 - "$1" <<'E2'
import struct, zlib, sys
d=open(sys.argv[1],"rb").read(); i=8; idat=b""
while i<len(d):
    n,t=struct.unpack(">I4s",d[i:i+8]); b=d[i+8:i+8+n]
    if t==b"IHDR": w,h=struct.unpack(">II",b[:8])
    if t==b"IDAT": idat+=b
    i+=12+n
raw=zlib.decompress(idat); y=400; x=240; print(*raw[y*(w*4+1)+1+x*4:][:3])
E2
}
read -r R G B <<<"$(pixel /tmp/webview-frame-14000.png)"
check "the page is composed into the window (green body pixel)" '[ "$G" -gt $((R + 50)) ]'
read -r R G B <<<"$(pixel /tmp/webview-frame-20000.png)"
check "a click reaches the page (its onclick turns the body red)" '[ "$R" -gt $((G + 80)) ]'
exit $fail
