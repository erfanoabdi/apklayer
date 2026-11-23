#!/usr/bin/env python3
"""The yardstick of the render filter: one command, one summary table (docs/FILTER-EVAL.md).
  tests/vm/evalfilter.py [--scales 35,50,75] [--filter label[:ENV=VAL,...]]... [--sharp 0.8706] [--only tile,...] [--json out.json] [--compare old.json] [--save dir] [-v]
A synthetic 1280x720 corpus (tests/vm/evalcorpus.py: text at several sizes and fonts, icons, thin lines, calibrated stems and edges, a UI panel, shaded
3D, foliage noise, gradients, anime flats, a perspective checker; no game data) is box-reduced to each scale (35/50/75% of 1280x720) and brought back
to 1280x720 by a bilinear stretch and by every --filter (the shell pass bridge/shell/sharpen.c through sharpen-test: dmabuf in, EGL/GLES, readback;
default filter 'fsr1' = the current default pipeline; a filter takes environment variables, e.g. --filter 'fsr1-noaa:APKLAYER_SHARPEN_AA=off' (the default AA is cmaa2-ultra) or 'fsr1-smaa:APKLAYER_SHARPEN_AA=smaa-high'
--filter 'vk:APKLAYER_SHARPEN_BACKEND=vulkan' --filter 'notext:APKLAYER_SHARPEN_TEXT=off'). Per tile: PSNR, SSIM and edge ratio (out/original; 1.0 = as sharp as the
original) against the original, halo (percent of pixels outside the original's local range by more than 3 levels). Text: stem contrast and width
ratio (1, 2, 3 px stems, both polarities; 1.0 = like the original) and edge acuity (10-90% rise in px, 1.0 = hard edge) with overshoot. The 'score'
rows average the tiles (text = the three text tiles, 3d = shaded_3d, foliage, checker_persp). Reads the sharpen-test binary from SHARPEN_TEST or the
meson build trees (b/, */, scratch); skips (exit 0, 'SKIP') without a render node. The accepted dmabuf modifier goes in SHARPEN_MODIFIER (hex; unset: none first, then NVIDIA's block-linear)."""
import argparse
import glob
import hashlib
import json
import os
import subprocess
import sys
import tempfile
from pathlib import Path

import numpy as np
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import evalcorpus as C  # noqa: E402
import evalmetrics as M  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
NVIDIA_MOD = '300000000606010'  # NVIDIA block-linear 2D, 16 gob
MARGIN = 6  # px cut from every tile's border before measuring
GROUPS = {'text': ('text_small', 'text_large', 'text_light'), '3d': ('shaded_3d', 'foliage', 'checker_persp'), 'ui': ('ui_panel', 'icons', 'thin_lines', 'anime_flat'), 'smooth': ('gradients',)}


def find_exe():
    if os.environ.get('SHARPEN_TEST'): return os.environ['SHARPEN_TEST']
    c = sorted(glob.glob(str(ROOT / 'bridge/shell/sharpen-test')) + glob.glob(str(ROOT / '*/bridge/shell/sharpen-test')), key=os.path.getmtime)
    return c[-1] if c else None


def run_filter(exe, small, W, H, sharp, env):
    with tempfile.TemporaryDirectory(prefix='apklayer-evalfilter-') as tmp:
        rin, rout = os.path.join(tmp, 'in.raw'), os.path.join(tmp, 'out.raw')
        Path(rin).write_bytes(small.convert('RGBA').tobytes())
        for mod in ([os.environ['SHARPEN_MODIFIER']] if os.environ.get('SHARPEN_MODIFIER') else [None, NVIDIA_MOD]):  # none first; NVIDIA's gbm has no linear render target
            args = [exe, rin, str(small.width), str(small.height), rout, str(W), str(H), str(sharp)] + ([mod] if mod else [])
            run = subprocess.run(args, capture_output=True, text=True, timeout=120, env={**os.environ, **env})
            if run.returncode != 5: break
        if run.returncode in (3, 4): sys.exit('SKIP the GPU cannot take the picture as a dma-buf (rc %d)' % run.returncode)
        if run.returncode != 0: sys.exit('sharpen-test failed (rc %d): %s' % (run.returncode, run.stderr.strip()))
        return Image.frombytes('RGBA', (W, H), Path(rout).read_bytes()).convert('RGB')


