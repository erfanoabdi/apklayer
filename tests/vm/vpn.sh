#!/bin/bash
# The host-wide VPN (docs/VPN.md): hello.vpn (VpnService) asks the person (the hub's dialog, answered here) and establishes a TUN on the host through
# NetworkManager (no password: this runs in the user's session; the hub's `apklayer` is apklayerd's child) with 10.88.0.1/24 and that prefix only.
#  unit:    the device is the user's, has the address and the route, the host's default route is untouched, `ping 10.88.0.2` and a UDP datagram of the host are
#           answered by the app through the TUN, `apklayer vpn status`, the resolver has the app's DNS; `apklayer vpn disconnect` revokes the app (onRevoke)
#           and the device, connection and route are gone.
#  crash:   the app is killed: its descriptor closes, the guard sees the carrier go, the hub removes the VPN (device and connection gone, status "no VPN").
#  hubcrash: the hub is killed (SIGKILL): the guard's pipe closes and it removes the VPN.
#  default: the default route through the TUN (0.0.0.0/1 + 128.0.0.0/1) with the ssh peer's /32 and a watchdog (systemd timer, 60 s) that deletes the connection
#           if this script does not finish; the active network is the VPN, an unprotected socket's datagram goes into the TUN, a protect()ed one does not;
#           the host's `ip route` is the same afterwards.
# On the VM, in the group's instance (tests/run vpn): APKLAYER_INSTANCE, APKLY_STATE (its logs); sudo -n for the peer route of the default phase only.
set -uo pipefail
inst=${APKLAYER_INSTANCE:-default}
: "${APKLY_STATE:=${XDG_STATE_HOME:-$HOME/.local/state}/apklayer/$inst}"
out=$APKLY_STATE/vpn-test.out
con="apklayer-vpn-$inst-"
watchdog="apklayer-vpn-watchdog-$inst"
peerroute=""
sd() { XDG_RUNTIME_DIR=/run/user/$(id -u) "$@"; }  # the user manager's bus: the instance's XDG_RUNTIME_DIR is not where it is
user() { sd systemd-run --user --pipe --wait -q "$@"; }  # NetworkManager's polkit rules: the user's session, not this ssh login
cleanup() {
    bin/apklayer stop >/dev/null 2>&1
    bin/apklayer vpn sweep >/dev/null 2>&1
    for c in a b; do user nmcli connection delete id "$con$c" >/dev/null 2>&1; done
    sd systemctl --user stop "$watchdog.timer" "$watchdog.service" >/dev/null 2>&1
    [ -z "$peerroute" ] || sudo -n ip route del $peerroute >/dev/null 2>&1
}
trap cleanup EXIT
fail() { echo "FAIL $*"; tail -5 "$out.answer" 2>/dev/null; grep -a "vpn" "$APKLY_STATE/hub.log" 2>/dev/null | tail -15; tail -12 "$out" 2>/dev/null; exit 1; }
nmconn() { user nmcli -t -f NAME connection show | grep -c "^$con"; }
start_owner() {  # phase: the app runs, its output in $out; returns once it waits for the revoke
    APKLAYER_JOPTS="-Dvpn.phase=$1 -Dapklayer.runms=150000" bin/apklayer run hello.vpn > "$out" 2>&1 & owner=$!
    for _ in $(seq 150); do grep -aq "waiting for revoke\|\[vpn\] FAIL" "$out" && break; sleep 1; done
    grep -a "^\[vpn\]" "$out"
    grep -aq "waiting for revoke" "$out" || fail "$1: the owner did not reach its revoke phase"
}
finish_owner() {  # the app ends with its run time (150 s) otherwise: once it printed its result the instance stops
    for _ in $(seq 20); do grep -aq "^\[vpn\] \(PASS\|FAIL\)" "$out" && break; sleep 1; done
    bin/apklayer stop >/dev/null 2>&1; wait $owner
}
vpniface() { bin/apklayer vpn status | cut -f5; }

bin/apklayer stop >/dev/null 2>&1
rm -rf "$APKLY_STATE/vpn"  # no consent from an earlier run: the dialog is asked
[ "$(bin/apklayer vpn status 2>&1)" = "no VPN" ] || fail "a VPN before the test"
[ "$(nmconn)" = 0 ] || fail "a VPN connection before the test"
routes=$(ip route)

