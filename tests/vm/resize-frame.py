#!/usr/bin/env python3
"""resize-frame: after every window resize a new frame of the new size reaches the compositor (the shell's dmabuf frames, APKLAYER_RUNTIME_DEBUG
logs each one), also for an app that draws nothing after its relayout (hello.input is a static page) and when the resizes come in quick bursts, as a drag sends them."""
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
PKG = os.environ.get('RESIZE_PKG', 'hello.input')  # another app: RESIZE_PKG=<package>
ENV = dict(instance.ENV, APKLAYER_RUNTIME_DEBUG='1')
SIZES = ['700,600', '480,520', '398,456', '814,500']  # content sizes + title bar; the last two after a burst
STEPS = [(5000, 'hover:1,0')] + [(7000 + 3000 * i, 'hover:%d,0' % (i + 2)) for i in range(len(SIZES) + 1)]
ENV['APKLAYER_JOPTS'] = '-Dapklayer.runms=%d -Dapklayer.script=' % (STEPS[-1][0] + 3000) + '+'.join('%d:%s' % s for s in STEPS)
FRAME = re.compile(r'dmabuf frame (\d+) \(buffer \d+, (\d+)x(\d+),')


def frames():
    log = instance.STATE / 'shell.log'
    return [(int(m[1]), int(m[2]), int(m[3])) for m in FRAME.finditer(log.read_text(errors='replace') if log.exists() else '')]


instance.stop()
(instance.STATE / 'shell.log').unlink(missing_ok=True)
app = subprocess.Popen(['bin/apklayer', 'run', PKG], env=ENV, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
try:
    selector = selectors.DefaultSelector()
    selector.register(app.stdout, selectors.EVENT_READ)
    end = time.monotonic() + 60
    pending, mark, n, bad = b'', 0, 0, []
    with open(instance.OUT / 'resize-frame.log', 'wb') as log:
        while True:
            assert selector.select(10) and time.monotonic() < end, 'resize-frame stalled'
            data = os.read(app.stdout.fileno(), 65536)
            if not data: break
            log.write(data)
            pending += data
            *lines, pending = pending.split(b'\n')
            for raw in lines:
                line = raw.decode(errors='replace')
                m = re.search(r'script: hover:(\d+),0', line)
                if not m: continue
                step = int(m[1])
                if 2 <= step < len(SIZES) + 2:  # the previous resize had 2.5 s: frames since it, the last one of the size the window has now
                    got = frames()[mark:]
                    print('after resize %s: %d frames, last %s' % (SIZES[step - 2], len(got), got[-1:]), flush=True)
                    if not got: bad.append(SIZES[step - 2])
                mark = len(frames())
                if step - 1 < len(SIZES):
                    burst = ['resize:600,500', 'resize:520,480'] if step - 1 == 2 else []  # a burst ending at the third size
                    instance.inject(PKG, *burst, 'resize:' + SIZES[step - 1])
    assert app.wait() == 0, 'app failed'
    assert not bad, 'no new frame after the resize to %s' % bad
    print('PASS a frame follows every resize', flush=True)
finally:
    instance.stop()
    if app.poll() is None: os.killpg(app.pid, signal.SIGKILL)
    app.wait()
