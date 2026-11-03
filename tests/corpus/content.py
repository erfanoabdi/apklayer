#!/usr/bin/env python3
"""Does a VM capture show app content, not a blank/uniform window? Finds the window under the shell's decoration (the widest run of its
(43,43,43) / unfocused (58,58,58) bar, as tests/vm/gpu-check-image.py does) and measures the area below it down to the screen edge.
Usage: content.py PNG...   prints "<verdict> <detail>" per file; verdict is content, uniform or nowindow."""
import sys
from PIL import Image

BAR = ((43, 43, 43), (58, 58, 58))


def runs(flags):
    start = None
    for x, match in enumerate([*flags, False]):
        if match and start is None: start = x
        elif not match and start is not None: yield start, x; start = None


def verdict(filename):
    im = Image.open(filename).convert('RGB')
    px = im.load()
    for top in range(im.height):
        bars = [r for r in runs([px[x, top] in BAR for x in range(im.width)]) if r[1] - r[0] >= 200]
        if not bars: continue
        left, right = max(bars, key=lambda r: r[1] - r[0])
        mid = (left + right) // 2
        body = top
        while body < im.height and px[mid, body] in BAR: body += 1
        # the decoration is a ~30 px tall flat block; the wallpaper's grey noise only has scattered runs of the same value
        if body - top >= 20 and all(px[x, y] in BAR for x in range(left + 40, right - 40, 3) for y in range(top, top + 20)): break
    else:
        return 'nowindow', 'no decoration bar'
    body += 2  # the bar's 1-2 px dark bottom edge
    if im.height - body < 50: return 'nowindow', 'window below the screen'
    area = im.crop((left + 2, body, right - 2, im.height)).quantize(64)  # 64 colours: tolerant of gradients and scaling noise
    counts = sorted((c for c, _ in area.getcolors(64)), reverse=True)
    share = counts[0] / sum(counts)
    detail = '%dx%d dominant %.1f%% colours %d' % (right - left, im.height - body, share * 100, len(counts))
    # a clear colour with nothing drawn on it, or only a sliver of something (a spinner)
    return ('uniform' if share > 0.985 or len(counts) < 3 else 'content'), detail


if __name__ == '__main__':
    for f in sys.argv[1:]:
        print(*verdict(f), f)
