#!/bin/bash
# system-apps.sh [scenario...]: the system apps and services as an app uses them (tests/apps/hello-system): contacts, calendar, downloads, settings,
# media, documents, userdict, telephony, notifications, alarms, jobs, build identity, signing certificates, display rotation, cache quota, storage stats, vibrator, toasts, accounts, account removal, location, input methods. No argument: all of them in one launch; with arguments: one launch
# each. Prints "[sys] PASS|FAIL name detail" per scenario and exits 1 when one failed or did not report.
# On the VM, in the group's instance (tests/run system-apps): APKLAYER_INSTANCE, APKLY_STATE (its logs) and hello-system installed by the runner.
set -uo pipefail
: "${APKLY_STATE:=${XDG_STATE_HOME:-$HOME/.local/state}/apklayer/${APKLAYER_INSTANCE:-default}}"
id=${APKLAYER_INSTANCE:-default}; [ "$id" = default ] && id=0
bin/apklayer stop >/dev/null 2>&1
rm -f "${XDG_DATA_HOME:-$HOME/.local/share}/apklayer/$id/accounts.json"  # accounts: a fresh store (a store left by an older run of the instance decided the scenario)
bin/apklayer ime enable hello.system >/dev/null  # the ime scenario: the user's choice, applied by the hub at the next start
www=$(mktemp -d) && head -c 40000 /dev/urandom > $www/check.bin
port=$(python3 -c 'import socket; s = socket.socket(); s.bind(("127.0.0.1", 0)); print(s.getsockname()[1])')
python3 -m http.server $port --bind 127.0.0.1 --directory $www > $www/server.log 2>&1 &
server=$!
trap 'kill $server; rm -rf $www' EXIT
status=0
for scenario in ${@:-all}; do
    out=$APKLY_STATE/sys-$scenario.out
    APKLAYER_JOPTS="-Dhello.sys=$scenario -Dapklayer.runms=90000 -Dapklayer.logcat=true -Dhello.url=http://127.0.0.1:$port/check.bin" bin/apklayer run hello.system > $out 2>&1 &
    run=$!
    until grep -aq "^\[sys\] DONE" $out 2>/dev/null; do kill -0 $run 2>/dev/null || break; read -t 1 <> <(:) || true; done
    kill $run 2>/dev/null; bin/apklayer stop >/dev/null 2>&1; wait $run 2>/dev/null
    grep -a "^\[sys\] \(PASS\|FAIL\)" $out
    if [ "$scenario" = all ]; then total=22; else total=1; fi
    got=$(grep -ac "^\[sys\] PASS" $out); [ "$got" -eq "$total" ] || { status=1; grep -a "^\[sys\] FAIL" $out > /dev/null || echo "[sys] no report ($got of $total passed)"; }
done
exit $status
