#!/usr/bin/env python3
"""F11 (bridge/shell/keyboard.c): the desktop's fullscreen key toggles the focused window's fullscreen, as a browser's does, whatever the
app asked for; the key does not reach the app. A real app (UiBench): F11 -> the shell logs "fullscreen on" and the compositor's configure
has no title bar; F11 again -> "fullscreen off" and the title bar is back. The app's log must not show the key."""
import os
from pathlib import Path
import re
import selectors
import signal
import subprocess
import sys
import time

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
import instance  # noqa: E402  (changes into the repo root)
PKG = 'com.android.test.uibench'
ENV = dict(instance.ENV, APKLAYER_JOPTS='-Dapklayer.runms=16000 -Dapklayer.debugkeys=true -Dapklayer.script=5000:hover:1,0+9000:hover:2,0+13000:hover:3,0')
CONFIGURE = re.compile(r'configure content \d+x\d+ @\d+% \(\d+x\d+ px\), title bar (\d+) logical')
LOG = instance.STATE / 'shell.log'
checks = []


def shell():
    return LOG.read_text(errors='replace') if LOG.exists() else ''


def bar():
    """The title bar of the last configure of this app's window (0 in fullscreen)."""
    text = shell()
    return [int(m[1]) for m in CONFIGURE.finditer(text) if PKG in text[:m.start()].rsplit('\n', 1)[-1]][-1]


instance.stop()
LOG.unlink(missing_ok=True)
app = subprocess.Popen(['bin/apklayer', 'run', PKG], env=ENV, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
seen = []
try:
    selector = selectors.DefaultSelector()
    selector.register(app.stdout, selectors.EVENT_READ)
    end, pending = time.monotonic() + 60, b''
    while True:
        assert selector.select(10) and time.monotonic() < end, 'F11 test stalled'
        data = os.read(app.stdout.fileno(), 65536)
        if not data: break
        pending += data
        *lines, pending = pending.split(b'\n')
        for line in (l.decode(errors='replace') for l in lines):
            seen.append(line)
            if 'script: hover:1,0' in line:
                assert bar() > 0, 'the window starts with a title bar'
                instance.inject(PKG, 'focus', 'key:87')
            elif 'script: hover:2,0' in line:
                assert 'fullscreen on' in shell() and bar() == 0, 'F11 did not make the window fullscreen (title bar %d)' % bar()
                checks.append('F11 fullscreen')
                instance.inject(PKG, 'key:87')
            elif 'script: hover:3,0' in line:
                assert 'fullscreen off' in shell() and bar() > 0, 'F11 again did not leave fullscreen (title bar %d)' % bar()
                checks.append('F11 again leaves it')
    assert app.wait() == 0, 'app failed'
    assert len(checks) == 2, checks
    assert not any('[apklayer] key ' in l and 'code=142' in l for l in seen), 'F11 reached the app'  # KEYCODE_F11
    print('PASS %s; the key stays with the shell' % ', '.join(checks), flush=True)
finally:
    instance.stop()
    if app.poll() is None: os.killpg(app.pid, signal.SIGKILL)
    app.wait()
    left = instance.mine('[a]pklayer.host.Main|[b]tload|[a]pklayer-shell')
    assert not left, left
