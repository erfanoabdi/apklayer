#!/bin/sh
# kill-instance.sh <instance> <pgrep -f pattern>...: kills the matching processes of one apklayer instance only, so stacks of other instances
# (parallel test copies) survive. A process without APKLAYER_INSTANCE in its environment belongs to "default". A sandboxed app process
# (cli/sandbox.cpp: its own uid, its environment private) belongs to the instance whose stack made its user namespace.
inst=$1; shift
stack=$(cat "${XDG_STATE_HOME:-$HOME/.local/state}/apklayer/$inst/stack.pid" 2>/dev/null)
ns=$(readlink /proc/${stack:-0}/ns/user 2>/dev/null); [ "$ns" != "$(readlink /proc/self/ns/user)" ] || ns=""  # a sandboxed stack's own namespace only
for pattern in "$@"; do
    for p in $(pgrep -f "$pattern"); do
        if [ -r /proc/$p/environ ]; then
            mine=$(tr '\0' '\n' < /proc/$p/environ 2>/dev/null | sed -n 's/^APKLAYER_INSTANCE=//p')
            [ "${mine:-default}" = "$inst" ] && kill "$p"
        elif [ -n "$ns" ] && [ "$(readlink /proc/$p/ns/user)" = "$ns" ]; then
            kill "$p"
        fi
    done
done
true
