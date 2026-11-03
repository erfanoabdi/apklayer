#!/usr/bin/env python3
"""Check UiBench's rendered dialog margins in the app window's own capture (apklayer screenshot: its content alone, 100% scale)."""
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
    pixels = im.load()
    left, right, first_row = 0, im.width, 7  # the window's edges; the first rows below the action bar's shadow
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