def measure(out, ref, tiles, stems):
    lo, lr = M.luma(out), M.luma(ref)
    res = {'tiles': {}, 'stems': {}}
    for n, (x0, y0, x1, y1) in tiles.items():
        x0, y0, x1, y1 = x0 + MARGIN, y0 + MARGIN, x1 - MARGIN, y1 - MARGIN  # neighbouring tiles bleed into the border pixels of the output
        a, b = lo[y0:y1, x0:x1], lr[y0:y1, x0:x1]
        res['tiles'][n] = {'psnr': M.psnr(np.asarray(out.crop((x0, y0, x1, y1))), np.asarray(ref.crop((x0, y0, x1, y1)))), 'ssim': M.ssim(a, b), 'edge': M.edge_ratio(a, b), 'halo': M.halo(a, b), 'hale': M.halo_energy(a, b)[1]}
    agg = {}
    for st in stems:
        m = M.stem_metrics(lo, lr, st)
        key = st[0] + ('+' if st[3] > 0 else '-')
        for k, v in m.items(): agg.setdefault(key, {}).setdefault(k, []).append(v)
    res['stems'] = {k: {m: float(np.nanmean(v)) for m, v in d.items()} for k, d in agg.items()}
    return res


def mean(res, names, key): return float(np.mean([res['tiles'][n][key] for n in names]))


def table(rows, header, widths):
    fmt = '  '.join('%%-%ds' % widths[0] if i == 0 else '%%%ds' % w for i, w in enumerate(widths))
    return '\n'.join([fmt % tuple(header), '-' * (sum(widths) + 2 * (len(widths) - 1))] + [fmt % tuple(r) for r in rows])


def report(all_res, labels, tiles, only):
    out = []
    for scale, per in all_res.items():
        out.append('\n== %s%% (%dx%d -> %dx%d) ==' % (scale, round(C.W * int(scale) / 100), round(C.H * int(scale) / 100), C.W, C.H))
        hdr = ['tile'] + [m + ' ' + l for m in ('PSNR', 'SSIM', 'edge', 'halo%', 'halo@e') for l in labels]
        rows = []
        for n in [t for t in tiles if not only or t in only]:
            r = [n]
            for key, f in (('psnr', '%.2f'), ('ssim', '%.4f'), ('edge', '%.2f'), ('halo', '%.2f'), ('hale', '%.2f')):
                r += [f % per[l]['tiles'][n][key] for l in labels]
            rows.append(r)
        for g, names in GROUPS.items():
            names = [n for n in names if not only or n in only]
            if not names: continue
            r = ['score ' + g]
            for key, f in (('psnr', '%.2f'), ('ssim', '%.4f'), ('edge', '%.2f'), ('halo', '%.2f'), ('hale', '%.2f')):
                r += [f % mean(per[l], names, key) for l in labels]
            rows.append(r)
        w = [14] + [max(len(h), 7) for h in hdr[1:]]
        out.append(table(rows, hdr, w))
        out.append('')
        hdr = ['text (1.0 = original)'] + [m + ' ' + l for m in ('contrast', 'width') for l in labels]
        rows = []
        for k in sorted(per[labels[0]]['stems']):
            if k.startswith('edge'): continue
            rows.append([k] + ['%.2f' % per[l]['stems'][k]['contrast'] for l in labels] + ['%.2f' % per[l]['stems'][k]['width'] for l in labels])
        out.append(table(rows, hdr, [22] + [max(len(h), 7) for h in hdr[1:]]))
        out.append('')
        hdr = ['edge acuity'] + [m + ' ' + l for m in ('rise px', 'over%') for l in labels]
        rows = [[k] + ['%.2f' % per[l]['stems'][k]['rise'] for l in labels] + ['%.1f' % per[l]['stems'][k]['over'] for l in labels] for k in sorted(per[labels[0]]['stems']) if k.startswith('edge')]
        out.append(table(rows, hdr, [22] + [max(len(h), 7) for h in hdr[1:]]))
    return '\n'.join(out)


