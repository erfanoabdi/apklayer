#!/usr/bin/env python3
"""mixtrain.py OUT.pt steps ch pf pp pg clamp   (pf+pp+pg = 1; the shipped student: python3 mixtrain.py m701515c.pt 12000 16,24,32,32,32 0.7 0.15 0.15 1; the teacher json is $NNUP_TEACHER, default ~/apklayer-nnup-work/w/up7_photo.json)
pf: filtered crops (area/bilinear-AA) -> waifu2x teacher target (as shipped s2); pp: point-sampled crops -> target = the original HR crop, the 2x output
goes through the resample (bicubic, antialias) to the HR crop size inside the loss; pg: gamegen aliased LR + supersampled GT at 2x. clamp=1: the 2x output is
clamped to the 3x3 min/max of the source (nearest-upsampled) in the forward (before the resample)."""
import sys, os, random, torch, torch.nn.functional as F, numpy as np
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))  # distill.py, pretrained.py, gamegen.py are in this folder
from PIL import Image
from pretrained import Waifu
from distill import Student, load_images
import gamegen

class CStudent(Student):
    clamp = False
    def forward(s, x, size=None):
        n = len(s.convs); y = F.pad(x, (n, n, n, n), mode='replicate')
        for i, cv in enumerate(s.convs):
            y = cv(y); y = F.leaky_relu(y, 0.1) if i < n - 1 else y
        o = F.interpolate(x, scale_factor=2, mode='bilinear', align_corners=False) + F.pixel_shuffle(y, 2)
        if s.clamp:
            p = F.pad(x, (1, 1, 1, 1), mode='replicate'); mx = F.max_pool2d(p, 3, 1); mn = -F.max_pool2d(-p, 3, 1)
            mx = F.interpolate(mx, scale_factor=2, mode='nearest'); mn = F.interpolate(mn, scale_factor=2, mode='nearest')
            o = torch.minimum(torch.maximum(o, mn), mx)
        if size is not None and tuple(o.shape[-2:]) != tuple(size): o = F.interpolate(o, size=size, mode='bicubic', align_corners=False, antialias=True)
        return o.clamp(0, 1)

if __name__ == '__main__':
    out = sys.argv[1]; steps = int(sys.argv[2]); ch = tuple(int(x) for x in sys.argv[3].split(',')); pf, pp, pg = map(float, sys.argv[4:7]); cl = int(sys.argv[7])
    files = load_images(); imgs = [torch.from_numpy(np.array(Image.open(f).convert('RGB'))).permute(2, 0, 1).contiguous() for f in files]
    dev = 'cuda'; teacher = Waifu(os.path.expanduser(os.environ.get('NNUP_TEACHER', '~/apklayer-nnup-work/w/up7_photo.json'))).to(dev)
    net = CStudent(ch).to(dev); net.clamp = bool(cl); print('student', ch, 'macs/px', net.macs(), 'mix', pf, pp, pg, 'clamp', cl, flush=True)
    opt = torch.optim.Adam(net.parameters(), 1e-3); sch = torch.optim.lr_scheduler.CosineAnnealingLR(opt, steps, 1e-5)
    N, L = 24, 72
    for st in range(steps):
        s = random.uniform(0.4, 0.8); C = round(L / s); r = random.random(); kind = 'f' if r < pf else ('p' if r < pf + pp else 'g')
        if kind == 'g':
            with torch.no_grad(): low, tgt = gamegen.lr_hr_batch(N, L, s)
        else:
            batch = []
            for _ in range(N):
                im = random.choice(imgs)
                if min(im.shape[-2:]) < C: continue
                y = random.randint(0, im.shape[1] - C); x = random.randint(0, im.shape[2] - C); batch.append(im[:, y:y + C, x:x + C])
            hr = torch.stack(batch).to(dev).float() / 255
            if kind == 'f':
                low = F.interpolate(hr, size=(L, L), mode='area') if random.random() < 0.5 else F.interpolate(hr, size=(L, L), mode='bilinear', align_corners=False, antialias=True)
                with torch.no_grad(): tgt = teacher(low, (2 * L, 2 * L))
            else:
                idx = ((torch.arange(L, device=dev).float() + random.random() * 0.0 + .5) * C / L).floor().long().clamp(max=C - 1)
                low = hr[:, :, idx][:, :, :, idx]; tgt = hr
        o = net(low, tuple(tgt.shape[-2:]))
        loss = F.l1_loss(o, tgt); opt.zero_grad(); loss.backward(); opt.step(); sch.step()
        if st % 1000 == 0: print(st, kind, round(loss.item(), 5), flush=True)
    torch.save({'ch': ch, 'sd': net.state_dict()}, out)
