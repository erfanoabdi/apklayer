#!/usr/bin/env python3
"""Compatibility corpus runner (on the Mac, drives the test VM): for every app in apps.txt install it, launch it, wait for the first GPU frame the shell receives
(inotify on the VM, no timers beyond a patience limit), take a screenshot of its window (`apklayer screenshot`; a desktop capture against one taken before the launch as the fallback), check it shows content (content.py), launch it a second time, stop it, and write
tests/corpus/results.md (results-subset.md when names are given) plus the fake-service ranking tests/corpus/fakes.md (the app's "[fake]" lines, FakeLog).
Each app's install and runs hold the VM's Android-stack lock (~/apklayer-vm.lock, flock -n): a busy lock is reported as "lock busy", nothing waits for it.
Usage: tests/corpus/run.py [name ...]   (no names: every app);  run.py --report name...  rebuilds the tables from tests/corpus/logs/*.json
Environment: CORPUS_PATIENCE (seconds per launch), CORPUS_VM_REPO (repo copy on the VM, default ~/apklayer), CORPUS_VM_NATIVE (APKLAYER_NATIVE on the VM),
CORPUS_SECOND=0 (skip the second launch)."""
import json
import os
import re
import subprocess
import sys
import threading
import time
from collections import Counter
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
sys.path.insert(0, str(ROOT / 'tests/vm'))
import config  # noqa: E402
import vmcapture  # noqa: E402
import content  # noqa: E402  (tests/corpus is on sys.path as the script's directory)

SSH = ['sshpass', '-p', config.PASS, 'ssh', '-o', 'StrictHostKeyChecking=no', config.VM]
STATE = '~/.local/state/apklayer/default'
REPO = os.environ.get('CORPUS_VM_REPO', '~/apklayer')
NATIVE = os.environ.get('CORPUS_VM_NATIVE')
ENV = 'cd %s; export XDG_RUNTIME_DIR=/run/user/1000 WAYLAND_DISPLAY=wayland-0 APKLAYER_ROOT=%s%s; ' % (REPO, REPO, ' APKLAYER_NATIVE=' + NATIVE if NATIVE else '')
PATIENCE = int(os.environ.get('CORPUS_PATIENCE', '150'))
SECOND = os.environ.get('CORPUS_SECOND', '1') != '0'
AGAIN = '/tmp/apklayer-corpus-again'  # present when a run ends: the VM script launches the app once more
BUSY = 75  # flock's exit status when the lock is held by someone else
CAUSES = ['UnsatisfiedLinkError', 'ClassNotFoundException', 'NoSuchMethodError', 'SecurityException', 'NullPointerException', 'Fatal signal', 'SIGSEGV', 'Exception', 'Error']
FAKE = re.compile(r'\[fake\] (\S+) (\S+)')


def ssh(command, **kw):
    return subprocess.run(SSH + [command], capture_output=True, text=True, **kw)


def cause(lines):
    for key in CAUSES:
        for line in lines:
            if key in line and 'symbol may need' not in line and 'cutils-trace' not in line and '[fake]' not in line:
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


def script(name):
    """The VM side of one app, under the stack lock: install, then one or two runs, each preceded by the shell.log offset its frames count from."""
    cli = 'b/cli/apklayer'
    offset = 'echo "OFFSET $(stat -c %s ' + STATE + '/shell.log 2>/dev/null || echo 0)"; '
    body = ('echo LOCKED; read go; ' + cli + ' stop >/dev/null 2>&1; rm -f ' + AGAIN + '; out=$(' + cli + ' install ~/corpus-' + name + '.apk 2>&1); echo "$out"; '
            'pkg=$(echo "$out" | sed -n "s/.*installed \\([^ ]*\\).*/\\1/p" | head -n1); [ -n "$pkg" ] || exit 3; '
            + offset + cli + ' run $pkg 2>&1; '
            'if [ -e ' + AGAIN + ' ]; then rm -f ' + AGAIN + '; echo SECOND; ' + offset + cli + ' run $pkg 2>&1; fi; '
            + cli + ' stop >/dev/null 2>&1; echo CORPUS-END')
    return "flock -n -o -E %d ~/apklayer-vm.lock sh -c '%s'" % (BUSY, body.replace("'", "'\\''"))


