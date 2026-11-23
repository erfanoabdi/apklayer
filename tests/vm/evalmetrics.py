"""Metrics of tests/vm/evalfilter.py (numpy only). Images are float32 luma arrays 0..255 unless said otherwise.
psnr, ssim (7x7 uniform window), edge_ratio (mean Sobel gradient out/ref; 1.0 is as sharp as the original), halo (percent of pixels more than 3 levels
outside the original's 5x5 min..max: ringing and overshoot), and the text-specific stem_metrics: for a calibrated vertical stem or step edge (the rows
y0..y1 averaged) the contrast ratio (peak above the background, out/ref), the width ratio (FWHM out/ref) and for edges the 10-90% rise distance
in output pixels (acuity; 1.0 is a hard edge) and the overshoot in percent of the step."""
import math

import numpy as np


def luma(im): return np.asarray(im.convert('RGB'), np.float32) @ np.array([0.299, 0.587, 0.114], np.float32)


def psnr(a, b):
    mse = float(np.mean((np.asarray(a, np.float64) - np.asarray(b, np.float64)) ** 2))
    return 99.0 if mse == 0 else 10 * math.log10(255 * 255 / mse)


def box(a, n):  # mean over n x n windows ('valid')
    c = np.cumsum(np.cumsum(np.pad(a, ((1, 0), (1, 0))), 0), 1)
    return (c[n:, n:] - c[:-n, n:] - c[n:, :-n] + c[:-n, :-n]) / (n * n)


def ssim(a, b, n=7):
    a, b = a.astype(np.float64), b.astype(np.float64)
    c1, c2 = (0.01 * 255) ** 2, (0.03 * 255) ** 2
    ma, mb = box(a, n), box(b, n)
    va, vb, cov = box(a * a, n) - ma * ma, box(b * b, n) - mb * mb, box(a * b, n) - ma * mb
    return float(np.mean(((2 * ma * mb + c1) * (2 * cov + c2)) / ((ma * ma + mb * mb + c1) * (va + vb + c2))))


def grad(a):
    p = np.pad(a, 1, mode='edge')
    gx = (p[:-2, 2:] + 2 * p[1:-1, 2:] + p[2:, 2:]) - (p[:-2, :-2] + 2 * p[1:-1, :-2] + p[2:, :-2])
    gy = (p[2:, :-2] + 2 * p[2:, 1:-1] + p[2:, 2:]) - (p[:-2, :-2] + 2 * p[:-2, 1:-1] + p[:-2, 2:])
    return np.hypot(gx, gy)


def edge_ratio(out, ref):
    g = float(grad(ref).mean())
    return float(grad(out).mean()) / g if g > 1e-6 else 1.0


def halo(out, ref, thr=3.0):
    p = np.pad(ref, 2, mode='edge')
    sh = [p[dy:dy + ref.shape[0], dx:dx + ref.shape[1]] for dy in range(5) for dx in range(5)]
    over = np.maximum(out - np.max(sh, 0), 0) + np.maximum(np.min(sh, 0) - out, 0)
    return float(np.mean(over > thr)) * 100


def halo_energy(out, ref):
    """the mean excess in levels of the output beyond the original's local 5x5 range: over all pixels, and over the pixels at the original's edges (Sobel > 40)"""
    p = np.pad(ref, 2, mode='edge')
    sh = [p[dy:dy + ref.shape[0], dx:dx + ref.shape[1]] for dy in range(5) for dx in range(5)]
    ex = np.maximum(out - np.max(sh, 0), 0) + np.maximum(np.min(sh, 0) - out, 0)
    edge = grad(ref) > 160  # a step of 40 levels or more (Sobel gain 4)
    return float(ex.mean()), float(ex[edge].mean()) if edge.any() else 0.0


def cross(p, level, start=0):  # first x >= start where the profile rises through level (linear interpolation), or None
    for i in range(start, len(p) - 1):
        if p[i] < level <= p[i + 1]: return i + (level - p[i]) / (p[i + 1] - p[i])
    return None


def fwhm(p):  # of a peak profile (positive peak above 0)
    pk = float(p.max())
    if pk <= 0: return 0.0
    i = int(p.argmax())
    h = pk / 2
    l = i
    while l > 0 and p[l] > h: l -= 1
    r = i
    while r < len(p) - 1 and p[r] > h: r += 1
    xl = l + (h - p[l]) / (p[l + 1] - p[l]) if p[l + 1] != p[l] else l
    xr = r - 1 + (p[r - 1] - h) / (p[r - 1] - p[r]) if p[r - 1] != p[r] else r
    return float(xr - xl)


def stem_metrics(out, ref, stem, half=8):
    """out/ref: luma of the full frame; stem: (kind, width, cx, polarity, y0, y1). Returns dict with contrast, width (ratios), rise and over (edges)."""
    kind, w, cx, pol, y0, y1 = stem
    x0 = int(round(cx)) - half
    po = pol * out[y0:y1, x0:x0 + 2 * half].mean(0)
    pr = pol * ref[y0:y1, x0:x0 + 2 * half].mean(0)
    bo, br = np.median(np.r_[po[:3], po[-3:]]), np.median(np.r_[pr[:3], pr[-3:]])
    if kind == 'edge':  # pol*luma: the stem polarity makes the step rise; the background is the left level
        step_r = float(pr[-3:].mean() - pr[:3].mean())
        step_o = float(po[-3:].mean() - po[:3].mean())
        lo, hi = po[:3].mean(), po[-3:].mean()
        a, b = cross(po, lo + 0.1 * (hi - lo)), cross(po, lo + 0.9 * (hi - lo))
        over = max(float(po.max() - hi), float(lo - po.min()), 0.0) / max(abs(step_o), 1e-6) * 100
        return {'rise': (b - a) if a is not None and b is not None and b >= a else float('nan'), 'over': over, 'contrast': step_o / step_r if step_r else 1.0}
    eo, er = np.maximum(po - bo, 0), np.maximum(pr - br, 0)
    return {'contrast': float(eo.max() / er.max()), 'width': fwhm(eo) / fwhm(er) if fwhm(er) > 0 else float('nan')}
