#!/usr/bin/env python3
"""USB without a HAL (HostUsb, ShadowHostUsbManager, HubUsb): tests/apps/hello-usb against Gadget Zero plugged in while it runs (tests/vm/usb-gadget.sh).
The hub's dialogs get, in order: "Open usb-check to handle Gadget Zero?" Cancel (the attach offer), "Allow usb-check to access Gadget Zero?" Allow (the app's
requestPermission); the app then opens the device and runs its transfers. The gadget goes and comes back: the detach broadcast, then the attach offer
answered OK starts the app's device_filter activity with the permission. Steps are the app's `[usb] PASS|FAIL <step>` lines."""
import os
import selectors
import subprocess
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
from instance import ENV, LOGDIR, inject, stop  # noqa: E402

STEPS = ['attach broadcast', 'no permission before asking', 'permission result', 'open', 'file descriptor', 'control GET_DESCRIPTOR', 'raw descriptors',
         'configurations', 'setConfiguration', 'claimInterface', 'bulk loopback', 'UsbRequest', 'releaseInterface', 'detach broadcast', 'attach launch']
ANSWERS = ['no=', 'yes=', 'yes=']
gadget = str(Path(__file__).with_name('usb-gadget.sh'))
log = Path(LOGDIR)
env = dict(ENV, APKLAYER_JOPTS='-Dapklayer.runms=120000')

subprocess.run([gadget, 'up'], check=True)
stop()
(log / 'hub.log').unlink(missing_ok=True)
lines, questions = [], []
phase = 'start'  # start -> attached -> detached -> again -> done
proc = subprocess.Popen(['bin/apklayer', 'run', 'hello.usb'], env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
try:
    end, pending = time.monotonic() + 100, b''
    with selectors.DefaultSelector() as events:
        events.register(proc.stdout, selectors.EVENT_READ)
        while time.monotonic() < end and phase != 'done':
            if events.select(0.5):
                chunk = os.read(proc.stdout.fileno(), 65536)
                if not chunk: break
                parts = (pending + chunk).split(b'\n'); pending = parts.pop()
                for raw in parts:
                    line = raw.decode(errors='replace'); lines.append(line)
                    if '[usb]' in line or 'FATAL' in line or 'Exception' in line: print(line, flush=True)
            if phase == 'start' and any('[usb] ready' in l for l in lines):
                subprocess.run([gadget, 'attach'], check=True); phase = 'attached'
            elif phase == 'attached' and any('[usb] DONE' in l for l in lines):
                subprocess.run([gadget, 'detach'], check=True); phase = 'detached'
            elif phase == 'detached' and any('detach broadcast' in l for l in lines):
                subprocess.run([gadget, 'attach'], check=True); phase = 'again'
            elif phase == 'again' and any('attach launch' in l for l in lines):
                phase = 'done'
            hub = (log / 'hub.log').read_text(errors='replace').split('\n') if (log / 'hub.log').exists() else []
            shown = [l for l in hub if 'dialog shown:' in l]
            if len(shown) > len(questions):  # other installed apps' device filters may match too: they are told no
                question = shown[len(questions)].split('dialog shown: ')[1]
                ours = [q for q in questions if 'usb-check' in q]
                field = ANSWERS[len(ours)] if 'usb-check' in question and len(ours) < len(ANSWERS) else 'no='
                questions.append(question)
                print('ANSWER %s %s' % (field[:-1], question), flush=True)
                xy = question.split(field)[1].split()[0]
                for attempt in range(20):  # the window takes input once the compositor configured it
                    try: inject('apklayer.installer', 'click:' + xy); break
                    except AssertionError as missing:
                        if 'no such window' not in str(missing) or attempt == 19: raise
                        time.sleep(0.5)
finally:
    stop()
    proc.wait()
    subprocess.run([gadget, 'down'])
hub = (log / 'hub.log').read_text(errors='replace') if (log / 'hub.log').exists() else ''
print('\n'.join(l for l in hub.split('\n') if 'usb' in l), flush=True)
passed = {l.split('[usb] PASS ')[1].strip() for l in lines if '[usb] PASS ' in l}
failed = [l.split('[usb] FAIL ')[1].strip() for l in lines if '[usb] FAIL ' in l]
expected = ['"Open usb-check to handle Gadget Zero?"', '"Allow usb-check to access Gadget Zero?"', '"Open usb-check to handle Gadget Zero?"']
order_ok = [q.split(' yes=')[0] for q in questions if 'usb-check' in q] == expected
for step in STEPS: print('%s %s' % ('PASS' if step in passed else 'FAIL', step))
print('%s dialogs %s' % ('PASS' if order_ok else 'FAIL', [q.split(' yes=')[0] for q in questions]))
if failed: print('FAIL lines: %s' % failed)
sys.exit(0 if all(s in passed for s in STEPS) and not failed and order_ok else 1)
