#!/usr/bin/env python3
"""Does a VM capture show app content, not a blank/uniform window? The window is what differs from a capture of the same desktop taken before the
launch (no assumption about decoration colours or where the compositor put it); its title bar (the top rows of one shade) is left out, the rest measured.
Usage: content.py SHOT.png BEFORE.png   or   content.py --selftest;  prints "<verdict> <detail>"; verdict is content, uniform or nowindow."""
import sys
from PIL import Image, ImageChops


def window(shot, before):
    """Bounding box of the changed area: rows and columns with >= 100 changed pixels (the panel clock and a dock highlight are smaller)."""
    diff = ImageChops.difference(shot, before).convert('L').point(lambda v: 255 if v > 8 else 0)
    w, h = diff.size
    data = diff.load()
    rows = [y for y in range(0, h, 2) if sum(1 for x in range(0, w, 2) if data[x, y]) >= 50]
    cols = [x for x in range(0, w, 2) if sum(1 for y in range(0, h, 2) if data[x, y]) >= 50]
    return (cols[0], rows[0], cols[-1] + 2, rows[-1] + 2) if rows and cols else None


def verdict(filename, before_name):
    shot, before = Image.open(filename).convert('RGB'), Image.open(before_name).convert('RGB')
    if shot.size != before.size: return 'nowindow', 'capture sizes differ'
    box = window(shot, before)
    if box is None: return 'nowindow', 'nothing changed on the desktop'
    left, top, right, bottom = box
    px = shot.load()
    # the decoration: rows at the top whose most common colour is the bar's shade (its title and buttons are the minority)
    shade = max(set(row := [px[x, top + 5] for x in range(left, right, 2)]), key=row.count) if bottom - top > 10 else None
    body = top
    while body < bottom and [px[x, body] for x in range(left, right, 2)].count(shade) * 2 > len(range(left, right, 2)): body += 1
    if body - top > 60: body = top  # no title bar (fullscreen, or the app's own colour matched): measure everything
    body += 2  # the bar's dark bottom edge
    if bottom - body < 50 or right - left < 50: return 'nowindow', 'changed area %dx%d' % (right - left, bottom - top)
    area = shot.crop((left + 2, body, right - 2, bottom)).quantize(64)  # 64 colours: tolerant of gradients and scaling noise
    counts = sorted((c for c, _ in area.getcolors(64)), reverse=True)
    share = counts[0] / sum(counts)
    detail = '%dx%d dominant %.1f%% colours %d' % (right - left - 4, bottom - body, share * 100, len(counts))
    # a clear colour with nothing drawn on it, or only a sliver of something (a spinner)
    return ('uniform' if share > 0.985 or len(counts) < 3 else 'content'), detail


def selftest(tmp='/tmp/apklayer-content-selftest'):
    from PIL import ImageDraw
    desktop = Image.new('RGB', (1280, 800), (44, 44, 44))
    ImageDraw.Draw(desktop).rectangle((0, 0, 1279, 31), fill=(0, 0, 0))
    desktop.save(tmp + '-before.png')
    for fill, text, want in (((0, 0, 0), False, 'uniform'), ((240, 240, 240), True, 'content')):
        im = desktop.copy()
        draw = ImageDraw.Draw(im)
        draw.rectangle((107, 112, 559, 155), fill=(48, 48, 48))
        draw.ellipse((115, 120, 135, 140), fill=(80, 80, 80))
        draw.text((300, 125), 'Title', fill=(255, 255, 255))
        draw.rectangle((107, 156, 559, 759), fill=fill)
        if text:
            draw.rectangle((107, 156, 559, 236), fill=(66, 133, 244))
            for y in range(280, 740, 40): draw.text((140, y), 'Some list item text', fill=(30, 30, 30))
        im.save(tmp + '.png')
        assert verdict(tmp + '.png', tmp + '-before.png')[0] == want, (want, verdict(tmp + '.png', tmp + '-before.png'))
    assert verdict(tmp + '-before.png', tmp + '-before.png')[0] == 'nowindow'
    print('PASS content.py selftest')


if __name__ == '__main__':
    if sys.argv[1:] == ['--selftest']: selftest(); sys.exit()
    print(*verdict(sys.argv[1], sys.argv[2]), sys.argv[1])
