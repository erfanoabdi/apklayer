#!/usr/bin/env python3
"""Check UiBench's rendered dialog margins in a Parallels capture (100% scale)."""
import sys
from PIL import Image


def runs(flags):
    start = None
    for x, match in enumerate([*flags, False]):
        if match and start is None:
            start = x
        elif not match and start is not None:
            yield start, x
            start = None


for filename in sys.argv[1:]:
    im = Image.open(filename).convert('RGB')
    # Locate the active shell decoration, rather than assuming a desktop x/y.
    for top in range(im.height):
        bars = list(runs([im.getpixel((x, top)) == (43, 43, 43) for x in range(im.width)]))
        if bars and max(b - a for a, b in bars) >= 240:
            left, right = max(bars, key=lambda p: p[1] - p[0])
            break
    else:
        raise AssertionError('active app decoration not visible')
    # Content starts below the 32px decoration. The dialog's top padding is
    # 16px; sample its blank header before the title text or scrollbar.
    y = top + 32 + 21
    white = list(runs([min(im.getpixel((x, y))) > 253 for x in range(left, right)]))
    a, b = max(white, key=lambda p: p[1] - p[0])
    margins = a, right - left - b
    assert all(15 <= n <= 17 for n in margins), (filename, margins)
    print(f'PASS dialog margins {margins[0]}px / {margins[1]}px: {filename}')
