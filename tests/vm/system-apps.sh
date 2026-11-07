#!/bin/bash
# system-apps.sh [scenario...]: the system apps and services as an app uses them (tests/apps/hello-system): contacts, calendar, downloads, settings,
# media, documents, userdict, telephony, notifications, alarms, jobs (synchronous and asynchronous), build identity, signing certificates, display rotation, cache quota, storage stats, vibrator, toasts, system properties, accounts, account removal, location, input methods. No argument: all of them in one launch; with arguments: one launch
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
trap 'kill $server; rm -rf $www; bin/apklayer ime disable hello.system >/dev/null' EXIT  # an enabled IME would bind to the fields of later groups
status=0
# an update across a key rotation, as on Android: hello.rotated signed with its past key, then hello-rotated (its v3 lineage continues that key) over it
unzip -p .tests/apks/hello-rotated.apk assets/hello-rotated-past.apk > $APKLY_STATE/rotated-past.apk
bin/apklayer uninstall hello.rotated >/dev/null 2>&1
past=$(bin/apklayer install $APKLY_STATE/rotated-past.apk 2>&1) update=$(bin/apklayer install .tests/apks/hello-rotated.apk 2>&1)
if [[ $past == *installed* && $update == *installed* && $update != *INSTALL_FAILED* ]]; then echo "[sys] PASS rotated update over the past key's install"
else echo "[sys] FAIL rotated update: $(tail -1 <<<"$past") / $(tail -1 <<<"$update")"; status=1; fi
launch() {  # launch <scenario> <out>: hello.system until it reports DONE, then the instance stopped
    APKLAYER_JOPTS="-Dhello.sys=$1 -Dapklayer.runms=90000 -Dapklayer.logcat=true -Dhello.url=http://127.0.0.1:$port/check.bin" bin/apklayer run hello.system > $2 2>&1 &
    run=$!
    until grep -aq "^\[sys\] DONE" $2 2>/dev/null; do kill -0 $run 2>/dev/null || break; read -t 1 <> <(:) || true; done
    kill $run 2>/dev/null; bin/apklayer stop >/dev/null 2>&1; wait $run 2>/dev/null
}
for scenario in ${@:-all}; do
    out=$APKLY_STATE/sys-$scenario.out
    launch $scenario $out
    grep -a "^\[sys\] \(PASS\|FAIL\)" $out
    if [ "$scenario" = all ]; then total=24; else total=1; fi
    got=$(grep -ac "^\[sys\] PASS" $out); [ "$got" -eq "$total" ] || { status=1; grep -a "^\[sys\] FAIL" $out > /dev/null || echo "[sys] no report ($got of $total passed)"; }
done
# the IME choice applies to a running instance at once (HubProtocol.SETTINGS_CHANGED): a hub booted without it, then `ime enable`, then the app sees it the default
if [ -z "${*:-}" ] || [[ " $* " == *" ime "* ]]; then
    bin/apklayer ime disable hello.system >/dev/null
    bin/apklayer hub > $APKLY_STATE/sys-ime-live-hub.out 2>&1 &
    hub=$!
    bin/apklayer wait hub >/dev/null 2>&1
    applied=$(bin/apklayer ime enable hello.system)
    out=$APKLY_STATE/sys-ime-live.out
    APKLAYER_JOPTS="-Dhello.sys=ime -Dapklayer.runms=60000" bin/apklayer run hello.system > $out 2>&1 &
    run=$!
    until grep -aq "^\[sys\] DONE" $out 2>/dev/null; do kill -0 $run 2>/dev/null || break; read -t 1 <> <(:) || true; done
    kill -0 $hub 2>/dev/null && same=yes || same=no  # the hub that booted without the choice served the launch
    kill $run 2>/dev/null; bin/apklayer stop >/dev/null 2>&1; wait $run $hub 2>/dev/null
    if [[ $applied == *"applied now"* ]] && [ $same = yes ] && grep -aq "^\[sys\] PASS ime" $out; then echo "[sys] PASS ime live: $applied"
    else echo "[sys] FAIL ime live ($applied, same hub $same): $(grep -a '^\[sys\] FAIL' $out)"; status=1; fi
fi
exit $status
