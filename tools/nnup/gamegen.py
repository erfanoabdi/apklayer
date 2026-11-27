"""gamegen.py: procedural game-like frames. A scene is a function of HR pixel coordinates (X,Y: B,H,W float, units = pixels of a 720p frame),
so it can be point-sampled at the low render resolution (aliased, no AA, as a game renders at a render scale) or supersampled at full resolution (ground truth)."""
import torch, math, random
def rnd(B, lo, hi, dev): return (torch.rand(B, 1, 1, device=dev) * (hi - lo) + lo)
def hsh(x, y, seed):
    return torch.frac(torch.sin(x * 127.1 + y * 311.7 + seed * 74.7) * 43758.5453)
def vnoise(X, Y, seed):
    xi, yi = torch.floor(X), torch.floor(Y); fx, fy = X - xi, Y - yi
    fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    a, b, c, d = hsh(xi, yi, seed), hsh(xi + 1, yi, seed), hsh(xi, yi + 1, seed), hsh(xi + 1, yi + 1, seed)
    return a + (b - a) * fx + (c - a) * fy + (a - b - c + d) * fx * fy
def fbm(X, Y, seed, oct=4):
    s, a, t = 0, 0.5, 0
    for i in range(oct): s = s + a * vnoise(X, Y, seed + i * 7.3); X, Y, a, t = X * 2.03 + 11.1, Y * 2.03 + 5.7, a * 0.5, t + a
    return s / t
def col(B, dev, lo=0.0, hi=1.0): return torch.rand(B, 3, 1, 1, device=dev) * (hi - lo) + lo
def over(img, m, c): return img * (1 - m[:, None]) + c * m[:, None]
def sm(x, a, b): return ((x - a) / (b - a)).clamp(0, 1)

