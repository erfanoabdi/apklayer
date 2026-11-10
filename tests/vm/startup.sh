#!/bin/bash
# Start-up timeline, run on the VM: `tests/vm/startup.sh <package> [runms]`. A cold start (the instance stopped first), then a warm one (stack, shell and
# hub left by the first run), each printed by tests/vm/startup-timeline.py; the instance is stopped at the end. The app exits after runms (default 15000).
# Honours APKLAYER_INSTANCE, APKLAYER_NATIVE, XDG_* like the CLI (an instance of its own runs beside others: docs/history/PLAN-2026-11-10.md).
cd "$(dirname "$0")/../.."
pkg="$1"; runms="${2:-15000}"
export APKLAYER_RUNTIME_DEBUG=1 APKLAYER_JOPTS="-Dapklayer.runms=$runms -Dapklayer.perf=true $APKLAYER_EXTRA_JOPTS"
export WAYLAND_DISPLAY="${WAYLAND_DISPLAY:-/run/user/1000/wayland-0}"
state="${XDG_STATE_HOME:-$HOME/.local/state}/apklayer/${APKLAYER_INSTANCE:-default}"
out="$state/startup-run"
bin/apklayer stop
for kind in cold warm; do
    bin/apklayer run "$pkg" > "$out.$kind" 2>&1
    echo "== $kind $pkg (exit $?)"
    python3 tests/vm/startup-timeline.py "$out.$kind" "$state/shell.log" "$state/hub.log"
done
bin/apklayer stop
