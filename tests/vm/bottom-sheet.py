#!/usr/bin/env python3
"""Dialogs at the bottom of the screen take taps where they are (hello.input's Sheet, -Dhello.sheet): a floating dialog with gravity BOTTOM
(its button, a tap outside cancels, Back cancels) and a Material-style bottom sheet (a full-screen dimmed dialog: its button, a tap on the
area outside the sheet cancels, a downward drag on its handle dismisses). Google Meet's "Share this joining info" sheet ate every tap."""
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
PKG = 'hello.input'
ENV = dict(instance.ENV, APKLAYER_RUNTIME_DEBUG='1', APKLAYER_DISPLAY_WIDTH='480', APKLAYER_DISPLAY_HEIGHT='600',
           APKLAYER_JOPTS='-Dhello.sheet=1 -Dapklayer.runms=30000')
SHOWN = re.compile(r'sheet (\d) shown sheet=(-?\d+),(-?\d+),(\d+),(\d+) window=(-?\d+),(-?\d+),(\d+),(\d+)')
seen = []
done = set()


def act(stage, x, y, w, h):
    button = (x + w // 2, y + h - 50)  # the button: the dialog's last 100 pixels
    outside = (240, 40)
    if stage in (1, 3):
        instance.inject(PKG, 'tap:%d,%d' % button)
        return outside
    if stage == 2:
        instance.inject(PKG, 'key:1')  # Esc is Back
    else:  # the handle (the sheet's first 60 pixels), dragged down
        hx, hy = x + w // 2, y + 30
        instance.inject(PKG, 'press:%d,%d' % (hx, hy), *('move:%d,%d' % (hx, hy + d) for d in (20, 50, 90, 130)), 'release:%d,%d' % (hx, hy + 130))
    return None


instance.stop()
app = subprocess.Popen(['bin/apklayer', 'run', PKG], env=ENV, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
try:
    selector = selectors.DefaultSelector()
    selector.register(app.stdout, selectors.EVENT_READ)
    end = time.monotonic() + 45
    pending, outside = b'', None
    with open(instance.OUT / 'bottom-sheet.log', 'wb') as log:
        while True:
            assert selector.select(5) and time.monotonic() < end, 'bottom sheet test stalled'
            data = os.read(app.stdout.fileno(), 65536)
            if not data: break
            log.write(data)
            pending += data
            *lines, pending = pending.split(b'\n')
            for raw in lines:
                line = raw.decode(errors='replace')
                if '[hello-input]' not in line and 'Exception' not in line: continue
                seen.append(line)
                print(line, flush=True)
                m = SHOWN.search(line)
                if m:
                    stage, x, y, w, h = (int(n) for n in m.groups()[:5])
                    assert y > 300, 'sheet %d is not at the bottom: %s' % (stage, line)
                    outside = act(stage, x, y, w, h)
                elif outside and re.search(r'sheet [13] button tapped', line):
                    done.add(line.split()[2] + ' button')
                    instance.inject(PKG, 'tap:%d,%d' % outside)
                    outside = None
                elif re.search(r'sheet \d (cancelled|dragged)', line):
                    done.add(' '.join(line.split()[2:4]))
                elif 'sheet stages done' in line:
                    instance.stop()
    want = {'1 button', '1 cancelled', '2 cancelled', '3 button', '3 cancelled', '4 dragged'}
    assert done == want, 'missing: %s' % (want - done)
    print('PASS bottom dialogs: button taps, an outside tap and Back cancel, a downward drag dismisses the sheet', flush=True)
finally:
    instance.stop()
    if app.poll() is None: os.killpg(app.pid, signal.SIGKILL)
    app.wait()
    left = instance.mine('[a]pklayer.host.Main|[b]tload|[a]pklayer-shell')
    assert not left, left
