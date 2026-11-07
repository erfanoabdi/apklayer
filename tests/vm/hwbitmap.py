#!/usr/bin/env python3
"""Hardware bitmaps (hello-hwbitmap): a copy to HARDWARE reads back, and Bitmap.createBitmap(Picture) (HardwareRenderer.createHardwareBitmap,
HWUI's own AImageReader render: Compose's GraphicsLayer.toImageBitmap) gives a HARDWARE bitmap with the recorded colour."""
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
import instance  # noqa: E402  (changes into the repo root)

env = dict(instance.ENV, APKLAYER_JOPTS='-Dapklayer.runms=6000 ' + instance.ENV.get('APKLAYER_EXTRA_JOPTS', ''))
lines = []
try:
    instance.stop()
    proc = subprocess.run(['bin/apklayer', 'run', 'hello.hwbitmap'], env=env, capture_output=True, text=True)
    lines = [l for l in (proc.stdout + proc.stderr).splitlines() if 'hwbitmap:' in l or 'Exception' in l]
finally:
    instance.stop()
print('\n'.join(lines))
out = '\n'.join(lines)
assert 'hwbitmap: readback ff0000ff ffff8000' in out, 'a HARDWARE copy does not read back'
assert 'hwbitmap: pictures 20/20' in out, 'Bitmap.createBitmap(Picture) gave no HARDWARE bitmap with the recorded colour'
print('PASS hwbitmap: HARDWARE copy and Bitmap.createBitmap(Picture) read back')
