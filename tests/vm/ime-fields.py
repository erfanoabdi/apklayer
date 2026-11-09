#!/usr/bin/env python3
"""The kinds of text field (input group) through the shell's test input (instance.inject): Enter in hello.input's multi-line field types a
newline (a key and an IME's "\\n"), an IME deletion with the text selected deletes the selection, an IME's "\\n" in the single-line email field
runs its editor action (IME_ACTION_GO), each field's EditorInfo reaches the shell, and a tap in the focused field asks the shell to show its
keyboard again (showSoftInput). The phones' keyboards (Maliit) take the same messages: docs/HALIUM.md."""
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
ENV = dict(instance.ENV, APKLAYER_DEBUG_INPUT='1', APKLAYER_DISPLAY_WIDTH='480', APKLAYER_DISPLAY_HEIGHT='600',
           APKLAYER_JOPTS='-Dapklayer.runms=20000 -Dapklayer.debugkeys=true')
FIELD, EMAIL, PASSWORD = (240, 82), (240, 352), (240, 400)  # content pixels (below the action bar: hello.input's dp layout at scale 1)
seen = []

instance.stop()
app = subprocess.Popen(['bin/apklayer', 'run', PKG], env=ENV, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
selector = selectors.DefaultSelector()
selector.register(app.stdout, selectors.EVENT_READ)
pending = b''
log = open(instance.OUT / 'ime-fields.log', 'wb')


def expect(what, after=0):
    """Reads the app's output until a line from index `after` on contains `what` (the app's own pace: no fixed waits)."""
    global pending
    end = time.monotonic() + 30
    while not any(what in l for l in seen[after:]):
        assert selector.select(10) and time.monotonic() < end, 'no "%s"; last lines: %s' % (what, seen[-6:])
        data = os.read(app.stdout.fileno(), 65536)
        assert data, 'the app ended without "%s"' % what
        log.write(data)
        pending += data
        *lines, pending = pending.split(b'\n')
        for raw in lines:
            line = raw.decode(errors='replace')
            if '[apklayer]' in line or '[hello-input]' in line:
                seen.append(line)
                print(line, flush=True)


def focused(after=0):
    """The EditorInfo (inputType, imeOptions) the shell got for the field that took focus after line `after`."""
    expect('[apklayer] text input on, type 0x', after)
    words = [l for l in seen[after:] if '[apklayer] text input on, type 0x' in l][0].split()
    return int(words[5], 16), int(words[7], 16)


try:
    kind, options = focused()  # the multi-line field: TYPE_CLASS_TEXT | TYPE_TEXT_FLAG_MULTI_LINE, IME_FLAG_NO_ENTER_ACTION
    assert kind & 0x20fff == 0x20001 and options & 0x40000000, (hex(kind), hex(options))
    instance.inject(PKG, 'focus', 'click:%d,%d' % FIELD, 'key:35', 'key:28', 'key:23')  # h, Enter, i
    expect('[hello-input] text=h\\ni')
    instance.inject(PKG, 'text:\n')  # an IME's Enter in a multi-line field: a newline (IME_FLAG_NO_ENTER_ACTION)
    expect('[hello-input] text=h\\ni\\n')
    n = len(seen)
    instance.inject(PKG, 'combo:ctrl+30')  # select all
    expect('key action=1 code=129', n)  # Ctrl's release (the shell's Android code for it): the selection is made before the deletion below arrives
    instance.inject(PKG, 'delete:1')  # the phones' keyboards send Backspace as an IME deletion
    n = len(seen)
    expect('[hello-input] text=', n)
    assert '[hello-input] text=' in seen[n:], 'Backspace with all selected did not delete the selection: ' + str(seen[n:])
    n = len(seen)
    instance.inject(PKG, 'tap:%d,%d' % EMAIL)
    kind, options = focused(n)  # TYPE_TEXT_VARIATION_EMAIL_ADDRESS, IME_ACTION_GO
    assert kind & 0x20fff == 0x21 and options & 0x400000ff == 2, (hex(kind), hex(options))
    expect('[apklayer] text input show request', n)  # the tap that focused it
    instance.inject(PKG, 'text:a', 'text:\n')
    expect('[hello-input] field1 action=2')  # the IME's Enter: the editor action, no newline
    assert not any(l.startswith('[hello-input] field1 text=a\\n') for l in seen), 'Enter typed into the single-line field'
    n = len(seen)
    instance.inject(PKG, 'tap:%d,%d' % EMAIL)  # the focused field again: the keyboard comes back if the user closed it
    expect('[apklayer] text input show request', n)
    n = len(seen)
    instance.inject(PKG, 'tap:%d,%d' % PASSWORD)
    kind, options = focused(n)
    assert kind & 0xfff == 0x81, hex(kind)  # TYPE_TEXT_VARIATION_PASSWORD
    shell_log = (instance.STATE / 'shell.log').read_text(errors='replace')
    assert 'text input show request\n' in shell_log, 'the shell got no show request:\n' + shell_log[-1500:]
    print('PASS Enter types a newline in a multi-line field and runs a single-line field\'s action, an IME deletion takes the selection, '
          'the fields\' EditorInfo and a tap\'s show request reach the shell', flush=True)
finally:
    instance.stop()
    if app.poll() is None: os.killpg(app.pid, signal.SIGKILL)
    app.wait()
    log.close()
    left = instance.mine('[a]pklayer.host.Main|[b]tload|[a]pklayer-shell')
    assert not left, left
