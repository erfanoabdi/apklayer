#!/usr/bin/env python3
"""A text field in the real Chromium WebView (hello.wvprobe -Dhello.wvinput) through the shell's test input: a long press selects a word and
shows Chromium's selection toolbar (a FLAG_NOT_FOCUSABLE PopupWindow); keys and IME text must still reach the field.
One step per scripted tick (the app's clock: ticks queued while the app was busy come at once), each once its precondition holds; a release
comes a second after its press (a long press)."""
import os
from pathlib import Path
import selectors
import signal
import subprocess
import sys
import time

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
import instance  # noqa: E402  (changes into the repo root)
PKG = 'hello.wvprobe'
AT = (30, 150)  # on the field's text (the field fills the page)
ENV = dict(instance.ENV, APKLAYER_DISPLAY_WIDTH='480', APKLAYER_DISPLAY_HEIGHT='400', APKLAYER_HOST_LIBS=instance.HOST_LIBS,
           APKLAYER_WEBVIEW_PROVIDER='com.android.webview', APKLAYER_LOGCAT='1', APKLAYER_RUNTIME_DEBUG='1', APKLAYER_DEBUG_INPUT='1')
ENV['APKLAYER_JOPTS'] = '-Dapklayer.realbinder=true -Dapklayer.instancehub=true -Dapklayer.runms=90000 -Dhello.wvinput=1 -Dapklayer.debugkeys=true -Dapklayer.script=' + '+'.join(
    '%d:hover:%d,0' % (t * 1000, t) for t in range(2, 88))
seen = []
pressed = [0.0]  # when the last press went in


def count(text):
    return sum(text in line for line in seen)


# (precondition, test input steps): a step runs at the first tick its precondition holds
STEPS = [(lambda: count('[wv] page finished'), ['focus', 'tap:%d,%d' % AT]),
         (lambda: count('[wv] focused'), ['press:%d,%d' % AT]),
         (lambda: time.monotonic() > pressed[0] + 1, ['release:%d,%d' % AT]),
         (lambda: count('[wv] action mode started'), ['key:45']),  # x replaces the selected word
         (lambda: count('[wv] value=x'), ['text:yz']),  # IME commit: text input stays on for the field
         (lambda: count('[wv] value=xyz'), ['press:%d,%d' % AT]),
         (lambda: time.monotonic() > pressed[0] + 1, ['release:%d,%d' % AT]),
         (lambda: count('[wv] action mode started') >= 2, ['key:44']),  # z replaces the selection again
         (lambda: count('[wv] value=z'), [])]  # (Back is Chromium's: it leaves the selection and the app finishes, input-inject checks Back)
instance.stop()
app = subprocess.Popen(['bin/apklayer', 'run', PKG], env=ENV, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
step = 0
try:
    selector = selectors.DefaultSelector()
    selector.register(app.stdout, selectors.EVENT_READ)
    end = time.monotonic() + 100
    pending = b''
    with open(instance.OUT / 'webview-input.log', 'wb') as log:
        while step < len(STEPS):
            assert selector.select(5) and time.monotonic() < end, 'webview input test stalled at step %d' % step
            data = os.read(app.stdout.fileno(), 65536)
            assert data, 'app ended at step %d' % step
            log.write(data)
            pending += data
            *lines, pending = pending.split(b'\n')
            for raw in lines:
                line = raw.decode(errors='replace')
                if '[wv]' not in line and '[apklayer]' not in line: continue
                seen.append(line)
                if '[wv]' in line: print(line, flush=True)
                if 'script: hover' in line and step < len(STEPS) and STEPS[step][0]():
                    if STEPS[step][1]: instance.inject(PKG, *STEPS[step][1])
                    if STEPS[step][1][:1] == ['press:%d,%d' % AT]: pressed[0] = time.monotonic()
                    step += 1
    print('PASS keys and IME text in a WebView text field during a long press selection toolbar', flush=True)
finally:
    instance.stop()
    if app.poll() is None: os.killpg(app.pid, signal.SIGKILL)
    app.wait()
