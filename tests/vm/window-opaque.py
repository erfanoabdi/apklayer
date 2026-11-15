#!/usr/bin/env python3
"""An Android window is opaque to the desktop: glbench's window-alpha mode draws frames whose alpha is 0.5 (premultiplied colour, green 0.1, blue 0.15),
which SurfaceFlinger shows as they are; the shell must tell the compositor the window is opaque (wl_surface.set_opaque_region, its log line), else the
desktop shows through wherever an app leaves alpha below 1 (Unity's 3D scenes: PGR looked dark and off). APKLAYER_WINDOW_ALPHA=1 is the negative control
(no opaque region). Where the host can screenshot the desktop (APKLY_HOST_SHOT, a command with {out}), the window's pixels must also be the opaque colour."""
import os
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
import instance  # noqa: E402  (changes into the repo root)
from PIL import Image  # noqa: E402

LINE = 'opaque region set'


def run(extra_env, tag):
    instance.stop()
    shot = instance.OUT / ('opaque-' + tag)
    for old in shot.parent.glob(shot.name + '.*'): old.unlink()
    env = dict(instance.ENV, GLBENCH_SHOT=str(shot), **extra_env)
    if os.environ.get('APKLY_HOST_SHOT'):
        env.update(GLBENCH_HOSTSHOT=str(shot) + '-host', GLBENCH_HOSTCMD=os.environ['APKLY_HOST_SHOT'])
    out = subprocess.run([sys.executable, 'tests/bench/glbench/run-android.py', 'window-alpha', 'clear', '1.5'], env=env, capture_output=True, text=True).stdout
    instance.stop()
    assert 'glbench: done' in out, 'glbench did not finish:\n' + out[-1500:]
    log = Path(instance.LOGDIR, 'shell.log').read_text(errors='replace')
    return shot, LINE in log


shot, opaque = run({}, 'on')
assert opaque, 'the shell never set the window opaque (no "%s" in shell.log)' % LINE
im = Image.open('%s.window-alpha.clear.png' % shot).convert('RGB')
pixel = im.getpixel((im.width // 2, im.height // 2))
print('SurfaceFlinger centre pixel', pixel)
assert abs(pixel[1] - 25) <= 3 and abs(pixel[2] - 38) <= 3, 'the frame did not carry the alpha-0.5 colour: %s' % (pixel,)
host = Path('%s-host.window-alpha.clear.png' % shot)
if host.exists():
    desk = Image.open(host).convert('RGB')
    # the window is the only place with exactly this colour (R varies per frame): a block of it, a different colour where the desktop shows through
    hits = sum(1 for p in desk.resize((desk.width // 8, desk.height // 8)).getdata() if abs(p[1] - 25) <= 3 and abs(p[2] - 38) <= 3)
    print('desktop pixels of the opaque colour (1/64 sample):', hits)
    assert hits > 500, 'the desktop does not show the window in its opaque colour'
else:
    print('no host screenshot (APKLY_HOST_SHOT): the compositor side is checked by the shell log line only')
_, opaque = run({'APKLAYER_WINDOW_ALPHA': '1'}, 'off')
assert not opaque, 'APKLAYER_WINDOW_ALPHA=1 still set the opaque region'
print('PASS window-opaque: the shell marks Android windows opaque')
