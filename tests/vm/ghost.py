#!/usr/bin/env python3
"""ghost.py <sharpen-test> [--scale 75] [--speeds 0.25,1,4,12] [--env K=V ...]: ghosting of the shell's temporal stabiliser (APKLAYER_SHARPEN_TEMPORAL, bridge/shell/sharpen.c).
A 14 px wide white bar on a dark, softly blotched background moves right at v output px per frame for 16 frames, then stands still for 8 (the source is point sampled: a game with no
anti-aliasing); the output is compared with the truth (the same scene box-filtered to 640x360). Per speed: err (RMS over all frames), trail (the largest error, in levels, at pixels
at least 5 px behind the bar's trailing edge or ahead of its leading edge: what a history that is not forgotten leaves behind), band (mean absolute error in the 2..10 px behind the
trailing edge, the visible smear), settle (RMS error over the last frame, when the bar stands still). SHARPEN_MODIFIER as in shimmer.py. --scene scroll: text lines scrolling left at v px per frame instead (--fg = the text level on the 24..40 background): err and edge/truth (output edge strength over the truth's: a history that smears moving detail lowers it). Run it with and without APKLAYER_SHARPEN_TEMPORAL=off (--env) to see what the stabiliser costs."""
import argparse, glob, os, subprocess, sys, tempfile
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw, ImageFont

OW, OH, K, BW = 640, 360, 8, 14
MOVE, STILL = 16, 8


def frames(sw, sh, v):
    rng = np.random.default_rng(3)
    bg = (rng.random((OH // 8, OW // 8)) * 30 + 60).astype(np.uint8)  # soft blotches of 8 output px (no aliasing in the source: the error left is the filter's)
    bg = np.asarray(Image.fromarray(bg).resize((OW * K, OH * K), Image.BICUBIC).convert('RGB'))
    src, gt, pos = [], [], []
    for i in range(MOVE + STILL):
        x = 100 + v * min(i, MOVE - 1)
        img = Image.fromarray(bg.copy())
        ImageDraw.Draw(img).rectangle((int(x * K), 20 * K, int((x + BW) * K) - 1, (OH - 20) * K - 1), fill=(240, 240, 240))
        s = np.asarray(img.resize((sw, sh), Image.NEAREST), np.uint8)
        src.append(np.dstack([s, np.full((sh, sw), 255, np.uint8)]))
        gt.append(np.asarray(img.resize((OW, OH), Image.BOX), np.float32))
        pos.append(x)
    return src, gt, pos


def scroll_frames(sw, sh, v, fg=235):
    """text lines scrolling left at v output px per frame (a list, a ticker): the case a history blend smears"""
    font = glob.glob('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf')
    big = Image.new('RGB', (OW * K + int(v * (MOVE + STILL) * K) + 64, OH * K), (24, 28, 40))
    d = ImageDraw.Draw(big)
    for i, size in enumerate((10, 12, 14, 18, 24, 11, 16)):
        fnt = ImageFont.truetype(font[0], size * K) if font else ImageFont.load_default()
        d.text((10 * K, (10 + i * 48) * K), 'Quick brown fox 0123 jumps over the lazy dog 4567 Settings Wi-Fi', font=fnt, fill=(fg, fg, fg))
    src, gt, pos = [], [], []
    for i in range(MOVE + STILL):
        ox = v * min(i, MOVE - 1) * K
        img = big.crop((int(ox), 0, int(ox) + OW * K, OH * K))
        s = np.asarray(img.resize((sw, sh), Image.NEAREST), np.uint8)
        src.append(np.dstack([s, np.full((sh, sw), 255, np.uint8)]))
        gt.append(np.asarray(img.resize((OW, OH), Image.BOX), np.float32))
        pos.append(0)
    return src, gt, pos


def edge(a):
    l = a @ np.array([0.299, 0.587, 0.114], np.float32)
    return float((np.abs(np.diff(l, axis=-1))[..., :-1, :] + np.abs(np.diff(l, axis=-2))[..., :, :-1]).mean())


def run(exe, src, sw, sh, env):
    with tempfile.TemporaryDirectory(prefix='apklayer-ghost-') as tmp:
        ri, ro = os.path.join(tmp, 'in.raw'), os.path.join(tmp, 'out.raw')
        Path(ri).write_bytes(b''.join(a.tobytes() for a in src))
        e = dict(os.environ, SHARPEN_SEQ=str(len(src)), **env)
        args = [exe, ri, str(sw), str(sh), ro, str(OW), str(OH), '0.8706'] + ([os.environ['SHARPEN_MODIFIER']] if os.environ.get('SHARPEN_MODIFIER') else [])
        r = subprocess.run(args, capture_output=True, text=True, timeout=300, env=e)
        assert r.returncode == 0, 'sharpen-test failed (rc %d): %s' % (r.returncode, r.stderr)
        return np.frombuffer(Path(ro).read_bytes(), np.uint8).reshape(len(src), OH, OW, 4)[..., :3].astype(np.float32)


def metrics(out, gt, pos, v):
    err = float(np.sqrt(np.mean([(o - g) ** 2 for o, g in zip(out, gt)])))
    trail, band = 0.0, []
    xs = np.arange(OW)
    for o, g, x in zip(out, gt, pos):
        e = np.abs(o - g)[40:OH - 40].mean(axis=(0, 2))  # per column, rows away from the bar's ends
        far = (xs < x - 5) | (xs > x + BW + 5)
        trail = max(trail, float(np.abs(o - g)[40:OH - 40][:, far].max()))
        bm = (xs >= x - 10) & (xs <= x - 2)
        band.append(float(e[bm].mean()))
    return err, trail, float(np.mean(band)), float(np.sqrt(((out[-1] - gt[-1]) ** 2).mean()))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('exe'); ap.add_argument('--scale', type=int, default=75); ap.add_argument('--speeds', default='0.25,1,4,12')
    ap.add_argument('--env', action='append', default=[]); ap.add_argument('--label', default=''); ap.add_argument('--fg', type=int, default=235, help='scroll: the text level on the 24..40 background (contrast = fg - 30)'); ap.add_argument('--scene', default='bar', help='bar | scroll (text scrolling v px per frame: err and edge strength of the output / truth)')
    a = ap.parse_args()
    sw, sh = round(OW * a.scale / 100), round(OH * a.scale / 100)
    env = dict(e.split('=', 1) for e in a.env)
    print('%s scale %d%%: speed(px/frame)  err   trail  band  settle' % (a.label, a.scale))
    for v in [float(x) for x in a.speeds.split(',')]:
        if a.scene == 'scroll':
            src, gt, pos = scroll_frames(sw, sh, v, a.fg)
            out = run(a.exe, src, sw, sh, env)
            err = float(np.sqrt(np.mean([(o - g) ** 2 for o, g in zip(out, gt)])))
            print('%s  scroll %6.2f  err %6.2f  edge/truth %.3f  settle err %.2f' % (a.label, v, err, edge(out[:MOVE]) / edge(np.stack(gt[:MOVE])), float(np.sqrt(((out[-1] - gt[-1]) ** 2).mean()))))
            continue
        src, gt, pos = frames(sw, sh, v)
        m = metrics(run(a.exe, src, sw, sh, env), gt, pos, v)
        print('%s  %6.2f  %6.2f  %6.1f  %6.2f  %6.2f' % (a.label, v, *m))


main()