def compare(all_res, old, labels):
    out = ['\n== vs saved run (current - saved; PSNR dB, SSIM, edge, halo%, text contrast/width, rise px) ==']
    for scale, per in all_res.items():
        for l in labels:
            o = old.get(scale, {}).get(l)
            if not o: continue
            c = per[l]
            d = {k: np.mean([c['tiles'][n][k] - o['tiles'][n][k] for n in c['tiles']]) for k in ('psnr', 'ssim', 'edge', 'halo', 'hale')}
            tw = [c['stems'][k]['width'] - o['stems'][k]['width'] for k in c['stems'] if 'width' in c['stems'][k]]
            tc = [c['stems'][k]['contrast'] - o['stems'][k]['contrast'] for k in c['stems'] if not k.startswith('edge')]
            rise = [c['stems'][k]['rise'] - o['stems'][k]['rise'] for k in c['stems'] if k.startswith('edge')]
            out.append('%s%% %-10s PSNR %+.3f  SSIM %+.4f  edge %+.3f  halo %+.3f  contrast %+.3f  width %+.3f  rise %+.3f' % (scale, l, d['psnr'], d['ssim'], d['edge'], d['halo'], np.mean(tc), np.mean(tw), np.mean(rise)))
    return '\n'.join(out)


def main():
    ap = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    ap.add_argument('--scales', default='35,50,75')
    ap.add_argument('--filter', action='append', default=[], help="label[:ENV=VAL,...]; default 'fsr1'")
    ap.add_argument('--sharp', type=float, default=0.8706, help='RCAS constant passed to the pass: exp2(-stops), default 0.8706 = 0.2 stops, the shell default')
    ap.add_argument('--only', default='', help='comma separated tiles')
    ap.add_argument('--json', help='write the numbers here')
    ap.add_argument('--compare', help='a --json file of an earlier run: print the mean differences')
    ap.add_argument('--save', help='write the corpus and every output as PNG into this directory')
    ap.add_argument('--check', action='store_true', help='exit 1 unless the first filter beats bilinear (PSNR, SSIM) on the smooth and 3D scores at every scale')
    ap.add_argument('--exe')
    a = ap.parse_args()
    exe = a.exe or find_exe()
    if not exe: sys.exit('SKIP sharpen-test not built (ninja -C b bridge/shell/sharpen-test)')
    if not glob.glob('/dev/dri/renderD*'): sys.exit('SKIP no render node')
    filters = []
    for f in a.filter or ['fsr1']:
        label, _, env = f.partition(':')
        filters.append((label, dict(kv.split('=', 1) for kv in env.split(',') if kv)))
    labels = ['bilinear'] + [l for l, _ in filters]
    only = set(filter(None, a.only.split(',')))
    ref, tiles, stems = C.corpus()
    print('corpus: %d tiles, sha1 %s, fonts %s; sharpness %.2f; %s' % (len(tiles), hashlib.sha1(ref.tobytes()).hexdigest()[:10], C.font_names(), a.sharp, exe))
    if a.save: os.makedirs(a.save, exist_ok=True); ref.save(os.path.join(a.save, 'ref.png'))
    all_res = {}
    for s in a.scales.split(','):
        sw, sh = round(C.W * int(s) / 100), round(C.H * int(s) / 100)
        small = ref.resize((sw, sh), Image.BOX)
        outs = {'bilinear': small.resize((C.W, C.H), Image.BILINEAR)}
        for label, env in filters: outs[label] = run_filter(exe, small, C.W, C.H, a.sharp, env)
        all_res[s] = {l: measure(o, ref, tiles, stems) for l, o in outs.items()}
        if a.save:
            for l, o in outs.items(): o.save(os.path.join(a.save, '%s-%s.png' % (s, l)))
    print(report(all_res, labels, list(tiles), only))
    if a.compare:
        print(compare(all_res, json.loads(Path(a.compare).read_text()), labels))
    if a.check:
        bad = [(s, g, k) for s, per in all_res.items() for g in ('smooth', '3d') for k in ('psnr', 'ssim')
               if mean(per[labels[1]], GROUPS[g], k) < mean(per['bilinear'], GROUPS[g], k)]
        if bad: sys.exit('FAIL %s' % ', '.join('%s%% %s %s below bilinear' % b for b in bad))
        print('PASS evalfilter: %s above bilinear on the smooth and 3D scores at %s%%' % (labels[1], '/'.join(all_res)))
    if a.json: Path(a.json).write_text(json.dumps(all_res, indent=1))


if __name__ == '__main__':
    main()
