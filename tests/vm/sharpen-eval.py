#!/usr/bin/env python3
"""sharpen-eval.py <sharpen-test> [--backend gles|vulkan] [--env K=V ...] [--sharp 0.8706] [--scales 0.75,0.6] [--json out.json]: measures the render filter
(bridge/shell/sharpen.c through tests/hals/sharpen-test.c) against the reference on synthetic pictures at 1280x720:
  text   UI text (four sizes, light on dark and dark on light), thin lines, icons (outlines, circles), a button row
  photo  a wallpaper picture (the 3D stand-in: gradients, soft edges), 1280x720 crop of /usr/share/wallpapers/EveningGlow
Each picture is box-filtered down to the scale (75%, 60%; the render scale's range starts at 60%) and upscaled by the filter; compared with PIL's bilinear stretch. Per picture (and the reference itself): PSNR, SSIM
(7x7 uniform window, luma), the mean edge strength and the 99th percentile gradient (Sobel magnitude) and the halo share (output pixels outside the reference's own 3x3 min/max by > 8/255).
SHARPEN_MODIFIER (hex) is the accepted dmabuf modifier (NVIDIA: 0x300000000606010). Prints one table; with --json also a machine readable copy (tools compare two runs)."""
import argparse, glob, json, os, subprocess, sys, tempfile
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw, ImageFont

W, H = 1280, 720


