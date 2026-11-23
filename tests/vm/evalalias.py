#!/usr/bin/env python3
"""The yardstick for anti-aliasing on ALIASED input (docs/FILTER-EVAL.md; tests/vm/evalfilter.py box-reduces an already smooth image and cannot show jaggies):
  tests/vm/evalalias.py [--scales 35,50,75] [--filter label[:ENV=VAL,...]]... [--sharp 0.8706] [--loops 100] [--save dir] [--json f]
A 1280x720 scene of 12 tiles is drawn at 4x4 supersampling (no anti-aliasing in PIL); the TRUTH is its box reduction to 1280x720, the SOURCE at a scale is the nearest sample of
the hires picture at the source size (a game rendering without AA), brought to 1280x720 by every --filter (bridge/shell/sharpen.c through sharpen-test, env as in evalfilter).
Tiles: text_small, text_large, icons, lines (UI) | shapes (shaded spheres and polygons with specular), city (buildings, lamp posts, wires, railings), foliage (3D) | char1..char4 (character-like:
hair strands 1-3 px, belts, straps, buckles, torn trouser edges, a thin blade, specular arcs, hue-only edges, in front of a city; the close-up char4 is mostly hair). Per group:
PSNR (dB, colour) against the truth, jag (mean abs colour error, levels, over the truth's edge pixels: lower = smoother and closer), edge (Sobel strength out/truth, 1.0 = as sharp),
halo% (pixels more than 8 levels outside the truth's 5x5 range) and ms (GPU plus fence wait per frame at 1280x720 output, --loops frames; 0.01 ms is noise)."""
import argparse
import glob
import json
import math
import os
import subprocess
import sys
import tempfile
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parent))
import evalcorpus as C  # noqa: E402
import evalfilter as F  # noqa: E402
import evalmetrics as M  # noqa: E402

K = 4
W, H, TW, TH = 1280, 720, 320, 240
NAMES = ['text_small', 'text_large', 'icons', 'lines', 'shapes', 'city', 'foliage', 'char1', 'char2', 'char3', 'char4', 'ui']
GROUPS = {'text': ('text_small', 'text_large'), 'ui': ('icons', 'lines', 'ui'), '3d': ('shapes', 'city', 'foliage'), 'char': ('char1', 'char2', 'char3', 'char4')}


