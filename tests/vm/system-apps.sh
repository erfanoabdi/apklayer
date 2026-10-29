#!/bin/bash
# The system apps and services as an app uses them (tests/apps/hello-system): contacts, calendar, downloads, settings, media, documents, userdict, telephony,
# notifications, alarms, jobs. Usage: tests/vm/system-apps.sh [scenario...]  (no argument: all of them in one launch; with arguments: one launch each).
# Prints "[sys] PASS|FAIL name detail" per scenario and exits 1 when one failed or did not report.
set -uo pipefail
cd "$(dirname "$0")/../.."
. tests/vm/config.sh
tests/apps/build.sh hello-system > /dev/null
sshpass -p "$PASS" scp -q build/hello-system.apk "$VM:hello-system.apk"
sshpass -p "$PASS" ssh "$VM" 'bash -s' -- "${@:-all}" <<'REMOTE'
cd ~/apklayer-m1 2>/dev/null || cd ~/apklayer
export XDG_RUNTIME_DIR=/run/user/1000 WAYLAND_DISPLAY=wayland-0
CLI=$(ls b/cli/apklayer)
$CLI stop >/dev/null 2>&1
$CLI install ~/hello-system.apk | grep -a installed
mkdir -p /tmp/sys-www && head -c 40000 /dev/urandom > /tmp/sys-www/check.bin
(cd /tmp/sys-www && setsid python3 -m http.server 8099 --bind 127.0.0.1 >/tmp/sys-www.log 2>&1 &)
status=0
for scenario in "$@"; do
    out=/tmp/sys-$scenario.out
    APKLAYER_JOPTS="-Dhello.sys=$scenario -Dapklayer.runms=90000 -Dhello.url=http://127.0.0.1:8099/check.bin" $CLI run hello.system > $out 2>&1 &
    run=$!
    until grep -aq "^\[sys\] DONE" $out 2>/dev/null; do kill -0 $run 2>/dev/null || break; read -t 1 <> <(:) || true; done
    kill $run 2>/dev/null; $CLI stop >/dev/null 2>&1; wait $run 2>/dev/null
    grep -a "^\[sys\] \(PASS\|FAIL\)" $out
    if [ "$scenario" = all ]; then total=11; else total=1; fi
    got=$(grep -ac "^\[sys\] PASS" $out); [ "$got" -eq "$total" ] || { status=1; grep -a "^\[sys\] FAIL" $out > /dev/null || echo "[sys] no report ($got of $total passed)"; }
done
pkill -f '[h]ttp.server 8099'
exit $status
REMOTE
