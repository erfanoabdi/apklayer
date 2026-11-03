#!/bin/bash
# Start-up timeline, run on the VM: `tests/vm/startup.sh <package> [runms]`. A cold start (everything stopped first), then a warm one (stack, shell and hub
# left by the first run), each printed by tests/vm/startup-timeline.py; everything is stopped at the end. The app exits after runms (default 15000).
# Honours APKLAYER_NATIVE / XDG_DATA_HOME / XDG_STATE_HOME like the CLI; wrap it in the VM lock (flock -n -o ~/apklayer-vm.lock).
cd "$(dirname "$0")/../.."
pkg="$1"; runms="${2:-15000}"
export XDG_RUNTIME_DIR=/run/user/1000 WAYLAND_DISPLAY=wayland-0 APKLAYER_RUNTIME_DEBUG=1 APKLAYER_JOPTS="-Dapklayer.runms=$runms -Dapklayer.perf=true $APKLAYER_EXTRA_JOPTS"
state="${XDG_STATE_HOME:-$HOME/.local/state}/apklayer/default"
out="$state/startup-run"
stopall() { bin/apklayer stop >/dev/null 2>&1; pkill -x apklayer-shell; pkill -f '[a]pklayer.host.Main'; }
stopall
for kind in cold warm; do
    bin/apklayer run "$pkg" > "$out.$kind" 2>&1
    echo "== $kind $pkg (exit $?)"
    python3 tests/vm/startup-timeline.py "$out.$kind" "$state/shell.log" "$state/hub.log" | grep -v 'perf\]'
done
stopall
