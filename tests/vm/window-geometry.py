#!/usr/bin/env python3
"""Window geometry and scale of one instance, without the desktop (instance.inject: the compositor's events through the shell's test input).
A real app (UiBench) under the compositor's preferred scale 200% and 150%, maximized, restored, and reshaped by an orientation request, with
a stale floating configure behind it. Checked: the shell's CONFIGURE (logical content size, scale, title bar in logical and buffer pixels),
the app's density, and the pixel size of the app's own picture (`apklayer screenshot`: the virtual display renders size * scale pixels,
so the app is sharp, not upscaled)."""
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
DECOR_H = 46  # bridge/shell/decor.h
ENV = dict(instance.ENV)
STEPS = ((5000, 'hover:1,0'), (8000, 'hover:2,0'), (11000, 'hover:3,0'), (14000, 'hover:4,0'), (17000, 'hover:5,0'), (17500, 'orient:0'),
         (20000, 'hover:6,0'), (22000, 'hover:7,0'))
ENV['APKLAYER_JOPTS'] = '-Dapklayer.runms=24000 -Dapklayer.script=' + '+'.join('%d:%s' % s for s in STEPS)
CONFIGURE = re.compile(r'configure content (\d+)x(\d+) @(\d+)% \((\d+)x(\d+) px\), title bar (\d+) logical = (\d+) px(, maximized)?')
seen, checks = [], []


def configures():
    """The shell's CONFIGUREs of this app's window so far: (w, h, scale, px w, px h, bar logical, bar px, maximized)."""
    log = instance.STATE / 'shell.log'
    text = log.read_text(errors='replace') if log.exists() else ''
    return [tuple(int(g) for g in m.groups()[:7]) + (bool(m.group(8)),) for m in CONFIGURE.finditer(text) if PKG in text[:m.start()].rsplit('\n', 1)[-1]]


def expect(w, h, scale, maximized=False):
    """The last CONFIGURE is w x h logical at scale, the title bar DECOR_H logical at that scale, the app took it (density) and its picture
    has the matching pixel size."""
    c = configures()[-1]
    px = (w * scale // 100, h * scale // 100)
    assert c == (w, h, scale) + px + (DECOR_H, (DECOR_H * scale + 99) // 100, maximized), 'configure %s, expected %dx%d @%d%%' % (c, w, h, scale)
    assert any('configure %dx%d @%d%%' % (w, h, scale) in line and '@%ddpi' % (160 * scale // 100) in line for line in seen), \
        'the app did not take %dx%d @%d%%' % (w, h, scale)
    shot = Image.open(instance.capture('geometry %dx%d@%d' % (w, h, scale), PKG)).size
    assert abs(shot[0] - px[0]) <= 1 and abs(shot[1] - px[1]) <= 1, 'the app renders %s px, expected %s' % (shot, px)
    checks.append('%dx%d@%d%s' % (w, h, scale, ' maximized' if maximized else ''))
    print('OK %dx%d @%d%%: %dx%d px, title bar %d px' % (w, h, scale, *shot, c[6]), flush=True)


instance.stop()
(instance.STATE / 'shell.log').unlink(missing_ok=True)  # the shell (stopped) starts a new one with the app
app = subprocess.Popen(['bin/apklayer', 'run', PKG], env=ENV, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
try:
    selector = selectors.DefaultSelector()
    selector.register(app.stdout, selectors.EVENT_READ)
    end = time.monotonic() + 60
    pending, first, landscape = b'', None, None
    with open(instance.OUT / 'window-geometry.log', 'wb') as log:
        while True:
            assert selector.select(10) and time.monotonic() < end, 'geometry test stalled'
            data = os.read(app.stdout.fileno(), 65536)
            if not data: break
            log.write(data)
            pending += data
            *lines, pending = pending.split(b'\n')
            for raw in lines:
                line = raw.decode(errors='replace')
                if '[apklayer]' not in line: continue
                seen.append(line)
                if 'script: hover:1,0' in line:
                    first = configures()[-1]
                    expect(*first[:3])
                    instance.inject(PKG, 'scale:200')
                elif 'script: hover:2,0' in line:
                    expect(first[0], first[1], 200)
                    instance.inject(PKG, 'scale:150')
                elif 'script: hover:3,0' in line:
                    expect(first[0], first[1], 150)
                    instance.inject(PKG, 'maximize:900,700')
                elif 'script: hover:4,0' in line:
                    expect(900, 700 - DECOR_H, 150, maximized=True)
                    instance.inject(PKG, 'unmaximize')
                elif 'script: hover:5,0' in line:
                    expect(first[0], first[1], 150)  # restored to the floating size
                elif 'script: hover:6,0' in line:
                    landscape = configures()[-1]
                    assert landscape[0] > landscape[1], 'the landscape request did not reshape the window: %s' % (landscape,)
                    # turned, the window fills 85% of the work area as the first (portrait) one does: grown, not only shrunk (bridge/shell/geometry.c fit_size)
                    bw, bh = (int(n) for n in re.search(r'first configure: display \d+x\d+, bounds (\d+)x(\d+)', (instance.STATE / 'shell.log').read_text(errors='replace')).groups())
                    assert bw <= 0 or abs(landscape[0] - bw * 85 // 100) <= 1 or abs(landscape[1] - (bh * 85 // 100 - DECOR_H)) <= 1, \
                        'the landscape window %s does not fill 85%% of the %dx%d work area' % (landscape[:2], bw, bh)
                    expect(*landscape[:3])
                    count = len(configures())
                    instance.inject(PKG, 'unmaximize:%d,%d' % (first[0], first[1] + DECOR_H))  # a floating configure the compositor sent before it saw the reshape
                elif 'script: hover:7,0' in line:
                    assert len(configures()) == count, 'a stale floating configure reshaped the window back: %s' % (configures()[-1],)
                    checks.append('stale configure ignored')
    assert app.wait() == 0, 'app failed'
    assert len(checks) == 7, checks
    print('PASS window geometry and scale: %s' % ', '.join(checks), flush=True)
finally:
    instance.stop()
    if app.poll() is None: os.killpg(app.pid, signal.SIGKILL)
    app.wait()
    left = instance.mine('[a]pklayer.host.Main|[b]tload|[a]pklayer-shell')
    assert not left, left
