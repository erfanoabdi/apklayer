#!/bin/sh
# push-check.sh [instance] [package]: after a real cloud message to a closed app (docs/PLAN.md "Plan: background push", the owner's check), reads the
# always-on instance's logs and says how far the push came: microG's McsService logged in, the message arrived, microG delivered
# com.google.android.c2dm.intent.RECEIVE, the closed app was started for it, its notification reached the desktop.
# Defaults: instance push, package org.telegram.messenger.web. microG's own lines (D/GmsGcm...) need APKLAYER_LOGCAT=1 in the instance's unit
# (~/.config/systemd/user/apklayer-instance@<id>.service.d/logcat.conf).
id=${1:-push} pkg=${2:-org.telegram.messenger.web}
logs=${XDG_STATE_HOME:-$HOME/.local/state}/apklayer/$id
mcs=$logs/component-com.google.android.gms-org.microg.gms.gcm.McsService.log
hub=$(mktemp)
trap 'rm -f "$hub"' EXIT
{ journalctl --user -u "apklayer-instance@$id.service" -o cat 2>/dev/null; cat "$logs/hub.log" 2>/dev/null; } > "$hub"
failed=0
step() {  # step <name> <found?> <detail>
    if [ "$2" = yes ]; then echo "ok    $1"; else echo "MISS  $1: $3"; failed=1; fi
}
has() { [ -f "$1" ] && grep -a -q -- "$2" "$1" && echo yes; }

step "microG's McsService logged in to mtalk.google.com" "$(has "$mcs" 'GmsGcmMcsSvc: Logged in')" \
     "no 'Logged in' in $mcs (microG: Settings > Google device registration and Cloud Messaging on; APKLAYER_LOGCAT=1 in the unit)"
step "McsService still connected (its last lines)" "$( [ -f "$mcs" ] && tail -n 200 "$mcs" | grep -a -E 'GmsGcmMcsSvc: (Logged in|Heartbeat)|Incoming message: HeartbeatAck' >/dev/null && echo yes)" \
     "no login or heartbeat in the last 200 lines of $mcs"
step "a cloud message for $pkg arrived" "$(has "$mcs" "category=$pkg")" "no DataMessageStanza for $pkg in $mcs"
step "microG delivered com.google.android.c2dm.intent.RECEIVE" "$(has "$hub" 'broadcast com.google.android.c2dm.intent.RECEIVE from com.google.android.gms')" \
     "the hub sent no RECEIVE broadcast (journalctl --user -u apklayer-instance@$id)"
step "the closed app was started for it" "$( { grep -a -q "starting $pkg launcherPid" "$hub" || [ -f "$logs/component-$pkg.log" ]; } && echo yes)" \
     "the hub started no process of $pkg"
step "its notification reached the desktop" "$(grep -a "notification popup=" "$hub" | grep -a -v -E 'popup=(0)?$' >/dev/null && echo yes)" \
     "no desktop notification with a non-zero id in the hub's lines"
grep -a "\[apklayer-hub\] notification $pkg " "$hub" | tail -n 3
exit $failed
