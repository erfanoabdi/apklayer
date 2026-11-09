#!/usr/bin/env python3
"""Deep and multisampled window configs reach the screen: glbench's window with an 8-bit, a 4x multisampled 8-bit, a 10-bit (AR30/AB30 and
XR30/XB30) and a half-float (AB4H, XB4H) EGL config; a screenshot of SurfaceFlinger's picture during each mode's clear scene must show the clear colour (green 0.2, blue 0.3), and no
"framebuffer incomplete" may be logged (the window present reads the app's buffer back)."""
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
import instance  # noqa: E402  (changes into the repo root)
from PIL import Image  # noqa: E402

MODES = ['window', 'window-msaa', 'window-10bit', 'window-10bit-opaque', 'window-fp16', 'window-fp16-opaque']
instance.stop()
SHOT = instance.OUT / 'formats'
for old in SHOT.parent.glob(SHOT.name + '.*'): old.unlink()
env = dict(instance.ENV, GLBENCH_SHOT=str(SHOT))
# fill, then clear: the screenshot taken after fill's result line shows the clear scene running
run = subprocess.run([sys.executable, 'tests/bench/glbench/run-android.py', ','.join(MODES), 'fill,clear', '1.5'], env=env, capture_output=True, text=True)
instance.stop()
print('\n'.join(l for l in run.stdout.splitlines() if 'config' in l or 'fps=' in l), flush=True)
assert 'glbench: done' in run.stdout, 'glbench did not finish'
assert 'framebuffer incomplete' not in run.stdout, 'the present could not read a buffer back'
bad = []
for mode in MODES:
    if 'glbench: %s no config' % mode in run.stdout:
        print('%s: no such config on this host, skipped' % mode)
        continue
    im = Image.open('%s.%s.fill.png' % (SHOT, mode)).convert('RGB')
    pixel = im.getpixel((im.width // 2, im.height // 2))
    print('%s: %s' % (mode, pixel), flush=True)
    if abs(pixel[1] - 51) > 3 or abs(pixel[2] - 77) > 3: bad.append(mode)
assert not bad, 'not the clear colour on screen: %s' % ' '.join(bad)
print('window-formats: every config shown')
