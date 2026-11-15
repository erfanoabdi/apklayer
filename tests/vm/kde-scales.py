#!/usr/bin/env python3
"""kde-scales: kde-window.py (KWin drives the window; the shell's CONFIGUREs, scale and geometry must agree with KWin's) and kde-interact.py (real pointer and
keys) on the desktop session's own output at 100%, 150% and 200% (kscreen-doctor; APKLY_KDE_SCALES="1 1.25 1.5 1.75 2" picks others), the display's scale put
back at the end. Only the session's first output changes, so the group runs alone. Needs the KDE session on the host: kscreen-doctor, qdbus; tests/lib/kwin.py."""
import os
from pathlib import Path
import subprocess
import sys
import time

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
import instance  # noqa: E402  (changes into the repo root)
import kwin  # noqa: E402

os.environ.update(DBUS_SESSION_BUS_ADDRESS=instance.ENV['DBUS_SESSION_BUS_ADDRESS'])
out = kwin.outputs()
if not out: sys.exit('SKIP no KDE session (kscreen-doctor lists no output)')
name, original = out[0]['name'], out[0]['scale']


def set_scale(scale):
    r = subprocess.run(['kscreen-doctor', 'output.%s.scale.%s' % (name, scale)], env=kwin._env(), capture_output=True, text=True)
    assert r.returncode == 0 and abs(kwin.outputs()[0]['scale'] - float(scale)) < 0.01, 'kscreen-doctor %s: %s%s, now %s' % (scale, r.stdout[-200:], r.stderr[-200:], kwin.outputs())


failed = []
try:
    for scale in os.environ.get('APKLY_KDE_SCALES', '1 1.5 2').split():
        set_scale(scale)
        print('== %s at scale %s' % (name, scale), flush=True)
        time.sleep(3)  # KWin rearranges its windows and the panel after the change
        for test in ('kde-window', 'kde-interact'):
            if subprocess.run([sys.executable, '-u', 'tests/vm/%s.py' % test], env=instance.ENV).returncode: failed.append('%s@%s' % (test, scale))
finally:
    set_scale(original)
print('%s KDE window and pointer at the scales: %s' % ('FAIL' if failed else 'PASS', 'failed: ' + ' '.join(failed) if failed else 'all passed'), flush=True)
sys.exit(1 if failed else 0)
