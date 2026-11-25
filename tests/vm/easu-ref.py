#!/usr/bin/env python3
"""EASU fidelity: AMD's FsrEasuF (ffx_fsr1.h, MIT) as a float CPU reference (numpy, no gather: the 12 taps at texel centres, edge clamp), against
the EASU fragment shader FS_UP of bridge/shell/sharpen_fsr.h run headless by tests/hals/easu-gl.c (build: gcc -o easu-gl easu-gl.c -lEGL -lGLESv2 -lgbm).
Reports max / mean abs error (8-bit levels) of the shader against the reference on synthetic and photo-like pictures, and PSNR against the
ground truth (a box-downscaled hi-res picture upscaled again). Usage: easu-ref.py [path/to/easu-gl]; exits 1 when the shader deviates > 1.5 levels (max). """
import ast, os, re, subprocess, sys, tempfile
import numpy as np

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
EXE = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, 'bridge/shell/easu-gl')


def shader_source():
    src = open(os.path.join(ROOT, 'bridge/shell/sharpen_fsr.h')).read()
    m = re.search(r'FS_UP =\s*(.*?);\n', src, re.S)
    lits = re.findall(r'"(?:[^"\\]|\\.)*"', m.group(1))  # (the C comments between the literals hold no quotes that matter)
    s = ''.join(ast.literal_eval(x) for x in lits)
    s = s.replace('#extension GL_OES_EGL_image_external : require\n', '').replace('samplerExternalOES', 'sampler2D')
    return re.sub(r'CALM_D = [0-9.]+', 'CALM_D = 0.0', s, count=1)  # the calm-texture lobe (apklayer patch) is a deliberate deviation: compare with it off


DT = np.float64


def lum(c):  # g + 0.5 (r + b) computed from the 8 bit levels (integers: the sums of equal luma tie exactly, as they do in the shader)
    q = np.round(c * 255.0)
    return (2 * q[..., 1] + q[..., 0] + q[..., 2]) / 510.0