def text_picture():
    fonts = glob.glob('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf') + glob.glob('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf')
    img = Image.new('RGB', (W, H), (24, 28, 40))
    d = ImageDraw.Draw(img)
    d.rectangle((640, 0, W, H // 2 + 60), fill=(238, 240, 244))
    for i, size in enumerate((12, 16, 22, 34)):
        f = ImageFont.truetype(fonts[0], size)
        d.text((30, 20 + i * 52), 'The quick brown fox jumps over the lazy dog 0123', font=f, fill=(235, 235, 235))
        d.text((660, 20 + i * 52), 'Settings  Wi-Fi  Bluetooth  1234567890', font=f, fill=(30, 34, 46))
    fb = ImageFont.truetype(fonts[-1], 20)
    d.text((30, 250), 'Bold: Start game / Options / Quit', font=fb, fill=(255, 210, 90))
    for i in range(0, 300, 7): d.line((700 + i, 330, 700 + i, 420), fill=(30, 34, 46), width=1)
    for i in range(0, 200, 9): d.line((60, 330 + i // 3, 360, 330 + i // 3), fill=(200, 210, 230), width=1)
    d.rectangle((60, 420, 360, 500), outline=(255, 200, 0), width=2)
    d.ellipse((420, 400, 560, 540), outline=(0, 200, 255), width=2)
    d.ellipse((600, 430, 660, 490), fill=(220, 60, 60))
    d.line((440, 560, 640, 640), fill=(255, 255, 255), width=2)
    d.rounded_rectangle((60, 560, 300, 620), radius=14, outline=(120, 220, 140), width=2)
    d.text((86, 575), 'OK  Cancel', font=ImageFont.truetype(fonts[0], 20), fill=(120, 220, 140))
    for i, c in enumerate(((255, 80, 80), (80, 255, 120), (90, 140, 255))):  # coloured text on dark
        d.text((700, 480 + i * 40), 'Coloured text %d: score 98765' % i, font=ImageFont.truetype(fonts[0], 18), fill=c)
    return img, (20, 10, 1250, 660)


def photo_picture():
    p = glob.glob('/usr/share/wallpapers/EveningGlow/contents/images/2560x1600.jpg')
    if not p: return None, None
    im = Image.open(p[0]).convert('RGB')
    return im.crop((0, 0, 2560, 1440)).resize((W, H), Image.LANCZOS), (0, 0, W, H)


def luma(a): return a[..., 0] * 0.299 + a[..., 1] * 0.587 + a[..., 2] * 0.114


def box(a, n):  # mean over n x n (valid, via cumulative sums)
    c = np.cumsum(np.cumsum(np.pad(a, ((1, 0), (1, 0))), 0), 1)
    return (c[n:, n:] - c[:-n, n:] - c[n:, :-n] + c[:-n, :-n]) / (n * n)


def ssim(a, b):
    a, b = luma(a), luma(b)
    ma, mb = box(a, 7), box(b, 7)
    va, vb, cv = box(a * a, 7) - ma * ma, box(b * b, 7) - mb * mb, box(a * b, 7) - ma * mb
    c1, c2 = (0.01 * 255) ** 2, (0.03 * 255) ** 2
    return float(np.mean(((2 * ma * mb + c1) * (2 * cv + c2)) / ((ma * ma + mb * mb + c1) * (va + vb + c2))))


def psnr(a, b):
    mse = np.mean((a - b) ** 2)
    return 99.0 if mse == 0 else 10 * np.log10(255 * 255 / mse)


def edge(a):
    l = luma(a)
    gx = l[1:-1, 2:] - l[1:-1, :-2]; gy = l[2:, 1:-1] - l[:-2, 1:-1]
    return float(np.mean(np.hypot(gx, gy)))


def steep(a):  # the 99th percentile of the gradient magnitude: how steep the steepest edges are
    l = luma(a)
    return float(np.percentile(np.hypot(l[1:-1, 2:] - l[1:-1, :-2], l[2:, 1:-1] - l[:-2, 1:-1]), 99))


def halo(a, ref):  # share of pixels beyond the reference's 3x3 range (per channel) by more than 8
    r = np.pad(ref, ((1, 1), (1, 1), (0, 0)), mode='edge')
    stack = np.stack([r[y:y + H, x:x + W] for y in range(3) for x in range(3)])
    over = (a > stack.max(0) + 8) | (a < stack.min(0) - 8)
    return float(np.mean(over.any(-1)))


def crop(a, box_):
    x0, y0, x1, y1 = box_
    return a[y0:y1, x0:x1]


def run(exe, backend, env, sharp, small):
    with tempfile.TemporaryDirectory(prefix='apklayer-sharpen-eval-') as tmp:
        ri, ro = os.path.join(tmp, 'in.raw'), os.path.join(tmp, 'out.raw')
        Path(ri).write_bytes(small.convert('RGBA').tobytes())
        e = dict(os.environ, APKLAYER_SHARPEN_AA='off')  # FSR 1 alone unless --env APKLAYER_SHARPEN_AA= (empty: the anti-aliasing the shell always runs, CMAA2 ultra)
        e.update(env)
        if backend == 'vulkan': e['APKLAYER_SHARPEN_BACKEND'] = 'vulkan'
        r = subprocess.run([exe, ri, str(small.width), str(small.height), ro, str(W), str(H), str(sharp)] + ([os.environ['SHARPEN_MODIFIER']] if os.environ.get('SHARPEN_MODIFIER') else []), capture_output=True, text=True, timeout=60, env=e)
        if r.returncode: sys.exit('%s failed (rc %d): %s' % (exe, r.returncode, r.stderr))
        return Image.frombytes('RGBA', (W, H), Path(ro).read_bytes()).convert('RGB')


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('exe'); ap.add_argument('--backend', default='gles'); ap.add_argument('--env', action='append', default=[])
    ap.add_argument('--sharp', type=float, default=0.8706); ap.add_argument('--scales', default='0.75,0.6'); ap.add_argument('--json'); ap.add_argument('--keep')
    a = ap.parse_args()
    env = dict(x.split('=', 1) for x in a.env)
    res = {}
    for name, (ref, region) in (('text', text_picture()), ('photo', photo_picture())):
        if ref is None: continue
        refa = np.asarray(ref, dtype=np.float64)
        for sc in [float(x) for x in a.scales.split(',')]:
            small = ref.resize((round(W * sc), round(H * sc)), Image.BOX)
            for label, img in (('bilinear', small.resize((W, H), Image.BILINEAR)), ('filter', run(a.exe, a.backend, env, a.sharp, small))):
                o = np.asarray(img, dtype=np.float64)
                if a.keep: img.save('%s-%s-%d-%s.png' % (a.keep, name, round(sc * 100), label))
                res.setdefault('%s reference' % name, dict(psnr=99.0, ssim=1.0, edge=edge(crop(refa, region)), steep=steep(crop(refa, region)), halo=0.0))
                res['%s %d%% %s' % (name, round(sc * 100), label)] = dict(psnr=psnr(crop(o, region), crop(refa, region)), ssim=ssim(crop(o, region), crop(refa, region)), edge=edge(crop(o, region)), steep=steep(crop(o, region)), halo=halo(o, refa) * 100)
    print('%-22s %7s %7s %7s %7s %7s' % ('', 'PSNR', 'SSIM', 'edge', 'steep', 'halo%'))
    for k, v in res.items(): print('%-22s %7.2f %7.4f %7.2f %7.1f %7.2f' % (k, v['psnr'], v['ssim'], v['edge'], v['steep'], v['halo']))
    if a.json: Path(a.json).write_text(json.dumps(res, indent=1))


main()
