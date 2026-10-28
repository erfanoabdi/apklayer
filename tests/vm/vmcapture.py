"""Screenshot of the test VM's desktop into a local PNG: Parallels through prlctl, any other VM (UTM) through flameshot in its GNOME session."""
import os
import subprocess

PARALLELS_NAME = os.environ.get('APKLAYER_VM_NAME', 'Ubuntu 24.04 (with Rosetta)')


def capture(image, vm=None, password=None):
    vm = vm or os.environ.get('APKLY_VM', 'utm@192.168.64.7')
    password = password or os.environ.get('APKLY_VM_PASS', '1234')
    if vm.startswith('parallels@'):
        subprocess.run(['prlctl', 'capture', PARALLELS_NAME, '--file', image], check=True)
        return
    remote = '/tmp/apklayer-vmcapture.png'
    ssh = ['sshpass', '-p', password, 'ssh', '-o', 'StrictHostKeyChecking=no', vm]
    subprocess.run(ssh + ['export XDG_RUNTIME_DIR=/run/user/1000 DBUS_SESSION_BUS_ADDRESS=unix:path=/run/user/1000/bus WAYLAND_DISPLAY=wayland-0 '
                          'XDG_SESSION_TYPE=wayland XDG_CURRENT_DESKTOP=GNOME; rm -f ' + remote + '; flameshot full -p ' + remote
                          + ' >/dev/null 2>&1'], check=True)
    subprocess.run(['sshpass', '-p', password, 'scp', '-q', '-o', 'StrictHostKeyChecking=no', vm + ':' + remote, image], check=True)