def pts_bez(p0, p1, p2, n=14): return [((1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * p1[0] + t * t * p2[0], (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * p1[1] + t * t * p2[1]) for t in np.linspace(0, 1, n)]


def line(d, pts, col, w): d.line([(x * K, y * K) for x, y in pts], fill=col, width=max(1, round(w * K)), joint='curve')


def poly(d, pts, col): d.polygon([(x * K, y * K) for x, y in pts], fill=col)


def tile_img(bg): return Image.new('RGB', (TW * K, TH * K), bg)


def text_tile(bg, fg, rows):
    im = tile_img(bg); d = ImageDraw.Draw(im); y = 6
    for kind, size, txt in rows:
        d.text((8 * K, y * K), txt, font=C.font(kind, size * K), fill=fg); y += int(size * 1.35) + 3
    return im


def t_text_small(): return text_tile((24, 28, 40), (232, 232, 232), [('sans', 11, 'Settings  Wi-Fi  Bluetooth  Display 0123456789'), ('sans', 13, 'The quick brown fox jumps over the lazy dog'), ('mono', 12, 'int main(void) { return 0; } // 12px'), ('sans', 15, 'Inventory  Quests  Map  Chat  Friends 42/99'), ('mono', 14, 'HP 1234/5678  MP 090/120  LV 56'), ('sans', 16, 'Confirm   Cancel   Retry   Back'), ('serif', 17, 'Quick zephyrs blow, vexing daft Jim.')])
def t_text_large(): return text_tile((40, 44, 56), (250, 250, 250), [('bold', 20, 'Battle Pass  Season 12'), ('sans', 24, 'Daily Missions 3/5'), ('bold', 28, 'Reward Claimed'), ('sans', 36, 'Start Game'), ('bold', 44, 'VICTORY 1280')])


def t_icons():
    im = tile_img((30, 34, 48)); d = ImageDraw.Draw(im)
    for i in range(6):
        for j in range(4):
            cx, cy, c = 30 + i * 50, 30 + j * 52, (230, 230, 120) if (i + j) % 2 else (120, 220, 240)
            if (i + j) % 3 == 0: d.ellipse(((cx - 14) * K, (cy - 14) * K, (cx + 14) * K, (cy + 14) * K), outline=c, width=2 * K)
            elif (i + j) % 3 == 1: d.rectangle(((cx - 13) * K, (cy - 13) * K, (cx + 13) * K, (cy + 13) * K), outline=c, width=2 * K); line(d, [(cx - 8, cy), (cx + 8, cy)], c, 2)
            else: poly(d, [(cx - 12, cy + 12), (cx, cy - 13), (cx + 12, cy + 12)], c)
    return im


def t_lines():
    im = tile_img((20, 22, 30)); d = ImageDraw.Draw(im)
    for i in range(12): a = math.radians(i * 7.5); line(d, [(30, 120), (30 + 140 * math.cos(a), 120 - 100 * math.sin(a))], (230, 230, 230), 1)
    for i in range(10): line(d, [(200 + i * 11, 20), (200 + i * 11, 100)], (200, 220, 255), 1 + (i % 3) * 0.5)
    for i in range(8): line(d, [(180, 130 + i * 12), (310, 130 + i * 12 + (i - 4) * 3)], (250, 190, 90), 1)
    line(d, pts_bez((20, 220), (100, 140), (170, 225)), (120, 255, 160), 1.2)
    return im


def t_ui():
    im = tile_img((236, 238, 242)); d = ImageDraw.Draw(im)
    d.rounded_rectangle((12 * K, 12 * K, 308 * K, 62 * K), radius=10 * K, fill=(255, 255, 255), outline=(200, 205, 215), width=K)
    d.rounded_rectangle((20 * K, 100 * K, 130 * K, 140 * K), radius=8 * K, fill=(32, 120, 230)); d.rounded_rectangle((170 * K, 100 * K, 290 * K, 140 * K), radius=8 * K, outline=(32, 120, 230), width=2 * K)
    d.text((26 * K, 68 * K), 'Notification title and body 12px', font=C.font('sans', 12 * K), fill=(40, 44, 54)); d.text((40 * K, 110 * K), 'Accept', font=C.font('bold', 14 * K), fill=(255, 255, 255)); d.text((196 * K, 110 * K), 'Decline', font=C.font('bold', 14 * K), fill=(32, 120, 230))
    for i in range(4): d.text((20 * K, (160 + i * 18) * K), ('Wi-Fi', 'Bluetooth', 'Brightness', 'Volume')[i] + '   on', font=C.font('sans', 12 * K), fill=(30, 34, 44))
    d.ellipse((230 * K, 160 * K, 290 * K, 220 * K), outline=(220, 70, 70), width=2 * K)
    return im


def t_shapes():
    ys, xs = np.mgrid[0:TH * K, 0:TW * K].astype(np.float32) / K
    img = np.zeros((TH * K, TW * K, 3), np.float32); img[:] = np.linspace(40, 90, TH * K)[:, None, None] * np.array([0.6, 0.7, 1.0])
    light = np.array([-0.5, -0.6, 0.62]); light /= np.linalg.norm(light)
    for cx, cy, r, col in ((90, 100, 70, (230, 90, 70)), (220, 90, 55, (80, 190, 120)), (170, 180, 45, (240, 210, 90))):
        dx, dy = xs - cx, ys - cy; m = dx * dx + dy * dy < r * r; z = np.sqrt(np.maximum(r * r - dx * dx - dy * dy, 0)); nrm = np.stack([dx, dy, z], -1) / r
        ndl = (nrm * light).sum(-1)[..., None]; spec = np.clip((2 * ndl * nrm - light)[..., 2:3], 0, 1) ** 40
        img[m] = (np.array(col) * (0.12 + 0.88 * np.clip(ndl, 0, 1)) + 255 * 0.6 * spec)[m]
    im = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8)); d = ImageDraw.Draw(im)
    poly(d, [(250, 150), (300, 200), (230, 236), (205, 190)], (200, 200, 230)); line(d, [(10, 230), (310, 190)], (240, 240, 240), 2)
    return im


