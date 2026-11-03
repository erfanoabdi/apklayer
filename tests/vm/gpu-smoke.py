#!/usr/bin/env python3
"""GPU smoke test: UiBench starts on the GPU route, buffers reach the shell, and a screenshot shows the app (differs from the desktop before it).
Window, dialog and resize behaviour: the gpu-window group (tests/vm/gpu-window-run.py)."""
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
import instance  # noqa: E402  (changes into the repo root)
import pixels  # noqa: E402

env = dict(instance.ENV, APKLAYER_RUNTIME_DEBUG='1', APKLAYER_JOPTS='-Dapklayer.runms=12000 ' + instance.ENV.get('APKLAYER_EXTRA_JOPTS', ''))
proc = None
try:
    instance.stop()
    before = instance.capture('gpu-before')
    proc = subprocess.Popen(['bin/apklayer', 'run', 'com.android.test.uibench'], env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    imported = 0
    shot = None
    for line in proc.stdout:
        if 'apklayer.egl: imported buffer' in line:
            imported += 1
            if imported == 3 and shot is None:  # the app is presenting GPU buffers: take the picture now
                shot = instance.capture('gpu-during')
        if 'Fatal signal' in line or 'SIGSEGV' in line or 'fatal error' in line:
            print(line.rstrip())
    code = proc.wait()
finally:
    instance.stop()
assert code == 0, 'UiBench JVM exited with %d' % code
assert shot, 'no GPU buffer reached the compositor'
changed = pixels.changed(before, shot)
assert changed > 0.15, 'the screenshot barely differs from the bare desktop (%.0f%% changed)' % (changed * 100)
print('PASS GPU smoke: UiBench drew, %.0f%% of the screen changed (%s)' % (changed * 100, shot))
