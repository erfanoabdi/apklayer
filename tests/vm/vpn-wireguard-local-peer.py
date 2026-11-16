#!/usr/bin/env python3
"""tests/vm/vpn-wireguard-local-peer.py [--apk ~/corpus-wireguard.apk] [slot]: the real WireGuard app against a throwaway peer on a LAN host (docs/VPN.md "Tests").
Mac side. Not a tests/run group (it needs a second machine and the WireGuard APK): run `tests/run vpn` first, it pushes and builds the tree on the VM.
The peer (APKLY_VPN_PEER, default the owner's PC: ssh, passwordless sudo, `wg`) gets a `wgapk0` interface with fresh keys in a directory of its own, the app's
configuration is piped to the VM (the private keys never print), tests/vm/vpn-wireguard.py drives the app, the peer's last handshake is compared with the moment
the VPN was established (WireGuard's first datagram must not go into its own tunnel: the handshake takes under a second, not the 5 s of a retry); everything on
both hosts is removed again. The VM's default route goes through the tunnel meanwhile: a 200 s systemd timer deletes the connection if this script dies."""
import argparse
import os
import re
import subprocess
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
import vm  # noqa: E402

ap = argparse.ArgumentParser()
ap.add_argument('--apk', default='$HOME/corpus-wireguard.apk')
ap.add_argument('slot', nargs='?', default=vm.config.SLOTS[0] if vm.config.SLOTS else 'tests')
args = ap.parse_args()
PEER = os.environ.get('APKLY_VPN_PEER', 'erfan@192.168.41.58')
ENDPOINT = PEER.split('@')[-1] + ':51821'
DIR = '~/apklayer-x86/vpn-wg-local-peer'  # the peer's scratch directory


def peer(command, **kw):
    return subprocess.run(['ssh', '-o', 'BatchMode=yes', PEER, command], text=True, capture_output=True, **kw)


def on_vm(command, **kw):
    return vm.ssh(vm.instance_env(args.slot) + command, text=True, **kw)


installed = []  # WireGuard was not there before: this test installs it and removes it again (an app of someone else stays)


def cleanup():
    on_vm('bin/apklayer stop >/dev/null 2>&1; bin/apklayer vpn sweep >/dev/null 2>&1; XDG_RUNTIME_DIR=/run/user/$(id -u) systemctl --user stop vpn-wg-watchdog.timer vpn-wg-watchdog.service 2>/dev/null; '
          'rm -f %s; %s' % (installed[1] if len(installed) > 1 else '/nonexistent', 'bin/apklayer uninstall com.wireguard.android >/dev/null 2>&1' if installed and installed[0] else 'true'), capture_output=True)
    peer('sudo ip link del wgapk0 2>/dev/null; rm -rf %s' % DIR)


status = 1
try:
    vm.push()  # this test's VM side (the build is tests/run vpn's)
    assert peer('set -e; mkdir -p %s; cd %s; umask 077; wg genkey > srv.key; wg pubkey < srv.key > srv.pub; wg genkey > cli.key; wg pubkey < cli.key > cli.pub; '
                'sudo ip link add wgapk0 type wireguard; sudo wg set wgapk0 listen-port 51821 private-key srv.key peer $(cat cli.pub) allowed-ips 10.99.0.2/32; '
                'sudo ip addr add 10.99.0.1/24 dev wgapk0; sudo ip link set wgapk0 up' % (DIR, DIR)).returncode == 0, 'the peer could not be set up'
    conf = peer('cd %s; printf "[Interface]\\nPrivateKey = %%s\\nAddress = 10.99.0.2/32\\nDNS = 10.99.0.1\\n\\n[Peer]\\nPublicKey = %%s\\nEndpoint = %s\\n'
                'AllowedIPs = 0.0.0.0/0\\nPersistentKeepalive = 25\\n" "$(cat cli.key)" "$(cat srv.pub)"' % (DIR, ENDPOINT)).stdout
    installed.append(on_vm('bin/apklayer info com.wireguard.android', capture_output=True).returncode != 0)
    r = on_vm('bin/apklayer install %s 2>&1 | tail -1; bin/apklayer info com.wireguard.android | grep -a "^dataDir"' % args.apk, capture_output=True)
    data = re.search(r'dataDir\t(\S+)', r.stdout)
    assert data, 'WireGuard is not installed: ' + r.stdout
    installed.append('%s/app/files/localpeer.conf' % data.group(1))
    on_vm('bin/apklayer stop >/dev/null 2>&1; d=%s/app/files; mkdir -p $d && umask 077 && cat > $d/localpeer.conf' % data.group(1), input=conf, capture_output=True)
    on_vm('XDG_RUNTIME_DIR=/run/user/$(id -u) systemd-run --user --on-active=200 --unit=vpn-wg-watchdog sh -c "nmcli connection delete id apklayer-vpn-$APKLAYER_INSTANCE-a; '
          'nmcli connection delete id apklayer-vpn-$APKLAYER_INSTANCE-b" >/dev/null 2>&1', capture_output=True)
    run = on_vm('python3 -u tests/vm/vpn-wireguard.py 10.99.0.1 2>&1 | grep -av "^\\[apklayer\\] view\\|^INJECT"', capture_output=True)
    print(run.stdout.strip())
    est = re.search(r'ESTABLISHED (\d+)', run.stdout)
    assert run.returncode == 0 and 'PASS vpn-wireguard' in run.stdout and est, 'the VM side failed'
    handshake = int(peer('sudo wg show wgapk0 latest-handshakes').stdout.split()[-1])
    delay = handshake - int(est.group(1))
    print('first handshake %d s after the VPN was established' % delay)
    assert -1 <= delay <= 3, 'the handshake took %d s: the first datagram went into the tunnel (a 5 s retry)' % delay
    print('PASS vpn-wireguard-local-peer')
    status = 0
finally:
    cleanup()
sys.exit(status)
