#!/usr/bin/env python3
"""The per-app render scale (`apklayer settings set render-scale[.<package>] 10..100`, bridge/shell/geometry.c window_send_configure): a game that
is fullscreen renders into a smaller display and the window shows it scaled up; windowed, or left fullscreen, the app has its full size again.
APKLAYER_RENDER_SCALE=50 as cli/run.cpp sets it from the setting. UiBench goes fullscreen (script step fullscreen:1) and leaves (fullscreen:0).
Checked: windowed the configure has no render scale and the picture is the window's pixels; fullscreen the configure says "render scale" with
half the scale, the same logical size, and the picture is half the pixels in the screen's aspect ratio (within a pixel); left, the full size again."""
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
CONFIGURE = re.compile(r'configure content (\d+)x(\d+) @(\d+)% \((\d+)x(\d+) px\)(, render scale)?, title bar')
SHELL_LOG = instance.STATE / 'shell.log'
checks = []


def configure():
    """The shell's last CONFIGURE of this app's window: (w, h, scale, render scale told)."""
    text = SHELL_LOG.read_text(errors='replace')
    c = [(int(m[1]), int(m[2]), int(m[3]), bool(m[6])) for m in CONFIGURE.finditer(text) if PKG in text[:m.start()].rsplit('\n', 1)[-1]]
    return c[-1]


def picture(name):
    for _ in range(3):  # the app redraws at its new size a moment after the configure
        size = Image.open(instance.capture(name, PKG)).size
        w, h, scale, _ = configure()
        if abs(size[0] - w * scale // 100) <= 1 and abs(size[1] - h * scale // 100) <= 1: return size
        time.sleep(1.5)
    raise AssertionError('picture %s px for configure %s' % (size, configure()))


instance.stop()
SHELL_LOG.unlink(missing_ok=True)
app = subprocess.Popen(['bin/apklayer', 'run', PKG], env=ENV, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
try:
    selector = selectors.DefaultSelector()
    selector.register(app.stdout, selectors.EVENT_READ)
    end = time.monotonic() + 70
    pending, full = b'', None
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
                if 'hover:1,0' in line:
                    w, h, scale, told = configure()
                    assert not told, 'a windowed app was told a render scale: %s' % (configure(),)
                    size = picture('windowed')
                    full = (w, h, scale, size)
                    checks.append('windowed %dx%d @%d%%' % (w, h, scale))
                elif 'hover:2,0' in line:
                    w, h, scale, told = configure()
                    assert told and scale == (full[2] * PCT + 50) // 100 and (w, h) != (full[0], full[1]), 'fullscreen: configure %s, windowed %s' % (configure(), full)
                    size = picture('fullscreen')
                    assert abs(size[0] * 2 - w * full[2] // 100) <= 2 and abs(size[1] * 2 - h * full[2] // 100) <= 2, 'half of %s expected, picture %s' % ((w, h), size)
                    assert abs(size[0] * h - size[1] * w) <= max(w, h) * 2, 'aspect ratio %s vs %dx%d' % (size, w, h)  # the window's shape, within a pixel
                    checks.append('fullscreen %dx%d @%d%%: picture %dx%d' % (w, h, scale, size[0], size[1]))
                elif 'hover:3,0' in line:
                    w, h, scale, told = configure()
                    assert not told and scale == full[2], 'left fullscreen: configure %s, windowed %s' % (configure(), full)
                    picture('left')
                    checks.append('left fullscreen: full size again')
    assert app.wait() == 0, 'app failed'
    assert len(checks) == 3, checks
    print('PASS render-scale: %s' % ', '.join(checks), flush=True)
finally:
    instance.stop()
    if app.poll() is None: os.killpg(app.pid, signal.SIGKILL)
    app.wait()
    left = instance.mine('[a]pklayer.host.Main|[b]tload|[a]pklayer-shell')
    assert not left, left
