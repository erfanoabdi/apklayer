#!/usr/bin/env python3
"""train.py OUT.pt [steps]: trains nnup.Net on random crops of the images in $NNUP_DATA (default ~/Pictures/Schermafdrukken), end to end:
HR crop -> downscale by a random render scale 0.6..1.0 -> net (2x, resampled back to the crop size) -> L1 against the crop."""
import sys, os, glob, random, torch, torch.nn.functional as F, numpy as np
from PIL import Image
from nnup import Net
out = sys.argv[1]; steps = int(sys.argv[2]) if len(sys.argv) > 2 else 3000
imgs = [torch.from_numpy(np.array(Image.open(f).convert('RGB'))).permute(2, 0, 1).float() / 255 for f in sorted(glob.glob(os.path.expanduser(os.environ.get('NNUP_DATA', '~/Pictures/Schermafdrukken')) + '/*.png'))]
imgs = [i for i in imgs if min(i.shape[-2:]) >= 400][:-3]  # the last three big ones are held out for eval.py
dev = 'cuda'; net = Net().to(dev); opt = torch.optim.Adam(net.parameters(), 1e-3); sch = torch.optim.lr_scheduler.CosineAnnealingLR(opt, steps)
N, C = 16, 192
for st in range(steps):
    s = random.uniform(0.6, 1.0); lr = max(8, round(C * s)); batch = []
    for _ in range(N):
        im = random.choice(imgs); y = random.randint(0, im.shape[1] - C); x = random.randint(0, im.shape[2] - C); batch.append(im[:, y:y + C, x:x + C])
    hr = torch.stack(batch).to(dev)
    low = F.interpolate(hr, size=(lr, lr), mode='area') if random.random() < 0.5 else F.interpolate(hr, size=(lr, lr), mode='bilinear', align_corners=False, antialias=True)
    loss = F.l1_loss(net(low, (C, C)), hr); opt.zero_grad(); loss.backward(); opt.step(); sch.step()
    if st % 250 == 0: print(st, round(loss.item(), 5), flush=True)
torch.save(net.state_dict(), out)
print('params', sum(p.numel() for p in net.parameters()))
