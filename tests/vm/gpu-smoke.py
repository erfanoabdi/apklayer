#!/usr/bin/env python3
"""GPU smoke test: UiBench starts on the GPU route, buffers reach the shell, and SurfaceFlinger's picture of its window shows a drawn UI.
Window, dialog and resize behaviour: the gpu-window group (tests/vm/gpu-window-run.py)."""
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
import instance  # noqa: E402  (changes into the repo root)
import pixels  # noqa: E402

PKG = 'com.android.test.uibench'
env = dict(instance.ENV, APKLAYER_RUNTIME_DEBUG='1', APKLAYER_JOPTS='-Dapklayer.runms=12000 ' + instance.ENV.get('APKLAYER_EXTRA_JOPTS', ''))
proc = None
try:
    instance.stop()
    proc = subprocess.Popen(['bin/apklayer', 'run', PKG], env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    imported = 0
    shot = None
    for line in proc.stdout:
        if 'apklayer.egl: imported buffer' in line:
            imported += 1
            if imported == 3 and shot is None:  # the app is presenting GPU buffers: take the picture now
                shot = instance.capture('gpu', PKG)
        if 'Fatal signal' in line or 'SIGSEGV' in line or 'fatal error' in line:
            print(line.rstrip())
    code = proc.wait()
finally:
    instance.stop()
assert code == 0, 'UiBench JVM exited with %d' % code
assert shot, 'no GPU buffer reached the compositor'
count = pixels.colors(shot)
assert count > 100, 'the window shows no drawn UI (%d colours)' % count
print('PASS GPU smoke: UiBench drew its window, %d colours (%s)' % (count, shot))
