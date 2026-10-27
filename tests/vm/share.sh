#!/usr/bin/env bash
# P6 exit test on the VM: app A shares text (ACTION_SEND text/plain) and the installed app B, whose manifest
# declares that filter, is started with the intent and receives the text.
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"; VM="${APKLY_VM:-utm@192.168.64.7}"; PASS="${APKLY_VM_PASS:-1234}"
for a in hello-share-dst hello-share-dst2 hello-share-src; do "$ROOT/tests/apps/build.sh" $a >/dev/null || { echo "FAIL  $a does not build"; exit 1; }; done
stop() { sshpass -p "$PASS" ssh -o StrictHostKeyChecking=no "$VM" 'pkill -x apklayer-shell; pkill -f "[a]pklayer.host.Main"; true'; }
# install both receivers (run for a few seconds, which also deploys the sources); two apps handle the intent
for r in hello-share-dst2 hello-share-dst; do APKLAYER_JOPTS='-Dapklayer.runms=6000' "$ROOT/scripts/vm-run.sh" "$ROOT/build/$r.apk" >/dev/null 2>&1; stop; done
LOG=$(APKLAYER_CHOOSER='echo hello.share.dst' APKLAYER_JOPTS='-Dapklayer.runms=40000' "$ROOT/scripts/vm-run.sh" "$ROOT/build/hello-share-src.apk" 2>&1); stop
fail=0
check() { if eval "$2"; then echo "PASS  $1"; else echo "FAIL  $1"; fail=1; fi; }
check "the sender's host routes the intent to the installed receiver" 'grep -q "intent android.intent.action.SEND sent to hello.share.dst" <<<"$LOG"'
check "the chosen receiver (not the other one) gets the text" 'grep -q "hello-share-dst\] got text: hello from the sender" <<<"$LOG" && ! grep -q "hello-share-dst2\] got text" <<<"$LOG"'
check "the answer comes back to the caller (startActivityForResult across apps)" 'grep -q "hello-share-src\] result request=7 code=-1 answer=42 for life" <<<"$LOG"'
exit $fail
