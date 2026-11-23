#!/usr/bin/env python3
"""The render filter's two backends on the same input (tests/hals/sharpen-test via APKLAYER_SHARPEN_BACKEND=gles|vulkan): FSR 1 (EASU, RCAS with its text aid,
the area downscale; no anti-aliasing: Vulkan has none) must agree within 8 LSB (the GPUs' float rounding; a handful of samples at a threshold of the text mask or an EASU direction tie may differ more, up to 20) on odd sizes, non-integer ratios, 1:1, tiny and
large outputs, with the text aid on and off. SKIP without a render node, Vulkan or a sharpen-test built with both backends (SHARPEN_TEST
names the binary). SHARPEN_MODIFIER: the accepted modifier (hex; NVIDIA needs one)."""
import glob, os, subprocess, sys, tempfile
import numpy as np
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
exe = os.environ.get('SHARPEN_TEST') or next(iter(sorted(glob.glob(ROOT + '/*/bridge/shell/sharpen-test') + glob.glob(ROOT + '/bridge/shell/sharpen-test'))), None)
if not exe: sys.exit('SKIP sharpen-test not built')
if not glob.glob('/dev/dri/renderD*'): sys.exit('SKIP no render node')
MOD = os.environ.get('SHARPEN_MODIFIER', '300000000606010' if os.path.exists('/proc/driver/nvidia/version') else '')


def picture(w, h):
    im = Image.new('RGB', (w, h), (24, 28, 40)); d = ImageDraw.Draw(im)
    d.text((3, 3), 'Quick fox 0123', fill=(235, 235, 235))
    for i in range(0, w, 5): d.line((i, h // 3, i, h // 2), fill=(255, 255, 255))
    d.ellipse((w // 8, h // 2, max(w // 8 + 2, w // 2), h - 1), outline=(0, 200, 255))
    d.rectangle((w // 2, h // 2, max(w // 2, w - 2), max(h // 2, h - 2)), fill=(200, 60, 60))
    return np.dstack([np.array(im), np.full((h, w), 255, np.uint8)]).tobytes()


def run(tmp, backend, w, h, ow, oh, env):
    out = os.path.join(tmp, backend + '.raw')
    r = subprocess.run([exe, os.path.join(tmp, 'in.raw'), str(w), str(h), out, str(ow), str(oh), '0.5'] + ([MOD] if MOD else []),
                       env=dict(os.environ, APKLAYER_SHARPEN_BACKEND=backend, APKLAYER_SHARPEN_AA='off', **env), capture_output=True, text=True, timeout=60)  # the Vulkan backend has no anti-aliasing (SMAA / CMAA2 are GLES only): both run without
    if r.returncode in (3, 4): sys.exit('SKIP %s cannot take the picture as a dma-buf (rc %d)' % (backend, r.returncode))
    if r.returncode and backend == 'vulkan' and 'Vulkan' not in r.stderr: sys.exit('SKIP no Vulkan backend (rc %d)' % r.returncode)
    assert r.returncode == 0, '%s failed (rc %d): %s' % (backend, r.returncode, r.stderr)
    return np.frombuffer(open(out, 'rb').read(), np.uint8).reshape(oh, ow, 4)[:, :, :3].astype(int)


cases = [(640, 360, 1280, 720, {}), (641, 361, 1283, 721, {}), (853, 480, 1280, 720, {}), (1000, 563, 1280, 720, {}), (64, 36, 1280, 720, {}), (1280, 720, 1281, 721, {}),
         (1, 1, 64, 64, {}), (2, 2, 5, 5, {}), (100, 50, 50, 25, {}), (640, 360, 640, 360, {}), (1920, 1080, 1280, 720, {}),
         (640, 360, 1280, 720, {'APKLAYER_SHARPEN_TEXT': 'off'}), (960, 540, 1280, 720, {}), (448, 252, 1280, 720, {})]  # ratios 2, 1.33, 2.86
with tempfile.TemporaryDirectory(prefix='apklayer-sharpen-par-') as tmp:
    for w, h, ow, oh, env in cases:
        open(os.path.join(tmp, 'in.raw'), 'wb').write(picture(w, h))
        d = np.abs(run(tmp, 'gles', w, h, ow, oh, env) - run(tmp, 'vulkan', w, h, ow, oh, env))
        print('%dx%d -> %dx%d %s: max %d, mean %.4f, %d samples over 8' % (w, h, ow, oh, env, d.max(), d.mean(), (d > 8).sum()))
        assert d.max() <= 20 and (d > 8).sum() <= 10, 'GLES and Vulkan differ'  # a few samples flip at a threshold (text mask, EASU direction ties): the GPUs' rounding
print('PASS sharpen-parity')
