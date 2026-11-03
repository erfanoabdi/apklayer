"""Screenshot of the test VM's desktop into a local PNG: through flameshot in its GNOME session."""
import os
import subprocess
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import config


def capture(image, vm=None, password=None):
    vm = vm or config.VM
    remote = '/tmp/apklayer-vmcapture.png'
    ssh = config.auth('ssh', password) + [vm]
    subprocess.run(ssh + ['export XDG_RUNTIME_DIR=/run/user/1000 DBUS_SESSION_BUS_ADDRESS=unix:path=/run/user/1000/bus WAYLAND_DISPLAY=wayland-0 '
                          'XDG_SESSION_TYPE=wayland XDG_CURRENT_DESKTOP=GNOME; rm -f ' + remote + '; flameshot full -p ' + remote
                          + ' >/dev/null 2>&1'], check=True)
    subprocess.run(config.auth('scp', password) + ['-q', vm + ':' + remote, image], check=True)


def app_capture(image, target, env='', cli='~/apklayer/b/cli/apklayer', vm=None, password=None):
    """One app window as SurfaceFlinger composes it (`apklayer screenshot <package|stack>`), independent of the desktop and of other instances.
    env: shell assignments for the instance (APKLAYER_INSTANCE=... XDG_*=...). Returns False when no such window or capture exists (callers fall back to capture())."""
    vm = vm or config.VM
    remote = '/tmp/apklayer-appcapture-%d.png' % os.getpid()
    ssh = config.auth('ssh', password) + [vm]
    if subprocess.run(ssh + ['%s %s screenshot %s %s >/dev/null 2>&1' % (env, cli, target, remote)]).returncode != 0: return False
    ok = subprocess.run(config.auth('scp', password) + ['-q', vm + ':' + remote, image]).returncode == 0
    subprocess.run(ssh + ['rm -f ' + remote])
    return ok


if __name__ == '__main__':  # vmcapture.py OUT.png (the desktop)   or   vmcapture.py OUT.png PACKAGE|STACK [ENV...] (one app window)
    if len(sys.argv) > 2: sys.exit(0 if app_capture(sys.argv[1], sys.argv[2], ' '.join(sys.argv[3:])) else 1)
    capture(sys.argv[1])