def render(B, X, Y, rng_seed=None, dev='cuda'):
    """X,Y: B,H,W pixel coordinates (720p units). Returns B,3,H,W in 0..1. Per-sample random scene parameters."""
    if rng_seed is not None: torch.manual_seed(rng_seed)
    W0, H0 = 1280., 720.
    # background sky gradient + clouds
    t = (Y / H0).clamp(0, 1)[:, None]; c1, c2 = col(B, dev), col(B, dev)
    img = c1 * (1 - t) + c2 * t
    sd = rnd(B, 0, 100, dev)
    cl = fbm(X / rnd(B, 80, 300, dev), Y / rnd(B, 40, 150, dev), sd, 4)
    img = over(img, sm(cl, 0.5, 0.8) * rnd(B, 0, .6, dev), col(B, dev, .6, 1))
    # distant hills (smooth silhouettes, aliased edges)
    for k in range(2):
        hz = rnd(B, 250, 500, dev) + k * 60; hh = hz + rnd(B, 20, 80, dev) * torch.sin(X / rnd(B, 60, 250, dev) + rnd(B, 0, 6, dev)) + 25 * (fbm(X / 40, Y * 0 + k, sd, 3) - .5)
        m = (Y > hh).float(); shade = col(B, dev, 0.1, .6) * (1 - 0.5 * sm(Y, hz, hz + 200)[:, None])
        img = over(img, m, shade)
    # ground with texture and perspective stripes
    gy = rnd(B, 380, 560, dev); m = (Y > gy + 15 * torch.sin(X / rnd(B, 50, 200, dev))).float()
    gt = fbm(X / rnd(B, 3, 20, dev), (Y - gy) / rnd(B, 3, 20, dev) * 2, sd + 3, 5)
    gc = col(B, dev, 0.05, .7); g = gc * (0.6 + 0.8 * gt[:, None]) * (1 - 0.3 * sm(Y, gy, gy + 300)[:, None])
    img = over(img, m, g)
    # road/tiles: perspective checker
    if random.random() < 0.5:
        d = (Y - gy).clamp(min=1); u = (X - W0 / 2) / d * 80; v = 4000 / d
        ch = ((torch.floor(u * rnd(B, .3, 1, dev)) + torch.floor(v * rnd(B, .05, .2, dev))) % 2)
        m = (Y > gy + 40).float(); img = over(img, m, img * (0.7 + 0.5 * ch[:, None]))
    # polygons (buildings / props): convex quads with gradient + texture
    for k in range(random.randint(2, 6)):
        cx, cy = rnd(B, 0, W0, dev), rnd(B, 150, 650, dev); r = rnd(B, 30, 200, dev); n = random.choice([3, 4, 5, 6])
        ang0 = rnd(B, 0, 6.28, dev); inside = torch.ones_like(X); 
        for j in range(n):
            a = ang0 + j * 6.2832 / n + rnd(B, -.3, .3, dev) / 1; a2 = ang0 + (j + 1) * 6.2832 / n
            px, py = cx + r * torch.cos(a), cy + r * torch.sin(a); qx, qy = cx + r * torch.cos(a2), cy + r * torch.sin(a2)
            inside = inside * (((qx - px) * (Y - py) - (qy - py) * (X - px)) > 0).float()
        base = col(B, dev, 0.05, 1); c2 = col(B, dev); gt = ((X - cx) * torch.cos(ang0) + (Y - cy) * torch.sin(ang0)) / r * .5 + .5
        c = base * (1 - gt[:, None]) + c2 * gt[:, None]
        typ = random.randint(0, 3)
        if typ == 1: c = c * (0.7 + 0.5 * ((torch.floor((X + Y) / rnd(B, 3, 14, dev))) % 2)[:, None])      # diagonal stripes
        if typ == 2: c = c * (0.6 + 0.8 * fbm(X / rnd(B, 2, 10, dev), Y / rnd(B, 2, 10, dev), sd + k, 4)[:, None])
        if typ == 3:  # windows grid
            w = rnd(B, 8, 30, dev); wm = ((X % w) < w * .6).float() * ((Y % w) < w * .6).float(); c = c * (0.5 + 0.9 * wm[:, None])
        img = over(img, inside, c)
        if random.random() < 0.5: img = over(img, inside * (1 - inside) , c)  # no-op placeholder
    # spheres with specular highlights
    for k in range(random.randint(1, 4)):
        cx, cy, r = rnd(B, 0, W0, dev), rnd(B, 200, 650, dev), rnd(B, 15, 120, dev)
        dx, dy = (X - cx) / r, (Y - cy) / r; q = dx * dx + dy * dy; m = (q < 1).float(); nz = torch.sqrt((1 - q).clamp(min=0))
        L = torch.tensor([-.5, -.6, .6], device=dev); L = L / L.norm(); dif = (dx * L[0] + dy * L[1] + nz * L[2]).clamp(0, 1)
        sp = ((2 * dif * nz - L[2]).clamp(0, 1)) ** rnd(B, 10, 80, dev)
        bc = col(B, dev, .05, 1); img = over(img, m, bc * (0.2 + 0.9 * dif[:, None]) + sp[:, None] * rnd(B, .3, 1, dev)[:, None])
    # foliage: warped stripe fields (thin repeated blades) in a region
    for k in range(random.randint(0, 2)):
        ang = rnd(B, 1.2, 1.9, dev); fr = rnd(B, 0.15, 0.6, dev); u = X * torch.cos(ang) + Y * torch.sin(ang); v = -X * torch.sin(ang) + Y * torch.cos(ang)
        cell = torch.floor(u * fr); f = u * fr - cell; wob = torch.sin(v / rnd(B, 20, 80, dev) + hsh(cell, cell * 0, 1) * 6) * rnd(B, 0, .5, dev)
        w = rnd(B, .08, .35, dev); m = ((f + wob - .5).abs() < w).float()
        ry = rnd(B, 300, 650, dev); reg = ((Y > ry) & (fbm(X / 90, Y / 90, sd + 9, 3) > .35)).float()
        gc = col(B, dev, 0, .6) * torch.tensor([.5, 1, .4], device=dev).view(1, 3, 1, 1) * (0.5 + hsh(cell, cell, 3)[:, None] * 1.2)
        img = over(img, m * reg, gc)
    # hair / wires: thin sinusoidal curves
    for k in range(random.randint(0, 3)):
        ang = rnd(B, 0, 3.14, dev); fr = rnd(B, 0.04, 0.3, dev); u = X * torch.cos(ang) + Y * torch.sin(ang); v = -X * torch.sin(ang) + Y * torch.cos(ang)
        s = u * fr + 1.5 * torch.sin(v / rnd(B, 30, 120, dev)); f = s - torch.floor(s); w = rnd(B, .03, .2, dev)
        m = ((f - .5).abs() < w).float() * ((vnoise(X / 150, Y / 150, sd + k) > .4).float())
        img = over(img, m, col(B, dev) * rnd(B, .2, 1, dev)[:, None])
    # lines / outline shapes
    for k in range(random.randint(0, 4)):
        x1, y1, x2, y2 = rnd(B, 0, W0, dev), rnd(B, 0, H0, dev), rnd(B, 0, W0, dev), rnd(B, 0, H0, dev)
        dx, dy = x2 - x1, y2 - y1; tt = (((X - x1) * dx + (Y - y1) * dy) / (dx * dx + dy * dy)).clamp(0, 1); d = torch.sqrt((X - x1 - tt * dx) ** 2 + (Y - y1 - tt * dy) ** 2)
        img = over(img, (d < rnd(B, .4, 3, dev)).float(), col(B, dev))
    # HUD: panels, bars, glyph-like blocks, crosshair
    if random.random() < 0.8:
        for k in range(random.randint(1, 4)):
            x0, y0, w, h = rnd(B, 0, W0 - 100, dev), rnd(B, 0, H0 - 40, dev), rnd(B, 60, 360, dev), rnd(B, 12, 60, dev)
            r = ((X > x0) & (X < x0 + w) & (Y > y0) & (Y < y0 + h)).float(); img = over(img, r, img * .3 + col(B, dev, 0, .3) * .4)
            bd = r * (1 - ((X > x0 + 2) & (X < x0 + w - 2) & (Y > y0 + 2) & (Y < y0 + h - 2)).float()); img = over(img, bd, col(B, dev, .6, 1))
            fill = r * ((X - x0) < w * rnd(B, .2, 1, dev)) * ((Y > y0 + 5) & (Y < y0 + h - 5)).float(); img = over(img, fill, col(B, dev, 0, 1) * torch.tensor([1., .4, .3], device=dev).view(1, 3, 1, 1))
            gw = rnd(B, 5, 10, dev); gh = gw * 1.7; gi, gj = torch.floor((X - x0) / gw), torch.floor((Y - y0 - h + 1) / gh)  # text-like glyph row under panel
            px_, py_ = (X - x0) / gw - gi, (Y - y0 - h + 1) / gh - gj
            gl = (hsh(gi, gj, sd + k) > .35).float() * ((px_ > .15) & (px_ < .8) & (py_ > .1) & (py_ < .9)).float() * (hsh(gi * 3, gj, sd) > .3).float() * ((gj == 0) & (gi >= 0) & (gi < w / gw)).float()
            img = over(img, gl * 1, col(B, dev, .8, 1))
    cx, cy = W0 / 2, H0 / 2
    cr = ((((X - cx).abs() < 1) & ((Y - cy).abs() < 9)) | (((Y - cy).abs() < 1) & ((X - cx).abs() < 9))).float() * (random.random() < 0.5)
    img = over(img, cr, torch.ones(B, 3, 1, 1, device=dev))
    # vignette + contrast
    vg = 1 - 0.3 * (((X - cx) / W0) ** 2 + ((Y - cy) / H0) ** 2)
    return (img * vg[:, None]).clamp(0, 1)

