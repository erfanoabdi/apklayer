#!/bin/bash
# arm64-stack: Android's own services on the arm64 VM (PLAN P7.1): properties, shell, servicemanager, graphics HALs, SurfaceFlinger,
# then a tool (default sftest). Never stalls: it ends as soon as ANY process of the stack exits (a crashed service, or the tool
# finishing) and stops everything it started. Usage (on the VM): scripts/arm64-stack.sh [tool-lib [args...]]
# env: APKLAYER_NATIVE (~/arm64-native), APKLAYER_INSTANCE (default), APKLAYER_START_SHELL (1), APKLAYER_LOGS (/tmp/apklayer-stack)
set -u
N="${APKLAYER_NATIVE:-$HOME/arm64-native}"; L="$HOME/android-arm64/lib64"; LOGS="${APKLAYER_LOGS:-/tmp/apklayer-stack}"
export XDG_RUNTIME_DIR="${XDG_RUNTIME_DIR:-/run/user/$(id -u)}" WAYLAND_DISPLAY="${WAYLAND_DISPLAY:-wayland-0}" APKLAYER_INSTANCE="${APKLAYER_INSTANCE:-default}"
export LD_LIBRARY_PATH="$HOME/bt-arm/bionic_translation/b:$N" XDG_DATA_DIRS="$N/share" LD_PRELOAD="$N/libapklayer-preload.so:libgcc_s.so.1" BIONIC_LD_LIBRARY_PATH="$L"
export APKLAYER_NATIVE="$N" APKLAYER_CRASH_TRACE=1 APKLAYER_HOST_LIBS="$N/libapklayer-hal-graphics.so"
mkdir -p "$LOGS"; rm -f "$LOGS"/*.log
declare -A name_of
start() { # start <name> <command...>: background, logged, remembered
    n="$1"; shift; "$@" > "$LOGS/$n.log" 2>&1 & name_of[$!]="$n"
}
trap 'kill "${!name_of[@]}" 2>/dev/null; pkill -x apklayer-shell 2>/dev/null' EXIT

rm -rf "$XDG_RUNTIME_DIR/apklayer/$APKLAYER_INSTANCE"
"$N/bin/aprop" load "$HOME/apklayer-m1/bridge/native/apklayer.prop" "$N/android-root/props/system_build.prop" 2> "$LOGS/aprop.log"
if [ "${APKLAYER_START_SHELL:-1}" = 1 ]; then
    pkill -x apklayer-shell 2>/dev/null; rm -f "$XDG_RUNTIME_DIR/apklayer/shell.sock"
    start shell "$HOME/apklayer-m1/b/bridge/shell/apklayer-shell"
fi
svc() { # svc <name> <lib> [args]; APKLAYER_GDB=<name> runs that one under gdb (backtrace of a crash goes to its log)
    if [ "${APKLAYER_GDB:-}" = "$1" ]; then
        start "$1" gdb -batch ${APKLAYER_GDBX:+-x $APKLAYER_GDBX} -ex "handle SIGUSR1 nostop noprint pass" -ex "handle SIGUSR2 nostop noprint pass" -ex run -ex "bt 8" -ex "info registers pc lr sp" \
            --args "$N/bin/btload" "$L" "$2" --main "${@:3}"
    else
        start "$1" "$N/bin/btload" "$L" "$2" --main "${@:3}"
    fi
}
svc servicemanager libapklayer_servicemanager.so /dev/binder
svc allocator libapklayer_allocator.so
svc composer libapklayer_composer.so
svc surfaceflinger libapklayer_surfaceflinger.so
tool="${1:-libapklayer_sftest.so}"; [ $# -gt 0 ] && shift
if [ "$tool" = none ]; then start keepalive tail -f /dev/null; else svc tool "$tool" "$@"; fi  # "none": only the services (an app brings its own windows); stop the stack with its pid file
echo "$$" > "$LOGS/stack.pid"

wait -n; status=$?   # the first process to exit ends the run
for pid in "${!name_of[@]}"; do kill -0 "$pid" 2>/dev/null || echo "exited: ${name_of[$pid]} (status $status)"; done
echo "logs: $LOGS/<name>.log"
