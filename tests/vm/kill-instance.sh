#!/bin/sh
# kill-instance.sh <instance> <pgrep -f pattern>...: kills the matching processes of one apklayer instance only, so stacks of other instances
# (parallel test copies) survive. A process without APKLAYER_INSTANCE in its environment belongs to "default".
inst=$1; shift
for pattern in "$@"; do
    for p in $(pgrep -f "$pattern"); do
        mine=$(tr '\0' '\n' < /proc/$p/environ 2>/dev/null | sed -n 's/^APKLAYER_INSTANCE=//p')
        [ "${mine:-default}" = "$inst" ] && kill "$p"
    done
done
true
