#!/usr/bin/env python3
"""A stopped activity's windows (input group): hello.input shows a full-window dialog and is then covered by its Covered activity. Taps
must reach the covering activity, never the dialog of the stopped one (Android's window manager hides it; Google Meet crashed when a tap
reached a stopped activity's fragments); a dialog the stopped activity shows later is hidden too. A start of an intent nobody handles leaves the
covering activity resumed.
`stopped-windows.py translucent`: the activity is covered by a translucent one instead, so it stays paused and visible, and shows a second
dialog while paused; both of its dialogs stay below the newer activity (ActivityRecord order), on the screen and for taps, also after a
configuration change relaunched both activities (a relaunched activity keeps its place in the task)."""
import os
from pathlib import Path
import selectors
import signal
import subprocess
import sys
import time

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
import instance  # noqa: E402  (changes into the repo root)
import pixels  # noqa: E402
TRANSLUCENT = sys.argv[1:] == ['translucent']
PKG = 'hello.input'
ENV = dict(instance.ENV, APKLAYER_DISPLAY_WIDTH='480', APKLAYER_DISPLAY_HEIGHT='600')
ENV['APKLAYER_JOPTS'] = ('-Dhello.translucent=1 -Dapklayer.runms=12000 -Dapklayer.script=5000:hover:1,0+9000:hover:2,0' if TRANSLUCENT else
                         '-Dhello.covered=1 -Dapklayer.runms=13000 -Dapklayer.script=5000:hover:1,0+8000:hover:2,0+11000:hover:3,0')
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
            assert selector.select(10) and time.monotonic() < end, 'stopped-windows test stalled'
            data = os.read(app.stdout.fileno(), 65536)
            if not data: break
            log.write(data)
            pending += data
            *lines, pending = pending.split(b'\n')
            for raw in lines:
                line = raw.decode(errors='replace')
                if '[apklayer]' not in line and '[hello-input]' not in line: continue
                if 'late dialog windowVisibility' in line and not TRANSLUCENT and 'paused' not in line:  # the stopped activity's late dialog is not on the screen
                    im = pixels.rgb(instance.capture('late dialog of the stopped activity', PKG))
                    r, g, b = im.getpixel((im.width // 2, im.height // 2))[:3]
                    assert not (g > 150 and r < 90 and b < 90), 'the late dialog of the stopped activity is on the screen'
                seen.append(line)
                print(line, flush=True)
                if TRANSLUCENT and ('script: hover:1,0' in line or 'script: hover:2,0' in line):
                    assert count('translucent activity resumed'), 'the translucent activity is not up'
                    if 'hover:2' in line: assert count('configuration change: recreated hello.input.Main') == 1, 'the activity below was not relaunched'
                    im = pixels.rgb(instance.capture('translucent over the paused dialogs' + (' relaunched' if 'hover:2' in line else ''), PKG))
                    r, g, b = im.getpixel((im.width // 2, im.height // 2))[:3]
                    assert r > 150 and g < 90 and b < 90, 'the paused activity or its dialog is drawn over the translucent one: %s' % ((r, g, b),)
                    instance.inject(PKG, 'tap:%d,%d' % CENTER)
                    # a configuration change relaunches both (no configChanges): the one below must keep its place under the translucent one
                    if 'hover:1' in line: subprocess.run(['bin/apklayer', 'settings', 'set', 'font-scale', '1.3'], env=ENV, stdout=subprocess.DEVNULL, check=True)
                elif 'script: hover:1,0' in line or 'script: hover:2,0' in line:
                    assert count('covering activity resumed') == 1, 'the covering activity is not up (or was resumed again)'
                    instance.inject(PKG, 'tap:%d,%d' % CENTER)
    assert app.wait() == 0, 'input app failed'
    if TRANSLUCENT:
        assert not count("paused activity's dialog tapped"), "a tap reached a dialog of the activity below the translucent one"
        assert count('translucent activity tapped') == 2, 'the taps did not reach the translucent activity'
        assert not count('paused activity stopped'), 'the activity behind a translucent one was stopped'
        assert count('paused late dialog windowVisibility=0') == 1, 'a dialog of the paused (visible) activity is not visible'
        print('PASS a translucent activity covers the dialogs of the paused one below it, those shown while paused too, and the relaunched one', flush=True)
        sys.exit(0)
    assert not count('covered dialog tapped'), 'a tap reached the stopped activity\'s dialog'
    assert count('late dialog windowVisibility=8 isShown=true') == 1, 'a dialog shown after its activity stopped was not hidden (GONE by app visibility)'
    assert count('covering activity tapped, stateSaved=false') == 2, 'the taps did not reach the covering activity'
    assert not count('covering activity paused') and not count('covering activity stopped'), 'the unhandled start changed the lifecycle'
    print('PASS taps go to the covering activity, not to the stopped one\'s dialog; an unhandled start leaves the lifecycle as it is', flush=True)
finally:
    if TRANSLUCENT: subprocess.run(['bin/apklayer', 'settings', 'unset', 'font-scale'], env=ENV, stdout=subprocess.DEVNULL)
    instance.stop()
    if app.poll() is None: os.killpg(app.pid, signal.SIGKILL)
    app.wait()
    left = instance.mine('[a]pklayer.host.Main|[b]tload|[a]pklayer-shell')
    assert not left, left
