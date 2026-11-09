#!/usr/bin/env python3
"""`apklayer run` of an app that already runs (activities group): the launch goes to the running process, never a second one (Android: a launcher
or `am start` reaches the running app). hello.acttarget runs; an intent file starts its singleTask PickActivity in that process, a second one
reaches it through onNewIntent, a launcher start brings the task forward without a new activity. One JVM of the package throughout, each such
`run` returns at once, and the shell asks the compositor to raise the window (xdg-activation). Then a start with its own intent file and a second
`run` while that process starts (run record, not registered: the hub queues the launch): the first file's intent is the first activity, the queued
one follows it (onNewIntent), as two `am start` in a row."""
import os
import re
import selectors
import subprocess
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
import instance  # noqa: E402
from instance import ENV, INSTANCE, LOGDIR, OUT, stop  # noqa: E402

PKG = 'hello.acttarget'
env = dict(ENV, APKLAYER_DISPLAY_WIDTH='480', APKLAYER_DISPLAY_HEIGHT='400', APKLAYER_DEBUG_INPUT='1', APKLAYER_JOPTS='-Dapklayer.runms=60000 -Dhello.act=none')
shell_log = Path(LOGDIR) / 'shell.log'


class App:
    def __init__(self, package, intent=None):
        run_env = dict(env)
        if intent:
            path = OUT / 'first-intent.properties'
            path.write_text(''.join('%s=%s\n' % kv for kv in intent.items()))
            run_env['APKLAYER_INTENT_FILE'] = str(path)
        self.proc = subprocess.Popen(['bin/apklayer', 'run', package], env=run_env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
        self.lines, self.pending, self.events = [], b'', selectors.DefaultSelector()
        self.events.register(self.proc.stdout, selectors.EVENT_READ)

    def expect(self, text, seconds=60):
        """The first line from now on that contains text."""
        seen = len(self.lines)
        end = time.monotonic() + seconds
        while time.monotonic() < end:
            for line in self.lines[seen:]:
                if text in line: return line
            seen = len(self.lines)
            if not self.events.select(1): continue
            data = os.read(self.proc.stdout.fileno(), 65536)
            if not data: raise AssertionError('%s ended (exit status %s) waiting for %r\n' % (PKG, self.proc.wait(), text) + '\n'.join(self.lines[-20:]))
            parts = (self.pending + data).split(b'\n')
            self.pending = parts.pop()
            self.lines += [p.decode(errors='replace') for p in parts]
        raise AssertionError('no %r within %d s\n' % (text, seconds) + '\n'.join(self.lines[-25:]))


def jvms(package):
    """This instance's JVMs of the package (its command line names it, or is its process name)."""
    found = []
    for proc in Path('/proc').iterdir():
        try:
            cmd = (proc / 'cmdline').read_bytes()
            if not (cmd.startswith(package.encode() + b'\0') or b'-Dapklayer.package=%s\0' % package.encode() in cmd): continue
        except OSError: continue
        if instance.belongs(proc.name): found.append(int(proc.name))
    return found


def relaunch(intent=None):
    """A second `apklayer run` of the package: hands its launch to the running app and returns."""
    run_env = dict(env)
    if intent:
        path = OUT / 'relaunch-intent.properties'
        path.write_text(''.join('%s=%s\n' % kv for kv in intent.items()))
        run_env['APKLAYER_INTENT_FILE'] = str(path)
    began = time.monotonic()
    done = subprocess.run(['bin/apklayer', 'run', PKG], env=run_env, capture_output=True, text=True, timeout=30)
    took = time.monotonic() - began
    assert done.returncode == 0 and 'launch handed to it' in done.stdout, 'the second run did not hand over (exit %d):\n%s%s' % (done.returncode, done.stdout, done.stderr)
    print('[relaunch] run returned in %.1f s: %s' % (took, done.stdout.strip()), flush=True)
    assert not intent or not (OUT / 'relaunch-intent.properties').exists(), 'the intent file was not taken'


stop()
apps = []
try:
    target = App(PKG)
    apps.append(target)
    main = target.expect('[act-t] main pid=')
    pid = main.split('pid=')[1].split()[0]
    assert 'ui=true' in main, 'an activity is not a UI context (Context.isUiContext): ' + main
    jvm = jvms(PKG)
    assert jvm == [int(pid)], 'the app JVM: %s, pid %s' % (jvm, pid)
    print('[relaunch] %s runs, pid %s' % (PKG, pid), flush=True)
    # the screen is the shell's output in pixels, turned like the window (WindowConfiguration max bounds), the window stays 480x400
    m = re.search(r'max=(\d+)x(\d+) real=(\d+)x(\d+) window=(\d+)x(\d+) cache=(\d+)', target.expect('[act-t] screen '))
    mw, mh, rw, rh, ww, wh, cache = map(int, m.groups())
    out = re.findall(r'first configure: .* output (\d+)x(\d+) @(\d+)%', shell_log.read_text(errors='replace'))[-1]
    opx = sorted(int(v) * int(out[2]) // 100 for v in out[:2])
    assert sorted((mw, mh)) == opx and (rw, rh) == (mw, mh) and (mw >= mh) == (ww >= wh) and cache == 4 * mw * mh and mw >= ww and mh >= wh, \
        'screen %s, output %s px' % (m.group(0), opx)
    print('[relaunch] screen %s (output %dx%d px)' % (m.group(0), *opx), flush=True)
    other = App('hello.actcaller')  # takes the desktop focus, so that raising the target is visible in its activated state
    apps.append(other)
    other.expect('[act] unknown scenario')
    shell_seen = shell_log.stat().st_size if shell_log.exists() else 0  # the shell may not have written its log yet (beside another group)

    view = {'action': 'hello.act.PICK', 'data': 'https://example.org/one', 'component': PKG + '.PickActivity', 'extra.text': 'one', 'extra.keep': '1'}
    # the running app's APK rewritten (a reinstall, a tree's install): the next activity's ActivityThread.getPackageInfo finds the package's
    # resources out of date and updates the LoadedApk, which must not build an Android class loader
    apk = Path(ENV.get('XDG_DATA_HOME', Path.home() / '.local/share')) / 'apklayer/apps' / PKG / 'base.apk'
    times = apk.stat()
    os.utime(apk)
    try:
        relaunch(view)
        line = target.expect('[act-t] create pid=%s ' % pid, 20)
    finally:
        os.utime(apk, ns=(times.st_atime_ns, times.st_mtime_ns))
    assert 'text=one' in line and 'data=https://example.org/one' in line, line
    print('[relaunch] new activity: ' + line.strip(), flush=True)
    relaunch(dict(view, data='https://example.org/two', **{'extra.text': 'two'}))
    line = target.expect('[act-t] newintent pid=%s ' % pid, 20)
    assert 'text=two' in line and 'data=https://example.org/two' in line, line
    print('[relaunch] singleTask onNewIntent: ' + line.strip(), flush=True)
    relaunch()
    line = target.expect('[apklayer] brought forward %s.Main' % PKG, 20)
    print('[relaunch] launcher start: ' + line.strip(), flush=True)
    assert sum('[act-t] main pid=' in l for l in target.lines) == 1, 'a second Main was created'
    jvm = jvms(PKG)
    assert jvm == [int(pid)], 'JVMs of %s after the relaunches: %s' % (PKG, jvm)
    print('[relaunch] one JVM of %s: %s' % (PKG, jvm), flush=True)

    with open(shell_log, 'rb') as f:
        f.seek(shell_seen)
        shell = [l for l in f.read().decode(errors='replace').splitlines() if PKG in l and ('activate' in l)]
    print('\n'.join('[relaunch] ' + l for l in shell), flush=True)
    requests = [l for l in shell if '[shell] activate ' in l]
    assert len(requests) == 3 and all('token' in l for l in requests), 'the shell did not raise the window each time: %s' % requests
    print('[relaunch] compositor: %s' % ('activated the window' if any(l.endswith(' activated') for l in shell) else 'no activated state for the window (focus-stealing prevention)'), flush=True)

    for app in apps:
        try: os.killpg(app.proc.pid, 15)
        except OSError: pass  # it ended already
    stop()
    first = App(PKG, dict(view, data='https://example.org/first', **{'extra.text': 'first'}))
    apps.append(first)
    end = time.monotonic() + 30
    while time.monotonic() < end and not any(r.name.startswith('run.') and len(r.read_text().split()) >= 2 and int(r.read_text().split()[1]) > 0
                                             for r in Path(LOGDIR).iterdir() if r.name.startswith('run.')):
        time.sleep(0.05)  # the launch's run record with its window sink: from then on `run` hands over
    relaunch(dict(view, data='https://example.org/queued', **{'extra.text': 'queued'}))
    line = first.expect('[act-t] create pid=', 60)
    assert 'text=first' in line, 'the first activity is not the launch\'s own intent: ' + line
    line = first.expect('[act-t] newintent pid=', 20)
    assert 'text=queued' in line, line
    queued = 'relaunch %s (still starting: queued)' % PKG in (Path(LOGDIR) / 'hub.log').read_text(errors='replace')
    print('[relaunch] a second run while it started (%s): its own intent first, then the other: %s' % ('queued by the hub' if queued else 'it had registered already', line.strip()), flush=True)
    print('PASS activities relaunch', flush=True)
finally:
    for app in apps:
        if app.proc.poll() is None:
            try: os.killpg(app.proc.pid, 15)
            except OSError: pass
    stop()
