#!/usr/bin/env python3
"""navbar-haptic: Back on the shell's navigation bar gives Android's light click (HapticFeedbackConstants.VIRTUAL_KEY: the hub's vibrator, the
vibrator HAL's CLICK effect), a Back from the keyboard (Esc, Alt+Left) or the mouse's back button does not. The instance has the fake vibrator
backend (providers set vibrator fake, whose log counts the HAL's on() calls); hello-input counts Backs (-Dhello.backlog); the window is mobile
style (the bar on any compositor) and a desktop-style one with `navbar on` for the bar of desktop windows. The fire-and-forget click must not hold the
key back: the app counts the Back whatever the vibrator does."""
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
ENV = dict(instance.ENV, APKLAYER_DEBUG_INPUT='1')
VIB = instance.STATE / 'vibrator.log'
TICK = 3000
results = []
backs = [0]


def case(name, ok, detail=''):
    results.append(ok)
    print('%s %s %s' % ('OK  ' if ok else 'FAIL', name, detail), flush=True)


def apklayer(*args):
    return subprocess.run(['bin/apklayer', *args], env=ENV, capture_output=True, text=True, timeout=60)


def clicks():
    """The HAL's on() calls so far (the fake backend's log)."""
    return len(re.findall(r'vibrator: on \d+ ms', VIB.read_text(errors='replace'))) if VIB.exists() else 0


def geometry():
    text = (instance.STATE / 'shell.log').read_text(errors='replace')
    m = [m for m in re.finditer(r'configure content (\d+)x(\d+) @\d+% .*?navigation bar (\d+) logical', text) if PKG in text[:m.start()].rsplit('\n', 1)[-1]][-1]
    return int(m[1]), int(m[2]), int(m[3])


state = {}


def mark():
    state['backs'], state['clicks'] = backs[0], clicks()


def bar_press():
    w, h, nav = geometry()
    assert nav > 0, 'no navigation bar: window not styled'
    mark()
    instance.inject(PKG, 'tap:%d,%d' % (w // 4, h - nav // 2))


def check_bar_press(what):
    case('%s: Back reaches the app' % what, backs[0] == state['backs'] + 1, 'Back count %d -> %d' % (state['backs'], backs[0]))
    case('%s: one click at the vibrator HAL' % what, clicks() == state['clicks'] + 1, 'HAL calls %d -> %d' % (state['clicks'], clicks()))


def keyboard_back():
    mark()
    instance.inject(PKG, 'key:1', 'combo:alt+105', 'xclick:50,50')  # Esc, Alt+Left, the mouse's back button


def check_keyboard_back():
    case('Esc, Alt+Left and the mouse back button: Back, no click', backs[0] == state['backs'] + 3 and clicks() == state['clicks'],
         'Back count %d -> %d, HAL calls %d -> %d' % (state['backs'], backs[0], state['clicks'], clicks()))


def desktop_bar():
    assert apklayer('settings', 'set', 'window-mode', 'desktop').returncode == 0
    assert apklayer('settings', 'set', 'navbar', 'on').returncode == 0


SEQUENCE = [
    ('', bar_press), ('', lambda: check_bar_press('mobile style')),
    ('', keyboard_back), ('', check_keyboard_back),
    ('desktop', lambda: None), ('', bar_press), ('', lambda: check_bar_press('desktop window, navbar on')),
]
ticks = [(6000 + TICK * i, 'hover:%d,0' % (i + 1)) for i in range(len(SEQUENCE) + 1)]
ENV['APKLAYER_JOPTS'] = '-Dhello.backlog=1 -Dapklayer.runms=%d -Dapklayer.script=' % (ticks[-1][0] + 4000) + '+'.join('%d:%s' % s for s in ticks)
instance.stop()
(instance.STATE / 'shell.log').unlink(missing_ok=True)
app = None
try:
    assert apklayer('providers', 'set', 'vibrator', 'fake').returncode == 0
    assert apklayer('settings', 'set', 'window-mode', 'mobile').returncode == 0
    app = subprocess.Popen(['bin/apklayer', 'run', PKG], env=ENV, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
    selector = selectors.DefaultSelector()
    selector.register(app.stdout, selectors.EVENT_READ)
    pending, end = b'', time.monotonic() + 120
    with open(instance.OUT / 'navbar-haptic.log', 'wb') as log:
        while selector.select(40) and time.monotonic() < end:
            data = os.read(app.stdout.fileno(), 65536)
            if not data: break
            log.write(data)
            pending += data
            *lines, pending = pending.split(b'\n')
            for raw in lines:
                line = raw.decode(errors='replace')
                if '[hello-input] back pressed' in line: backs[0] += 1
                m = re.search(r'script: hover:(\d+),0', line)
                if not m: continue
                step = int(m[1])
                if step >= 2:
                    try: SEQUENCE[step - 2][1]()
                    except Exception as e: case('tick %d' % step, False, 'error: %r' % (e,))
                if step - 1 < len(SEQUENCE) and SEQUENCE[step - 1][0] == 'desktop':
                    try: desktop_bar()
                    except Exception as e: case('desktop style, navbar on', False, 'error: %r' % (e,))
    ok = results and all(results) and len(results) >= 5
    print('%s navigation bar haptics: %d cases' % ('PASS' if ok else 'FAIL', len(results)), flush=True)
    sys.exit(0 if ok else 1)
finally:
    for d in ('window-mode', 'navbar'): apklayer('settings', 'unset', d)
    apklayer('providers', 'unset', 'vibrator')
    instance.stop()
    if app and app.poll() is None: os.killpg(app.pid, signal.SIGKILL)
