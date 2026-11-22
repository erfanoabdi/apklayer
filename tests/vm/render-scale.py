#!/usr/bin/env python3
"""The per-app render scale (`apklayer settings set render-scale[.<package>] 25..200`, bridge/shell/geometry.c window_send_configure): the app renders
into a display of that share of its window's pixels, windowed or fullscreen, and the window shows it scaled over the whole window.
APKLAYER_RENDER_SCALE=50 as cli/run.cpp sets it from the setting. UiBench goes fullscreen (script step fullscreen:1) and leaves (fullscreen:0).
Checked at each of the three stages: the configure says "render scale 50% of WxH px" (the window's own pixels), the picture is half those pixels,
in the window's shape (within a pixel), and the logical size is the window's (dp unchanged)."""
import os
from pathlib import Path
import re
import selectors
import signal
import subprocess
import sys
import time

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
import instance  # noqa: E402  (changes into the repo root)
PKG = 'com.android.test.uibench'
PCT = 50
ENV = dict(instance.ENV, APKLAYER_RENDER_SCALE=str(PCT))
STEPS = ((5000, 'navinset'), (5200, 'hover:1,0'), (6000, 'fullscreen:1'), (9000, 'hover:2,0'), (10000, 'fullscreen:0'), (13000, 'hover:3,0'), (14500, 'hover:4,0'))
ENV['APKLAYER_JOPTS'] = '-Dapklayer.debugkeys=true -Dapklayer.runms=16000 -Dapklayer.script=' + '+'.join('%d:%s' % s for s in STEPS)
CONFIGURE = re.compile(r'configure content (\d+)x(\d+) @(\d+)% \((\d+)x(\d+) px\)(?:, render scale \d+% of (\d+)x(\d+) px)?, title bar')
SHELL_LOG = instance.STATE / 'shell.log'
checks = []


def configure():
    """The shell's last CONFIGURE of this app's window: (logical w, h, told scale, the window's own px w, h; None without a render scale)."""
    text = SHELL_LOG.read_text(errors='replace')
    c = [(int(m[1]), int(m[2]), int(m[3]), int(m[6]) if m[6] else None, int(m[7]) if m[7] else None)
         for m in CONFIGURE.finditer(text) if PKG in text[:m.start()].rsplit('\n', 1)[-1]]
    return c[-1]


def picture(name):
    """The app's picture must be PCT percent of the window's own pixels, in the window's shape; returns the checked text."""
    for _ in range(3):  # the app redraws at its new size a moment after the configure
        size = Image.open(instance.capture(name, PKG)).size
        w, h, scale, ww, wh = configure()
        assert ww and wh, 'no render scale in the configure %s' % (configure(),)
        if abs(size[0] - ww * PCT / 100) <= 1.5 and abs(size[1] - wh * PCT / 100) <= 1.5: break
        time.sleep(1.5)
    assert abs(size[0] - ww * PCT / 100) <= 1.5 and abs(size[1] - wh * PCT / 100) <= 1.5, 'picture %s px, expected %d%% of %dx%d' % (size, PCT, ww, wh)
    assert abs(size[0] * wh - size[1] * ww) <= max(ww, wh) * 2, 'aspect ratio: picture %s, window %dx%d' % (size, ww, wh)
    return '%s: %dx%d of the window\'s %dx%d' % (name, size[0], size[1], ww, wh)


instance.stop()
SHELL_LOG.unlink(missing_ok=True)
app = subprocess.Popen(['bin/apklayer', 'run', PKG], env=ENV, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
try:
    selector = selectors.DefaultSelector()
    selector.register(app.stdout, selectors.EVENT_READ)
    end = time.monotonic() + 70
    pending = b''
    with open(instance.OUT / 'render-scale.log', 'wb') as log:
        while True:
            assert selector.select(10) and time.monotonic() < end, 'render-scale test stalled'
            data = os.read(app.stdout.fileno(), 65536)
            if not data: break
            log.write(data)
            pending += data
            *lines, pending = pending.split(b'\n')
            for raw in lines:
                line = raw.decode(errors='replace')
                if 'script: hover:' not in line: continue
                stage = {'hover:1,0': 'windowed', 'hover:2,0': 'fullscreen', 'hover:3,0': 'left fullscreen'}
                for step, name in stage.items():
                    if step in line: checks.append(picture(name))
    assert app.wait() == 0, 'app failed'
    assert len(checks) == 3, checks
    print('PASS render-scale: %s' % ', '.join(checks), flush=True)
finally:
    instance.stop()
    if app.poll() is None: os.killpg(app.pid, signal.SIGKILL)
    app.wait()
    left = instance.mine('[a]pklayer.host.Main|[b]tload|[a]pklayer-shell')
    assert not left, left
