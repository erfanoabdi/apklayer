#!/usr/bin/env python3
"""A USB serial adapter for a serial app: Linux's serial gadget (g_serial, CDC ACM 0525:a4a7, USB_GADGET=serial tests/vm/usb-gadget.sh) plugged in before
tests/apps/hello-usb starts, held by the host's cdc_acm driver as a real adapter is. The app asks for it (the hub's dialog: Allow), claims its interfaces
with force (the kernel driver detached), sets the line coding and writes a line; the gadget's end (/dev/ttyGS0, read as root) answers "pong: <line>".
Steps are the app's `[usb] PASS|FAIL serial <step>` lines. SUDO: the sudo command (default `sudo -n`)."""
import os
import selectors
import subprocess
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
from instance import ENV, LOGDIR, inject, stop  # noqa: E402

STEPS = ['serial open', 'serial interfaces', 'serial claim (kernel driver detached)', 'serial line coding', 'serial write', 'serial read']
SUDO = os.environ.get('SUDO', 'sudo -n').split()
gadget = [str(Path(__file__).with_name('usb-gadget.sh'))]
genv = dict(os.environ, USB_GADGET='serial')
# the gadget's end: raw, answers the app's line (a modem manager probing the host's tty first writes AT lines: ignored), gives up after 90 s
FAR = r'''
import os, select, termios, tty
fd = os.open('/dev/ttyGS0', os.O_RDWR | os.O_NOCTTY)
tty.setraw(fd)
got = b''
while select.select([fd], [], [], 90)[0]:
    try: data = os.read(fd, 256)
    except OSError: break  # the gadget went
    if not data: break
    got += data
    if b'hello serial\n' in got:
        os.write(fd, b'pong: hello serial\n'); termios.tcdrain(fd); print('far side answered', flush=True); break
'''
log = Path(LOGDIR)
env = dict(ENV, APKLAYER_JOPTS='-Dapklayer.runms=60000')

subprocess.run(gadget + ['up'], env=genv, check=True)
subprocess.run(gadget + ['attach'], env=genv, check=True)
far = subprocess.Popen(SUDO + ['python3', '-c', FAR])
stop()
(log / 'hub.log').unlink(missing_ok=True)
lines, questions = [], []
proc = subprocess.Popen(['bin/apklayer', 'run', 'hello.usb'], env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
try:
    end, pending = time.monotonic() + 60, b''
    with selectors.DefaultSelector() as events:
        events.register(proc.stdout, selectors.EVENT_READ)
        while time.monotonic() < end and not any('[usb] SERIAL DONE' in l for l in lines):
            if events.select(0.5):
                chunk = os.read(proc.stdout.fileno(), 65536)
                if not chunk: break
                parts = (pending + chunk).split(b'\n'); pending = parts.pop()
                for raw in parts:
                    line = raw.decode(errors='replace'); lines.append(line)
                    if '[usb]' in line or 'FATAL' in line or 'Exception' in line: print(line, flush=True)
            hub = (log / 'hub.log').read_text(errors='replace').split('\n') if (log / 'hub.log').exists() else []
            shown = [l for l in hub if 'dialog shown:' in l]
            if len(shown) > len(questions):  # the app's own question gets Allow; another app's attach offer is told no
                question = shown[len(questions)].split('dialog shown: ')[1]
                field = 'yes=' if question.startswith('"Allow usb-check') else 'no='
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
    far.terminate()  # sudo passes it on to the far side (its real uid is ours)
    far.wait()
    subprocess.run(gadget + ['down'], env=genv)
passed = {l.split('[usb] PASS ')[1].strip() for l in lines if '[usb] PASS ' in l}
failed = [l.split('[usb] FAIL ')[1].strip() for l in lines if '[usb] FAIL ' in l]
for step in STEPS: print('%s %s' % ('PASS' if step in passed else 'FAIL', step))
if failed: print('FAIL lines: %s' % failed)
sys.exit(0 if all(s in passed for s in STEPS) and not failed else 1)
