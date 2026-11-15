#!/bin/bash
# spawn.sh [command,with,commas [max-mean-ms]]: hello-spawn starts the command (default /usr/bin/true) 20 times through ProcessBuilder; fails when the mean is above max-mean-ms (default 250).
: "${APKLY_STATE:=${XDG_STATE_HOME:-$HOME/.local/state}/apklayer/${APKLAYER_INSTANCE:-default}}"
out=$APKLY_STATE/spawn.out
bin/apklayer stop >/dev/null 2>&1
APKLAYER_JOPTS="-Dapklayer.runms=60000 -Dhello.cmd=${1:-/usr/bin/true}" bin/apklayer run hello.spawn > $out 2>&1 || true
grep -a "\[spawn\] \(MEAN\|FAILED\)" $out
bin/apklayer stop >/dev/null 2>&1
grep -aq "\[spawn\] DONE" $out && awk -v max="${2:-250}" '/\[spawn\] MEAN/ {ok = $3 + 0 <= max} END {exit !ok}' $out