class Stream:
    """The VM script's output, read on a thread; wait_for blocks on the next marker line (or the end of the stream)."""
    def __init__(self, proc):
        self.proc, self.lines, self.cond = proc, [], threading.Condition()
        self.done = False
        self.thread = threading.Thread(target=self._read, daemon=True)
        self.thread.start()

    def _read(self):
        for line in self.proc.stdout:
            with self.cond: self.lines.append(line); self.cond.notify_all()
        with self.cond: self.done = True; self.cond.notify_all()

    def wait_for(self, prefix, start=0):
        with self.cond:
            while True:
                for i in range(start, len(self.lines)):
                    if self.lines[i].startswith(prefix): return i
                if self.done: return None
                self.cond.wait()


def end_run(again):
    """Ends the running app (this stack is ours while the lock script runs); the script then starts it again or stops the stack."""
    # the app JVM and its `run` only, not the stack daemon: a second launch reuses the running stack and window shell; the script's stop ends them
    kill = 'tests/vm/kill-instance.sh default '  # only this instance's processes: other instances (parallel test copies) keep running
    ssh('cd ~/apklayer; ' + ('touch ' + AGAIN + '; ' if again else kill + "'^[^ ]*apklayer-shell'; ") + kill + "'[a]pklayer.host.Main' '[c]li/apklayer run '")


def launch(name, stream, at, row, key, before):
    """One launch from the OFFSET line at index `at`: a steady picture (20 frames while still running), a screenshot, its content check."""
    offset = stream.lines[at].split()[1]
    frame = ssh('python3 %s/tests/corpus/first-frame.py %s/shell.log %s %d 20' % (REPO, STATE, offset, PATIENCE))
    running = not any(l.startswith(('SECOND', 'CORPUS-END')) for l in stream.lines[at:])  # the run command has not returned
    if 'FRAME' not in frame.stdout or not running:
        row[key] = 'FAIL'
        if 'FRAME' in frame.stdout: stream.lines.append('exited after its first frames\n')
        return False
    shot = HERE / 'shots' / (name + ('' if key == 'frame' else '-2') + '.png')
    shot.parent.mkdir(exist_ok=True)
    # the app's own window from SurfaceFlinger (no desktop, no other instance's windows); the desktop against the before-launch capture only as a fallback
    pkg = next((m.group(1) for l in stream.lines for m in [re.search(r'installed (\S+)', l)] if m), None)
    if pkg and vmcapture.app_capture(str(shot), pkg, ENV, cli=REPO + '/b/cli/apklayer'):
        verdict, detail = content.verdict(str(shot))
    else:
        vmcapture.capture(str(shot))
        verdict, detail = content.verdict(str(shot), str(before))
    row[key] = 'ok' if verdict == 'content' else verdict
    if key == 'frame': row['content'] = detail
    return verdict == 'content'


def run_one(name, spec, kind):
    row = {'name': name, 'kind': kind, 'install': '-', 'launch': '-', 'frame': '-', 'second': '-', 'content': '-', 'note': '-', 'fakes': []}
    apk = fetch(name, spec)
    if apk is None:
        row['note'] = 'no apk'; return row
    subprocess.run(['sshpass', '-p', config.PASS, 'scp', '-q', str(apk), config.VM + ':corpus-' + name + '.apk'], check=True)
    proc = subprocess.Popen(SSH + [ENV + script(name)], stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, errors='replace')
    stream = Stream(proc)
    before = HERE / 'shots' / (name + '-before.png')  # the fallback's desktop without the app (content.py takes the window as what differs), taken holding the lock
    if stream.wait_for('LOCKED') is not None:
        before.parent.mkdir(exist_ok=True)
        vmcapture.capture(str(before))
        proc.stdin.write('go\n'); proc.stdin.flush()
    first = stream.wait_for('OFFSET')
    if first is None:
        proc.wait(); stream.thread.join()
        if proc.returncode == BUSY:
            row['note'] = 'lock busy (another agent runs a stack): rerun this app'; return row
        row['install'] = 'FAIL'; row['note'] = cause(stream.lines) if stream.lines else 'no output, exit %s' % proc.returncode; return row
    row['install'] = 'ok'
    ok = launch(name, stream, first, row, 'frame', before)
    again = SECOND and ok
    end_run(again)
    if again:
        second = stream.wait_for('OFFSET', first + 1)
        if second is not None:
            launch(name, stream, second, row, 'second', before)
            end_run(False)
    proc.wait(); stream.thread.join()
    lines = stream.lines
    row['launch'] = 'ok' if any('started ' in l and 'depth=1' in l for l in lines) else 'FAIL'
    row['fakes'] = sorted({m.group(1) + ' ' + m.group(2) for l in lines for m in [FAKE.search(l)] if m})
    if row['frame'] != 'ok' or row['launch'] != 'ok' or row['second'] not in ('ok', '-'):
        row['note'] = cause(lines)
        if row['note'] == '-': row['note'] = next((l.strip()[:110].replace('|', '/') for l in reversed(lines) if l.strip() and '[fake]' not in l and 'CORPUS-END' not in l), 'no output')
    (HERE / 'logs').mkdir(exist_ok=True)
    (HERE / 'logs' / (name + '.log')).write_text(''.join(lines))
    (HERE / 'logs' / (name + '.json')).write_text(json.dumps(row))
    return row


