#!/usr/bin/env python3
"""Collects the hello-identity probe lines for the runtime identity matrix (docs/runtime-identity-matrix.md).

  collect.py apklayer OUT       tests/run identity-matrix on the VM of APKLY_VM_CONF (own APKLY_VM_ROOT/APKLY_SLOTS), lines into OUT
  collect.py apklayer OUT --runtime DIR
                                the same app against an already built tree DIR on the VM (a VM without room for its own build): pushes this
                                tree to APKLY_VM_ROOT for the scripts, installs and runs in instance APKLY_SLOTS[0] with cwd DIR; nothing is built
  collect.py waydroid OUT       Android reference: the host of APKLY_VM_CONF runs Waydroid. Starts the session only for the run, installs the
                                x86_64 build as a real app, launches it, reads files/identity.txt, removes the app, stops the session and restores
                                the container state and binder nodes it found.
Then tests/identity/compare.py joins the files."""
import argparse
import os
import re
import selectors
import shlex
import subprocess
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
import vm  # noqa: E402

REPO = vm.REPO
PKG = 'hello.identity'


def lines_of(text):
    out = []
    for line in text.splitlines():
        if '[identity] ' in line:
            item = line[line.index('[identity] ') + 11:]
            if item != 'END':
                out.append(item)
    return out


def build(abi=None):
    name = 'hello-identity' + ('-' + abi if abi else '')
    apk = REPO / 'build' / (name + '.apk')
    env = dict(os.environ, APP_ABI=abi or '')
    subprocess.run(['tests/apps/build.sh', 'hello-identity', str(apk)], cwd=REPO, env=env, check=True, capture_output=True)
    return apk


