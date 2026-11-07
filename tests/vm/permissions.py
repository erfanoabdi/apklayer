#!/usr/bin/env python3
"""The runtime permission prompt (HubPermissionPrompt, HostPortals.requestPermissions/requestRole): tests/apps/hello-permissions with prompts on
(APKLAYER_PERMISSIONS=ask; tests/run grants without asking). RECORD_AUDIO is granted in the manager first; the hub's questions get, in order:
camera Allow, contacts Don't allow, contacts Don't allow, the browser role Set as default. No other question may appear (the third contacts
request, the manager's grant, the camera again and the SMS role are answered without one). Then the manager lists the answers."""
import os
import selectors
import subprocess
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
from instance import ENV, INSTANCE, LOGDIR, capture, inject, stop  # noqa: E402

PKG = 'hello.permissions'
STEPS = ['unasked camera not held', 'manager grant held at start', 'allow: camera granted', 'looper ran during the dialog', 'deny once: denied, rationale',
         'deny twice: denied, no rationale', 'third request denied', 'manager grant: no question', 'granted: not asked again',
         'sms role refused without telephony', 'browser role given']
ANSWERS = [('take pictures and record video', 'yes='), ('access your contacts', 'no='), ('access your contacts', 'no='), ('default browser app', 'yes=')]
data = Path(os.environ.get('XDG_DATA_HOME', Path.home() / '.local/share')) / 'apklayer' / ('0' if INSTANCE == 'default' else INSTANCE)
log = Path(LOGDIR)

stop()
for name in ('permissions-granted', 'permissions-revoked'):  # a fresh store for the app: nothing answered yet
    f = data / name
    if f.exists(): f.write_text(''.join(l + '\n' for l in f.read_text().split('\n') if l and not l.startswith(PKG + '\t')))
assert subprocess.run(['bin/apklayer', 'permissions', 'grant', PKG, 'android.permission.RECORD_AUDIO'], env=ENV).returncode == 0
(log / 'hub.log').unlink(missing_ok=True)
env = dict(ENV, APKLAYER_PERMISSIONS='ask', APKLAYER_JOPTS='-Dapklayer.runms=60000')
lines, questions, shot = [], [], False
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
                expected = ANSWERS[len(questions)] if len(questions) < len(ANSWERS) else ('', 'no=')
                questions.append(question)
                field = expected[1]
                print('ANSWER %s %s' % (field[:-1], question), flush=True)
                xy = question.split(field)[1].split()[0]
                for attempt in range(20):  # the window takes input once the compositor configured it
                    try:
                        if not shot and len(questions) == 1:  # the first question as the desktop shows it
                            shot = True; time.sleep(1)
                            try: capture('permission-dialog')
                            except Exception as no_capture: print('no desktop screenshot: %s' % no_capture, flush=True)
                        inject('apklayer.installer', 'click:' + xy); break
                    except AssertionError as missing:
                        if 'no such window' not in str(missing) or attempt == 19: raise
                        time.sleep(0.5)
finally:
    stop()
    proc.wait()
listing = subprocess.run(['bin/apklayer', 'permissions', PKG], env=ENV, capture_output=True, text=True).stdout
print(listing, end='')
held = {l.split('\t')[0]: l.split('\t')[2] for l in listing.split('\n') if l.count('\t') == 2}
passed = {l.split('[perm] PASS ')[1].split(' (')[0].strip() for l in lines if '[perm] PASS ' in l}
failed = [l.split('[perm] FAIL ')[1].strip() for l in lines if '[perm] FAIL ' in l]
asked = [q.split(' yes=')[0] for q in questions]
order_ok = len(questions) == len(ANSWERS) and all(a[0] in q for a, q in zip(ANSWERS, asked))
manager_ok = held.get('android.permission.CAMERA') == '1' and held.get('android.permission.READ_CONTACTS') == '0' and held.get('android.permission.RECORD_AUDIO') == '1'
revoked = (data / 'permissions-revoked').read_text() if (data / 'permissions-revoked').exists() else ''
for step in STEPS: print('%s %s' % ('PASS' if step in passed else 'FAIL', step))
print('%s questions %s' % ('PASS' if order_ok else 'FAIL', asked))
print('%s manager shows the answers' % ('PASS' if manager_ok else 'FAIL'))
print('%s contacts denied for good' % ('PASS' if PKG + '\tandroid.permission.READ_CONTACTS\n' in revoked else 'FAIL'))
if failed: print('FAIL lines: %s' % failed)
sys.exit(0 if all(s in passed for s in STEPS) and not failed and order_ok and manager_ok and PKG + '\tandroid.permission.READ_CONTACTS\n' in revoked else 1)
