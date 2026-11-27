"""Pretrained luminance 2x nets, torch re-implementations of the frozen graphs (weights/*.npz; licences in weights/README.md):
ESPCN_x2 (fannymonori/TF-ESPCN, Apache-2.0) and FSRCNN_x2 / FSRCNN-small_x2 (Saafke/FSRCNN_Tensorflow, Apache-2.0). They take Y (BT.601, 0..1) and return 2x Y;
chroma is a bicubic 2x of the colour differences (B-Y, R-Y), then the result is resampled to the output size like nnup.Net."""
import os, numpy as np, torch, torch.nn.functional as F
D = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'weights')
class Pre(torch.nn.Module):
    def __init__(s, name):
        super().__init__(); s.name = name; z = np.load(os.path.join(D, name + '.npz'))
        t = lambda k: torch.from_numpy(z[k].astype(np.float32))
        s.k = {k: t(k) for k in z.files}
        s.w = {k: v.permute(3, 2, 0, 1).contiguous() for k, v in s.k.items() if k.startswith('f')}  # HWIO -> OIHW
    def to(s, dev):
        s.k = {k: v.to(dev) for k, v in s.k.items()}; s.w = {k: v.to(dev) for k, v in s.w.items()}; return s
    def y2x(s, y):
        c = lambda x, i: F.conv2d(x, s.w['f%d' % i], s.k['b%d' % i], padding=s.w['f%d' % i].shape[-1] // 2)
        if s.name.startswith('ESPCN'):
            x = F.relu(c(y, 1)); x = F.relu(c(x, 2)); x = F.pixel_shuffle(c(x, 3), 2); return torch.tanh(x)
        n = len([k for k in s.w]); x = y
        for i in range(1, n):
            x = c(x, i); a = s.k['alpha%d' % i].view(1, -1, 1, 1); x = F.relu(x) + a * (x - x.abs()) * 0.5
        return F.pixel_shuffle(F.conv2d(x, s.w['f%d' % n], None, padding=s.w['f%d' % n].shape[-1] // 2), 2) + s.k['b%d' % n]
    def forward(s, x, size):
        r, g, b = x[:, 0:1], x[:, 1:2], x[:, 2:3]; y = .299 * r + .587 * g + .114 * b
        up = lambda t: F.interpolate(t, scale_factor=2, mode='bicubic', align_corners=False)
        y2 = s.y2x(y); dr, db = up(r - y), up(b - y); R, B = y2 + dr, y2 + db; G = (y2 - .299 * R - .114 * B) / .587
        o = torch.cat([R, G, B], 1)
        if tuple(o.shape[-2:]) != tuple(size): o = F.interpolate(o, size=size, mode='bicubic', align_corners=False, antialias=True)
        return o.clamp(0, 1)