# ---- unit ----
python3 -u tests/vm/answer-dialog.py yes 90 > "$out.answer" 2>&1 & answer=$!
start_owner unit
wait $answer || fail "the consent question was not answered: $(tail -1 "$out.answer")"
grep -aq 'ANSWER yes "Allow Hello VPN to set up a VPN connection?"' "$out.answer" || fail "the consent question was not 'Allow Hello VPN to set up a VPN connection?': $(cat "$out.answer")"
grep -qx hello.vpn "$APKLY_STATE/vpn/consent" || fail "the consent was not remembered"
status=$(bin/apklayer vpn status); echo "$status"
printf '%s' "$status" | grep -aq "^VPN	hello.vpn	hello-vpn	since .*	tunapk[0-9a-f]*[ab]	some routes$" || fail "vpn status: $status"
if=$(vpniface)
ip -d link show "$if" | grep -q "tun type tun.* user $(id -un)" || fail "$if is not a TUN owned by $(id -un): $(ip -d link show "$if")"
ip -4 addr show dev "$if" | grep -q "inet 10.88.0.1/24" || fail "no address on $if"
ip route show 10.88.0.0/24 | grep -q "dev $if" || fail "no route to 10.88.0.0/24 on $if"
[ "$(ip route show default)" = "$(printf '%s\n' "$routes" | grep '^default')" ] || fail "the default route changed: $(ip route show default)"
[ "$(nmconn)" = 1 ] || fail "NetworkManager has no connection of the VPN"
ping -c 2 -W 2 10.88.0.2 >/dev/null || fail "ping 10.88.0.2 is not answered through the TUN"
python3 - <<'EOF' || fail "UDP to 10.88.0.2:7777 is not echoed"
import socket
s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
s.settimeout(3)
s.sendto(b"hello-from-the-host", ("10.88.0.2", 7777))
data, peer = s.recvfrom(100)
assert data == b"hello-from-the-host" and peer == ("10.88.0.2", 7777), (data, peer)
EOF
if command -v resolvectl >/dev/null; then resolvectl status "$if" | grep -q "DNS Servers: 10.88.0.53" || fail "the resolver has no DNS of the VPN on $if: $(resolvectl status "$if" 2>&1)"; fi
bin/apklayer vpn disconnect || fail "vpn disconnect"
finish_owner
grep -aq "^\[vpn\] onRevoke" "$out" && grep -aq "^\[vpn\] PASS" "$out" || fail "the owner was not revoked"
[ "$(bin/apklayer vpn status)" = "no VPN" ] || fail "vpn status after disconnect"
python3 tests/vm/vpn-link.py absent tunapk 15 || fail "the TUN $if is still there after disconnect"
[ "$(nmconn)" = 0 ] || fail "the connection is still there after disconnect"
[ "$(ip route)" = "$routes" ] || fail "the routes are not the same after disconnect: $(ip route)"
echo "unit ok"

# ---- crash ----
start_owner crash
if=$(vpniface)
tests/vm/kill-instance.sh "$inst" '[h]ello\.vpn'
python3 tests/vm/vpn-link.py absent tunapk 20 || fail "the TUN $if is still there after the app was killed: $(ip -br link | grep tunapk) $(date +%T)"
wait $owner
[ "$(nmconn)" = 0 ] || fail "the connection is still there after the app was killed"
[ "$(ip route)" = "$routes" ] || fail "the routes are not the same after the app was killed: $(ip route)"
[ "$(bin/apklayer vpn status)" = "no VPN" ] || fail "vpn status after the app was killed"
echo "crash ok"

# ---- hub crash ----
start_owner crash
if=$(vpniface)
kill -9 $(cat "$APKLY_STATE/hub.pid")  # the hub dies without a word: its guard sees its pipe close and removes the VPN
python3 tests/vm/vpn-link.py absent tunapk 20 || fail "the TUN $if is still there after the hub was killed"
[ "$(nmconn)" = 0 ] || fail "the connection is still there after the hub was killed"
[ "$(ip route)" = "$routes" ] || fail "the routes are not the same after the hub was killed: $(ip route)"
bin/apklayer stop >/dev/null 2>&1; wait $owner
echo "hub crash ok"

# ---- default route ----
peer=${SSH_CLIENT%% *}
gateway=$(ip route show default | awk '{print $3; exit}')
device=$(ip route show default | awk '{print $5; exit}')
if [ -n "$peer" ] && [ -n "$gateway" ]; then
    peerroute="$peer/32 via $gateway dev $device"
    sudo -n ip route add $peerroute || fail "cannot add the route to the ssh peer $peer"
fi
routes=$(ip route)
sd systemd-run --user --quiet --unit="$watchdog" --on-active=60 sh -c "nmcli connection delete id ${con}a; nmcli connection delete id ${con}b" || fail "cannot arm the watchdog"
start_owner default
if=$(vpniface)
status=$(bin/apklayer vpn status); echo "$status"
printf '%s' "$status" | grep -aq "all traffic$" || fail "vpn status: $status"
ip route show | grep -q "^0.0.0.0/1 dev $if" && ip route show | grep -q "^128.0.0.0/1 dev $if" || fail "no 0.0.0.0/1 and 128.0.0.0/1 routes on $if: $(ip route)"
if command -v resolvectl >/dev/null; then resolvectl status "$if" | grep -q "DNS Servers: 10.88.0.53" && resolvectl status "$if" | grep -q "Default Route: yes" || fail "the resolver does not use the VPN's DNS for everything on $if: $(resolvectl status "$if" 2>&1)"; fi
ip route get 8.8.8.8 | grep -q "dev $if" || fail "8.8.8.8 is not routed into the TUN: $(ip route get 8.8.8.8)"
[ -z "$peer" ] || ip route get "$peer" | grep -q "dev $device" || fail "the ssh peer $peer is not reached by $device: $(ip route get "$peer")"
bin/apklayer vpn disconnect || fail "vpn disconnect"
finish_owner
grep -aq "^\[vpn\] PASS" "$out" || fail "the default phase of the owner failed"
python3 tests/vm/vpn-link.py absent tunapk 15 || fail "the TUN is still there after disconnect"
sd systemctl --user stop "$watchdog.timer" "$watchdog.service" >/dev/null 2>&1
[ "$(ip route)" = "$routes" ] || fail "the routes are not the same after the default phase: $(ip route)"
echo "default ok"
echo "PASS vpn"
