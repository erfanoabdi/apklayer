#!/usr/bin/env python3
"""shimmer.py <sharpen-test> [--scales 65,75,100,150] [--frames 16] [--env K=V ...] [--aliased|--box] [--configs bilinear,fsr,default] [--json out.json]
Temporal stability of the render filter (bridge/shell/sharpen.c through tests/hals/sharpen-test.c with SHARPEN_SEQ): a scene of foliage-like noise, hair strands,
1 px lines, text and circles in a world of 8x the 640x360 output, seen through a camera that moves 0.25 source px per frame in x and 0.125 in y (so every frame is a
different sub-pixel phase). The source of a frame is the world point-sampled (--aliased, a game with no anti-aliasing, the default) or box-filtered (--box); the ground
truth of a frame is the world box-filtered to the output at the same camera position. The pass keeps no state between frames, so all that varies is the phase.
Metrics per configuration (bilinear stretch, FSR 1 alone = APKLAYER_SHARPEN_AA=off, the default pipeline), over the whole picture and per region:
  err    RMS error against the ground truth (0..255), mean over the frames
  flick  RMS of the frame-to-frame change of the error (the true motion cancels out: what is left is the filter's own crawl)
  pulse  relative std over the frames of the mean edge strength (a filter that blurs on some phases and sharpens on others pulses)
  noise  still scene, +-2 LSB uniform input noise redrawn every frame: the mean per-pixel std over the frames of the output (bilinear shows what the noise itself does)
SHARPEN_MODIFIER (hex) is the accepted dmabuf modifier (NVIDIA: 0x300000000606010); SHARPEN_BACKEND=vulkan runs the Vulkan backend."""
import argparse, glob, json, os, subprocess, sys, tempfile
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw, ImageFont

OW, OH, K = 640, 360, 8  # the output, and the world's pixels per output pixel
REGIONS = {  # x0, y0, x1, y1 in output pixels
    'foliage': (10, 10, 300, 170), 'hair': (320, 10, 630, 170), 'lines': (10, 190, 200, 350), 'text': (215, 190, 440, 350), 'circle': (450, 190, 630, 350)}
SX, SY = 0.25, 0.125  # the camera step per frame, in source pixels


