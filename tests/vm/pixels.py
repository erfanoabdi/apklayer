"""Pixel checks of screenshots (instance.capture: an app window, or the desktop for window geometry) for the GPU, media and window tests; PIL on the VM."""
from collections import Counter
from PIL import Image, ImageChops


def rgb(image):
    return Image.open(image).convert('RGB')


def count(image, test):
    return count_image(rgb(image), test)


def count_image(im, test):
    return sum(1 for p in im.getdata() if test(*p))


def difference(a, b):
    return ImageChops.difference(rgb(a), rgb(b))


def red_surface(image):
    """A decoded red SurfaceView (hello-native-surface / -player with red-h264.mp4)."""
    red = count(image, lambda r, g, b: r > 180 and g < 70 and b < 70)
    assert red > 50000, f'no decoded red SurfaceView: {red} pixels'
    print(f'PASS decoded red SurfaceView: {red} pixels', flush=True)


def video_level(image, asset):
    """Range check: limited-range video (Y 16..235) must reach the full 0 / 255 (white-/black- assets), not 16 / 235 (washed out)."""
    top = Counter(rgb(image).getdata()).most_common(3)
    print('most common colors', top, flush=True)
    level = max((c for c in top if c[0][0] == c[0][1] == c[0][2] and c[1] > 50000), key=lambda c: c[1], default=None)
    assert level, f'no uniform video area: {top}'
    value = level[0][0]
    assert (value >= 250) if asset.startswith('white') else (value <= 4), f'{asset}: video shows gray {value}'
    print(f'PASS {asset} shows gray {value}', flush=True)


def colors(image):
    """Distinct colours of a screenshot: antialiased text and edges of a drawn UI give hundreds, an empty or black window a handful."""
    return len(rgb(image).getcolors(1 << 24))


def game_content(image):
    """How much of an app window capture is lit, 0 when it is flat (few colours): OTR2's frames."""
    pixels = list(rgb(image).getdata())
    lit = sum(1 for p in pixels if max(p) > 40) / len(pixels)
    colors = len({(p[0] >> 4, p[1] >> 4, p[2] >> 4) for p in pixels})
    print(f'IMAGE {image}: {lit:.0%} lit, {colors} colors', flush=True)
    return lit if colors > 200 else 0.0


def headers(image, count=2, maximize=False, content_y=None):
    """Locates app window decorations (the shell's dark title bars) in a capture: permille points to click, lower window first, so uinput can
    focus them without relying on Alt-Tab state or fixed desktop coordinates."""
    im = rgb(image)
    px = im.load()
    found = []
    for y in range(32, im.height):
        start = None
        for x in range(im.width + 1):
            match = x < im.width and px[x, y] in ((43, 43, 43), (58, 58, 58))
            if match and start is None:
                start = x
            elif not match and start is not None:
                if x - start >= 240 and not any(abs(y - r[1]) < 32 and min(x, r[2]) - max(start, r[0]) >= 240 for r in found):
                    found.append((start, y, x))
                start = None
    assert len(found) == count, ('unexpected app decorations', found)
    found.sort(key=lambda r: r[1], reverse=True)
    return [[round((right - 60 if maximize else (left + right) / 2) * 1000 / im.width),
             round((top + 16 if content_y is None else top + 32 + content_y) * 1000 / im.height)] for left, top, right in found]
