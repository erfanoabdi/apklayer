#!/usr/bin/env python3
"""The render filter's pass (bridge/shell/sharpen.c) without a compositor: a text and line picture rendered at 50% (a box-filtered copy of a
1280x720 reference) goes through the pass to 1280x720 (tests/hals/sharpen-test.c: dmabuf in, EGL, FSR 1's EASU and RCAS, readback). Checked: the
result is sharper than a bilinear stretch (the mean edge strength in the text region at least 15% higher), no further from the reference
(PSNR within 0.5 dB of bilinear's, usually above it); then the same with the anti-aliasing the shell always runs before the upscale (CMAA2 ultra, SMAA ultra
without OpenGL ES 3.1 compute; none on Vulkan): still a sane picture. APKLAYER_SHARPEN_AA=off is the test knob that leaves it out, to measure FSR 1 alone. SHARPEN_TEST is the sharpen-test binary to use (else the first one found in a build dir), SHARPEN_BACKEND=vulkan runs the Vulkan backend. The GPU needs EGL with dma-buf import: skipped without a render node. The accepted modifier
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

def render():
    with tempfile.TemporaryDirectory(prefix='apklayer-sharpen-') as tmp:
        raw_in, raw_out = os.path.join(tmp, 'in.raw'), os.path.join(tmp, 'out.raw')
        Path(raw_in).write_bytes(small.convert('RGBA').tobytes())
        args = [exe, raw_in, str(W // 2), str(H // 2), raw_out, str(W), str(H), '0.8706'] + ([os.environ['SHARPEN_MODIFIER']] if os.environ.get('SHARPEN_MODIFIER') else [])
        env = dict(os.environ, APKLAYER_SHARPEN_AA='off')  # FSR 1 alone: the anti-aliasing (always on, CMAA2 ultra) would change the edges this test measures
        if os.environ.get('SHARPEN_BACKEND'): env['APKLAYER_SHARPEN_BACKEND'] = os.environ['SHARPEN_BACKEND']
        run = subprocess.run(args, capture_output=True, text=True, timeout=60, env=env)
        print(run.stderr.strip())
        if run.returncode in (3, 4): sys.exit('SKIP the GPU cannot take the picture as a dma-buf (rc %d)' % run.returncode)
        assert run.returncode == 0, 'sharpen-test failed (rc %d): %s' % (run.returncode, run.stderr)
        return Image.frombytes('RGBA', (W, H), Path(raw_out).read_bytes()).convert('RGB')


ours = render()
bilinear = small.resize((W, H), Image.BILINEAR)


def psnr(a, b):
    diff = ImageChops.difference(a, b)
    mse = sum(sum(p * p for p in px) for px in diff.getdata()) / (W * H * 3)
    return 99.0 if mse == 0 else 10 * math.log10(255 * 255 / mse)


def edges(image):  # the mean gradient strength of the text region
    region = image.crop((30, 20, 700, 290)).convert('L').filter(ImageFilter.FIND_EDGES)
    return sum(region.getdata()) / (670 * 270)


e_ours, e_bil = edges(ours), edges(bilinear)
p_ours, p_bil = psnr(ours, ref), psnr(bilinear, ref)
print('edge strength: ours %.2f, bilinear %.2f (x%.2f); PSNR vs the original: ours %.2f dB, bilinear %.2f dB' % (e_ours, e_bil, e_ours / e_bil, p_ours, p_bil))
assert e_ours >= e_bil * 1.15, 'not sharper than bilinear'
assert p_ours >= p_bil - 0.5, 'further from the original than bilinear'
print('PASS sharpen: x%.2f edge strength, %+.2f dB' % (e_ours / e_bil, p_ours - p_bil))

# the anti-aliasing (CMAA2 ultra, SMAA ultra without ES 3.1 compute; the log says which) runs before the upscale: the result must stay a sane picture (not further from the original
# than bilinear by more than 0.5 dB, as sharp as bilinear or sharper). Removed settings are not read any more: an old APKLAYER_SHARPEN_AA value other than off changes nothing.
for aa in (None, 'cmaa2-high', 'smaa-low', 'smaa'):  # the default, two removed values (ignored: the same ultra run) and the test knob for the SMAA ultra fallback
    with tempfile.TemporaryDirectory(prefix='apklayer-sharpen-') as tmp:
        raw_in, raw_out = os.path.join(tmp, 'in.raw'), os.path.join(tmp, 'out.raw')
        Path(raw_in).write_bytes(small.convert('RGBA').tobytes())
        env = {k: v for k, v in os.environ.items() if k != 'APKLAYER_SHARPEN_AA'}
        if aa: env['APKLAYER_SHARPEN_AA'] = aa
        if os.environ.get('SHARPEN_BACKEND'): env['APKLAYER_SHARPEN_BACKEND'] = os.environ['SHARPEN_BACKEND']
        run = subprocess.run([exe, raw_in, str(W // 2), str(H // 2), raw_out, str(W), str(H), '0.6'] + ([os.environ['SHARPEN_MODIFIER']] if os.environ.get('SHARPEN_MODIFIER') else []),
                             capture_output=True, text=True, timeout=60, env=env)
        assert run.returncode == 0, '%s: sharpen-test failed (rc %d): %s' % (aa, run.returncode, run.stderr)
        img = Image.frombytes('RGBA', (W, H), Path(raw_out).read_bytes()).convert('RGB')
    assert edges(img) >= e_bil, '%s: not as sharp as bilinear' % aa
    assert psnr(img, ref) >= p_bil - 0.5, '%s: further from the original than bilinear' % aa
    print('PASS with anti-aliasing (APKLAYER_SHARPEN_AA=%s): x%.2f edge strength, %+.2f dB' % (aa, edges(img) / e_bil, psnr(img, ref) - p_bil))

# Render scale 200 (a 2x source per axis, shown downscaled): the frame budget (7680x4320 px, display.c / AppHost.hostResize) lets sources up to 33 Mpx
# through, so the pass must take them: 1080p x2, a maximized 3440x1384 window x2 (6880x2768) and 8K (7680x4320 -> 3840x2160), every anti-aliasing level
# (the Vulkan backend has none). Checked: the pass ends 0 and the output has the asked size (4 bytes per pixel).
for (iw, ih, ow, oh) in ((3840, 2160, 1920, 1080), (6880, 2768, 3440, 1384), (7680, 4320, 3840, 2160)):
    with tempfile.TemporaryDirectory(prefix='apklayer-sharpen-') as tmp:
        raw_in, raw_out = os.path.join(tmp, 'in.raw'), os.path.join(tmp, 'out.raw')
        Path(raw_in).write_bytes(Image.frombytes('RGB', (256, 256), os.urandom(256 * 256 * 3)).resize((iw, ih), Image.NEAREST).convert('RGBA').tobytes())
        for aa in ('off', 'smaa-ultra', 'cmaa2-ultra'):
            run = subprocess.run([exe, raw_in, str(iw), str(ih), raw_out, str(ow), str(oh), '0.6'] + ([os.environ['SHARPEN_MODIFIER']] if os.environ.get('SHARPEN_MODIFIER') else []),
                                 capture_output=True, text=True, timeout=120, env=dict(os.environ, APKLAYER_SHARPEN_AA=aa, **({'APKLAYER_SHARPEN_BACKEND': os.environ['SHARPEN_BACKEND']} if os.environ.get('SHARPEN_BACKEND') else {})))
            assert run.returncode == 0, '%dx%d -> %dx%d %s: sharpen-test failed (rc %d): %s' % (iw, ih, ow, oh, aa, run.returncode, run.stderr)
            assert os.path.getsize(raw_out) == ow * oh * 4, '%dx%d -> %dx%d %s: output of %d bytes' % (iw, ih, ow, oh, aa, os.path.getsize(raw_out))
    print('PASS %dx%d -> %dx%d (render scale 200): every level' % (iw, ih, ow, oh))

# The neural upscaler (sharpen_nn.c, APKLAYER_SHARPEN_NN=1; the network is bridge/native/nn/student.bin): skipped where the GLES backend or ES 3.1 compute is missing (the pass then
# runs FSR 1 and says "neural upscaler unavailable"). At the render scales 75, 60, 50 and 40 % it must give a picture at least as close to the original as FSR 1's (0.3 dB slack) and
# at least as sharp as bilinear.
weights = ROOT / 'bridge/native/nn/student.bin'
if os.environ.get('SHARPEN_BACKEND') == 'vulkan' or not weights.exists():
    print('SKIP neural: %s' % ('Vulkan backend' if os.environ.get('SHARPEN_BACKEND') == 'vulkan' else 'no weights file'))
else:
    for pct in (75, 60, 50, 40):
        sm = ref.resize((round(W * pct / 100), round(H * pct / 100)), Image.BOX); res = {}
        for name, extra in (('fsr1', {}), ('neural', {'APKLAYER_SHARPEN_NN': '1', 'APKLAYER_NN_WEIGHTS': str(weights)})):
            with tempfile.TemporaryDirectory(prefix='apklayer-sharpen-') as tmp:
                raw_in, raw_out = os.path.join(tmp, 'in.raw'), os.path.join(tmp, 'out.raw')
                Path(raw_in).write_bytes(sm.convert('RGBA').tobytes())
                run = subprocess.run([exe, raw_in, str(sm.width), str(sm.height), raw_out, str(W), str(H), '0.8706'] + ([os.environ['SHARPEN_MODIFIER']] if os.environ.get('SHARPEN_MODIFIER') else []),
                                     capture_output=True, text=True, timeout=120, env=dict(os.environ, APKLAYER_SHARPEN_AA='off', APKLAYER_SHARPEN_BACKEND='gles', **extra))
                assert run.returncode == 0, 'neural %d%%: sharpen-test failed (rc %d): %s' % (pct, run.returncode, run.stderr)
                if name == 'neural' and 'neural upscaler unavailable' in run.stderr:
                    print('SKIP neural: unavailable here (%s)' % run.stderr.strip().splitlines()[-1]); break
                res[name] = Image.frombytes('RGBA', (W, H), Path(raw_out).read_bytes()).convert('RGB')
        else:
            bil = sm.resize((W, H), Image.BILINEAR)
            pf, pn = psnr(res['fsr1'], ref), psnr(res['neural'], ref)
            assert pn >= pf - 0.3, 'neural %d%%: %.2f dB against FSR 1 %.2f dB' % (pct, pn, pf)
            assert edges(res['neural']) >= edges(bil), 'neural %d%%: not as sharp as bilinear' % pct
            print('PASS neural %d%%: %.2f dB (FSR 1 %.2f, %+.2f), edge strength x%.2f of FSR 1' % (pct, pn, pf, pn - pf, edges(res['neural']) / edges(res['fsr1'])))