def world(rng):
    """the world at K x the output, plus a margin for the camera"""
    mw, mh = OW * K + 256, OH * K + 256
    img = Image.new('RGB', (mw, mh), (70, 80, 90))
    d = ImageDraw.Draw(img)
    def P(v): return int(v * K)
    # foliage: three octaves of noise, green, then leaf-sized ellipses of two tones
    def octave(period, amp):
        n = rng.random((int(160 / period) + 3, int(290 / period) + 3)).astype(np.float32)
        return np.asarray(Image.fromarray((n * 255).astype(np.uint8)).resize((int(290 * K) + 2 * K * 10, int(160 * K) + 2 * K * 10), Image.BICUBIC), np.float32) / 255 * amp
    f = octave(1.3, 0.5) + octave(3.1, 0.4) + octave(7.0, 0.3)
    f = (f - f.mean()) / f.std() * 26 + 105
    fol = np.stack([f * 0.55, f * 1.0, f * 0.4], -1).clip(0, 255).astype(np.uint8)
    img.paste(Image.fromarray(fol), (P(10) - 0, P(10) - 0))
    for _ in range(260):
        x, y, r = rng.uniform(15, 295), rng.uniform(15, 165), rng.uniform(0.8, 2.6)
        c = (int(rng.uniform(30, 60)), int(rng.uniform(90, 170)), int(rng.uniform(20, 60)))
        d.ellipse((P(x - r), P(y - r * 0.5), P(x + r), P(y + r * 0.5)), fill=c)
    # hair: curved dark and light strands 0.6 .. 1.2 px wide on a mid background
    d.rectangle((P(320), P(10), P(630), P(170)), fill=(150, 120, 90))
    for i in range(70):
        x0, y0 = rng.uniform(320, 630), 10.0
        ph, am, fr = rng.uniform(0, 6.3), rng.uniform(2, 12), rng.uniform(0.02, 0.06)
        pts = [(P(min(max(x0 + am * np.sin(fr * (y - 10) * 6 + ph) + 0.2 * (y - 10), 320), 630)), P(y)) for y in np.arange(10, 170, 1.0)]
        d.line(pts, fill=(30, 22, 16) if i % 3 else (225, 190, 150), width=int(K * rng.uniform(0.6, 1.2)))
    # 1 px lines: horizontal, vertical, two slopes, white on dark, and a pair of 1px lines 2 px apart
    d.rectangle((P(10), P(190), P(200), P(350)), fill=(25, 28, 36))
    for i in range(0, 60, 6): d.line((P(15), P(195 + i), P(100), P(195 + i)), fill=(255, 255, 255), width=K)
    for i in range(0, 80, 6): d.line((P(110 + i), P(195), P(110 + i), P(250)), fill=(255, 255, 255), width=K)
    d.line((P(15), P(260), P(195), P(285)), fill=(255, 255, 255), width=K)
    d.line((P(15), P(290), P(195), P(300)), fill=(255, 255, 255), width=K)
    d.line((P(15), P(310), P(195), P(345)), fill=(255, 220, 90), width=K)
    # text: light on dark and dark on light, four sizes
    font = glob.glob('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf')
    d.rectangle((P(215), P(190), P(330), P(350)), fill=(24, 28, 40))
    d.rectangle((P(330), P(190), P(440), P(350)), fill=(238, 240, 244))
    for i, size in enumerate((9, 11, 14, 20)):
        fnt = ImageFont.truetype(font[0], int(size * K)) if font else ImageFont.load_default()
        d.text((P(220), P(193 + i * 40)), 'Quick fox 0123', font=fnt, fill=(235, 235, 235))
        d.text((P(334), P(193 + i * 40)), 'Settings 98', font=fnt, fill=(30, 34, 46))
    # circles: outlines 1 and 2 px, a filled disc, a thin ring
    d.rectangle((P(450), P(190), P(630), P(350)), fill=(40, 44, 60))
    d.ellipse((P(460), P(200), P(530), P(270)), outline=(0, 200, 255), width=K)
    d.ellipse((P(540), P(200), P(620), P(280)), outline=(255, 200, 0), width=2 * K)
    d.ellipse((P(470), P(285), P(520), P(335)), fill=(220, 60, 60))
    d.ellipse((P(545), P(290), P(615), P(340)), outline=(255, 255, 255), width=K)
    return img


def frames(wd, sw, sh, scale, n, aliased, noise, rng, still=False):
    """the n source frames (sw x sh x 4 uint8) and the n ground truths"""
    src, gt = [], []
    for i in range(n):
        ox, oy = 0 if still else SX * i * K / scale, 0 if still else SY * i * K / scale  # world px
        box = (ox, oy, ox + sw * K / scale, oy + sh * K / scale)
        s = wd.resize((sw, sh), Image.NEAREST if aliased else Image.BOX, box=box)
        if not still or i == 0: g = wd.resize((OW, OH), Image.BOX, box=(ox, oy, ox + OW * K, oy + OH * K))
        a = np.asarray(s, np.int16)
        if noise: a = a + rng.integers(-2, 3, a.shape)
        src.append(np.dstack([a.clip(0, 255).astype(np.uint8), np.full((sh, sw), 255, np.uint8)]))
        gt.append(np.asarray(g, np.float32))
    return src, gt


def run(exe, src, sw, sh, env, sharp):
    with tempfile.TemporaryDirectory(prefix='apklayer-shimmer-') as tmp:
        raw_in, raw_out = os.path.join(tmp, 'in.raw'), os.path.join(tmp, 'out.raw')
        Path(raw_in).write_bytes(b''.join(a.tobytes() for a in src))
        e = dict(os.environ, SHARPEN_SEQ=str(len(src)), **env)
        args = [exe, raw_in, str(sw), str(sh), raw_out, str(OW), str(OH), str(sharp)] + ([os.environ['SHARPEN_MODIFIER']] if os.environ.get('SHARPEN_MODIFIER') else [])
        r = subprocess.run(args, capture_output=True, text=True, timeout=300, env=e)
        if r.returncode in (3, 4): sys.exit('SKIP the GPU cannot take the picture as a dma-buf (rc %d)' % r.returncode)
        assert r.returncode == 0, 'sharpen-test failed (rc %d): %s' % (r.returncode, r.stderr)
        out = np.frombuffer(Path(raw_out).read_bytes(), np.uint8).reshape(len(src), OH, OW, 4)[..., :3]
    return out.astype(np.float32)


