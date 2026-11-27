#!/usr/bin/env python3
"""eval.py MODEL.pt SHARPEN_TEST: held-out crops (the last three big screenshots of $NNUP_DATA, 1280x720) downscaled to render scales 0.75 and 0.6, then
upscaled by bilinear, FSR 1 (tests/hals/sharpen-test, sharpness 0.8706, AA off and CMAA2 on) and the net; PSNR and SSIM against the original."""
import sys, os, glob, subprocess, tempfile, torch, torch.nn.functional as F, numpy as np
from pathlib import Path
from PIL import Image
from nnup import Net
from pretrained import Pre, Waifu
from distill import Student
W, H = 1280, 720
def ssim(a, b):  # N,3,H,W in 0..1, gaussian 11x11
    g = torch.exp(-((torch.arange(11) - 5.0) ** 2) / (2 * 1.5 ** 2)); g = (g / g.sum()); k = (g[:, None] * g[None]).expand(3, 1, 11, 11).to(a.device)
    f = lambda x: F.conv2d(x, k, groups=3)
    ma, mb = f(a), f(b); va, vb, cab = f(a * a) - ma ** 2, f(b * b) - mb ** 2, f(a * b) - ma * mb
    return (((2 * ma * mb + 1e-4) * (2 * cab + 9e-4)) / ((ma ** 2 + mb ** 2 + 1e-4) * (va + vb + 9e-4))).mean().item()
def psnr(a, b): return (10 * torch.log10(1 / F.mse_loss(a, b))).item()
def fsr(exe, small, aa):
    with tempfile.TemporaryDirectory(prefix='apklayer-nnup-eval-') as t:
        ri, ro = t + '/i.raw', t + '/o.raw'; Path(ri).write_bytes(small.convert('RGBA').tobytes())
        e = dict(os.environ); e['APKLAYER_SHARPEN_AA'] = '' if aa else 'off'
        r = subprocess.run([exe, ri, str(small.width), str(small.height), ro, str(W), str(H), '0.8706'] + ([os.environ['SHARPEN_MODIFIER']] if os.environ.get('SHARPEN_MODIFIER') else []), capture_output=True, text=True, env=e, timeout=60)
        if r.returncode: sys.exit(r.stderr)
        return Image.frombytes('RGBA', (W, H), Path(ro).read_bytes()).convert('RGB')
T = lambda im: torch.from_numpy(np.array(im.convert('RGB'))).permute(2, 0, 1).float().unsqueeze(0).cuda() / 255
net = Net().cuda(); net.load_state_dict(torch.load(sys.argv[1])); net.eval()
pre = {n: Pre(n).to('cuda') for n in ('ESPCN_x2', 'FSRCNN_x2', 'FSRCNN-small_x2')}
files = [f for f in sorted(glob.glob(os.path.expanduser(os.environ.get('NNUP_DATA', '~/Pictures/Schermafdrukken')) + '/*.png')) if min(Image.open(f).size) >= 400][-3:]
wf = {n: Waifu(os.path.expanduser('~/apklayer-nnup-work/w/up7_%s.json' % n)).to('cuda') for n in ('art', 'photo')}  # 10 MB each, fetched from nagadomi/waifu2x models/upconv_7 (not in the repo)
wf = {'waifu2x-' + k: v for k, v in wf.items()}
stu = {}
for p in os.environ.get('NNUP_STUDENTS', '').split(','):
    if p:
        d = torch.load(p); m = Student(d['ch']).cuda(); m.load_state_dict(d['sd']); m.eval(); stu['student-' + os.path.basename(p).replace('.pt', '')] = m
res = {}
for f in files:
    im = Image.open(f).convert('RGB'); 
    for cx, cy in ((0.2, 0.2), (0.6, 0.5)):
        x0, y0 = int((im.width - W) * cx), int((im.height - H) * cy); hr = im.crop((x0, y0, x0 + W, y0 + H))
        for s in [float(x) for x in os.environ.get('NNUP_SCALES', '0.75,0.6').split(',')]:
            small = hr.resize((round(W * s), round(H * s)), Image.BOX)
            outs = {'bilinear': small.resize((W, H), Image.BILINEAR), 'fsr1': fsr(sys.argv[2], small, False), 'fsr1+cmaa2': fsr(sys.argv[2], small, True)}
            with torch.no_grad(): outs['net'] = Image.fromarray((net(T(small), (H, W))[0].permute(1, 2, 0).cpu().numpy() * 255 + .5).astype(np.uint8))
            for n, p in pre.items():
                with torch.no_grad(): outs[n] = Image.fromarray((p(T(small), (H, W))[0].permute(1, 2, 0).cpu().numpy() * 255 + .5).astype(np.uint8))
            for n, p in wf.items():
                with torch.no_grad(): outs[n] = Image.fromarray((p(T(small), (H, W))[0].permute(1, 2, 0).cpu().numpy() * 255 + .5).astype(np.uint8))
            for n, p in stu.items():
                with torch.no_grad(): outs[n] = Image.fromarray((p(T(small), (H, W))[0].permute(1, 2, 0).cpu().numpy() * 255 + .5).astype(np.uint8))
            for k, o in outs.items():
                r = res.setdefault((s, k), []); r.append((psnr(T(o), T(hr)), ssim(T(o), T(hr))))
for (s, k), v in sorted(res.items(), key=lambda kv: (-kv[0][0], kv[0][1])):
    print('scale %.2f %-11s PSNR %.2f SSIM %.4f (n=%d)' % (s, k, np.mean([x[0] for x in v]), np.mean([x[1] for x in v]), len(v)))