def easu_ref(img, ow, oh, eps=0.0):
    """img: float h,w,3 in 0..1 -> float oh,ow,3. Follows FsrEasuF / FsrEasuSetF / FsrEasuTapF line by line (exact divisions instead of APrxLoRcp).
    eps shifts the sample position (a sensitivity probe: how far the reference itself moves for a float32-sized position error)."""
    h, w, _ = img.shape
    pos = (np.arange(oh)[:, None] + 0.5) * h / oh - 0.5 + eps, (np.arange(ow)[None, :] + 0.5) * w / ow - 0.5 + eps
    img = img.astype(DT)
    pos = pos[0].astype(DT), pos[1].astype(DT)
    fy, fx = np.floor(pos[0]), np.floor(pos[1])
    ppy, ppx = (pos[0] - fy) + 0 * pos[1], (pos[1] - fx) + 0 * pos[0]
    fy = fy.astype(int) + np.zeros((oh, ow), int)
    fx = fx.astype(int) + np.zeros((oh, ow), int)

    def T(dx, dy):
        return img[np.clip(fy + dy, 0, h - 1), np.clip(fx + dx, 0, w - 1)]
    names = {'b': (0, -1), 'c': (1, -1), 'e': (-1, 0), 'f': (0, 0), 'g': (1, 0), 'h': (2, 0), 'i': (-1, 1), 'j': (0, 1), 'k': (1, 1), 'l': (2, 1), 'n': (0, 2), 'o': (1, 2)}
    col = {k: T(*v) for k, v in names.items()}
    L = {k: lum(v) for k, v in col.items()}
    dirx = np.zeros((oh, ow)); diry = np.zeros((oh, ow)); ln = np.zeros((oh, ow))

    def setf(wt, a, b, c, d, e):
        nonlocal dirx, diry, ln
        lx = np.maximum(np.abs(d - c), np.abs(c - b)); lx = 1.0 / np.maximum(lx, 1e-5)  # APrxLoRcpF1 of 0 is huge: the product is 0 anyway
        dx = d - b; dirx = dirx + dx * wt
        lx = np.clip(np.abs(dx) * lx, 0, 1); ln = ln + lx * lx * wt
        ly = np.maximum(np.abs(e - c), np.abs(c - a)); ly = 1.0 / np.maximum(ly, 1e-5)
        dy = e - a; diry = diry + dy * wt
        ly = np.clip(np.abs(dy) * ly, 0, 1); ln = ln + ly * ly * wt
    setf((1 - ppx) * (1 - ppy), L['b'], L['e'], L['f'], L['g'], L['j'])
    setf(ppx * (1 - ppy), L['c'], L['f'], L['g'], L['h'], L['k'])
    setf((1 - ppx) * ppy, L['f'], L['i'], L['j'], L['k'], L['n'])
    setf(ppx * ppy, L['g'], L['j'], L['k'], L['l'], L['o'])
    d2 = dirx * dirx + diry * diry
    zro = d2 < 1.0 / 32768.0
    rc = np.where(zro, 1.0, 1.0 / np.sqrt(np.maximum(d2, 1e-30)))
    dirx = np.where(zro, 1.0, dirx) * rc; diry = diry * rc
    ln = ln * 0.5; ln = ln * ln
    easu_ref.inter = (dirx, diry, ln)  # for debugging against the shader
    stretch = (dirx * dirx + diry * diry) / np.maximum(np.abs(dirx), np.abs(diry))
    lx, ly = 1 + (stretch - 1) * ln, 1 - 0.5 * ln
    lob = 0.5 + ((0.25 - 0.04) - 0.5) * ln
    clp = 1.0 / lob
    ac = np.zeros((oh, ow, 3)); aw = np.zeros((oh, ow))
    for k, (ox, oy) in names.items():
        ox = ox - ppx; oy = oy - ppy
        vx = (ox * dirx + oy * diry) * lx; vy = (ox * -diry + oy * dirx) * ly
        d = np.minimum(vx * vx + vy * vy, clp)
        wb = 0.4 * d - 1; wa = lob * d - 1
        wb = wb * wb; wa = wa * wa
        wb = 25.0 / 16.0 * wb - (25.0 / 16.0 - 1)
        wgt = wb * wa
        ac += col[k] * wgt[..., None]; aw += wgt
    pix = ac / aw[..., None]
    f, g, j, k = col['f'], col['g'], col['j'], col['k']
    pix = np.minimum(np.maximum(np.maximum(f, g), np.maximum(j, k)), np.maximum(np.minimum(np.minimum(f, g), np.minimum(j, k)), pix))
    return np.clip(pix, 0, 1)


def gl(shader, img8, ow, oh, tmp, tag):
    h, w, _ = img8.shape
    fs, fin, fout = [os.path.join(tmp, tag + x) for x in ('.frag', '.in', '.out')]
    open(fs, 'w').write(shader)
    np.dstack([img8, np.full((h, w), 255, np.uint8)]).tofile(fin)
    subprocess.run([EXE, fs, fin, str(w), str(h), fout, str(ow), str(oh)], check=True)
    return np.fromfile(fout, np.uint8).reshape(oh, ow, 4)[..., :3]


