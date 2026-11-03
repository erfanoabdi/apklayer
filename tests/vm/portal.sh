#!/bin/bash
# The file chooser portal behind ACTION_OPEN_DOCUMENT: the app opens the desktop's dialog, a real Esc key cancels it, the app gets RESULT_CANCELED.
# On the VM, in the group's instance (tests/run portal): APKLAYER_INSTANCE, APKLY_STATE (its logs) and the test apps installed by the runner.
: "${APKLY_STATE:=${XDG_STATE_HOME:-$HOME/.local/state}/apklayer/${APKLAYER_INSTANCE:-default}}"
out=$APKLY_STATE/portal.out keys=${APKLY_OUT:-/tmp}/uinput-keys
gcc -O1 -o $keys tests/vm/uinput-keys.c
bin/apklayer stop >/dev/null 2>&1
# nothing tells the test when the dialog has appeared and can take a key (GNOME does not give an app that event): once the request is made, Esc goes out once a
# second until the app has its answer
APKLAYER_JOPTS="-Dapklayer.runms=40000" bin/apklayer run hello.portal 2>&1 | while IFS= read -r line; do
    case "$line" in
        *"[apklayer] portal request"*) echo "$line"; ( for i in $(seq 1 25); do sleep 1; sudo -n $keys key:1; done ) & sender=$! ;;
        *"[portal]"*|*"file chooser portal unavailable"*) echo "$line"; kill ${sender:-0} 2>/dev/null; break ;;
    esac
done > $out
cat $out
bin/apklayer stop >/dev/null 2>&1
grep -aq "CANCELED" $out
