#!/bin/bash
# The file chooser portal behind ACTION_OPEN_DOCUMENT: the app opens the desktop's dialog, the portal backend closes it (its own
# org.freedesktop.impl.portal.Request.Close on this request's handle: no key to the shared desktop's focus), the app gets RESULT_CANCELED.
# On the VM, in the group's instance (tests/run portal): APKLAYER_INSTANCE, APKLY_STATE (its logs) and the test apps installed by the runner.
: "${APKLY_STATE:=${XDG_STATE_HOME:-$HOME/.local/state}/apklayer/${APKLAYER_INSTANCE:-default}}"
out=$APKLY_STATE/portal.out
backends=$(gdbus call --session --dest org.freedesktop.DBus --object-path /org/freedesktop/DBus --method org.freedesktop.DBus.ListNames | grep -o "org\.freedesktop\.impl\.portal\.desktop\.[a-z]*")
bin/apklayer stop >/dev/null 2>&1
# nothing tells the test when the backend has made its dialog (its request object): once the request is made, Close goes out once a second
# until a backend has the request
APKLAYER_JOPTS="-Dapklayer.runms=40000" bin/apklayer run hello.portal 2>&1 | while IFS= read -r line; do
    case "$line" in
        *"[apklayer] portal request "*) echo "$line"; handle=${line##* }
            ( for i in $(seq 1 25); do sleep 1; for b in $backends; do gdbus call --session --dest $b --object-path $handle \
                --method org.freedesktop.impl.portal.Request.Close >/dev/null 2>&1 && exit; done; done ) & sender=$! ;;
        *"[portal]"*|*"file chooser portal unavailable"*) echo "$line"; kill ${sender:-0} 2>/dev/null; break ;;
    esac
done > $out
cat $out
bin/apklayer stop >/dev/null 2>&1
grep -aq "CANCELED" $out