def box(img, f):
    h, w, c = img.shape
    return img[:h // f * f, :w // f * f].reshape(h // f, f, w // f, f, c).mean((1, 3))


def blur(a, n):
    for _ in range(n):
        a = (np.roll(a, 1, 0) + 2 * a + np.roll(a, -1, 0)) / 4
        a = (np.roll(a, 1, 1) + 2 * a + np.roll(a, -1, 1)) / 4
    return a


def pictures(rng):
    """hi-res (2x the largest output) float pictures"""
    n = 384
    y, x = np.mgrid[0:n, 0:n] / n
    out = {}
    t = np.zeros((n, n))  # text-like strokes
    for r in range(8):
        for cI in range(12):
            x0, y0 = 16 + cI * 28, 20 + r * 44
            t[y0:y0 + 22, x0:x0 + 4] = 1; t[y0:y0 + 4, x0:x0 + 16] = 1; t[y0 + 18:y0 + 22, x0:x0 + 16] = 1 if (r + cI) % 2 else 0
            if (r * cI) % 3 == 0:
                for d in range(18): t[y0 + d, x0 + d // 2: x0 + d // 2 + 3] = 1
    out['text'] = np.dstack([1 - t] * 3)
    ang = np.arctan2(y - .5, x - .5)
    out['diag-edges'] = np.dstack([(((x * 3 + y * 2) % 1) > .5) * .8 + .1, (np.hypot(x - .5, y - .5) < .3) * .7 + .15, (ang > 0) * .6 + .2])
    out['checker'] = np.dstack([(((x * 24).astype(int) + (y * 24).astype(int)) % 2) * .9 + .05] * 3)
    out['rings'] = np.dstack([.5 + .5 * np.sin(2 * np.pi * 14 * (x - .5) ** 2 * 4 + 2 * np.pi * 14 * (y - .5) ** 2 * 4)] * 3)
    out['gradient'] = np.dstack([x, y, (x + y) / 2])
    out['noise'] = rng.random((n, n, 3))
    base = np.dstack([blur(rng.random((n, n)), 9) for _ in range(3)])
    base = (base - base.min()) / (base.max() - base.min())
    sh = ((np.hypot(x - .4, y - .55) < .22) * .5 + (np.abs(x - y * .7 - .1) < .03) * .4)[..., None]
    photo = np.clip(base * .7 + sh * np.array([.9, .6, .3]) + blur(rng.random((n, n, 3)).mean(2), 1)[..., None] * .1, 0, 1)
    out['photo-like'] = photo
    out['photo-grain'] = np.clip(photo + rng.normal(0, .03, photo.shape), 0, 1)
    return out


def psnr(a, b):
    m = np.mean((a - b) ** 2)
    return 99.0 if m == 0 else 10 * np.log10(1.0 / m)


def main():
    rng = np.random.default_rng(7)
    sh = shader_source()
    variants = {'exact': sh, 'floatluma': sh.replace('vec3 q = floor(c * 255.0 + 0.5);', 'vec3 q = c * 255.0;')}  # floatluma: without the 8 bit lattice patch
    worst, tot = 0.0, {}
    print('error of the shader against the float reference (8-bit levels), then PSNR dB against the hi-res truth')
    print('%-11s %-8s | %-17s | %-19s | %6s %6s %7s %6s' % ('picture', 'in->out', 'shader: max mean', 'float luma: max mean', 'ref', 'shader', 'fl.luma', 'bilin'))
    with tempfile.TemporaryDirectory(prefix='easu-ref-') as tmp:
        for name, hi in pictures(rng).items():
            for iw in (96, 192, 256):
                ow = 384
                src = np.clip(np.round(255 * np.array(Image_resize(hi, iw))), 0, 255).astype(np.uint8)
                r = easu_ref(src / 255.0, ow, ow)
                res = {k: gl(v, src, ow, ow, tmp, 'x').astype(float) for k, v in variants.items()}
                err = {k: np.abs(v - 255 * r) for k, v in res.items()}
                worst = max(worst, err['exact'].max())
                ps = [psnr(r, hi)] + [psnr(res[k] / 255.0, hi) for k in variants] + [psnr(Image_resize(src / 255.0, ow), hi)]
                for i, k in enumerate(('ref', 'exact', 'floatluma', 'bilin')):
                    tot.setdefault(k, []).append(ps[i])
                print('%-11s %3d->%-4d | %6.2f %9.4f | %6.2f %11.4f | %s' % (name, iw, ow, err['exact'].max(), err['exact'].mean(), err['floatluma'].max(), err['floatluma'].mean(), ' '.join('%6.2f' % x for x in ps)))
    print('mean PSNR over all pictures: ' + ', '.join('%s %.2f' % (k, np.mean(v)) for k, v in tot.items()))
    print('shader worst max error against the reference: %.2f levels' % worst)
    return 0 if worst <= 2.5 else 1


def Image_resize(a, n):
    """area/bilinear resample of an h,w,3 float array to n x n (box when shrinking by an integer, else bilinear)"""
    h = a.shape[0]
    if h == n:
        return a
    if h % n == 0:
        return box(a, h // n)
    ys = (np.arange(n) + .5) * h / n - .5
    y0 = np.clip(np.floor(ys).astype(int), 0, h - 2); fy = np.clip(ys - y0, 0, 1)
    t = a[y0] * (1 - fy)[:, None, None] + a[y0 + 1] * fy[:, None, None]
    return t[:, y0] * (1 - fy)[None, :, None] + t[:, y0 + 1] * fy[None, :, None]


if __name__ == '__main__':
    sys.exit(main())
