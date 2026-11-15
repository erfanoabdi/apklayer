#!/usr/bin/env python3
"""The location screen of the hub's permission prompt (HubPermissionPrompt, HubInstallDialog.choose): tests/apps/hello-permissions with -Dhello.perm.place asks
for precise+approximate location, then background location, then precise again. The answers by test input: "Approximate" + "While using the app" (only
the coarse permission is granted), "Allow all the time" (the background question, a dialog of its own), "Only this time" (precise until the process ends).
A second process must hold coarse and background but not precise. Each question must offer what Android's does: the first two radio rows and three buttons."""
import os
import selectors
import subprocess
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
from instance import ENV, INSTANCE, LOGDIR, capture, inject, stop  # noqa: E402

PKG = 'hello.permissions'
PLACE = 'android.permission.ACCESS_FINE_LOCATION'
ANSWERS = [('access this device’s location?', ['approximate', 'yes'], ['precise', 'approximate', 'yes', 'once', 'no']),
           ('location in the background', ['yes'], ['yes', 'no']),
           ('access this device’s location?', ['once'], ['precise', 'approximate', 'yes', 'once', 'no'])]
STEPS = ['approximate: coarse granted, precise not', 'background location: its own question, granted', 'only this time: precise granted',
         'only this time: precise revoked when the process ended', 'approximate and background kept']
data = Path(os.environ.get('XDG_DATA_HOME', Path.home() / '.local/share')) / 'apklayer' / ('0' if INSTANCE == 'default' else INSTANCE)
log = Path(LOGDIR)


def run(extra, answers):
    """One app process; its lines and the questions the hub showed (each answered by the clicks of the next entry of `answers`)."""
    (log / 'hub.log').unlink(missing_ok=True)
    env = dict(ENV, APKLAYER_PERMISSIONS='ask', APKLAYER_JOPTS='-Dapklayer.runms=60000 ' + extra)
    lines, questions, once = [], [], ''
    proc = subprocess.Popen(['bin/apklayer', 'run', PKG], env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
    try:
        end, pending = time.monotonic() + 60, b''
        with selectors.DefaultSelector() as events:
            events.register(proc.stdout, selectors.EVENT_READ)
            while time.monotonic() < end and not any('[perm] DONE' in l for l in lines):
                if events.select(0.5):
                    chunk = os.read(proc.stdout.fileno(), 65536)
                    if not chunk: break
                    parts = (pending + chunk).split(b'\n'); pending = parts.pop()
                    for raw in parts:
                        line = raw.decode(errors='replace'); lines.append(line)
                        if '[perm]' in line or 'FATAL' in line or 'Exception' in line: print(line, flush=True)
                hub = (log / 'hub.log').read_text(errors='replace').split('\n') if (log / 'hub.log').exists() else []
                shown = [l for l in hub if 'dialog shown:' in l]
                if len(shown) > len(questions):
                    question = shown[len(questions)].split('dialog shown: ')[1]
                    questions.append(question)
                    heading, clicks, offered = answers[len(questions) - 1]
                    assert heading in question, 'question %d is "%s", not "%s"' % (len(questions), question, heading)
                    found = [w.split('=')[0] for w in question.split('" ')[1].split() if '=' in w and not w.startswith('size=')]
                    assert found == offered, 'the question offers %s, not %s' % (found, offered)
                    if len(questions) == 1:  # the question as the desktop shows it
                        time.sleep(1)
                        try: capture('permission-location')
                        except Exception as no_capture: print('no desktop screenshot: %s' % no_capture, flush=True)
                    for click in clicks:
                        xy = question.split(' %s=' % click)[1].split()[0]
                        print('ANSWER %s %s' % (click, question), flush=True)
                        for attempt in range(20):  # the window takes input once the compositor configured it
                            try: inject('apklayer.installer', 'click:' + xy); break
                            except AssertionError as missing:
                                if 'no such window' not in str(missing) or attempt == 19: raise
                                time.sleep(0.5)
                        time.sleep(0.3)
        once = (data / 'permissions-once').read_text() if (data / 'permissions-once').exists() else ''  # while the app still runs
    finally:
        stop()
        proc.wait()
    return lines, questions, once


stop()
for name in ('permissions-granted', 'permissions-revoked', 'permissions-once'):  # a fresh store for the app: nothing answered yet
    f = data / name
    if f.exists(): f.write_text(''.join(l + '\n' for l in f.read_text().split('\n') if l and not l.startswith(PKG + '\t')))
lines, questions, once = run('-Dhello.perm.place=true', ANSWERS)
second, _, _ = run('-Dhello.perm.place2=true', [])  # the hub of this start ended the one-time grant
passed = {l.split('[perm] PASS ')[1].split(' (')[0].strip() for l in lines + second if '[perm] PASS ' in l}
failed = [l for l in lines + second if '[perm] FAIL ' in l]
for step in STEPS: print('%s %s' % ('PASS' if step in passed else 'FAIL', step))
listed = PKG + '\t' + PLACE + '\n' in once
print('%s the one-time grant was listed to end (%r)' % ('PASS' if listed else 'FAIL', once))
granted = (data / 'permissions-granted').read_text() if (data / 'permissions-granted').exists() else ''
ended = PKG + '\t' + PLACE + '\n' not in granted and 'ACCESS_COARSE_LOCATION' in granted
print('%s only the one-time grant is gone from the store' % ('PASS' if ended else 'FAIL'))
if failed: print('FAIL lines: %s' % failed)
sys.exit(0 if all(s in passed for s in STEPS) and not failed and listed and ended and len(questions) == len(ANSWERS) else 1)
