#!/usr/bin/env python3
"""Per-operation authentication and the locked state (HubLockScreen, ShadowHostBiometricPrompt): tests/apps/hello-keystore with -Dhello.ks=auth.
The hub's password dialogs get, in order: BiometricPrompt Cancel; BiometricPrompt a wrong password (Enter), then the right one (Confirm); the
confirm intent the right one (Enter). Then the desktop session's lock (logind's LockedHint, set and cleared here) must reach keystore2 and
KeyguardManager. The right password is in APKLAYER_TEST_LOGIN_PASSWORD_FILE (the VM user's login password; tests/vm/<vm>.conf APKLY_VM_ENV): without it
the right-password answers are Cancel and the app expects the refusals instead (-Dhello.ks.nopass=true)."""
import os
import selectors
import subprocess
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
from instance import ENV, LOGDIR, inject, stop  # noqa: E402

PKG, LOGIN = 'hello.keystore', 'org.freedesktop.login1'
secret = os.environ.get('APKLAYER_TEST_LOGIN_PASSWORD_FILE')  # a file of the VM user's (mode 600): the password in a command line would show in ps
password = Path(secret).read_text().rstrip('\n') if secret and Path(secret).exists() else None
right = (['text:' + password, 'key:28'] if password else ['no'])
ANSWERS = [['no'], ['text:wrong-password', 'key:28'], ['text:' + password, 'yes'] if password else ['no'], right]
log = Path(LOGDIR)


def gdbus(*args):
    return subprocess.run(['gdbus', 'call', '--system', '--dest', LOGIN, *args], env=ENV, capture_output=True, text=True, check=True).stdout


session = gdbus('--object-path', '/org/freedesktop/login1/user/self', '--method', 'org.freedesktop.DBus.Properties.Get', LOGIN + '.User', 'Display')
session = session.split("objectpath '")[1].split("'")[0]  # (<('2', objectpath '/org/freedesktop/login1/session/_32')>,)


def lock(on):
    print('LOCKEDHINT %s %s' % (session, on), flush=True)
    gdbus('--object-path', session, '--method', LOGIN + '.Session.SetLockedHint', 'true' if on else 'false')


stop()
(log / 'hub.log').unlink(missing_ok=True)
env = dict(ENV, APKLAYER_JOPTS='-Dhello.ks=auth -Dapklayer.runms=90000' + ('' if password else ' -Dhello.ks.nopass=true'))
lines, shown_count, locked = [], 0, False
proc = subprocess.Popen(['bin/apklayer', 'run', PKG], env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
try:
    end, pending = time.monotonic() + 90, b''
    with selectors.DefaultSelector() as events:
        events.register(proc.stdout, selectors.EVENT_READ)
        while time.monotonic() < end and not any('[ks] PASS auth' in l or '[ks] FAIL' in l for l in lines):
            if events.select(0.5):
                chunk = os.read(proc.stdout.fileno(), 65536)
                if not chunk: break
                parts = (pending + chunk).split(b'\n'); pending = parts.pop()
                for raw in parts:
                    line = raw.decode(errors='replace'); lines.append(line)
                    if '[ks]' in line or 'credential' in line or 'BiometricPrompt' in line or 'Exception' in line: print(line, flush=True)
                    if '[ks] lock now' in line: lock(True); locked = True
                    if '[ks] locked:' in line: lock(False); locked = False
            hub = (log / 'hub.log').read_text(errors='replace').split('\n') if (log / 'hub.log').exists() else []
            shown = [l for l in hub if 'dialog shown:' in l]
            if len(shown) > shown_count:
                question = shown[shown_count].split('dialog shown: ')[1]
                steps = ANSWERS[shown_count] if shown_count < len(ANSWERS) else ['no']
                shown_count += 1
                print('ANSWER %d %s: %s' % (shown_count, question, ' '.join('text:***' if s.startswith('text:') else s for s in steps)), flush=True)
                steps = ['click:' + question.split(s + '=')[1].split()[0] if s in ('yes', 'no') else s for s in steps]
                for attempt in range(20):  # the window takes input once the compositor configured it
                    try: inject('apklayer.installer', 'focus', *steps, secret=True); break
                    except AssertionError as missing:
                        if 'no such window' not in str(missing) or attempt == 19: raise
                        time.sleep(0.5)
finally:
    if locked: lock(False)
    stop()
    proc.wait()
hub = (log / 'hub.log').read_text(errors='replace') if (log / 'hub.log').exists() else ''
checks = {
    'four password dialogs': shown_count == 4,
    'a wrong password refused': 'credential confirmation of hello.keystore: wrong password' in hub,
    'the desktop lock reached the hub': 'desktop locked' in hub and 'desktop unlocked' in hub,
    'app checks': any('[ks] PASS auth' in l for l in lines),
}
for name, ok in checks.items(): print('%s %s' % ('PASS' if ok else 'FAIL', name))
sys.exit(0 if all(checks.values()) else 1)
