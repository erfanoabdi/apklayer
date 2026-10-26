#!/usr/bin/env bash
# Desktop notification policy on the VM: a notification is shown once, an identical repeat is ignored, a changed one
# updates its popup, and low-importance and ongoing ones stay off the desktop.
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"; VM="${APKLY_VM:-parallels@10.211.55.4}"; PASS="${APKLY_VM_PASS:-1234}"
STEPS='9000:notify:A+9500:notify:A+10000:notify:A2,1+10500:notify:Quiet,2,2+11000:notify:Sticky,3,3,ongoing+11500:notify:Z,4'
LOG=$(APKLAYER_JOPTS="-Dapklayer.runms=14000 -Dapklayer.script=$STEPS" "$ROOT/scripts/vm-run.sh" 2>&1)
sshpass -p "$PASS" ssh -o StrictHostKeyChecking=no "$VM" 'pkill -x apklayer-shell; pkill -f "[a]pklayer.host.Main"; true'
fail=0
check() { if eval "$2"; then echo "PASS  $1"; else echo "FAIL  $1"; fail=1; fi; }
check "a new notification is shown" 'grep -q "notification shown: A /" <<<"$LOG" && grep -q "notification shown: Z /" <<<"$LOG"'
check "an identical repeat is not shown again" '[ "$(grep -c "notification shown: A /" <<<"$LOG")" = 1 ]'
check "a changed notification updates its popup" 'grep -q "notification updated: A2 /" <<<"$LOG"'
check "low importance and ongoing notifications stay off the desktop" '! grep -qE "notification (shown|updated): (Quiet|Sticky)" <<<"$LOG"'
exit $fail
