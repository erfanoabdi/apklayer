"""Shared model of the neural upscaler experiment (docs/PLAN.md "Neural upscaler"): an ESPCN-style 2x net (Shi et al. 2016, the idea of
TheMrKingent/NeuralUpscale, own weights) that predicts a residual over a bicubic 2x; the result is resampled to the output size, so any
render scale 60..100 % works. The convolutions run at the source size."""
import torch, torch.nn as nn, torch.nn.functional as F

class Net(nn.Module):
    def __init__(s, c=int(__import__('os').environ.get('NNUP_C', 24))):
        super().__init__()
        s.a = nn.Conv2d(3, c, 5, padding=2); s.b = nn.Conv2d(c, c, 3, padding=1); s.c = nn.Conv2d(c, c, 3, padding=1); s.d = nn.Conv2d(c, 12, 3, padding=1)
    def forward(s, x, size):  # x: N,3,h,w in 0..1; size: (H, W) of the output
        base = F.interpolate(x, scale_factor=2, mode='bicubic', align_corners=False)
        y = F.relu(s.a(x)); y = F.relu(s.b(y)); y = F.relu(s.c(y))
        y = base + F.pixel_shuffle(s.d(y), 2)
        if tuple(y.shape[-2:]) != tuple(size): y = F.interpolate(y, size=size, mode='bicubic', align_corners=False, antialias=True)
        return y.clamp(0, 1)
