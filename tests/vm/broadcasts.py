#!/usr/bin/env python3
"""Cross-app broadcasts through the instance hub: one phase per run (manifest | basic | ordered | sticky), every JVM owned, drained and reaped."""
import os as _os
LOGDIR = _os.path.expanduser('~/.local/state/apklayer/default')  # the default instance's logs (apklayer tool)
import ctypes
import os
from pathlib import Path
import selectors
import signal
import subprocess
import sys
import time

os.chdir(Path.home() / 'apklayer-m1')
PHASE = sys.argv[1]
BASE = '-Dapklayer.realbinder=true -Dapklayer.instancehub=true -Dapklayer.runms=60000' + (' -Dhello.trace=1' if os.environ.get('BC_TRACE') else '')
ENV = dict(os.environ, XDG_RUNTIME_DIR='/run/user/1000', APKLAYER_NATIVE=str(Path.home() / 'arm64-native'),
    APKLAYER_DISPLAY_WIDTH='480', APKLAYER_DISPLAY_HEIGHT='400', APKLAYER_JOPTS=BASE)
ENV['APKLAYER_HOST_LIBS'] = ENV['APKLAYER_NATIVE'] + '/libapklayer-hal-graphics.so:' + ENV['APKLAYER_NATIVE'] + '/libapklayer-hal-media.so'
LOGS = Path(LOGDIR)
libc = ctypes.CDLL(None, use_errno=True)


