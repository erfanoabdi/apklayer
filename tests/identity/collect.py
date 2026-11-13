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
    Path(out).write_text('\n'.join(lines_of(text)) + '\n')
    print('\n'.join(text.splitlines()[-1 if 'PASS identity probes' in text else -40:]) if text else 'no output')
    return 0 if 'PASS identity probes' in text else 1


WAYDROID_STATE = 'sudo -n waydroid status 2>&1 | tr "\\t" " "; ls /dev/binderfs | tr "\\n" " "; echo; ls -la /dev | grep -i binder | awk "{print \\$NF}" | tr "\\n" " "'


def waydroid(out):
    before = vm.ssh(WAYDROID_STATE, capture_output=True, text=True).stdout
    print('before:\n' + before)
    apk = build('x86_64')
    vm.copy([apk], '$HOME/apklayer-x86/matrix/apks')
    user = 'XDG_RUNTIME_DIR=/run/user/$(id -u) WAYLAND_DISPLAY=wayland-0 DBUS_SESSION_BUS_ADDRESS=unix:path=/run/user/$(id -u)/bus'
    started = 'Session:	RUNNING' not in before.replace(' ', '\t') or 'Container: STOPPED' in before
    try:
        # the session start command returns when the session is up; -w on later commands waits for Android's init
        print(stream('%s waydroid session start 2>&1' % user, 'Android with user 0 is ready', 180)[-600:])
        print(vm.ssh('sudo -n waydroid -w shell -- getprop sys.boot_completed; sudo -n waydroid app install '
                     '$HOME/apklayer-x86/matrix/apks/%s 2>&1 | tail -2' % apk.name, capture_output=True, text=True).stdout)
        # the app prints END when its file is written; logcat -m 1 returns at that line
        vm.ssh('sudo -n waydroid shell -- logcat -c', capture_output=True)
        launch = vm.popen('sudo -n waydroid shell -- am start -W -n %s/.Main 2>&1' % PKG, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        print(stream('sudo -n waydroid shell -- logcat -v raw -s System.out', '[identity] END', 120)[-200:])
        launch.wait()
        text = vm.ssh('sudo -n waydroid shell -- cat /data/data/%s/files/identity.txt' % PKG, capture_output=True, text=True).stdout
        Path(out).write_text(text)
        print('%d lines' % len(text.splitlines()))
    finally:
        vm.ssh('sudo -n waydroid shell -- am force-stop %s; sudo -n waydroid app remove %s; %s waydroid session stop' % (PKG, PKG, user),
               capture_output=True)
        vm.ssh('rm -f $HOME/apklayer-x86/matrix/apks/%s' % apk.name)
        after = vm.ssh(WAYDROID_STATE, capture_output=True, text=True).stdout
        print('after:\n' + after)
        if started:
            # binder nodes and /dev links that were not there before belong to this run
            nodes = set(before.splitlines()[-2].split()) if len(before.splitlines()) > 1 else set()
            for node in set(after.splitlines()[-2].split()) - nodes if len(after.splitlines()) > 1 else []:
                if re.fullmatch(r'[\w-]+', node) and node not in ('binder-control', 'features'):
                    vm.ssh('sudo -n rm -f /dev/binderfs/%s' % shlex.quote(node))
            links = set(before.splitlines()[-1].split())
            for link in set(after.splitlines()[-1].split()) - links:
                pass  # link targets only; reported in the after state for a manual look
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
