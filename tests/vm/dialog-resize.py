#!/usr/bin/env python3
"""A dialog follows its window (AppHost.hostResize, HostConfiguration.resize): a full-screen dialog with a text field over a real app (UiBench,
script step `dialog`: KuroSDK's login dialog in PGR), then the compositor's scale goes from 100% to 200% (the window moved to another output, as
on a tester's two monitors): the dialog's window must be laid out at the window's new pixel size and density (step `dialogsize`), and the app's
picture must show it (magenta) over the whole window, not a dim rectangle of the old size."""
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
STEPS = ((4000, 'dialog'), (6000, 'dialogsize'), (6100, 'hover:1,0'), (10000, 'dialogsize'), (10100, 'hover:2,0'))
ENV = dict(instance.ENV, APKLAYER_JOPTS='-Dapklayer.runms=14000 -Dapklayer.script=' + '+'.join('%d:%s' % s for s in STEPS))
CONFIGURE = re.compile(r'configure content (\d+)x(\d+) @(\d+)% \((\d+)x(\d+) px\)')
LOG = instance.STATE / 'shell.log'


def window_px():
    """The pixel size of the app's window in the shell's last configure."""
    text = LOG.read_text(errors='replace') if LOG.exists() else ''
    last = [m for m in CONFIGURE.finditer(text) if PKG in text[:m.start()].rsplit('\n', 1)[-1]][-1]
    return int(last[4]), int(last[5])


instance.stop()
LOG.unlink(missing_ok=True)
app = subprocess.Popen(['bin/apklayer', 'run', PKG], env=ENV, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
sizes, checks = [], []
try:
    selector = selectors.DefaultSelector()
    selector.register(app.stdout, selectors.EVENT_READ)
    end, pending = time.monotonic() + 60, b''
    while True:
        assert selector.select(10) and time.monotonic() < end, 'dialog test stalled'
        data = os.read(app.stdout.fileno(), 65536)
        if not data: break
        pending += data
        *lines, pending = pending.split(b'\n')
        for line in (l.decode(errors='replace') for l in lines):
            m = re.search(r'\[apklayer\] dialogsize (\d+)x(\d+) @(\d+)dpi', line)
            if m: sizes.append(tuple(int(g) for g in m.groups()))
            if 'script: hover:1,0' in line:
                w, h = window_px()
                assert sizes and sizes[-1][:2] == (w, h), 'the dialog is %s, its window %dx%d' % (sizes[-1:], w, h)
                checks.append('dialog %dx%d @%ddpi' % sizes[-1])
                instance.inject(PKG, 'scale:200')
            elif 'script: hover:2,0' in line:
                w, h = window_px()
                assert sizes[-1] == (w, h, 320), 'after the scale change the dialog is %s, its window %dx%d @320dpi' % (sizes[-1], w, h)
                shot = Image.open(instance.capture('dialog-resize', PKG)).convert('RGB')
                assert shot.size == (w, h), 'the picture is %s, the window %dx%d' % (shot.size, w, h)
                corner = shot.getpixel((w - 4, h - 4))
                assert corner[0] > 200 and corner[1] < 60 and corner[2] > 200, 'the dialog does not reach the window corner: %s' % (corner,)
                checks.append('after 200%%: dialog %dx%d @320dpi, drawn to the corner' % (w, h))
    assert app.wait() == 0, 'app failed'
    assert len(checks) == 2, checks
    print('PASS %s' % '; '.join(checks), flush=True)
finally:
    instance.stop()
    if app.poll() is None: os.killpg(app.pid, signal.SIGKILL)
    app.wait()
    left = instance.mine('[a]pklayer.host.Main|[b]tload|[a]pklayer-shell')
    assert not left, left