def stop():
    subprocess.run(['bin/apklayer', 'stop'], env=ENV, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    subprocess.run(['pkill', '-9', '-x', 'tail'], check=False)


class Apps:
    def __init__(self):
        self.events = selectors.DefaultSelector()
        self.children, self.lines, self.pending = {}, {}, {}

    def start(self, name, package, scenario=None):
        env = dict(ENV)
        if scenario: env['APKLAYER_JOPTS'] = BASE + ' -Dhello.bc=' + scenario
        proc = subprocess.Popen(['bin/apklayer', 'run', package], env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
        self.children[name] = proc
        self.lines[name], self.pending[name] = [], b''
        self.events.register(proc.stdout, selectors.EVENT_READ, name)

    def pump(self, seconds):
        """Read whatever the apps print for up to `seconds`; returns False when nothing arrived."""
        ready = self.events.select(seconds)
        for key, _ in ready:
            name = key.data
            data = os.read(key.fd, 65536)
            if not data:
                self.events.unregister(key.fileobj)
                continue
            parts = (self.pending[name] + data).split(b'\n')
            self.pending[name] = parts.pop()
            for raw in parts:
                line = raw.decode(errors='replace')
                self.lines[name].append(line)
                if os.environ.get('BC_VERBOSE') or '[bc' in line or '[apklayer-hub]' in line or '[apklayer] activity manager' in line or 'PendingResult' in line or '[bcr] trace' in line or ('\tat ' in line and 'android.app' in line): print(name + ': ' + line, flush=True)
                assert not ('[bcs]' in line and 'FAIL' in line), line
        return bool(ready)

    def wait_for(self, name, needle, seconds=12):
        end = time.monotonic() + seconds
        while time.monotonic() < end:
            if any(needle in line for line in self.lines[name]): return next(line for line in self.lines[name] if needle in line)
            if not self.pump(min(2, max(0.1, end - time.monotonic()))) and time.monotonic() >= end: break
        raise AssertionError('timed out waiting for: ' + needle + '\n' + '\n'.join(self.lines[name][-20:]))

    def settle(self, name, needle, seconds):
        """Wait up to `seconds` and report whether the needle showed (for lines that must NOT appear: the wait is the evidence window)."""
        end = time.monotonic() + seconds
        while time.monotonic() < end: self.pump(min(1, max(0.1, end - time.monotonic())))
        return any(needle in line for line in self.lines[name])

    def count(self, name, needle): return sum(1 for line in self.lines[name] if needle in line)


def wait_file(path, needle, seconds):
    """Block on inotify (no polling) until the file contains the needle."""
    fd = libc.inotify_init1(os.O_NONBLOCK | os.O_CLOEXEC)
    os.makedirs(path.parent, exist_ok=True)
    assert libc.inotify_add_watch(fd, os.fsencode(path.parent), 0x102) >= 0  # IN_MODIFY | IN_CREATE in the directory
    end = time.monotonic() + seconds
    with selectors.DefaultSelector() as events:
        events.register(fd, selectors.EVENT_READ)
        while time.monotonic() < end:
            if path.exists() and needle in path.read_text(errors='replace'): os.close(fd); return
            if events.select(min(2, max(0.1, end - time.monotonic()))): os.read(fd, 65536)
    os.close(fd)
    raise AssertionError('timed out waiting for ' + needle + ' in ' + str(path) + '\n' + (path.read_text(errors='replace')[-2000:] if path.exists() else '(no file)'))


stop()
apps = Apps()
try:
    if PHASE == 'manifest':
        apps.start('sender', 'hello.bcsender', 'manifest')
        apps.wait_for('sender', '[bcs] done')
        # the receiver's package was not running: the hub started it headless and delivered
        wait_file(LOGS / "component-hello.bcreceiver.log", "[bcr] manifest hello.bc.MANIFEST", 15)
        print('PASS manifest receiver of a package that was not running', flush=True)
    elif PHASE == 'basic':
        apps.start('receiver', 'hello.bcreceiver')
        apps.wait_for('receiver', '[bcr] ready')
        apps.start('sender', 'hello.bcsender', 'basic')
        apps.wait_for('sender', '[bcs] done')
        apps.wait_for('receiver', '[bcr] dynamic hello.bc.DYNAMIC')
        apps.wait_for('receiver', '[bcr] manifest hello.bc.MANIFEST pid=')
        apps.wait_for('receiver', 'extra=explicit component')
        apps.wait_for('receiver', '[bcr] protected hello.bc.PROTECTED')
        apps.wait_for('receiver', '[bcr] guarded dynamic hello.bc.GUARDED')
        assert not apps.settle('receiver', 'PRIVATE reached', 2), 'a private manifest receiver was reached from another app'
        assert not apps.settle('receiver', 'INTERNAL reached', 1), 'a not-exported dynamic receiver was reached from another app'
        before = (apps.count('receiver', 'protected hello.bc.PROTECTED'), apps.count('receiver', 'guarded dynamic'), apps.count('receiver', 'dynamic hello.bc.DYNAMIC'))
        apps.start('denied', 'hello.bcdenied', 'basic')
        apps.wait_for('denied', '[bcs] done')
        apps.wait_for('receiver', '[bcr] dynamic hello.bc.DYNAMIC', 5) if apps.count('receiver', 'dynamic hello.bc.DYNAMIC') > before[2] else None
        apps.settle('receiver', 'never', 3)
        after = (apps.count('receiver', 'protected hello.bc.PROTECTED'), apps.count('receiver', 'guarded dynamic'), apps.count('receiver', 'dynamic hello.bc.DYNAMIC'))
        assert after[0] == before[0], 'a sender without the permission reached a protected manifest receiver'
        assert after[1] == before[1], 'a sender without the permission reached a protected dynamic receiver'
        assert after[2] == before[2] + 1, 'the unprotected dynamic receiver missed the second sender: %s -> %s' % (before, after)
        print('PASS dynamic and manifest delivery across apps, exported rules, sender permissions', flush=True)
    elif PHASE == 'ordered':
        apps.start('receiver', 'hello.bcreceiver')
        apps.wait_for('receiver', '[bcr] ready')
        apps.start('sender', 'hello.bcsender', 'ordered')
        apps.wait_for('sender', '[bcs] ordered result code=2 data=start|M|D')
        apps.wait_for('sender', '[bcs] abort result code=0 data=aborted by manifest')
        assert not apps.settle('receiver', 'ABORT reached the dynamic receiver', 2), 'an aborted ordered broadcast reached a lower priority receiver'
        print('PASS ordered broadcast: priority order across manifest and dynamic receivers, result propagation, abort, final result receiver', flush=True)
    elif PHASE == 'async':
        apps.start('receiver', 'hello.bcreceiver')
        apps.wait_for('receiver', '[bcr] ready')
        apps.start('sender', 'hello.bcsender', 'async')
        apps.wait_for('sender', '[bcs] async result code=10 data=start|async')
        print('PASS goAsync across apps: the ordered broadcast waited for the late finish() and carried its result', flush=True)
    elif PHASE == 'once':
        apps.start('receiver', 'hello.bcreceiver')
        apps.wait_for('receiver', '[bcr] ready')
        apps.start('sender', 'hello.bcsender', 'once')
        apps.wait_for('receiver', '[bcr] once: unregistered')
        assert not apps.settle('receiver', 'extra=second', 3), 'an unregistered receiver was called again'
        assert apps.count('receiver', 'dynamic hello.bc.ONCE') == 1
        print('PASS unregisterReceiver in onReceive: no later delivery', flush=True)
    elif PHASE == 'death':
        apps.start('receiver', 'hello.bcreceiver')
        ready = apps.wait_for('receiver', '[bcr] ready pid=')
        os.kill(int(ready.split('pid=')[1]), signal.SIGKILL)
        apps.wait_for('receiver', 'never seen', 3) if False else apps.settle('receiver', 'never', 2)
        apps.start('sender', 'hello.bcsender', 'ordered')
        # the dead process's dynamic receiver is gone; the manifest receiver starts the package again, headless
        apps.wait_for('sender', '[bcs] ordered result code=1 data=start|M', 25)
        print('PASS receiver process death: its dynamic registrations are gone, the manifest receiver restarts the package', flush=True)
    elif PHASE == 'sticky':
        apps.start('sender', 'hello.bcsender', 'sticky')
        apps.wait_for('sender', '[bcs] sent sticky')
        apps.start('receiver', 'hello.bcreceiver')
        apps.wait_for('receiver', '[bcr] dynamic hello.bc.STICKY')
        assert 'sticky=true' in apps.wait_for('receiver', '[bcr] dynamic hello.bc.STICKY'), 'sticky flag missing'
        print('PASS sticky broadcast delivered to a receiver registered later', flush=True)
    else:
        raise SystemExit('unknown phase ' + PHASE)
finally:
    for proc in apps.children.values():
        if proc.poll() is None: os.killpg(proc.pid, signal.SIGTERM)
    stop()
    subprocess.run(['pkill', '-f', '[a]pklayer.host.Main --hub'], check=False)
    subprocess.run(['pkill', '-f', '[c]omponent-host'], check=False)
