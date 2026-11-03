#!/bin/bash
# sysprov.sh [settings contacts dictionary]: the tree's own providers (SettingsProvider, ContactsProvider, UserDictionaryProvider) installed as apps (scripts/install-system-apps.sh) and reached from another app through the hub.
# On the VM, in the group's instance (tests/run sysprov): APKLAYER_INSTANCE, APKLY_STATE (its logs) and the test apps installed by the runner.
: "${APKLY_STATE:=${XDG_STATE_HOME:-$HOME/.local/state}/apklayer/${APKLAYER_INSTANCE:-default}}"
status=0
for what in ${@:-settings contacts dictionary}; do
    out=$APKLY_STATE/sysprov-$what.out
    APKLAYER_JOPTS="-Dhello.sys=$what -Dapklayer.runms=40000" bin/apklayer run hello.sysprov > $out 2>&1 || true
    grep -a '\[sys\]' $out || { grep -a 'Exception\|Error' $out | head -4; status=1; }
    bin/apklayer stop >/dev/null 2>&1
done
exit $status
