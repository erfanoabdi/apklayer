#!/bin/bash
# The always-on unit's hub against a stray `apklayer hub` (CHANGELOG 2026-11-09), run on the VM; needs the user's systemd manager.
#   stray    a hub launcher outside the unit (one an app of the previous stack starts during a restart) holds the hub when the unit starts:
#            the unit's hub replaces it and the unit stays active (it used to return 0, and its ExecStopPost stop ended the instance)
#   restart  `systemctl --user restart`: the unit's hub is the instance's only one
# The unit is this instance's own (apklayer-instance@<id>.service with the test's environment), removed at the end.
cd "$(dirname "$0")/../.."
inst=${APKLAYER_INSTANCE:-default}
state="${XDG_STATE_HOME:-$HOME/.local/state}/apklayer/$inst"
sc() { XDG_RUNTIME_DIR=/run/user/$(id -u) DBUS_SESSION_BUS_ADDRESS=unix:path=/run/user/$(id -u)/bus systemctl --user "$@"; }
confdir=$(sc show-environment | sed -n 's/^XDG_CONFIG_HOME=//p'); unitfile="${confdir:-$HOME/.config}/systemd/user/apklayer-instance@$inst.service"
unit=apklayer-instance@$inst.service
cli="$PWD/b/cli/apklayer"
marker_was=$([ -e "$state/autostart" ] && echo 1)
mkdir -p "$(dirname "$unitfile")" "$state"
{
    printf '[Service]\nType=simple\n'
    for v in APKLAYER_NATIVE APKLAYER_PREBUILTS APKLAYER_BINDER APKLAYER_SANDBOX APKLAYER_PERMISSIONS XDG_DATA_HOME XDG_STATE_HOME XDG_CACHE_HOME XDG_CONFIG_HOME \
             XDG_RUNTIME_DIR WAYLAND_DISPLAY DBUS_SESSION_BUS_ADDRESS PULSE_SERVER; do
        [ -n "${!v}" ] && printf 'Environment="%s=%s"\n' "$v" "${!v}"
    done
    printf 'ExecStart=%s --instance %%i hub\nExecStopPost=%s --instance %%i stop\nSuccessExitStatus=143\n' "$cli" "$cli"
} > "$unitfile"
echo 1 > "$state/autostart"  # what `instance autostart on` writes: the hub never exits when idle
cleanup() {
    sc stop "$unit"; rm -f "$unitfile"; sc daemon-reload
    [ -n "$marker_was" ] || rm -f "$state/autostart"
    bin/apklayer stop
}
trap cleanup EXIT
fail() { echo "FAIL $*"; sc status "$unit" --no-pager -n 20 | cut -c1-200; exit 1; }
# the instance's hub JVMs (their environment names the instance)
hubs() { for p in $(pgrep -f '[a]pklayer.host.Main --hub'); do tr '\0' '\n' < /proc/$p/environ 2>/dev/null | grep -qx "APKLAYER_INSTANCE=$inst" && echo "$p"; done | wc -l; }
owned() {  # active, the hub's launcher (hub.pid's second line) is the unit's main process, one hub JVM
    [ "$(sc is-active "$unit")" = active ] || fail "$1: the unit is $(sc is-active "$unit")"
    main=$(sc show -p MainPID --value "$unit"); launcher=$(sed -n 2p "$state/hub.pid")
    [ "$main" = "$launcher" ] || fail "$1: hub.pid's launcher $launcher is not the unit's $main"
    [ "$(hubs)" = 1 ] || fail "$1: $(hubs) hub JVMs"
}

bin/apklayer stop
sc daemon-reload
sc start "$unit" && bin/apklayer wait hub || fail "the unit's hub did not start"
owned start

sc stop "$unit"
setsid bin/apklayer hub > "$state/stray-hub.log" 2>&1 < /dev/null &
stray=$!
bin/apklayer wait hub || fail "the stray hub did not start"
sc start "$unit"
wait "$stray"  # ended by the unit's hub (before the fix: by the unit's ExecStopPost, the unit deactivating)
[ "$(sc is-active "$unit")" = active ] || fail "stray: the unit is $(sc is-active "$unit") once the stray hub ended"
bin/apklayer wait hub || fail "stray: no hub after the takeover"
owned stray
echo "PASS stray: the unit's hub replaced a hub launched outside it"

sc restart "$unit" && bin/apklayer wait hub || fail "restart: no hub"
owned restart
echo "PASS restart: one hub, the unit's"
