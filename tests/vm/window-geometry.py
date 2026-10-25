#!/usr/bin/env python3
"""Locate the apklayer window in a VM screenshot (Parallels `prlctl capture`).
Prints: left top right  (screen px of the title bar: its left edge, top row, right edge).
The bar is a dark-gray (R==G==B < 70) strip 32 px high; we look for it right of the dock (x>70)."""
import sys
from PIL import Image

im = Image.open(sys.argv[1]).convert("RGB")
W, H = im.size

def bar(p):
    return p[0] == p[1] == p[2] and 40 <= p[0] <= 70

best = None
for y in range(34, H - 40):
    run = None
    xs = [x for x in range(70, W) if bar(im.getpixel((x, y)))]
    if len(xs) > 200:
        best = (min(xs), y, max(xs))
        break
if not best:
    sys.exit(1)
# top row of the bar: walk up while the same column stays bar-colored
x = best[0] + 60
top = best[1]
while top > 0 and bar(im.getpixel((x, top - 1))):
    top -= 1
print(best[0], top, best[2])