def t_city():
    im = tile_img((120, 150, 190)); d = ImageDraw.Draw(im); rs = np.random.RandomState(5)
    x = 0
    while x < TW:
        w, h = rs.randint(24, 50), rs.randint(60, 170); c = int(rs.randint(70, 130)); poly(d, [(x, TH - 30), (x, TH - 30 - h), (x + w, TH - 30 - h), (x + w, TH - 30)], (c, c + 8, c + 25))
        for wy in range(TH - 30 - h + 6, TH - 36, 9):
            for wx in range(int(x) + 4, int(x + w) - 6, 8): poly(d, [(wx, wy), (wx + 4, wy), (wx + 4, wy + 5), (wx, wy + 5)], (230, 220, 150) if rs.rand() < 0.4 else (50, 60, 80))
        x += w + 2
    poly(d, [(0, TH - 30), (TW, TH - 30), (TW, TH), (0, TH)], (60, 60, 66))
    for px in (50, 130, 210, 285):  # lamp posts: 2 px pole, a thin arm and a lamp, wires between them
        line(d, [(px, TH - 30), (px, 70)], (30, 32, 38), 2); line(d, [(px, 72), (px + 18, 68)], (30, 32, 38), 1.5); poly(d, [(px + 16, 66), (px + 24, 66), (px + 22, 72), (px + 18, 72)], (255, 240, 170))
    for px in (50, 130, 210): line(d, pts_bez((px, 76), ((px + 80) / 1, 98), (px + 80, 76)), (25, 25, 30), 1)
    for i in range(0, TW, 10): line(d, [(i, TH - 12), (i, TH - 2)], (200, 200, 200), 1.5)
    line(d, [(0, TH - 12), (TW, TH - 12)], (200, 200, 200), 1.5)
    return im


def t_foliage():
    rs = np.random.RandomState(7); im = tile_img((30, 70, 28)); d = ImageDraw.Draw(im)
    for _ in range(1400):
        x, y = rs.uniform(0, TW), rs.uniform(0, TH); a, b = rs.uniform(1.5, 5), rs.uniform(1, 2.5); g = rs.randint(60, 190)
        d.ellipse(((x - a) * K, (y - b) * K, (x + a) * K, (y + b) * K), fill=(int(g * rs.uniform(.3, .7)), g, int(g * rs.uniform(.15, .45))))
    return im


def character(seed, zoom, face):
    """a character-like figure in front of a city; zoom scales the figure, face > 0 puts the hair across the whole tile (a close-up)"""
    rs = np.random.RandomState(seed); im = t_city() if face == 0 else tile_img((70, 80, 110)); d = ImageDraw.Draw(im)
    if face: [poly(d, [(i * 40, 0), (i * 40, 100 + rs.randint(0, 100)), (i * 40 + 36, 100 + rs.randint(0, 100)), (i * 40 + 36, 0)], (90 + i * 4, 100, 130)) for i in range(8)]
    ox, oy = 160 + rs.randint(-30, 30), 238
    def P(x, y): return (ox + x * zoom, oy + y * zoom)
    cloth = [(28, 32, 58), (230, 232, 238), (40, 50, 40), (120, 30, 40)][seed % 4]
    skin = (240, 205, 185)
    if face:
        d.ellipse(((ox - 70 * zoom) * K, (oy - 190 * zoom) * K, (ox + 70 * zoom) * K, (oy - 40 * zoom) * K), fill=skin)
        poly(d, [P(-90, 0), P(-60, -50), P(60, -50), P(90, 0)], cloth)
    else:
        poly(d, [P(-26, -100), P(26, -100), P(30, -60), P(20, -10), P(-20, -10), P(-30, -60)], cloth)                    # torso
        poly(d, [P(-24, -10), P(-4, -10), P(-6, 20), P(-12, 70), P(-18, 100), P(-22, 60)], cloth)                          # left leg
        poly(d, [P(4, -10), P(24, -10), P(22, 60), P(18, 100), P(10, 100), P(6, 40)], cloth)                                # right leg
        poly(d, [P(-18, 100), P(-12, 112), P(-16, 106), P(-9, 118), P(-4, 108), P(0, 120), P(4, 106), P(10, 118), P(14, 104), P(18, 100)], (20, 20, 24))  # torn hem
        poly(d, [P(-30, -98), P(-52, -40), P(-44, -36), P(-26, -80)], skin); poly(d, [P(30, -98), P(50, -50), P(56, -20), P(48, -18), P(42, -48), P(26, -80)], skin)  # arms
        d.ellipse(((ox - 14 * zoom) * K, (oy - 142 * zoom) * K, (ox + 14 * zoom) * K, (oy - 106 * zoom) * K), fill=skin)                                                # face
    # hair: strands as thin curves, over the face/background; colour by seed
    hc = [(235, 238, 250), (250, 200, 90), (40, 28, 30), (230, 90, 140)][seed % 4]; hc2 = [(170, 180, 215), (200, 140, 60), (90, 70, 80), (170, 50, 100)][seed % 4]
    hy, n = (-142, 70) if not face else (-190, 140)
    for i in range(n):
        a = rs.uniform(-1, 1); x0 = a * 13 * (1 if not face else 5); L = rs.uniform(20, 55) * (1 if not face else 2.2); sway = rs.uniform(-1, 1) * 20 * (1 if not face else 2)
        line(d, pts_bez(P(x0, hy), P(x0 + sway * 0.6, hy + L * 0.5), P(x0 + sway + a * 6, hy + L)), hc if i % 3 else hc2, rs.choice([1.0, 1.5, 2.0, 3.0]))
    if not face:
        for i in range(3):  # belts, buckles, a diagonal strap, isoluminant red/green trim, a thin blade, specular arcs
            line(d, [P(-26, -30 + i * 9), P(26, -30 + i * 9 + rs.uniform(-3, 3))], [(200, 160, 60), (160, 160, 170), (60, 160, 90)][i], 5 - i)
        poly(d, [P(-4, -33), P(6, -33), P(6, -24), P(-4, -24)], (240, 220, 120)); line(d, [P(-28, -96), P(24, -16)], (190, 70, 60), 4); line(d, [P(-24, -96), P(26, -20)], (60, 150, 60), 1.5)
        line(d, [P(60, -140), P(-2, 30)], (230, 240, 255), 1.5); line(d, pts_bez(P(-10, -138), P(0, -148), P(12, -136)), (255, 255, 255), 1)
    else:
        for i in range(4): line(d, [P(-60 + i * 30, -40), P(-50 + i * 33, 0)], [(200, 160, 60), (190, 70, 60), (60, 150, 60), (230, 230, 240)][i], 4)
    return im