def bilinear(src, sw, sh):
    return np.stack([np.asarray(Image.fromarray(a[..., :3]).resize((OW, OH), Image.BILINEAR if sw < OW else Image.BOX), np.float32) for a in src])


def edge(a):  # mean gradient magnitude per frame of a luma picture
    l = a @ np.array([0.299, 0.587, 0.114], np.float32)
    gx, gy = np.abs(np.diff(l, axis=-1))[..., :-1, :], np.abs(np.diff(l, axis=-2))[..., :, :-1]
    return gx, gy


def metrics(out, gt, region):
    x0, y0, x1, y1 = region
    o, g = out[:, y0:y1, x0:x1], np.stack(gt)[:, y0:y1, x0:x1]
    e = o - g
    err = float(np.sqrt((e ** 2).mean(axis=(1, 2, 3))).mean())
    flick = float(np.sqrt((np.diff(e, axis=0) ** 2).mean()))
    l = o @ np.array([0.299, 0.587, 0.114], np.float32)
    gx, gy = np.abs(np.diff(l, axis=2))[:, :-1, :], np.abs(np.diff(l, axis=1))[:, :, :-1]
    es = (gx + gy).mean(axis=(1, 2))
    pulse = float(es.std() / es.mean())
    return err, flick, pulse, float(es.mean())


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('exe'); ap.add_argument('--scales', default='65,75,100,150'); ap.add_argument('--frames', type=int, default=16)
    ap.add_argument('--env', action='append', default=[]); ap.add_argument('--box', action='store_true'); ap.add_argument('--aliased', action='store_true')
    ap.add_argument('--configs', default='bilinear,fsr,default'); ap.add_argument('--sharp', type=float, default=0.8706); ap.add_argument('--json')
    ap.add_argument('--regions', default='all,foliage,hair,lines,text,circle')
    a = ap.parse_args()
    aliased = not a.box
    extra = dict(e.split('=', 1) for e in a.env)
    if os.environ.get('SHARPEN_BACKEND'): extra['APKLAYER_SHARPEN_BACKEND'] = os.environ['SHARPEN_BACKEND']
    rng = np.random.default_rng(7)
    wd = world(rng)
    res = {}
    print('source %s; %d frames; camera step %.3f, %.3f source px; sharp %.3f; env %s' % ('aliased (point sampled)' if aliased else 'box filtered', a.frames, SX, SY, a.sharp, extra))
    print('%-6s %-9s %-8s %7s %7s %7s %7s | %7s' % ('scale', 'config', 'region', 'err', 'flick', 'pulse%', 'edge', 'noise'))
    for sc in [int(s) for s in a.scales.split(',')]:
        scale = sc / 100.0
        sw, sh = int(round(OW * scale)), int(round(OH * scale))
        src, gt = frames(wd, sw, sh, scale, a.frames, aliased, False, rng)
        nsrc, _ = frames(wd, sw, sh, scale, a.frames, aliased, True, rng, still=True)
        for cfg in a.configs.split(','):
            if cfg == 'bilinear': out, nout = bilinear(src, sw, sh), bilinear(nsrc, sw, sh)
            elif cfg == 'gt': out, nout = np.stack(gt), bilinear(nsrc, sw, sh)  # the ground truth itself: its edge strength is the target
            else:
                env = dict(extra, **({'APKLAYER_SHARPEN_AA': 'off'} if cfg == 'fsr' else {}))
                out, nout = run(a.exe, src, sw, sh, env, a.sharp), run(a.exe, nsrc, sw, sh, env, a.sharp)
            nz = float(nout.std(axis=0).mean())
            for rg in a.regions.split(','):
                r = (0, 0, OW, OH) if rg == 'all' else REGIONS[rg]
                m = metrics(out, gt, r)
                nzr = float(nout[:, r[1]:r[3], r[0]:r[2]].std(axis=0).mean())
                res['%d/%s/%s' % (sc, cfg, rg)] = dict(err=m[0], flick=m[1], pulse=m[2], edge=m[3], noise=nzr)
                print('%-6d %-9s %-8s %7.2f %7.2f %7.2f %7.2f | %7.3f' % (sc, cfg, rg, m[0], m[1], 100 * m[2], m[3], nzr), flush=True)
    if a.json: Path(a.json).write_text(json.dumps(res, indent=1))


main()
