#!/usr/bin/env python3
"""The render filter's pass (bridge/shell/sharpen.c) without a compositor: a text and line picture rendered at 50% (a box-filtered copy of a
1280x720 reference) goes through the pass to 1280x720 (tests/hals/sharpen-test.c: dmabuf in, EGL, FSR 1's EASU and RCAS, readback). Checked: the
result is sharper than a bilinear stretch (the mean edge strength in the text region at least 15% higher), no further from the reference
(PSNR within 0.5 dB of bilinear's, usually above it), and the text aid (APKLAYER_SHARPEN_TEXT=on, the default) makes the strokes steeper than with it off
without lowering the PSNR by more than 0.3 dB. SHARPEN_TEST is the sharpen-test binary to use (else the first one found in a build dir), SHARPEN_BACKEND=vulkan runs the Vulkan backend. The GPU needs EGL with dma-buf import: skipped without a render node. The accepted modifier
can be given in SHARPEN_MODIFIER (hex; NVIDIA's gbm has no linear render target)."""
import glob
import math
import os
import subprocess
import sys
import tempfile
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[2]
exe = os.environ.get('SHARPEN_TEST') or next(iter(sorted(glob.glob(str(ROOT / '*/bridge/shell/sharpen-test')) + glob.glob(str(ROOT / 'bridge/shell/sharpen-test')))), None)
if not exe: sys.exit('SKIP sharpen-test not built (no EGL/GBM/GLES on this machine)')
if not glob.glob('/dev/dri/renderD*'): sys.exit('SKIP no render node')
W, H = 1280, 720
ref = Image.new('RGB', (W, H), (24, 28, 40))
d = ImageDraw.Draw(ref)
fonts = glob.glob('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf')
for i, size in enumerate((14, 18, 24, 40)):
    font = ImageFont.truetype(fonts[0], size) if fonts else ImageFont.load_default()
    d.text((40, 30 + i * 60), 'The quick brown fox jumps over the lazy dog 0123456789', font=font, fill=(235, 235, 235))
for i in range(0, 300, 6): d.line((700 + i, 40, 700 + i, 200), fill=(255, 255, 255), width=1)
d.rectangle((60, 320, 360, 420), outline=(255, 200, 0), width=2)
d.ellipse((420, 300, 620, 500), outline=(0, 200, 255), width=2)
small = ref.resize((W // 2, H // 2), Image.BOX)

def render(text):
    with tempfile.TemporaryDirectory(prefix='apklayer-sharpen-') as tmp:
        raw_in, raw_out = os.path.join(tmp, 'in.raw'), os.path.join(tmp, 'out.raw')
        Path(raw_in).write_bytes(small.convert('RGBA').tobytes())
        args = [exe, raw_in, str(W // 2), str(H // 2), raw_out, str(W), str(H), '0.8706'] + ([os.environ['SHARPEN_MODIFIER']] if os.environ.get('SHARPEN_MODIFIER') else [])
        env = dict(os.environ, APKLAYER_SHARPEN_AA='off', APKLAYER_SHARPEN_TEXT=text)  # FSR 1 alone: SMAA / CMAA2 (default cmaa2-ultra) would change the edges this test measures
        if os.environ.get('SHARPEN_BACKEND'): env['APKLAYER_SHARPEN_BACKEND'] = os.environ['SHARPEN_BACKEND']
        run = subprocess.run(args, capture_output=True, text=True, timeout=60, env=env)
        print(run.stderr.strip())
        if run.returncode in (3, 4): sys.exit('SKIP the GPU cannot take the picture as a dma-buf (rc %d)' % run.returncode)
        assert run.returncode == 0, 'sharpen-test failed (rc %d): %s' % (run.returncode, run.stderr)
        return Image.frombytes('RGBA', (W, H), Path(raw_out).read_bytes()).convert('RGB')


ours, ours_off = render('on'), render('off')
bilinear = small.resize((W, H), Image.BILINEAR)


def psnr(a, b):
    diff = ImageChops.difference(a, b)
    mse = sum(sum(p * p for p in px) for px in diff.getdata()) / (W * H * 3)
    return 99.0 if mse == 0 else 10 * math.log10(255 * 255 / mse)


def edges(image):  # the mean gradient strength of the text region
    region = image.crop((30, 20, 700, 290)).convert('L').filter(ImageFilter.FIND_EDGES)
    return sum(region.getdata()) / (670 * 270)


e_ours, e_bil = edges(ours), edges(bilinear)
e_off = edges(ours_off)
p_ours, p_bil, p_off = psnr(ours, ref), psnr(bilinear, ref), psnr(ours_off, ref)
print('text aid off: edge strength %.2f, PSNR %.2f dB' % (e_off, p_off))
print('edge strength: ours %.2f, bilinear %.2f (x%.2f); PSNR vs the original: ours %.2f dB, bilinear %.2f dB' % (e_ours, e_bil, e_ours / e_bil, p_ours, p_bil))
assert e_ours >= e_bil * 1.15, 'not sharper than bilinear'
assert p_ours >= p_bil - 0.5, 'further from the original than bilinear'
assert e_ours >= e_off and p_ours >= p_off - 0.3, 'the text aid does not help'
print('PASS sharpen: x%.2f edge strength, %+.2f dB' % (e_ours / e_bil, p_ours - p_bil))

# SMAA 1x and CMAA2 (render-aa smaa-*, cmaa2-*) run before the upscale: the result must stay a sane picture (not further from the original than bilinear by more than
# 0.5 dB, as sharp as bilinear or sharper), whichever level; CMAA2 without ES 3.1 compute falls back to SMAA (the log says so)
for aa in ('smaa-high', 'smaa-ultra', 'cmaa2-high', 'cmaa2-ultra', 'smaa-low', 'smaa-medium', 'cmaa2-low', 'cmaa2-medium', 'smaa', 'cmaa2', 'low', 'high'):  # the last eight: removed values, read as smaa-high or cmaa2-high
    with tempfile.TemporaryDirectory(prefix='apklayer-sharpen-') as tmp:
        raw_in, raw_out = os.path.join(tmp, 'in.raw'), os.path.join(tmp, 'out.raw')
        Path(raw_in).write_bytes(small.convert('RGBA').tobytes())
        run = subprocess.run([exe, raw_in, str(W // 2), str(H // 2), raw_out, str(W), str(H), '0.6'] + ([os.environ['SHARPEN_MODIFIER']] if os.environ.get('SHARPEN_MODIFIER') else []),
                             capture_output=True, text=True, timeout=60, env=dict(os.environ, APKLAYER_SHARPEN_AA=aa, **({'APKLAYER_SHARPEN_BACKEND': os.environ['SHARPEN_BACKEND']} if os.environ.get('SHARPEN_BACKEND') else {})))  # with SHARPEN_BACKEND=vulkan the level is ignored (no anti-aliasing there): the same sanity limits hold
        assert run.returncode == 0, '%s: sharpen-test failed (rc %d): %s' % (aa, run.returncode, run.stderr)
        img = Image.frombytes('RGBA', (W, H), Path(raw_out).read_bytes()).convert('RGB')
    assert edges(img) >= e_bil, '%s: not as sharp as bilinear' % aa
    assert psnr(img, ref) >= p_bil - 0.5, '%s: further from the original than bilinear' % aa
    print('PASS %s: x%.2f edge strength, %+.2f dB' % (aa, edges(img) / e_bil, psnr(img, ref) - p_bil))
