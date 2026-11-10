#!/usr/bin/env python3
"""The host's on-screen keyboard (the phones' Maliit keyboard, bridge/shell/maliit.c) reaches the app as the IME's insets, not as the
navigation bar (input group): the shell's test input sets the keyboard's area (instance.inject 'ime:<px>', as maliit.c's set_area) and
hello.input (-Dhello.ime) reports what it sees each frame. adjustResize: WindowInsets.isVisible(ime()), getInsets(ime()).bottom = the
keyboard, the content shrinks by it and grows back when it hides; adjustPan: the same insets, the content keeps its size and the window
pans so the focused field near the bottom sits above the keyboard. The navigation bar inset stays the bar's own (none here)."""
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
NUMBER = (240, 448)  # hello.input's number field, low in the 600 px window (content pixels at scale 1, as ime-fields.py)
KEYBOARD = 300
IME = re.compile(r'\[hello-input\] ime visible=(\w+) bottom=(\d+) content=(\d+) focus=(\d+)')


def run(mode):
    env = dict(instance.ENV, APKLAYER_DEBUG_INPUT='1', APKLAYER_DISPLAY_WIDTH='480', APKLAYER_DISPLAY_HEIGHT='600',
               APKLAYER_JOPTS='-Dapklayer.runms=20000 -Dapklayer.debugkeys=true -Dhello.ime=' + mode)
    instance.stop()
    app = subprocess.Popen(['bin/apklayer', 'run', PKG], env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
    selector = selectors.DefaultSelector()
    selector.register(app.stdout, selectors.EVENT_READ)
    seen, pending = [], [b'']
    log = open(instance.OUT / ('ime-insets-%s.log' % mode), 'wb')

    def state(want, after=0):
        """Reads the app's output until an "ime" report from index `after` on matches want(visible, bottom, content, focus); returns it."""
        end = time.monotonic() + 30
        while True:
            for line in seen[after:]:
                m = IME.search(line)
                if m and want(m[1] == 'true', int(m[2]), int(m[3]), int(m[4])): return m[1] == 'true', int(m[2]), int(m[3]), int(m[4])
            after = len(seen)
            assert selector.select(10) and time.monotonic() < end, 'no such ime state; last lines: %s' % seen[-6:]
            data = os.read(app.stdout.fileno(), 65536)
            assert data, 'the app ended; last lines: %s' % seen[-6:]
            log.write(data)
            *lines, pending[0] = (pending[0] + data).split(b'\n')
            for raw in lines:
                line = raw.decode(errors='replace')
                if '[apklayer]' in line or '[hello-input]' in line:
                    seen.append(line)
                    print(line, flush=True)

    try:
        _, _, full, _ = state(lambda v, b, c, f: not v and b == 0 and c > 0)
        if mode == 'pan':
            n = len(seen)
            instance.inject(PKG, 'focus', 'tap:%d,%d' % NUMBER)
            _, _, _, low = state(lambda v, b, c, f: f > 400, n)  # the number field has focus, under where the keyboard comes
            n = len(seen)
            instance.inject(PKG, 'ime:%d' % KEYBOARD)
            # the pan animates (ViewRootImpl's scroller): until the field (48 px) sits above the keyboard in the 579 px window
            _, _, content, focus = state(lambda v, b, c, f: v and b == KEYBOARD and f + 48 <= 580 - KEYBOARD, n)  # (1 px: ViewRootImpl rounds the rect)
            assert content == full, 'adjustPan resized the content: %d -> %d' % (full, content)
            result = 'adjustPan: ime insets %d px, the content keeps %d px, the focused field panned from %d to %d' % (KEYBOARD, full, low, focus)
        else:
            n = len(seen)
            instance.inject(PKG, 'ime:%d' % KEYBOARD)
            _, _, content, _ = state(lambda v, b, c, f: v and b == KEYBOARD, n)
            assert content == full - KEYBOARD, 'adjustResize: the content is %d px with the keyboard, not %d - %d' % (content, full, KEYBOARD)
            n = len(seen)
            instance.inject(PKG, 'ime:0')
            state(lambda v, b, c, f: not v and b == 0 and c == full, n)
            result = 'adjustResize: ime insets %d px, the content %d -> %d px and back when it hides' % (KEYBOARD, full, content)
        shell_log = (instance.STATE / 'shell.log').read_text(errors='replace')
        assert 'keyboard %d px' % KEYBOARD in shell_log, 'the shell told no keyboard:\n' + shell_log[-1500:]
        assert not re.search(r'navigation bar \d+ logical = [1-9]', shell_log), 'the keyboard went out as the navigation bar:\n' + shell_log[-1500:]
        return result
    finally:
        instance.stop()
        if app.poll() is None: os.killpg(app.pid, signal.SIGKILL)
        app.wait()
        log.close()
        left = instance.mine('[a]pklayer.host.Main|[b]tload|[a]pklayer-shell')
        assert not left, left


print('PASS ' + '; '.join(run(m) for m in ('resize', 'pan')), flush=True)
