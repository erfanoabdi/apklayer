"""Screenshot of the test VM's desktop into a local PNG: through flameshot in its GNOME session."""
import os
import subprocess
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import config


def capture(image, vm=None, password=None):
    vm = vm or config.VM
    password = password or config.PASS
    remote = '/tmp/apklayer-vmcapture.png'
    ssh = ['sshpass', '-p', password, 'ssh', '-o', 'StrictHostKeyChecking=no', vm]
    subprocess.run(ssh + ['export XDG_RUNTIME_DIR=/run/user/1000 DBUS_SESSION_BUS_ADDRESS=unix:path=/run/user/1000/bus WAYLAND_DISPLAY=wayland-0 '
                          'XDG_SESSION_TYPE=wayland XDG_CURRENT_DESKTOP=GNOME; rm -f ' + remote + '; flameshot full -p ' + remote
                          + ' >/dev/null 2>&1'], check=True)
    subprocess.run(['sshpass', '-p', password, 'scp', '-q', '-o', 'StrictHostKeyChecking=no', vm + ':' + remote, image], check=True)
