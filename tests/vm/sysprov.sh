#!/bin/bash
# sysprov.sh [settings androidid contacts dictionary geoclue]: the tree's own providers (SettingsProvider, ContactsProvider, UserDictionaryProvider) installed as apps (tools/stage sysapps) and reached from another app through the hub.
# androidid: the app's ANDROID_ID read, the instance stopped and started again, read again: the same (SettingsProvider persists it a little after the read).
# geoclue: the hub's GeoClue probe hangs (a stand-in that never answers): the boot settings are written after the probe's 2 s bound (under 30 s on a loaded VM), not when the stand-in ends (60 s).
# On the VM, in the group's instance (tests/run sysprov): APKLAYER_INSTANCE, APKLY_STATE (its logs) and the test apps installed by the runner.
: "${APKLY_STATE:=${XDG_STATE_HOME:-$HOME/.local/state}/apklayer/${APKLAYER_INSTANCE:-default}}"
mkdir -p $APKLY_STATE
status=0
launch() {  # launch <scenario> <out>: hello.sysprov until it reports, then the instance stopped at once (androidid: right after the first read)
    grep -aq -m1 '^\[sys\] \(PASS\|FAIL\)' < <(APKLAYER_JOPTS="-Dhello.sys=$1 -Dapklayer.runms=40000" bin/apklayer run hello.sysprov 2>&1 | tee $2)
    if [ $1 = androidid ]; then  # the settings host ended first, within its 0.2 s save delay (`stop` may end it in any order)
        host=$(grep -a 'registered com.android.providers.settings pid=' $APKLY_STATE/hub.log | tail -1 | sed 's/.*pid=\([0-9]*\).*/\1/')
        tr '\0' '\n' < /proc/$host/environ 2>/dev/null | grep -qx "APKLAYER_INSTANCE=${APKLAYER_INSTANCE:-default}" && kill -TERM $host
    fi
    bin/apklayer stop >/dev/null 2>&1
    wait $! 2>/dev/null
    grep -a '\[sys\]' $2; grep -aq '^\[sys\] PASS' $2 || { grep -a 'Exception\|Error' $2 | head -4; status=1; }
}
for what in ${@:-settings androidid contacts dictionary geoclue}; do
    out=$APKLY_STATE/sysprov-$what.out
    if [ $what = geoclue ]; then
        bin/apklayer stop >/dev/null 2>&1  # a fresh hub: its boot probes GeoClue
        echo 'import time; time.sleep(60)' > $APKLY_STATE/geoclue-hang.py
        APKLAYER_GEOCLUE_PROBE=$APKLY_STATE/geoclue-hang.py launch settings $out
        boot=$(grep -a '^\[apklayer-hub\] boot count' $APKLY_STATE/hub.log | tail -1) ms=$(echo "$boot" | sed -n 's/.*(\([0-9]*\) ms)$/\1/p')
        if grep -aq 'GeoClue did not answer within' $APKLY_STATE/hub.log && [ -n "$ms" ] && [ "$ms" -lt 30000 ]; then echo "[sys] PASS a hanging GeoClue: $boot"
        else echo "[sys] FAIL a hanging GeoClue: ${boot:-no boot line}"; status=1; fi
        continue
    fi
    launch $what $out
    [ $what = androidid ] || continue
    launch $what $out.2
    # the second launch reads the first one's android_id and the value it wrote just before the stop
    id1=$(grep -a -o 'android_id [^ ]*' $out) id2=$(grep -a -o 'android_id [^ ]*' $out.2)
    wrote=$(grep -a -o 'wrote screen_off_timeout [0-9]*' $out | grep -o '[0-9]*$') read=$(grep -a -o 'android_id .* screen_off_timeout [0-9]*' $out.2 | grep -o '[0-9]*$')
    grep -a 'component-host stopped com.android.providers.settings' $APKLY_STATE/component-com.android.providers.settings.log | tail -2
    if [ -n "$id1" ] && [ "$id1" = "$id2" ] && [ -n "$wrote" ] && [ "$wrote" = "$read" ]; then echo "[sys] PASS kept across stop: $id1, screen_off_timeout $read"
    else echo "[sys] FAIL across stop: $id1 -> $id2, screen_off_timeout wrote $wrote read $read"; status=1; fi
done
exit $status
