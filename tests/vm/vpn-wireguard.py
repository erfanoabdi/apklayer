#!/usr/bin/env python3
"""VM side of tests/vm/vpn-wireguard-local-peer.py (docs/VPN.md): the real WireGuard app (tunnel "localpeer", its conf already in the app's files directory, the peer
answering on the LAN) switched on through its UI, in this instance (tests/lib/instance.py). Checks, printing `ESTABLISHED <epoch>` for the driver's handshake check:
 - the VPN is established (default route as the two /1 halves through the TUN) without APKLAYER_RAW_SYSCALL_VIEW=off and without a directory made by hand (the Go
   library's raw mkdirat/bind at /data/data/<pkg>/cache/wireguard go through the path layer: raw_syscall.c, seccomp user notification);
 - a ping to the peer's tunnel address is answered through the TUN;
 - the connection deleted from outside (nmcli) ends the VPN and revokes the app (hub.log "onRevoke"), TUN and routes gone."""
import os
import subprocess
import sys
import time

sys.path.insert(0, 'tests/corpus')
import uidrive  # noqa: E402

PKG, TOGGLE = 'com.wireguard.android', (406, 96)  # the first tunnel's switch (the 448x797 window)
peer = sys.argv[1]  # the peer's tunnel address
state = os.environ['APKLY_STATE']


def sh(command):
    return subprocess.run(command, shell=True, capture_output=True, text=True).stdout.strip()


def until(test, seconds, step=0.25):
    end = time.monotonic() + seconds
    while time.monotonic() < end:
        if test(): return True
        time.sleep(step)
    return False


def fail(why):
    print('FAIL', why)
    print(sh('grep -a "vpn" %s/hub.log | grep -v "VPN_STATUS\\|notification" | tail -12' % state))
    print(sh('grep -av "view\\|Resources\\|apklayer.egl\\|^$\\|Click this" %s/app-%s.log | tail -40 | cut -c1-240' % (state, PKG)))
    sys.exit(1)


os.makedirs(state + '/vpn', exist_ok=True)
open(state + '/vpn/consent', 'w').write(PKG + '\n')
app = uidrive.App(PKG, 120)
app.settle(40, 4)
app.text('localpeer', 40)
if until(lambda: sh('bin/apklayer vpn status') != 'no VPN', 4):  # WireGuard brings back the tunnel that was up when it last ended: switch it off, then on
    app.tap(*TOGGLE)
    until(lambda: sh('bin/apklayer vpn status') == 'no VPN', 15) or fail('the tunnel did not switch off')
    time.sleep(2)
app.tap(*TOGGLE)
until(lambda: sh('bin/apklayer vpn status') != 'no VPN', 30) or fail('no VPN after the switch')
established = time.time()
status = sh('bin/apklayer vpn status')
print('ESTABLISHED %d' % established)
print(status)
iface = status.split('\t')[4]
until(lambda: sh('ip route show 0.0.0.0/1').count(iface), 5) or fail('no 0.0.0.0/1 route through %s: %s' % (iface, sh('ip route')))
'128.0.0.0/1 dev ' + iface in sh('ip route') or fail('no 128.0.0.0/1 route through ' + iface)
until(lambda: ' 0% packet loss' in sh('ping -c 2 -i 0.3 -W 3 ' + peer + ' | grep -a packets'), 12) or fail('the peer %s does not answer through the tunnel' % peer)
print('ping through the tunnel ok')
data = sh("bin/apklayer info %s | awk -F'\\t' '$1==\"dataDir\" {print $2}'" % PKG)
# the Go library's own mkdirat of cache/wireguard and its UAPI socket (bind) at /data/data/<pkg>: only mapped when raw mkdirat/bind/inotify_add_watch are
sock = '%s/app/cache/wireguard/%s.sock' % (data, iface)  # named after the interface
until(lambda: os.path.exists(sock), 5) or fail('the Go library made no UAPI socket at ' + sock)
log = open(state + '/app-%s.log' % PKG, errors='replace').read()
'UAPIOpen' not in log and 'UAPIListen' not in log or fail('the Go library failed at its UAPI socket: ' + '; '.join(l for l in log.splitlines() if 'UAPI' in l)[:300])
print('UAPI socket made through the raw syscalls')
connection = sh('XDG_RUNTIME_DIR=/run/user/$(id -u) systemd-run --user --pipe --wait -q nmcli -t -f NAME connection show | grep "^apklayer-vpn-"')
connection or fail('no NetworkManager connection of the VPN')
subprocess.run('XDG_RUNTIME_DIR=/run/user/$(id -u) systemd-run --user --pipe --wait -q nmcli connection delete id ' + connection, shell=True, capture_output=True)
until(lambda: sh('bin/apklayer vpn status') == 'no VPN', 5) or fail('the VPN did not end when its connection was deleted from outside')
'deleted from outside: onRevoke' in open(state + '/hub.log', errors='replace').read() or fail('the app was not revoked')
until(lambda: iface not in sh('ip -br link'), 10) or fail('the TUN %s is still there' % iface)
'dev ' + iface not in sh('ip route') or fail('routes of %s are still there' % iface)
print('PASS vpn-wireguard: established, answered, revoked after an outside delete')
app.proc.terminate()
