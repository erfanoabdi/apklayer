#!/usr/bin/env python3
"""Widget input of one instance without the desktop (instance.inject: the shell's test input, bridge/shell/testinput.c): focus, a click into
the field, typed keys, Ctrl+A, IME preedit and commit into the proxied field, a touch tap on a button. Runs in parallel with other
groups; the real seat (uinput, IBus, the desktop clipboard) is tests/vm/input.py (group input-desktop, alone)."""
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
PKG = 'hello.input'
ENV = dict(instance.ENV, APKLAYER_RUNTIME_DEBUG='1', APKLAYER_DEBUG_INPUT='1', APKLAYER_DISPLAY_WIDTH='480', APKLAYER_DISPLAY_HEIGHT='600')
FIELD, COPY = (240, 82), (240, 154)  # content pixels of hello.input's field and "Copy Android text" button (below the action bar)
ENV['APKLAYER_JOPTS'] = '-Dapklayer.runms=15000 -Dapklayer.debugkeys=true -Dapklayer.script=' + '+'.join(
    '%d:hover:%d,0' % (t, step) for t, step in ((3000, 1), (6000, 2), (7000, 3), (9000, 4), (11000, 5), (13000, 6)))
seen = []
checks = set()


def saw(text):
    return any(text in line for line in seen)


instance.stop()
app = subprocess.Popen(['bin/apklayer', 'run', PKG], env=ENV, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
try:
    selector = selectors.DefaultSelector()
    selector.register(app.stdout, selectors.EVENT_READ)
    end = time.monotonic() + 40
    pending = b''
    with open(instance.OUT / 'input-inject.log', 'wb') as log:
        while True:
            assert selector.select(5) and time.monotonic() < end, 'input test stalled'
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
                if 'script: hover:1,0' in line:
                    instance.inject(PKG, 'focus', 'click:%d,%d' % FIELD, 'key:35', 'key:23')  # h, i
                elif 'script: hover:2,0' in line:
                    assert saw('[hello-input] text=hi'), 'touch focus and keys did not type hi'
                    checks.add('typing')
                    instance.inject(PKG, 'combo:ctrl+30')  # select all
                elif 'script: hover:3,0' in line:
                    # a step of its own: the app applies IME text on the shell's reader thread but keys on its main thread, so text sent
                    # right behind a key can overtake it (docs/PLAN.md)
                    instance.inject(PKG, 'preedit:ni', 'text:你好')
                elif 'script: hover:4,0' in line:
                    assert saw('[hello-input] text=你好'), 'IME commit did not replace the selection'
                    checks.add('ime')
                    instance.inject(PKG, 'tap:%d,%d' % COPY)
                elif 'script: hover:5,0' in line:
                    assert saw('[hello-input] clip=android-copy'), 'touch tap did not reach the button'
                    checks.add('tap')
                    shot = instance.capture('verified input', PKG)
                    assert pixels.colors(shot) > 50, 'flat app window'
    assert app.wait() == 0, 'input app failed'
    assert checks == {'typing', 'ime', 'tap'}, checks
    assert saw('[hello-input] text=ni'), 'IME preedit did not reach the field'
    print('PASS touch, keys, Ctrl+A, IME preedit/commit and a touch tap through the shell\'s test input', flush=True)
finally:
    instance.stop()
    if app.poll() is None: os.killpg(app.pid, signal.SIGKILL)
    app.wait()
    left = instance.mine('[a]pklayer.host.Main|[b]tload|[a]pklayer-shell')
    assert not left, left
