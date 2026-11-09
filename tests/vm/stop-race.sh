#!/bin/bash
# `apklayer stop` ends every process of the instance, also what starts while it runs (CHANGELOG 2026-11-09): an app bound to a service in a
# component host the hub started (the hub restarted that host once stop had ended it; the host and its spare holder outlived the stop), and a
# component-host launch racing the stop. After the stop no process carries the instance in its environment and no run.* record is left.
cd "$(dirname "$0")/../.."
inst=${APKLAYER_INSTANCE:-default}
state="${XDG_STATE_HOME:-$HOME/.local/state}/apklayer/$inst"
export APKLAYER_DISPLAY_WIDTH=480 APKLAYER_DISPLAY_HEIGHT=400 APKLAYER_JOPTS='-Dapklayer.realbinder=true -Dapklayer.runms=60000 -Dapklayer.instancehub=true'
export APKLAYER_HOST_LIBS="$APKLAYER_NATIVE/libapklayer-hal-graphics.so:$APKLAYER_NATIVE/libapklayer-hal-media.so"
fail() { echo "FAIL $*"; bin/apklayer stop >/dev/null 2>&1; exit 1; }

# this script and its callers carry the instance too (the harness's environment): the instance's processes are the others
declare -A own
for ((p = $$; p > 1; )); do own[$p]=1; read -r stat < /proc/$p/stat; stat=${stat##*) }; set -- $stat; p=$2; done
left() {
    local p env
    for d in /proc/[0-9]*; do
        p=${d#/proc/}
        [ -n "${own[$p]}" ] || [ "$p" = "$BASHPID" ] && continue  # $BASHPID: the $(left) subshell
        { mapfile -d '' env < "$d/environ"; } 2>/dev/null || continue
        [[ " ${env[*]} " == *" APKLAYER_INSTANCE=$inst "* ]] && echo "$p $(tr '\0' ' ' < "$d/cmdline" | cut -c1-160)"
    done
}

bin/apklayer stop >/dev/null 2>&1
[ -z "$(left)" ] || fail "processes before the test: $(left)"
exec 3< <(exec bin/apklayer run hello.hubclient 2>&1)
while read -r line <&3; do
    case $line in *"[hello-hub-client] READY serverPid="*) ready=1; break;; esac
done
[ -n "$ready" ] || fail "hello.hubclient did not bind its service"
bin/apklayer component-host hello.hubserver >/dev/null 2>&1 &  # a launch racing the stop
bin/apklayer stop >/dev/null 2>&1
exec 3<&-
leftover=$(left)
records=$(cd "$state" && ls run.* 2>/dev/null)
[ -z "$leftover" ] || fail "left after stop:"$'\n'"$leftover"
[ -z "$records" ] || fail "run records left after stop: $records"
wait  # the racing launch (ended by the stop: nothing of it is left)
echo "PASS stop: no process of the instance is left, with a bound component host and a launch racing the stop"
