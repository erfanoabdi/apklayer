#!/usr/bin/env python3
"""Edge swipes on a fullscreen window (bridge/shell/edgeswipe.c): compositors block their screen edges over a fullscreen window (KWin), so an
inward swipe from an edge takes the touch from the app and leaves fullscreen for a while, as Android's transient bars. A real app (UiBench),
fullscreen by F11: a finger that goes down in the window's edge zone and moves along it stays the app's; one that moves inward makes the shell
log "edge swipe", the app gets ACTION_CANCEL (no ACTION_UP of it) and the window leaves fullscreen (its size); 3 s with no touch later the window is
fullscreen again ("fullscreen again after the edge swipe", the fullscreen size). A tap in the middle stays an ordinary tap."""
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
ENV = dict(instance.ENV, APKLAYER_JOPTS='-Dapklayer.runms=20000 -Dapklayer.debugkeys=true '
           '-Dapklayer.script=5000:hover:1,0+8000:hover:2,0+9500:hover:3,0+10500:hover:4,0+17000:hover:5,0')
CONFIGURE = re.compile(r'configure content (\d+x\d+) @')
LOG = instance.STATE / 'shell.log'
checks = []


def shell():
    return LOG.read_text(errors='replace') if LOG.exists() else ''


def full():
    """The window's last configure is the fullscreen one (the content size of the first configure after "fullscreen on"; the title bar
    cannot tell: with server-side decorations, KWin's, it is 0 in every state)."""
    text = shell()
    at = text.index('fullscreen on')
    sizes = [(m.start(), m[1]) for m in CONFIGURE.finditer(text) if PKG in text[:m.start()].rsplit('\n', 1)[-1]]
    return sizes[-1][1] == next(size for pos, size in sizes if pos > at)


instance.stop()
LOG.unlink(missing_ok=True)
app = subprocess.Popen(['bin/apklayer', 'run', PKG], env=ENV, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
seen = []
try:
    selector = selectors.DefaultSelector()
    selector.register(app.stdout, selectors.EVENT_READ)
    end, pending, mark = time.monotonic() + 70, b'', 0
    while True:
        assert selector.select(10) and time.monotonic() < end, 'edge swipe test stalled'
        data = os.read(app.stdout.fileno(), 65536)
        if not data: break
        pending += data
        *lines, pending = pending.split(b'\n')
        for line in (l.decode(errors='replace') for l in lines):
            seen.append(line)
            if 'script: hover:1,0' in line:
                instance.inject(PKG, 'focus', 'key:87')
            elif 'script: hover:2,0' in line:
                assert 'fullscreen on' in shell() and full(), 'F11 did not make the window fullscreen'
                instance.inject(PKG, 'press:4,300', 'move:4,340', 'move:5,420', 'release:5,420')  # along the edge: the app's
                assert 'edge swipe' not in shell(), 'a drag along the edge was taken as an edge swipe'
                mark = len(seen)
                instance.inject(PKG, 'press:4,300', 'move:20,302', 'move:70,305', 'move:120,305', 'release:120,305')  # inward
            elif 'script: hover:3,0' in line:
                assert 'edge swipe, out of fullscreen' in shell(), 'the inward swipe from the edge was not taken'
                assert not full(), 'the window stayed fullscreen after the edge swipe'
                swipe = [l for l in seen[mark:] if '[apklayer] touch action=' in l]
                downs = [i for i, l in enumerate(swipe) if 'ACTION_DOWN' in l]  # the drag's lines may come after the mark: the last down's
                swipe = swipe[downs[-1]:] if downs else swipe
                assert any('ACTION_CANCEL' in l for l in swipe), 'the app did not get ACTION_CANCEL: %s' % swipe
                assert not any('ACTION_UP' in l for l in swipe), 'the app got the swipe\'s ACTION_UP: %s' % swipe
                checks.append('edge swipe leaves fullscreen, the app gets a cancel')
            elif 'script: hover:4,0' in line:
                assert 'fullscreen again' not in shell(), 'fullscreen came back too early'
                mark = len(seen)
                instance.inject(PKG, 'tap:300,300')  # the user works with the window: it stays out for another 3 s
            elif 'script: hover:5,0' in line:
                assert 'fullscreen again after the edge swipe' in shell() and full(), 'not fullscreen again'
                tap = [l for l in seen[mark:] if '[apklayer] touch action=' in l]
                assert any('ACTION_UP' in l for l in tap), 'the tap did not reach the app: %s' % tap
                checks.append('fullscreen again 3 s after the last touch')
    assert app.wait() == 0, 'app failed'
    assert len(checks) == 2, checks
    print('PASS %s' % ', '.join(checks), flush=True)
finally:
    instance.stop()
    if app.poll() is None: os.killpg(app.pid, signal.SIGKILL)
    app.wait()
    left = instance.mine('[a]pklayer.host.Main|[b]tload|[a]pklayer-shell')
    assert not left, left
