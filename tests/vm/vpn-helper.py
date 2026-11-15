#!/usr/bin/env python3
"""The polkit helper of the host-wide VPN (cli/vpn_helper.cpp), run as root through `sudo -n env PKEXEC_UID=<uid>` (what pkexec does; the password dialog itself needs a
person): bad input is refused without touching the host, a good spec makes a TUN owned by the user with MTU, address and route (metric 50) which the user opens
without privilege, a name that exists is never removed, the device goes when the pipe closes, and with --watchdog when no "confirm" comes. Uses tunapkhelper* only."""
import fcntl
import os
import struct
import subprocess
import sys
import time
from pathlib import Path

HELPER = Path(__file__).resolve().parents[2] / 'inst/lib/apklayer/cli/apklayer-vpn-helper'  # tests/run's meson install
UID = str(os.getuid())
LINK = Path(__file__).with_name('vpn-link.py')


def start(*args, uid=UID):
    env = {'PKEXEC_UID': uid} if uid else {}
    return subprocess.Popen(['sudo', '-n', 'env', '-i'] + ['%s=%s' % kv for kv in env.items()] + [str(HELPER)] + list(args), stdin=subprocess.PIPE,
                            stdout=subprocess.PIPE, text=True)


def say(p, text):
    p.stdin.write(text)
    p.stdin.flush()


def good(name='tunapkhelper1', extra=''):
    return 'ifname %s\nmtu 1400\naddr 10.89.0.1/24\nroute 10.89.1.0/24\ndns 10.89.0.53\n%sgo\n' % (name, extra)


def ip(*args):
    return subprocess.run(['ip'] + list(args), capture_output=True, text=True).stdout


def gone(prefix='tunapkhelper', seconds=10):
    return subprocess.run([sys.executable, str(LINK), 'absent', prefix, str(seconds)]).returncode == 0


def check(ok, what):
    if not ok:
        print('FAIL', what)
        subprocess.run(['sudo', '-n', 'ip', 'link', 'del', 'tunapkhelper1'], capture_output=True)
        sys.exit(1)


# bad input: an answer "error ...", the exit status 1, no device
for bad in ('ifname eth0\ngo\n', 'ifname tunapkhelper1\naddr 1.2.3.4/33\ngo\n', 'ifname tunapkhelper1\nroute 10.0.0.0/8; reboot\ngo\n', 'ifname tunapkhelper1\ndns notanip\ngo\n',
            'ifname tunapkhelper1\nmtu 10\ngo\n', 'ifname tunapkhelper1\nunknown 1\ngo\n', 'go\n', 'ifname tunapk../x\ngo\n'):
    p = start()
    say(p, bad)
    out = p.stdout.read()
    check(p.wait() == 1 and out.startswith('error'), 'bad input %r not refused: %r' % (bad, out))
check(not Path('/sys/class/net/tunapkhelper1').exists(), 'a bad spec made a device')
p = start(uid='')
say(p, good())
check(p.wait() == 1 and p.stdout.read().startswith('error'), 'ran without PKEXEC_UID')
p = start(uid='0')
say(p, good())
check(p.wait() == 1, 'ran for uid 0')
p = start('--watchdog', '0')
check(p.wait() == 2, 'a bad --watchdog was accepted')

# a good spec
p = start()
say(p, good())
check(p.stdout.readline().strip() == 'ready', 'no ready')
detail = ip('-d', 'addr', 'show', 'tunapkhelper1')
link = ip('-d', 'link', 'show', 'tunapkhelper1')
check('tun type tun' in link and ' user %s ' % os.environ.get('USER', '?') in link, 'not a TUN of the user: ' + link)
check('mtu 1400' in detail and '10.89.0.1/24' in detail, 'mtu or address missing: ' + detail)
check('dev tunapkhelper1' in ip('route', 'show', '10.89.1.0/24') and 'metric 50' in ip('route', 'show', '10.89.1.0/24'), 'route missing: ' + ip('route', 'show'))
fd = os.open('/dev/net/tun', os.O_RDWR)
fcntl.ioctl(fd, 0x400454ca, struct.pack('16sH', b'tunapkhelper1', 0x1001))  # the user attaches without privilege
os.close(fd)
# a name that exists is not touched by a second helper
q = start()
say(q, good())
check(q.wait() == 1 and q.stdout.read().startswith('error'), 'the second helper took an existing name')
check(Path('/sys/class/net/tunapkhelper1').exists(), 'the second helper removed the first one')
p.stdin.close()  # the hub's pipe closes: the device goes
check(p.wait() == 0 and gone(), 'the device stays after the pipe closed')

# the watchdog: no confirm, it goes; a confirm, it stays until the pipe closes
p = start('--watchdog', '2')
say(p, good())
check(p.stdout.readline().strip() == 'ready', 'no ready with a watchdog')
check(gone(seconds=8) and p.wait() == 0, 'the watchdog did not remove the device')
p = start('--watchdog', '2')
say(p, good())
check(p.stdout.readline().strip() == 'ready', 'no ready with a confirmed watchdog')
say(p, 'confirm\n')
time.sleep(3)  # longer than the watchdog: the device must stay (this is the one wait that proves a negative)
check(Path('/sys/class/net/tunapkhelper1').exists(), 'a confirmed device was removed by the watchdog')
p.stdin.close()
check(p.wait() == 0 and gone(), 'the confirmed device stays after the pipe closed')
# SIGTERM removes it too
p = start()
say(p, good())
check(p.stdout.readline().strip() == 'ready', 'no ready before the signal')
subprocess.run(['sudo', '-n', 'pkill', '-TERM', '-f', '[a]pklayer-vpn-helper'])
check(p.wait() == 0 and gone(), 'SIGTERM left the device')
print('PASS vpn-helper')
