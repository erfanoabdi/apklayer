#!/bin/sh
# arm64-stack: run Android's own services on the arm64 VM (PLAN P7.1), start to finish in ONE command: properties, servicemanager,
# the graphics HALs, SurfaceFlinger, then a tool (default: the sftest smoke test); everything started is stopped on exit.
# Usage (on the VM): scripts/arm64-stack.sh [tool-lib [args...]]     env: APKLAYER_NATIVE (default ~/arm64-native), APKLAYER_INSTANCE
set -u
N="${APKLAYER_NATIVE:-$HOME/arm64-native}"
LIBS="$HOME/android-arm64/lib64"
LOGS="${APKLAYER_LOGS:-/tmp/apklayer-stack}"
export XDG_RUNTIME_DIR="${XDG_RUNTIME_DIR:-/run/user/$(id -u)}" WAYLAND_DISPLAY="${WAYLAND_DISPLAY:-wayland-0}"
export APKLAYER_INSTANCE="${APKLAYER_INSTANCE:-default}"
export LD_LIBRARY_PATH="$HOME/bt-arm/bionic_translation/b:$N" XDG_DATA_DIRS="$N/share" LD_PRELOAD=libgcc_s.so.1 BIONIC_LD_LIBRARY_PATH="$LIBS"
export APKLAYER_CRASH_TRACE=1
mkdir -p "$LOGS"; : > "$LOGS/pids"
run() { # run <name> <library> [args]: a service in the background
    name="$1"; lib="$2"; shift 2
    "$N/bin/btload" "$LIBS" "$lib" --main "$@" > "$LOGS/$name.log" 2>&1 &
    echo "$!" >> "$LOGS/pids"
}
cleanup() { while read -r pid; do kill "$pid" 2>/dev/null; done < "$LOGS/pids"; pkill -x apklayer-shell 2>/dev/null; }
trap cleanup EXIT INT TERM

rm -rf "$XDG_RUNTIME_DIR/apklayer/$APKLAYER_INSTANCE"
"$N/bin/aprop" load "$N/../apklayer-m1/bridge/native/apklayer.prop" "$N/android-root/props/system_build.prop" 2> "$LOGS/aprop.log"
if [ "${APKLAYER_START_SHELL:-1}" = 1 ]; then
    pkill -x apklayer-shell 2>/dev/null; rm -f "$XDG_RUNTIME_DIR/apklayer/shell.sock"
    "$HOME/apklayer-m1/b/bridge/shell/apklayer-shell" > "$LOGS/shell.log" 2>&1 &
    echo "$!" >> "$LOGS/pids"
fi
run servicemanager libapklayer_servicemanager.so /dev/binder
run allocator libapklayer_allocator.so
run composer libapklayer_composer.so
run surfaceflinger libapklayer_surfaceflinger.so
tool="${1:-libapklayer_sftest.so}"; [ $# -gt 0 ] && shift
"$N/bin/btload" "$LIBS" "$tool" --main "$@" 2>&1 | tee "$LOGS/tool.log"
