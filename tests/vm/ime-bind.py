#!/usr/bin/env python3
"""An enabled Android input method binds to a focused text field of another app (HubInputMethods): hello.system's Ime gets bindInput, startInput
and its input view, and the text it commits reaches hello.input's EditText. The IME is disabled again afterwards (other groups of the instance)."""
import os
from pathlib import Path
import selectors
import subprocess
import sys
import time

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
import instance  # noqa: E402  (changes into the repo root)

ENV = dict(instance.ENV, APKLAYER_DISPLAY_WIDTH='480', APKLAYER_DISPLAY_HEIGHT='400', APKLAYER_HOST_LIBS=instance.HOST_LIBS, APKLAYER_JOPTS='-Dapklayer.runms=45000')
LOGS = Path(instance.LOGDIR)
instance.stop()
subprocess.run(['bin/apklayer', 'ime', 'enable', 'hello.system'], env=ENV, check=True, stdout=subprocess.DEVNULL)
proc = subprocess.Popen(['bin/apklayer', 'run', 'hello.input'], env=ENV, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
lines, pending, passed = [], b'', False
try:
    end = time.monotonic() + 40
    with selectors.DefaultSelector() as events:
        events.register(proc.stdout, selectors.EVENT_READ)
        while time.monotonic() < end and not passed:
            if not events.select(2): continue
            data = os.read(proc.stdout.fileno(), 65536)
            if not data: break
            parts = (pending + data).split(b'\n')
            pending = parts.pop()
            for line in (raw.decode(errors='replace') for raw in parts):
                lines.append(line)
                if '[hello-input] text=' in line or 'input method' in line or 'cannot start input' in line: print(line, flush=True)
                if '[hello-input] text=hello from ime' in line: passed = True
    assert passed, 'the text the IME committed did not reach the field\n' + '\n'.join(lines[-25:])
    ime = instance.wait_file(LOGS / 'component-hello.system.log', '[ime] onStartInputView hello.input', 10)  # the last callback: shown after startInput
    for callback in ('[ime] onBindInput', '[ime] onStartInput hello.input', '[ime] commitText true', '[ime] onCreateInputView', '[ime] onStartInputView hello.input'):
        assert callback in ime, callback + ' missing in the IME process\n' + ime[-3000:]
    print(*[l for l in ime.split('\n') if l.startswith('[ime]')], sep='\n', flush=True)
    hub = (LOGS / 'hub.log').read_text(errors='replace')
    print(*[l for l in hub.split('\n') if 'input method' in l], sep='\n', flush=True)
    print('PASS ime-bind: bindInput, startInput, the input view, and the committed text reached hello.input', flush=True)
finally:
    if proc.poll() is None: os.killpg(proc.pid, 15)
    instance.stop()
    subprocess.run(['bin/apklayer', 'ime', 'disable', 'hello.system'], env=ENV, stdout=subprocess.DEVNULL)