def stream(command, until, seconds):
    """Runs a VM command and reads its output until a line contains `until` (or it ends); kills it after `seconds`. Returns the text."""
    p = vm.popen(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    text, deadline = b'', time.monotonic() + seconds
    with selectors.DefaultSelector() as sel:
        sel.register(p.stdout, selectors.EVENT_READ)
        while time.monotonic() < deadline:
            if not sel.select(3):
                continue
            chunk = os.read(p.stdout.fileno(), 65536)
            if not chunk:
                break
            text += chunk
            if until.encode() in text:
                break
    if p.poll() is None:
        p.kill()
    p.wait()
    return text.decode(errors='replace')


def apklayer(out, runtime):
    if not runtime:
        r = subprocess.run([str(REPO / 'tests/run'), 'identity-matrix'], cwd=REPO)
        log = (REPO / 'build/tests/identity-matrix.log').read_text(errors='replace')
        Path(out).write_text('\n'.join(lines_of(log)) + '\n')
        return r.returncode
    slot = (vm.config.SLOTS or ['mx1'])[0]
    vm.push()
    vm.copy([build()], vm.WORK + '/apks')
    env = vm.instance_env(slot) + 'cd %s && export APKLAYER_NATIVE=%s/inst/lib/apklayer/native; ' % (runtime, runtime)
    install = vm.ssh(env + 'bin/apklayer install %s/apks/hello-identity.apk 2>&1 | tail -2; bin/apklayer stop' % vm.WORK,
                     capture_output=True, text=True)
    print(install.stdout.strip())
    text = stream(env + 'python3 -u %s/tests/vm/identity-matrix.py 2>&1' % vm.ROOT, 'PASS identity probes', 150)
    if 'PASS identity probes' not in text:  # the run's output can miss the app's lines; the instance's app log has them
        text = vm.ssh(env + 'cat "${XDG_STATE_HOME:-$HOME/.local/state}/apklayer/%s/app-hello.identity.log"' % slot,
                      capture_output=True, text=True).stdout
        text += '\nPASS identity probes (app log)' if '[identity] END' in text else ''
    Path(out).write_text('\n'.join(lines_of(text)) + '\n')
    print('\n'.join(text.splitlines()[-1 if 'PASS identity probes' in text else -40:]) if text else 'no output')
    return 0 if 'PASS identity probes' in text else 1


ADD_NODES = '''
import fcntl, os, struct
for name in ("anbox-binder", "anbox-hwbinder", "anbox-vndbinder"):
    if not os.path.exists("/dev/binderfs/" + name):
        with open("/dev/binderfs/binder-control", "rb") as control:
            fcntl.ioctl(control, 0xC1086201, bytearray(name.encode().ljust(256, b"\\0") + bytes(8)))
    if not os.path.lexists("/dev/" + name):  # the session (the desktop user) opens them: 0666 as Waydroid makes them
        os.symlink("/dev/binderfs/" + name, "/dev/" + name)
    os.chmod("/dev/binderfs/" + name, 0o666)
'''


def snapshot():
    """Waydroid's container state, the binderfs nodes and the /dev binder links (name -> target)."""
    text = vm.ssh('sudo -n waydroid status 2>&1 | grep -i container; echo NODES $(ls /dev/binderfs); '
                  'for l in /dev/*binder*; do [ -L "$l" ] && echo "LINK $l $(readlink $l)"; done', capture_output=True, text=True).stdout
    nodes = next((l.split()[1:] for l in text.splitlines() if l.startswith('NODES')), [])
    links = {l.split()[1]: l.split()[2] for l in text.splitlines() if l.startswith('LINK ')}
    return text, set(nodes), links


def android(command):
    """A shell command inside the running Waydroid container, as root with Android's environment (what `waydroid shell` sets)."""
    setup = ('eval "$(sed -n \'s/^export \\([A-Z_]*\\) /export \\1=/p\' /data/system/environ/classpath)"; '
             'export PATH=/product/bin:/apex/com.android.runtime/bin:/apex/com.android.art/bin:/system_ext/bin:/system/bin:/system/xbin:/vendor/bin '
             'ANDROID_ROOT=/system ANDROID_DATA=/data ANDROID_ART_ROOT=/apex/com.android.art ANDROID_I18N_ROOT=/apex/com.android.i18n '
             'ANDROID_TZDATA_ROOT=/apex/com.android.tzdata ANDROID_RUNTIME_ROOT=/apex/com.android.runtime; ')
    return 'sudo -n lxc-attach -P /var/lib/waydroid/lxc -n waydroid --clear-env -- /system/bin/sh -c %s' % shlex.quote(setup + command)


def waydroid(out):
    state, nodes, links = snapshot()
    print('before:\n' + state)
    apk = build('x86_64')
    vm.copy([apk], '$HOME/apklayer-x86/matrix/apks')
    user = 'XDG_RUNTIME_DIR=/run/user/$(id -u) WAYLAND_DISPLAY=wayland-0 DBUS_SESSION_BUS_ADDRESS=unix:path=/run/user/$(id -u)/bus'
    data = '$HOME/.local/share/waydroid/data'  # the container's /data (waydroid.prop host_data_path)
    try:
        # Waydroid's binder nodes (waydroid.cfg: anbox-binder, -hwbinder, -vndbinder) through binder-control's BINDER_CTL_ADD, linked into /dev
        vm.ssh("sudo -n python3 -c " + shlex.quote(ADD_NODES))
        # the session runs detached (its own session id: ssh's end does not stop it); it starts the container
        vm.ssh('%s setsid -f waydroid session start > $HOME/apklayer-x86/matrix/waydroid-session.log 2>&1 < /dev/null' % user)
        # boot: the events buffer's boot_progress_enable_screen (logcat replays the buffer, then follows)
        # (logcat -m 1 returns at the first match; a remote command a local kill leaves behind would keep the container busy)
        print(vm.ssh(android('logcat -b events -v brief -e boot_progress_enable_screen -m 1'), capture_output=True, text=True, timeout=300).stdout[-200:])
        vm.ssh('sudo -n cp $HOME/apklayer-x86/matrix/apks/%s %s/local/tmp/identity.apk' % (apk.name, data))
        print(vm.ssh(android('pm install -r /data/local/tmp/identity.apk; rm /data/local/tmp/identity.apk; logcat -c'),
                     capture_output=True, text=True, timeout=120).stdout)
        # the app prints END when its file is written
        vm.ssh(android('am start -n %s/.Main' % PKG))
        print(vm.ssh(android('logcat -v raw -s System.out -m 1 -e "identity. END"'), capture_output=True, text=True, timeout=120).stdout[-200:])
        text = vm.ssh(android('cat /data/data/%s/files/identity.txt' % PKG), capture_output=True, text=True).stdout
        Path(out).write_text(text)
        print('%d lines' % len(text.splitlines()))
    finally:
        vm.ssh(android('am force-stop %s; pm uninstall %s' % (PKG, PKG)), capture_output=True)
        vm.ssh(android('rm -f /data/local/tmp/identity.apk'), capture_output=True)
        vm.ssh('%s waydroid session stop' % user, capture_output=True)
        vm.ssh('rm -f $HOME/apklayer-x86/matrix/apks/%s' % apk.name)
        if 'STOPPED' in state:
            vm.ssh('sudo -n waydroid container stop', capture_output=True)
        after, after_nodes, after_links = snapshot()
        for node in sorted(after_nodes - nodes):  # binder nodes this run made
            if re.fullmatch(r'[\w-]+', node):
                vm.ssh('sudo -n rm -f /dev/binderfs/%s' % node)
        for link in sorted(set(after_links) - set(links)):
            vm.ssh('sudo -n rm -f %s' % shlex.quote(link))
        print('after:\n' + snapshot()[0])
    return 0


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('mode', choices=['apklayer', 'waydroid'])
    parser.add_argument('out')
    parser.add_argument('--runtime')
    args = parser.parse_args()
    return apklayer(args.out, args.runtime) if args.mode == 'apklayer' else waydroid(args.out)


if __name__ == '__main__':
    sys.exit(main())
