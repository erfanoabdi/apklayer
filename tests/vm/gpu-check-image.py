#!/usr/bin/env python3
"""Check UiBench's rendered dialog margins in a VM capture (100% scale): a desktop capture, or with --app the app window's own
(apklayer screenshot: the content alone)."""
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


def desktop_window(im, pixels):
    """The app window's columns in a desktop capture and the first content row to check."""
    # Locate the active shell decoration, rather than assuming a desktop x/y.
    for top in range(im.height):
        bars = list(runs([pixels[x, top] == (43, 43, 43) for x in range(im.width)]))
        if bars and max(b - a for a, b in bars) >= 240:
            left, right = max(bars, key=lambda p: p[1] - p[0])
            break
    else:
        # Mutter may move an oversized resized window's decoration above the
        # screen. Its neutral content background still exposes both edges.
        top = 0
        body = list(runs([
            sum(min(pixels[x, y]) >= 190 and
                max(pixels[x, y]) - min(pixels[x, y]) <= 3
                for y in range(32, im.height)) > (im.height - 32) * .75
            for x in range(im.width)]))
        left, right = body[0][0], body[-1][1]
    return left, right, max(32, top + 32 + 21)


app = '--app' in sys.argv
for filename in [a for a in sys.argv[1:] if a != '--app']:
    im = Image.open(filename).convert('RGB')
    pixels = im.load()
    # an app capture is the content alone: its edges are the window's, the rows a desktop capture skips below the title bar are 7
    left, right, first_row = (0, im.width, 7) if app else desktop_window(im, pixels)
    # Most rows in each dialog edge column are white. Averaging excludes text,
    # dividers and the narrow scrollbar thumb without mistaking the grey inset
    # background for the dialog itself.
    white = list(runs([
        sum(min(pixels[x, y]) > 253 for y in range(first_row, im.height))
            > (im.height - first_row) * .75
        for x in range(left, right)]))
    # Text can split the white columns: the outermost runs are the dialog edges.
    a, b = white[0][0], white[-1][1]
    margins = a, right - left - b
    assert all(15 <= n <= 17 for n in margins), (filename, margins)
    print(f'PASS dialog margins {margins[0]}px / {margins[1]}px: {filename}')
