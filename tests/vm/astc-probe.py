#!/usr/bin/env python3
"""Real ASTC content through the Vulkan HAL's emulation (tests/hals/vulkan-astc-probe.cpp), every block size, UNORM and sRGB, 2 mip levels x 2 array layers,
a size that is not a block multiple (61x45): astcenc encodes noisy/gradient/edge images, the probe uploads the blocks, the HAL decodes them and the
RGBA8 texels are compared with astcenc's own decode. Usage: astc-probe.py PROBE-BINARY [GPU-name-substring] (needs astcenc, python3-pil, python3-numpy).
The group vulkan-textures only uploads void-extent blocks; this checks the decoder on weights, partitions, dual planes, endpoints, alpha."""
import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

import numpy as np
from PIL import Image

PROBE = sys.argv[1]  # the Vulkan probe, or "gl:PATH-TO-astc-gl" for the host's own GLES (Mesa's CPU ASTC decoder)
GPU = sys.argv[2] if len(sys.argv) > 2 else None  # a name substring (Vulkan) or a render node path (GL)
BLOCKS = [(4, 4), (5, 4), (5, 5), (6, 5), (6, 6), (8, 5), (8, 6), (8, 8), (10, 5), (10, 6), (10, 8), (10, 10), (12, 10), (12, 12)]
W, H = 61, 45
TOLERANCE = 2  # astcenc rounds its float decode, the GPU decoder its own way


def picture(w, h, seed):
    rng = np.random.default_rng(seed)
    y, x = np.mgrid[0:h, 0:w]
    img = np.zeros((h, w, 4), np.float32)
    img[..., 0] = x / max(w - 1, 1)
    img[..., 1] = y / max(h - 1, 1)
    img[..., 2] = 0.5 + 0.5 * np.sin((x + y) / 5.0 + seed)
    img[..., 3] = 1 - x / max(w - 1, 1) * 0.8
    img[(x // 8 + y // 8) % 2 == 0, :3] = 1 - img[(x // 8 + y // 8) % 2 == 0, :3]  # hard edges
    img[h // 3:h // 2, w // 4:w // 2] = rng.random((h // 2 - h // 3, w // 2 - w // 4, 4))  # noise patch
    return Image.fromarray((img * 255).astype(np.uint8), 'RGBA')


def run(cmd, **env):
    r = subprocess.run(cmd, capture_output=True, text=True, env=dict(os.environ, **env))
    return r


def main():
    work = Path(tempfile.mkdtemp(prefix='astc-probe-'))
    results, worst = [], 0
    # rowlen 1: the uploads name their rows in texels (bufferRowLength/bufferImageHeight, as Unity's do; the HAL must round them up to whole
    # blocks: Endfield's 6x6 textures came out sheared on AMD, CHANGELOG 2026-11-21); the host GLES path has no such copies
    for rowlen, srgb in [(r, c) for r in ((0,) if PROBE.startswith('gl:') else (0, 1)) for c in (0, 1)]:
        for i, (bx, by) in enumerate(BLOCKS):
            name = '%dx%d%s%s' % (bx, by, '-srgb' if srgb else '', '-rowlen' if rowlen else '')
            files, refs = [], []
            for layer in range(2):
                for level in range(2):
                    w, h = max(W >> level, 1), max(H >> level, 1)
                    png = work / ('%s.L%dM%d.png' % (name, layer, level))
                    picture(w, h, 3 * layer + level + 1).save(png)
                    astc = work / ('%s.L%dM%d.astc' % (name, layer, level))
                    ref = work / ('%s.L%dM%d.ref.png' % (name, layer, level))
                    c = run(['astcenc', '-cs' if srgb else '-cl', str(png), str(astc), '%dx%d' % (bx, by), '-medium'])
                    d = run(['astcenc', '-ds' if srgb else '-dl', str(astc), str(ref)])
                    assert astc.exists() and ref.exists(), c.stdout + c.stderr + d.stdout + d.stderr
                    files.append(str(astc)); refs.append(ref)
            bb = 16
            fmt = 157 + 2 * i + srgb
            if PROBE.startswith('gl:'):  # a texture per layer/level through the host's GLES
                p = subprocess.CompletedProcess([], 0, 'device ?\n', '')
                for k, a in enumerate(files):
                    layer, level = divmod(k, 2)
                    q = run([PROBE[3:], a, str(srgb), str(work / ('%s.L%dM%d.rgba' % (name, layer, level)))], **({'APKLAYER_GPU': GPU} if GPU else {}))
                    if q.returncode: p = subprocess.CompletedProcess([], q.returncode, 'FAIL astc-gl exit %d: %s' % (q.returncode, q.stdout.strip()[-100:]), q.stderr); break
                    p.stdout = 'device ' + q.stdout.split('renderer ')[-1].splitlines()[0] + '\n'
            else:
                p = run([PROBE, str(fmt), str(W), str(H), str(bx), str(by), str(bb), str(work / name), '2', '2'] + files,
                        **dict({'PROBE_GPU': GPU} if GPU else {}, **({'PROBE_ROW_LENGTH': '1'} if rowlen else {})))
            line = [l for l in p.stdout.splitlines() if l.startswith(('FAIL', 'PASS', 'SKIP'))]
            status = 'PASS'
            detail = []
            if p.returncode != 0:
                status = 'FAIL'; detail.append('probe exit %d %s' % (p.returncode, ' | '.join(line)[:200] or p.stderr[-200:]))
            else:
                for k, ref in enumerate(refs):
                    layer, level = divmod(k, 2)
                    w, h = max(W >> level, 1), max(H >> level, 1)
                    got = np.fromfile(work / ('%s.L%dM%d.rgba' % (name, layer, level)), np.uint8).reshape(h, w, 4).astype(int)
                    want = np.array(Image.open(ref).convert('RGBA')).astype(int)
                    diff = np.abs(got - want)
                    bad = int((diff.max(axis=2) > TOLERANCE).sum())
                    worst = max(worst, int(diff.max()))
                    if bad:
                        status = 'FAIL'
                        detail.append('L%dM%d: %d/%d texels off, max %d' % (layer, level, bad, w * h, diff.max()))
                        Image.fromarray(got.astype(np.uint8), 'RGBA').save('%s/%s.L%dM%d.got.png' % (os.environ.get('ASTC_KEEP', work), name, layer, level))
                        Image.open(ref).save('%s/%s.L%dM%d.ref.png' % (os.environ.get('ASTC_KEEP', work), name, layer, level))
            print('%-4s %-12s %s' % (status, name, '; '.join(detail)), flush=True)
            results.append(status)
    print('device line: %s' % ([l for l in p.stdout.splitlines() if l.startswith('device')] or ['?'])[0])
    print('summary: %d PASS %d FAIL, worst diff %d' % (results.count('PASS'), results.count('FAIL'), worst))
    if not os.environ.get('ASTC_KEEP'): shutil.rmtree(work)
    return 1 if 'FAIL' in results else 0


sys.exit(main())
