#!/usr/bin/env python3
"""A stopped activity's windows (input group): hello.input shows a full-window dialog and is then covered by its Covered activity. Taps
must reach the covering activity, never the dialog of the stopped one (Android's window manager hides it; Google Meet crashed when a tap
reached a stopped activity's fragments). A start of an intent nobody handles leaves the covering activity resumed."""
import os
from pathlib import Path
import selectors
import signal
import subprocess
import sys
import time

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
import instance  # noqa: E402  (changes into the repo root)
PKG = 'hello.input'
ENV = dict(instance.ENV, APKLAYER_DISPLAY_WIDTH='480', APKLAYER_DISPLAY_HEIGHT='600')
ENV['APKLAYER_JOPTS'] = '-Dhello.covered=1 -Dapklayer.runms=13000 -Dapklayer.script=5000:hover:1,0+8000:hover:2,0+11000:hover:3,0'
CENTER = (240, 300)
seen = []


def count(text):
    return sum(text in line for line in seen)


instance.stop()
app = subprocess.Popen(['bin/apklayer', 'run', PKG], env=ENV, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
try:
    selector = selectors.DefaultSelector()
    selector.register(app.stdout, selectors.EVENT_READ)
    end = time.monotonic() + 40
    pending = b''
    with open(instance.OUT / 'stopped-windows.log', 'wb') as log:
        while True:
            assert selector.select(5) and time.monotonic() < end, 'stopped-windows test stalled'
            data = os.read(app.stdout.fileno(), 65536)
            if not data: break
            log.write(data)
            pending += data
            *lines, pending = pending.split(b'\n')
            for raw in lines:
                line = raw.decode(errors='replace')
                if '[apklayer]' not in line and '[hello-input]' not in line: continue
                seen.append(line)
                print(line, flush=True)
                if 'script: hover:1,0' in line or 'script: hover:2,0' in line:
                    assert count('covering activity resumed') == 1, 'the covering activity is not up (or was resumed again)'
                    instance.inject(PKG, 'tap:%d,%d' % CENTER)
    assert app.wait() == 0, 'input app failed'
    assert not count('covered dialog tapped'), 'a tap reached the stopped activity\'s dialog'
    assert count('covering activity tapped, stateSaved=false') == 2, 'the taps did not reach the covering activity'
    assert not count('covering activity paused') and not count('covering activity stopped'), 'the unhandled start changed the lifecycle'
    print('PASS taps go to the covering activity, not to the stopped one\'s dialog; an unhandled start leaves the lifecycle as it is', flush=True)
finally:
    instance.stop()
    if app.poll() is None: os.killpg(app.pid, signal.SIGKILL)
    app.wait()
    left = instance.mine('[a]pklayer.host.Main|[b]tload|[a]pklayer-shell')
    assert not left, left
