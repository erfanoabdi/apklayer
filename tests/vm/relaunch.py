#!/usr/bin/env python3
"""`apklayer run` of an app that already runs (activities group): the launch goes to the running process, never a second one (Android: a launcher
or `am start` reaches the running app). hello.acttarget runs; an intent file starts its singleTask PickActivity in that process, a second one
reaches it through onNewIntent, a launcher start brings the task forward without a new activity. One JVM of the package throughout, each such
`run` returns at once, and the shell asks the compositor to raise the window (xdg-activation)."""
import os
import selectors
import subprocess
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
from instance import ENV, INSTANCE, LOGDIR, OUT, stop  # noqa: E402

PKG = 'hello.acttarget'
env = dict(ENV, APKLAYER_DISPLAY_WIDTH='480', APKLAYER_DISPLAY_HEIGHT='400', APKLAYER_DEBUG_INPUT='1', APKLAYER_JOPTS='-Dapklayer.runms=60000 -Dhello.act=none')
shell_log = Path(LOGDIR) / 'shell.log'


class App:
    def __init__(self, package):
        self.proc = subprocess.Popen(['bin/apklayer', 'run', package], env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
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
            environ = (proc / 'environ').read_bytes().split(b'\0')
        except OSError: continue
        if b'APKLAYER_INSTANCE=' + INSTANCE.encode() in environ: found.append(int(proc.name))
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
    pid = target.expect('[act-t] main pid=').split('pid=')[1].split()[0]
    jvm = jvms(PKG)
    assert jvm == [int(pid)], 'the app JVM: %s, pid %s' % (jvm, pid)
    print('[relaunch] %s runs, pid %s' % (PKG, pid), flush=True)
    other = App('hello.actcaller')  # takes the desktop focus, so that raising the target is visible in its activated state
    apps.append(other)
    other.expect('[act] unknown scenario')
    shell_seen = shell_log.stat().st_size

    view = {'action': 'hello.act.PICK', 'data': 'https://example.org/one', 'component': PKG + '.PickActivity', 'extra.text': 'one', 'extra.keep': '1'}
    relaunch(view)
    line = target.expect('[act-t] create pid=%s ' % pid, 20)
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
    print('PASS activities relaunch', flush=True)
finally:
    for app in apps:
        if app.proc.poll() is None:
            try: os.killpg(app.proc.pid, 15)
            except OSError: pass
    stop()
