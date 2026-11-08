#!/usr/bin/env python3
"""FLAG_ALT_FOCUSABLE_IM (input group): hello.input shows a dialog with that flag and a focused field. Keys reach the field; the input method's
text does not (WindowManager.LayoutParams.mayUseInputMethod: the window is no IME target), nor the activity's field below it."""
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
ENV['APKLAYER_JOPTS'] = '-Dhello.altim=1 -Dapklayer.debugkeys=true -Dapklayer.runms=9000 -Dapklayer.script=4000:hover:1,0+7000:hover:2,0'
seen = []


def saw(text):
    return any(text in line for line in seen)


instance.stop()
app = subprocess.Popen(['bin/apklayer', 'run', PKG], env=ENV, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
try:
    selector = selectors.DefaultSelector()
    selector.register(app.stdout, selectors.EVENT_READ)
    end = time.monotonic() + 40
    pending = b''
    with open(instance.OUT / 'alt-ime.log', 'wb') as log:
        while True:
            assert selector.select(5 if saw('packages registered') else 15) and time.monotonic() < end, 'alt-ime test stalled'
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
                    assert saw('[hello-input] alt dialog shown'), 'no dialog'
                    instance.inject(PKG, 'text:ime', 'key:30')  # an IME commit, then the key a
    assert app.wait() == 0, 'input app failed'
    assert saw('[hello-input] alt text=a'), 'a key did not reach the FLAG_ALT_FOCUSABLE_IM window\'s field'
    assert not saw('text=ime'), 'IME text reached a field of a FLAG_ALT_FOCUSABLE_IM window or the window below'
    print('PASS keys reach a FLAG_ALT_FOCUSABLE_IM window\'s field, the input method does not', flush=True)
finally:
    instance.stop()
    if app.poll() is None: os.killpg(app.pid, signal.SIGKILL)
    app.wait()
