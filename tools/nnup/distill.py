#!/usr/bin/env python3
"""distill.py OUT.pt [steps] [channels, e.g. 24,32,48,64,64]: a small student for waifu2x upconv_7 (the teacher, pretrained.Waifu, photo weights). Inputs are crops of
local images ($NNUP_DATA dirs, colon separated: default the screenshots minus the three held out for eval.py, plus ~/Downloads) shrunk by a random render scale 0.4..0.8;
the student learns the teacher's 2x output (L1) from the same input. Student: five valid 3x3 convs (LeakyReLU 0.1) + a sixth to 12 channels, pixel shuffle, added to a
bilinear 2x of the input; the input is replicate padded by 6 (like the teacher) so the output is exactly 2x."""
import sys, os, glob, random, torch, torch.nn as nn, torch.nn.functional as F, numpy as np
from PIL import Image
from pretrained import Waifu

class Student(nn.Module):
    def __init__(s, ch=(24, 32, 48, 64, 64)):
        super().__init__(); c = (3,) + tuple(ch); s.ch = tuple(ch)
        s.convs = nn.ModuleList([nn.Conv2d(c[i], c[i + 1], 3) for i in range(len(ch))] + [nn.Conv2d(c[-1], 12, 3)])
    def forward(s, x, size=None):
        n = len(s.convs); y = F.pad(x, (n, n, n, n), mode='replicate')
        for i, cv in enumerate(s.convs):
            y = cv(y); y = F.leaky_relu(y, 0.1) if i < n - 1 else y
        o = F.interpolate(x, scale_factor=2, mode='bilinear', align_corners=False) + F.pixel_shuffle(y, 2)
        if size is not None and tuple(o.shape[-2:]) != tuple(size): o = F.interpolate(o, size=size, mode='bicubic', align_corners=False, antialias=True)
        return o.clamp(0, 1)
    def macs(s):  # per source pixel
        return sum(c.weight.numel() for c in s.convs)

def load_images():
    dirs = os.environ.get('NNUP_DATA', '~/Pictures/Schermafdrukken:~/Downloads').split(':'); out = []
    for d in dirs:
        fs = sorted(glob.glob(os.path.expanduser(d) + '/*.png') + glob.glob(os.path.expanduser(d) + '/*.jpg'))
        big = [f for f in fs if min(Image.open(f).size) >= 400]
        if 'Schermafdrukken' in d: big = big[:-3]  # held out
        out += big
    return out

if __name__ == '__main__':
    out = sys.argv[1]; steps = int(sys.argv[2]) if len(sys.argv) > 2 else 20000
    ch = tuple(int(x) for x in sys.argv[3].split(',')) if len(sys.argv) > 3 else (24, 32, 48, 64, 64)
    files = load_images(); print(len(files), 'images', flush=True)
    imgs = [torch.from_numpy(np.array(Image.open(f).convert('RGB'))).permute(2, 0, 1).contiguous() for f in files]  # uint8, CPU
    dev = 'cuda'; teacher = Waifu(os.path.expanduser('~/apklayer-nnup-work/w/up7_photo.json')).to(dev)
    net = Student(ch).to(dev); print('student', ch, 'macs/px', net.macs(), flush=True)
    opt = torch.optim.Adam(net.parameters(), 1e-3); sch = torch.optim.lr_scheduler.CosineAnnealingLR(opt, steps, 1e-5)
    N, L = 24, 72
    for st in range(steps):
        s = random.uniform(0.4, 0.8); C = round(L / s); batch = []
        for _ in range(N):
            im = random.choice(imgs)
            if min(im.shape[-2:]) < C: continue
            y = random.randint(0, im.shape[1] - C); x = random.randint(0, im.shape[2] - C); batch.append(im[:, y:y + C, x:x + C])
        hr = torch.stack(batch).to(dev).float() / 255
        low = F.interpolate(hr, size=(L, L), mode='area') if random.random() < 0.5 else F.interpolate(hr, size=(L, L), mode='bilinear', align_corners=False, antialias=True)
        with torch.no_grad(): tgt = teacher(low, (2 * L, 2 * L))
        loss = F.l1_loss(net(low), tgt); opt.zero_grad(); loss.backward(); opt.step(); sch.step()
        if st % 500 == 0: print(st, round(loss.item(), 5), flush=True)
    torch.save({'ch': ch, 'sd': net.state_dict()}, out)