def main():
    report = sys.argv[1:2] == ['--report']  # only rebuild the tables from the rows saved in logs/ (runs split over several calls)
    wanted = sys.argv[2:] if report else sys.argv[1:]
    entries = []
    for line in (HERE / 'apps.txt').read_text().splitlines():
        if line.strip() and not line.startswith('#'):
            name, spec, kind = [p.strip() for p in line.split('|')]
            if not wanted or name in wanted: entries.append((name, spec, kind))
    if report:
        rows = [json.loads((HERE / 'logs' / (e[0] + '.json')).read_text()) for e in entries if (HERE / 'logs' / (e[0] + '.json')).exists()]
    else:
        ssh('mkdir -p %s/tests/corpus' % REPO)
        subprocess.run(['sshpass', '-p', config.PASS, 'scp', '-q', str(HERE / 'first-frame.py'), config.VM + ':' + REPO.replace('~/', '') + '/tests/corpus/first-frame.py'], check=True)
        rows = [run_one(*e) for e in entries]
        rows = [run_one(*e) if r['note'].startswith('lock busy') else r for e, r in zip(entries, rows)]  # one more try for the apps that found the lock busy
    out = ['# Compatibility corpus results', '', time.strftime('Run %Y-%m-%d %H:%M'), '',
           '"first frame" = 20 GPU frames while running and the screenshot shows content (uniform/nowindow otherwise); "second" = the same after a second launch.', '',
           '| app | kind | install | launch | first frame | second | fakes | note |', '|---|---|---|---|---|---|---|---|']
    out += ['| {name} | {kind} | {install} | {launch} | {frame} | {second} | %d | {note} |'.format(**r) % len(r['fakes']) for r in rows]
    passed = sum(1 for r in rows if r['frame'] == 'ok')
    out += ['', '%d of %d reach a steady GPU picture with content.' % (passed, len(rows))]
    (HERE / ('results-subset.md' if wanted else 'results.md')).write_text('\n'.join(out) + '\n')
    print('\n'.join(out))
    write_fakes(rows)


def write_fakes(rows):
    """Which fakes the apps hit, most apps first: the services worth making real next."""
    hits = Counter(f for r in rows for f in r['fakes'])
    by = {f: sorted(r['name'] for r in rows if f in r['fakes']) for f in hits}
    out = ['# Fake services hit by the corpus', '', time.strftime('Run %Y-%m-%d %H:%M') + ', apps: ' + ', '.join(r['name'] for r in rows), '',
           'First call per process to a Robolectric null/deep proxy, a missing or Robolectric-shadowed manager, or HostMediaStub ("[fake]" lines, FakeLog).', '',
           '| apps | service | call | which |', '|---|---|---|---|']
    out += ['| %d | %s | %s | %s |' % (n, *f.split(' ', 1), ', '.join(by[f])) for f, n in sorted(hits.items(), key=lambda h: (-h[1], h[0]))]
    (HERE / 'fakes.md').write_text('\n'.join(out) + '\n')
    print('\n'.join(out[:40]))


if __name__ == '__main__':
    main()