def lr_batch(B, L, s, zoom=None, dev='cuda'):
    """aliased render at LxL render pixels, render scale s: sample points (x0+(i+.5)/s)."""
    i = (torch.arange(L, device=dev).float() + .5) / s
    ox = torch.rand(B, 1, 1, device=dev) * 900; oy = torch.rand(B, 1, 1, device=dev) * 350
    X = ox + i.view(1, 1, L).expand(B, L, L); Y = oy + i.view(1, L, 1).expand(B, L, L)
    return render(B, X, Y, dev=dev)

def pair(W=1280, H=720, s=0.5, ss=2, seed=0, dev='cuda'):
    """eval pair: aliased LR (point-sampled at render scale s) and supersampled HR (ss x ss) of the same scene (same seed => same params)."""
    lw, lh = round(W * s), round(H * s)
    def grid(w, h, sx, sy, ox, oy):
        X = ((torch.arange(w, device=dev).float() + ox) * sx).view(1, 1, w).expand(1, h, w); Y = ((torch.arange(h, device=dev).float() + oy) * sy).view(1, h, 1).expand(1, h, w); return X, Y
    random.seed(seed); st = random.getstate(); X, Y = grid(lw, lh, W / lw, H / lh, .5, .5); lr = render(1, X, Y, rng_seed=seed, dev=dev)
    random.setstate(st); acc = 0
    X, Y = grid(W * ss, H * ss, 1 / ss, 1 / ss, .5, .5); hr = render(1, X, Y, rng_seed=seed, dev=dev)
    hr = torch.nn.functional.avg_pool2d(hr, ss)
    return lr, hr


def lr_hr_batch(B, L, s, ss=2, dev='cuda'):
    """aliased LR (point samples) + supersampled 2x target on the same scene (params fixed by one seed)."""
    seed = random.randrange(1 << 30); ox = random.random() * 900; oy = random.random() * 350
    st = random.getstate()
    def g(n, step, off):
        i = (torch.arange(n, device=dev).float() + off) * step
        return (ox + i.view(1, 1, n).expand(B, n, n)), (oy + i.view(1, n, 1).expand(B, n, n))
    X, Y = g(L, 1 / s, .5); random.seed(seed); lr = render(B, X, Y, rng_seed=seed, dev=dev)
    X, Y = g(2 * L * ss, 1 / (2 * s * ss), .5); random.seed(seed); hr = render(B, X, Y, rng_seed=seed, dev=dev)
    random.setstate(st); torch.manual_seed(random.randrange(1 << 30))
    return lr, torch.nn.functional.avg_pool2d(hr, ss)
