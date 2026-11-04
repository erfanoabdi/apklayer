#!/usr/bin/env python3
"""Dialogs at the bottom of the screen take taps where they are (hello.input's Sheet, -Dhello.sheet): a floating dialog with gravity BOTTOM
(its button, a tap outside cancels, Back cancels) and a Material-style bottom sheet (a full-screen dimmed dialog: its button, a tap on the
area outside the sheet cancels, a downward drag on its handle dismisses). Google Meet's "Share this joining info" sheet ate every tap,
and stayed above the green room that its Join started (a newer activity covers an older one's dialogs)."""
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
import pixels  # noqa: E402
PKG = 'hello.input'
ENV = dict(instance.ENV, APKLAYER_RUNTIME_DEBUG='1', APKLAYER_DISPLAY_WIDTH='480', APKLAYER_DISPLAY_HEIGHT='600',
           APKLAYER_JOPTS='-Dhello.sheet=1 -Dapklayer.runms=30000')
SHOWN = re.compile(r'sheet (\d) shown sheet=(-?\d+),(-?\d+),(\d+),(\d+) window=(-?\d+),(-?\d+),(\d+),(\d+)')
seen = []
done = set()
shown = gone = over = None


def level(shot):
    """Brightness where the sheet's handle is while it is up (light gray over the dark activity)."""
    im = pixels.rgb(shot)
    return sum(im.getpixel((im.width // 2, im.height * 470 // 600))) // 3


def act(stage, x, y, w, h):
    button = (x + w // 2, y + h - 50)  # the button: the dialog's last 100 pixels
    if stage in (1, 3, 5, 6):
        instance.inject(PKG, '%s:%d,%d' % ('click' if stage == 3 else 'tap', *button))  # the mouse (the pointer path) in stage 3
        return 'key:1' if stage >= 5 else '%s:240,40' % ('click' if stage == 3 else 'tap')  # then outside the sheet, or Back
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
                    if stage == 3: shown = level(instance.capture('sheet shown', PKG))
                    if stage == 6: over = level(instance.capture('sheet over popup', PKG))
                    outside = act(stage, x, y, w, h)
                elif 'covering activity shown' in line:  # stage 5: the newer activity is above the older one's sheet, for the screen and for taps
                    im = pixels.rgb(instance.capture('activity over the sheet', PKG))
                    r, g, b = im.getpixel((im.width // 2, im.height * 470 // 600))[:3]
                    assert r > 150 and g < 90 and b < 90, 'the sheet of the activity below is drawn over the covering activity: %s' % ((r, g, b),)
                    instance.inject(PKG, 'tap:240,560')
                elif 'covering activity tapped' in line:
                    done.add('covering tapped')
                elif outside and re.search(r'sheet [1356] button tapped', line):
                    done.add(line.split()[2] + ' button')
                    instance.inject(PKG, outside)
                    outside = None
                elif re.search(r'sheet \d (cancelled|dragged)', line):
                    done.add(' '.join(line.split()[2:4]))
                elif 'sheet stages done' in line:
                    gone = level(instance.capture('sheet gone', PKG))
                    instance.stop()
    want = {'1 button', '1 cancelled', '2 cancelled', '3 button', '3 cancelled', '4 dragged', 'covering tapped', '5 button', '5 cancelled', '6 button', '6 cancelled'}
    assert done == want, 'missing: %s' % (want - done)
    assert not any('popup under the sheet tapped' in l for l in seen), 'a tap on the sheet reached a popup of the activity below it'
    print('brightness at the handle of sheet 3: %d, after the sheets: %d' % (shown, gone), flush=True)
    assert shown > gone + 40, 'the sheet stays on the screen after the dialogs went away'
    print('brightness at the handle of sheet 6 over the popup: %d' % over, flush=True)
    assert abs(over - shown) < 10, 'the popup of the activity is drawn over the sheet'
    print('PASS bottom dialogs: button taps, an outside tap and Back cancel, a downward drag dismisses the sheet; the sheet is gone from the screen; a popup of the activity below the sheet gets none of its taps; a newer activity covers the sheet', flush=True)
finally:
    instance.stop()
    if app.poll() is None: os.killpg(app.pid, signal.SIGKILL)
    app.wait()
    left = instance.mine('[a]pklayer.host.Main|[b]tload|[a]pklayer-shell')
    assert not left, left