def corpus():
    builders = {'text_small': t_text_small, 'text_large': t_text_large, 'icons': t_icons, 'lines': t_lines, 'shapes': t_shapes, 'city': t_city, 'foliage': t_foliage,
                'char1': lambda: character(1, 1.0, 0), 'char2': lambda: character(2, 1.15, 0), 'char3': lambda: character(3, 0.85, 0), 'char4': lambda: character(4, 1.0, 1), 'ui': t_ui}
    hi = Image.new('RGB', (W * K, H * K)); tiles = {}
    for i, n in enumerate(NAMES):
        x, y = (i % 4) * TW, (i // 4) * TH
        hi.paste(builders[n](), (x * K, y * K)); tiles[n] = (x, y, x + TW, y + TH)
    return hi, hi.resize((W, H), Image.BOX), tiles


def source(hi, sw, sh):  # the nearest sample at the pixel centres: what a renderer without AA produces at sw x sh
    xs = ((np.arange(sw) + 0.5) * hi.width / sw).astype(int); ys = ((np.arange(sh) + 0.5) * hi.height / sh).astype(int)
    return Image.fromarray(np.asarray(hi)[ys][:, xs])


def timing(exe, small, sharp, env, loops):
    with tempfile.TemporaryDirectory(prefix='apklayer-evalalias-') as tmp:
        rin, rout = os.path.join(tmp, 'in.raw'), os.path.join(tmp, 'out.raw'); Path(rin).write_bytes(small.convert('RGBA').tobytes())
        mod = [os.environ['SHARPEN_MODIFIER']] if os.environ.get('SHARPEN_MODIFIER') else []
        for m in (mod or [None, F.NVIDIA_MOD]):
            r = subprocess.run([exe, rin, str(small.width), str(small.height), rout, str(W), str(H), str(sharp)] + ([m] if m else []), capture_output=True, text=True, env={**os.environ, **env, 'SHARPEN_LOOPS': str(loops)})
            if r.returncode != 5: break
        for l in r.stderr.splitlines():
            if l.startswith('timing:'): return float(l.split()[1])
    return float('nan')


def bil(a, y, x):  # bilinear sample of a at float coordinates (clamped)
    y, x = np.clip(y, 0, a.shape[0] - 1.001), np.clip(x, 0, a.shape[1] - 1.001); y0, x0 = y.astype(int), x.astype(int); fy, fx = y - y0, x - x0
    return (a[y0, x0] * (1 - fx) + a[y0, x0 + 1] * fx) * (1 - fy) + (a[y0 + 1, x0] * (1 - fx) + a[y0 + 1, x0 + 1] * fx) * fy


def stair_map(la, lb):
    """roughness along the edges: the mean |second difference| of the output's luma along the TRUTH's edge tangent (+-1 px), at the edge pixels ea (lower = smoother; a stair step shows as a bump)"""
    p = np.pad(lb, 1, mode='edge')
    gx = (p[:-2, 2:] + 2 * p[1:-1, 2:] + p[2:, 2:]) - (p[:-2, :-2] + 2 * p[1:-1, :-2] + p[2:, :-2]); gy = (p[2:, :-2] + 2 * p[2:, 1:-1] + p[2:, 2:]) - (p[:-2, :-2] + 2 * p[:-2, 1:-1] + p[:-2, 2:])
    n = np.maximum(np.hypot(gx, gy), 1e-3); tx, ty = -gy / n, gx / n
    ys, xs = np.mgrid[0:la.shape[0], 0:la.shape[1]].astype(np.float32)
    return np.abs(bil(la, ys + ty, xs + tx) + bil(la, ys - ty, xs - tx) - 2 * la)


def measure(out, truth, tiles):
    a, b = np.asarray(out, np.float32), np.asarray(truth, np.float32); la, lb = M.luma(out), M.luma(truth)
    res = {}; sm = stair_map(la, lb)
    for n, (x0, y0, x1, y1) in tiles.items():
        x0, y0, x1, y1 = x0 + F.MARGIN, y0 + F.MARGIN, x1 - F.MARGIN, y1 - F.MARGIN
        ea = M.grad(lb)[y0:y1, x0:x1] > 60  # the truth's edge pixels (step of 15 levels or more)
        full = M.grad(lb) > 60; fm = np.zeros_like(full); fm[y0:y1, x0:x1] = True
        err = np.abs(a[y0:y1, x0:x1] - b[y0:y1, x0:x1]).mean(-1)
        res[n] = {'psnr': M.psnr(a[y0:y1, x0:x1], b[y0:y1, x0:x1]), 'jag': float(err[ea].mean()) if ea.any() else 0.0, 'edge': M.edge_ratio(la[y0:y1, x0:x1], lb[y0:y1, x0:x1]), 'halo': M.halo(la[y0:y1, x0:x1], lb[y0:y1, x0:x1], 8.0), 'stair': float(sm[full & fm].mean())}
    return res


def main():
    ap = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    ap.add_argument('--scales', default='35,50,75'); ap.add_argument('--filter', action='append', default=[]); ap.add_argument('--sharp', type=float, default=0.8706)
    ap.add_argument('--loops', type=int, default=100); ap.add_argument('--save'); ap.add_argument('--json'); ap.add_argument('--exe'); ap.add_argument('--only', default='')
    a = ap.parse_args()
    exe = a.exe or F.find_exe()
    if not exe: sys.exit('SKIP sharpen-test not built')
    if not glob.glob('/dev/dri/renderD*'): sys.exit('SKIP no render node')
    filters = [('bilinear', None), ('truth', 'truth')]
    for f in a.filter or ['off:APKLAYER_SHARPEN_AA=off', 'low:APKLAYER_SHARPEN_AA=low', 'high:APKLAYER_SHARPEN_AA=high']:
        label, _, env = f.partition(':'); filters.append((label, dict(kv.split('=', 1) for kv in env.split(';') if kv)))
    hi, truth, tiles = corpus()
    if a.save: os.makedirs(a.save, exist_ok=True); truth.save(os.path.join(a.save, 'truth.png'))
    allres = {}
    for s in a.scales.split(','):
        small = source(hi, round(W * int(s) / 100), round(H * int(s) / 100)); allres[s] = {}
        for label, env in filters:
            out = truth if env == 'truth' else small.resize((W, H), Image.BILINEAR) if env is None else F.run_filter(exe, small, W, H, a.sharp, env)
            r = measure(out, truth, tiles); r['ms'] = timing(exe, small, a.sharp, env, a.loops) if isinstance(env, dict) and a.loops else 0.0
            allres[s][label] = r
            if a.save: out.save(os.path.join(a.save, '%s-%s.png' % (s, label)))
        if a.save: small.save(os.path.join(a.save, '%s-src.png' % s))
    cols = [('text', 'edge'), ('ui', 'psnr'), ('3d', 'psnr'), ('3d', 'jag'), ('3d', 'stair'), ('char', 'psnr'), ('char', 'jag'), ('char', 'stair'), ('char', 'edge'), ('char', 'halo')]
    for s, per in allres.items():
        print('\n== %s%% ==' % s)
        print('%-14s %8s %8s %8s %8s %8s %9s %8s %8s %8s %8s %7s' % ('filter', 'txtEdge', 'uiPSNR', '3dPSNR', '3dJag', '3dStair', 'charPSNR', 'charJag', 'chStair', 'charEdg', 'charHalo', 'ms'))
        for l, r in per.items():
            v = [np.mean([r[n][k] for n in GROUPS[g]]) for g, k in cols]
            print('%-14s %8.2f %8.2f %8.2f %8.2f %8.2f %9.2f %8.2f %8.2f %8.2f %8.2f %7.3f' % ((l,) + tuple(v) + (r['ms'],)))
    if a.json: Path(a.json).write_text(json.dumps(allres, indent=1))


if __name__ == '__main__':
    main()
