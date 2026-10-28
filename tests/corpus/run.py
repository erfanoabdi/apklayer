#!/usr/bin/env python3
"""Compatibility corpus runner (on the Mac, drives the test VM): for every app in apps.txt install it, launch it, wait for the first GPU frame the shell receives
(inotify on the VM, no timers beyond a patience limit), take a screenshot, stop it, and write tests/corpus/results.md.
Usage: tests/corpus/run.py [name ...]   (no names: every app)"""
import os
import re
import subprocess
import sys
import threading
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
sys.path.insert(0, str(ROOT / 'tests/vm'))
import config  # noqa: E402
import vmcapture  # noqa: E402

SSH = ['sshpass', '-p', config.PASS, 'ssh', '-o', 'StrictHostKeyChecking=no', config.VM]
STATE = '~/.local/state/apklayer/default'
ENV = 'cd ~/apklayer; export XDG_RUNTIME_DIR=/run/user/1000 WAYLAND_DISPLAY=wayland-0; '
PATIENCE = int(os.environ.get('CORPUS_PATIENCE', '90'))
CAUSES = ['UnsatisfiedLinkError', 'ClassNotFoundException', 'NoSuchMethodError', 'SecurityException', 'NullPointerException', 'Fatal signal', 'SIGSEGV', 'Exception', 'Error']


def ssh(command, **kw):
    return subprocess.run(SSH + [command], capture_output=True, text=True, **kw)


def cause(lines):
    for key in CAUSES:
        for line in lines:
            if key in line and 'symbol may need' not in line and 'cutils-trace' not in line:
                return line.strip()[:110].replace('|', '/')
    return '-'


def fetch(name, spec):
    if spec.startswith('tree:'):
        out = Path('/tmp') / (name + '.apk')
        with open(out, 'wb') as f:
            subprocess.run(['orb', '-m', 'ubuntu22-x86', 'cat', '/home/erfanabdi/los24/out/target/product/generic_arm64/' + spec[5:]], stdout=f, check=True)
        return out
    path = Path(os.path.expanduser(spec))
    if not path.is_absolute(): path = ROOT / path
    return path if path.exists() else None


def run_one(name, spec, kind):
    row = {'name': name, 'kind': kind, 'install': '-', 'launch': '-', 'frame': '-', 'note': '-'}
    apk = fetch(name, spec)
    if apk is None:
        row['note'] = 'no apk'; return row
    subprocess.run(['sshpass', '-p', config.PASS, 'scp', '-q', str(apk), config.VM + ':corpus-' + name + '.apk'], check=True)
    ssh(ENV + 'b/cli/apklayer stop >/dev/null 2>&1')
    install = ssh(ENV + 'b/cli/apklayer install ~/corpus-' + name + '.apk 2>&1')
    match = re.search(r'installed (\S+)', install.stdout)
    if not match:
        row['install'] = 'FAIL'; row['note'] = cause(install.stdout.splitlines()); return row
    row['install'] = 'ok'
    package = match.group(1)
    offset = int((ssh('stat -c %s ' + STATE + '/shell.log 2>/dev/null || echo 0').stdout.strip() or '0'))
    app = subprocess.Popen(SSH + [ENV + 'exec b/cli/apklayer run ' + package], stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, errors='replace')
    lines = []
    reader = threading.Thread(target=lambda: [lines.append(l) for l in app.stdout], daemon=True)
    reader.start()
    # a steady picture, not a flash: 20 frames, and the app must still be running when they arrived
    frame = ssh('python3 ~/apklayer/tests/corpus/first-frame.py ' + STATE + '/shell.log ' + str(offset) + ' ' + str(PATIENCE) + ' 20')
    row['launch'] = 'ok' if any('started ' in l and 'depth=1' in l for l in lines) else 'FAIL'
    if 'FRAME' in frame.stdout and app.poll() is None:
        row['frame'] = 'ok'
        shot = HERE / 'shots' / (name + '.png')
        shot.parent.mkdir(exist_ok=True)
        vmcapture.capture(str(shot))
    else:
        row['frame'] = 'FAIL'
        if 'FRAME' in frame.stdout: lines.append('exited after its first frames (status %s)' % app.poll())
    row['note'] = cause(lines) if row['frame'] != 'ok' or row['launch'] != 'ok' else '-'
    if row['frame'] != 'ok' and row['note'] == '-': row['note'] = lines[-1].strip()[:110].replace('|', '/') if lines else 'no output'
    app.terminate()
    ssh(ENV + 'b/cli/apklayer stop >/dev/null 2>&1')
    return row


def main():
    wanted = sys.argv[1:]
    entries = []
    for line in (HERE / 'apps.txt').read_text().splitlines():
        if line.strip() and not line.startswith('#'):
            name, spec, kind = [p.strip() for p in line.split('|')]
            if not wanted or name in wanted: entries.append((name, spec, kind))
    ssh('mkdir -p ~/apklayer/tests/corpus')
    subprocess.run(['sshpass', '-p', config.PASS, 'scp', '-q', str(HERE / 'first-frame.py'), config.VM + ':apklayer/tests/corpus/first-frame.py'], check=True)
    rows = [run_one(*e) for e in entries]
    out = ['# Compatibility corpus results', '', time.strftime('Run %Y-%m-%d %H:%M'), '', '| app | kind | install | launch | first frame | note |', '|---|---|---|---|---|---|']
    out += ['| {name} | {kind} | {install} | {launch} | {frame} | {note} |'.format(**r) for r in rows]
    passed = sum(1 for r in rows if r['frame'] == 'ok')
    out += ['', '%d of %d reach a first GPU frame.' % (passed, len(rows))]
    (HERE / 'results.md').write_text('\n'.join(out) + '\n')
    print('\n'.join(out))


if __name__ == '__main__':
    main()
