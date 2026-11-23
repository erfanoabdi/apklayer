"""The synthetic corpus of tests/vm/evalfilter.py: one 1280x720 frame of 12 tiles (4x3, 320x240 each), all generated (PIL + numpy, fixed seed,
no game data). Fonts come from the system (DejaVu; evalfilter prints the names). corpus() returns (image, tiles, stems): tiles maps a name to its
box; stems lists the calibrated stems of the 'stems' tile as (kind, width px, x of the centre, polarity +1 light on dark / -1 dark on light, y0, y1).
Shapes are drawn at 4x and box-reduced (anti-aliased like a UI toolkit); text goes through FreeType at its final size."""
import glob
import math

import numpy as np
from PIL import Image, ImageDraw, ImageFont

W, H, TW, TH = 1280, 720, 320, 240
NAMES = ['text_small', 'text_large', 'text_light', 'icons', 'thin_lines', 'stems',
         'ui_panel', 'shaded_3d', 'foliage', 'gradients', 'anime_flat', 'checker_persp']
FONT_DIR = '/usr/share/fonts/truetype/dejavu/'
FONTS = {'sans': 'DejaVuSans.ttf', 'bold': 'DejaVuSans-Bold.ttf', 'mono': 'DejaVuSansMono.ttf', 'serif': 'DejaVuSerif.ttf'}


def font(kind, size):
    for p in (FONT_DIR + FONTS[kind], *glob.glob('/usr/share/fonts/**/' + FONTS[kind], recursive=True)):
        try: return ImageFont.truetype(p, size)
        except OSError: pass
    return ImageFont.load_default()


def font_names(): return ', '.join(FONTS[k] if glob.glob('/usr/share/fonts/**/' + FONTS[k], recursive=True) else k + '(default)' for k in FONTS)


def ss(draw_fn, size, bg, k=4):  # shapes at k x, reduced with a box filter
    im = Image.new('RGB', (size[0] * k, size[1] * k), bg)
    draw_fn(ImageDraw.Draw(im), k)
    return im.resize(size, Image.BOX)


def text_tile(bg, fg, lines):
    im = Image.new('RGB', (TW, TH), bg)
    d = ImageDraw.Draw(im)
    y = 6
    for kind, size, txt in lines:
        d.text((8, y), txt, font=font(kind, size), fill=fg)
        y += int(size * 1.35) + 3
    return im


def t_text_small():
    return text_tile((24, 28, 40), (232, 232, 232), [('sans', 11, 'Settings  Wi-Fi  Bluetooth  Display 0123456789'), ('sans', 13, 'The quick brown fox jumps over the lazy dog'),
        ('mono', 12, 'int main(void) { return 0; } // 12px'), ('serif', 13, 'Pack my box with five dozen liquor jugs'), ('sans', 15, 'Inventory  Quests  Map  Chat  Friends 42/99'),
        ('mono', 14, 'HP 1234/5678  MP 090/120  LV 56'), ('sans', 16, 'Confirm   Cancel   Retry   Back to lobby'), ('serif', 17, 'Quick zephyrs blow, vexing daft Jim.')])


def t_text_large():
    return text_tile((40, 44, 56), (250, 250, 250), [('bold', 20, 'Battle Pass  Season 12'), ('sans', 24, 'Daily Missions 3/5'), ('bold', 28, 'Reward Claimed'), ('sans', 36, 'Start Game'), ('bold', 44, 'VICTORY 1280')])


def t_text_light():  # dark on light
    return text_tile((244, 244, 240), (20, 20, 24), [('sans', 11, 'Terms of service and privacy policy 0123456789'), ('sans', 13, 'The quick brown fox jumps over the lazy dog'),
        ('serif', 14, 'Sphinx of black quartz, judge my vow'), ('mono', 12, 'x = [i*2 for i in range(10)]'), ('sans', 16, 'Account  Security  Notifications  About'), ('bold', 20, 'Welcome back, Player 1')])


def t_icons():
    def draw(d, k):
        col = (235, 235, 235)
        for i in range(6):  # 24 and 32 px outline icons on a grid
            x, y, s = 16 + (i % 3) * 100, 16 + (i // 3) * 100, 24 + 8 * (i % 2)
            x, y, s = x * k, y * k, s * k
            if i == 0: d.ellipse((x, y, x + s, y + s), outline=col, width=2 * k)
            elif i == 1: d.rounded_rectangle((x, y, x + s, y + s), radius=5 * k, outline=col, width=2 * k)
            elif i == 2: d.line((x, y + s // 2, x + s // 3, y + s, x + s, y), fill=col, width=3 * k, joint='curve')
            elif i == 3: d.polygon([(x + s // 2, y), (x + s, y + s), (x, y + s)], outline=col, width=2 * k)
            elif i == 4:
                for j in range(3): d.line((x, y + (j + 1) * s // 4, x + s, y + (j + 1) * s // 4), fill=col, width=2 * k)
            else: d.line((x, y, x + s, y + s), fill=col, width=2 * k); d.line((x, y + s, x + s, y), fill=col, width=2 * k)
        for i in range(5):  # filled glyphs
            x, y = (20 + i * 58) * k, 150 * k
            d.ellipse((x, y, x + 36 * k, y + 36 * k), fill=(255, 190 - i * 20, 40 + i * 40))
            d.rectangle((x + 14 * k, y + 8 * k, x + 22 * k, y + 28 * k), fill=(30, 30, 40))
    return ss(draw, (TW, TH), (30, 34, 46))


def t_thin_lines():
    def draw(d, k):
        for i in range(0, 150, 5): d.line((8 * k + i * k, 8 * k, 8 * k + i * k, 100 * k), fill=(255, 255, 255), width=k)  # 1 px vertical comb
        for i in range(0, 80, 8): d.line((170 * k, (8 + i) * k, 310 * k, (8 + i) * k), fill=(255, 255, 255), width=k * (1 + (i // 8) % 2))  # 1 and 2 px horizontals
        for a in range(0, 90, 10):  # a fan of 1 px lines at all angles
            r = 100 * k
            d.line((100 * k, 220 * k, 100 * k + r * math.cos(math.radians(a)), 220 * k - r * math.sin(math.radians(a))), fill=(255, 210, 60), width=k)
        d.rectangle((190 * k, 130 * k, 300 * k, 220 * k), outline=(0, 200, 255), width=k)
        d.ellipse((210 * k, 140 * k, 280 * k, 210 * k), outline=(255, 120, 120), width=k)
    return ss(draw, (TW, TH), (14, 16, 24))


STEMS = []


def t_stems():  # calibrated: vertical stems of 1/2/3 px, light on dark and dark on light, and big step edges; every row alike
    im = np.zeros((TH, TW, 3), np.uint8)
    top, bot = (22, 24, 34), (236, 236, 232)
    im[:120] = top; im[120:] = bot
    stems = []
    for y0, y1, fg, pol in ((0, 120, (240, 240, 240), 1), (120, 240, (24, 24, 30), -1)):
        for j, w in enumerate((1, 2, 3, 1, 2, 3)):
            x = 20 + j * 40
            im[y0 + 10:y1 - 10, x:x + w] = fg
            stems.append(('stem%dpx' % w, w, x + w / 2.0, pol, y0 + 10, y1 - 10))
        x = 270  # a step edge
        im[y0 + 10:y1 - 10, x:x + 40] = fg
        stems.append(('edge', 0, float(x), pol, y0 + 10, y1 - 10))
    STEMS[:] = stems
    return Image.fromarray(im)


def t_ui_panel():
    def draw(d, k):
        d.rectangle((0, 0, TW * k, TH * k), fill=(236, 238, 242))
        d.rounded_rectangle((10 * k, 10 * k, 310 * k, 70 * k), radius=8 * k, fill=(255, 255, 255), outline=(200, 204, 212), width=k)
        d.rounded_rectangle((20 * k, 85 * k, 150 * k, 125 * k), radius=20 * k, fill=(32, 120, 230))
        d.rounded_rectangle((165 * k, 85 * k, 300 * k, 125 * k), radius=20 * k, outline=(32, 120, 230), width=2 * k)
        for i in range(4): d.line((20 * k, (150 + i * 20) * k, 300 * k, (150 + i * 20) * k), fill=(210, 214, 222), width=k)
        d.ellipse((20 * k, 20 * k, 56 * k, 56 * k), fill=(250, 170, 60))
        d.rounded_rectangle((250 * k, 190 * k, 310 * k, 230 * k), radius=20 * k, fill=(40, 190, 110))
    im = ss(draw, (TW, TH), (236, 238, 242))
    dd = ImageDraw.Draw(im)
    dd.text((66, 18), 'Notification title', font=font('bold', 14), fill=(20, 24, 32)); dd.text((66, 40), 'Tap to open the message in the app', font=font('sans', 12), fill=(90, 96, 108))
    dd.text((50, 96), 'Accept', font=font('bold', 14), fill=(255, 255, 255)); dd.text((205, 96), 'Decline', font=font('bold', 14), fill=(32, 120, 230))
    for i, t in enumerate(('Wi-Fi', 'Bluetooth', 'Brightness', 'Volume')): dd.text((20, 140 + i * 20), t + '   on', font=font('sans', 12), fill=(30, 34, 44))
    return im


def t_shaded_3d():  # lit spheres on a ground strip: smooth shading, hard silhouettes, 3x3 supersampled
    n = 3
    ys, xs = np.mgrid[0:TH * n, 0:TW * n].astype(np.float32) / n
    img = np.zeros((TH * n, TW * n, 3), np.float32)
    img[:] = np.linspace(40, 90, TH * n)[:, None, None] * np.array([0.6, 0.7, 1.0])
    light = np.array([-0.5, -0.6, 0.62]); light /= np.linalg.norm(light)
    for cx, cy, r, col in ((90, 100, 70, (230, 90, 70)), (220, 90, 55, (80, 190, 120)), (170, 180, 45, (240, 210, 90))):
        dx, dy = xs - cx, ys - cy
        m = dx * dx + dy * dy < r * r
        z = np.sqrt(np.maximum(r * r - dx * dx - dy * dy, 0))
        nrm = np.stack([dx, dy, z], -1) / r
        ndl = (nrm * light).sum(-1)[..., None]
        spec = np.clip((2 * ndl * nrm - light)[..., 2:3], 0, 1) ** 40
        img[m] = (np.array(col) * (0.12 + 0.88 * np.clip(ndl, 0, 1)) + 255 * 0.6 * spec)[m]
    g = ys > 200
    img[g] = img[g] * 0.5 + np.array([60, 55, 50]) * 0.5
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8)).resize((TW, TH), Image.BOX)


def t_foliage():  # dense small leaves: high-frequency, low-contrast colour noise
    rs = np.random.RandomState(7)
    def draw(d, k):
        for _ in range(1400):
            x, y = rs.uniform(0, TW), rs.uniform(0, TH)
            a, b = rs.uniform(1.5, 5), rs.uniform(1, 2.5)
            g = rs.randint(60, 190)
            d.ellipse(((x - a) * k, (y - b) * k, (x + a) * k, (y + b) * k), fill=(int(g * rs.uniform(.3, .7)), g, int(g * rs.uniform(.15, .45))))
    return ss(draw, (TW, TH), (30, 70, 28))


def t_gradients():
    y, x = np.mgrid[0:TH, 0:TW].astype(np.float32)
    im = np.zeros((TH, TW, 3), np.float32)
    im[:80] = (x[:80] / TW * 255)[..., None] * np.array([1, 0.8, 0.5])
    r = np.hypot(x[80:160] - 160, y[80:160] - 120) / 90
    im[80:160] = np.clip(255 * (1 - r), 0, 255)[..., None] * np.array([0.4, 0.7, 1.0]) + 20
    im[160:] = (np.sin(x[160:] / 28) * 0.5 + 0.5)[..., None] * 220 * np.array([1, 0.6, 0.9]) + 20
    return Image.fromarray(np.clip(im, 0, 255).astype(np.uint8))


def t_anime_flat():  # flat colour with 2 px dark outlines and a hard cel shade
    def draw(d, k):
        o = (20, 14, 28)
        d.polygon([(40 * k, 220 * k), (60 * k, 120 * k), (110 * k, 100 * k), (170 * k, 120 * k), (190 * k, 220 * k)], fill=(250, 214, 196), outline=o, width=2 * k)
        d.polygon([(60 * k, 120 * k), (110 * k, 100 * k), (110 * k, 220 * k), (40 * k, 220 * k)], fill=(226, 176, 160))
        d.ellipse((50 * k, 20 * k, 170 * k, 130 * k), fill=(252, 226, 210), outline=o, width=2 * k)
        d.pieslice((44 * k, 12 * k, 176 * k, 120 * k), 180, 360, fill=(120, 80, 220), outline=o, width=2 * k)
        d.ellipse((78 * k, 70 * k, 98 * k, 94 * k), fill=(60, 140, 230), outline=o, width=2 * k)
        d.ellipse((122 * k, 70 * k, 142 * k, 94 * k), fill=(60, 140, 230), outline=o, width=2 * k)
        d.rectangle((210 * k, 30 * k, 300 * k, 120 * k), fill=(255, 120, 150), outline=o, width=2 * k)
        d.ellipse((220 * k, 150 * k, 290 * k, 220 * k), fill=(255, 220, 90), outline=o, width=2 * k)
    return ss(draw, (TW, TH), (170, 220, 250))


def t_checker_persp():  # a textured floor in perspective: the classic aliasing and moire test
    ys, xs = np.mgrid[0:TH * 3, 0:TW * 3].astype(np.float32) / 3
    hor = 60.0
    img = np.zeros((TH * 3, TW * 3, 3), np.float32); img[:] = (120, 160, 220)
    g = ys > hor
    z = 80.0 / np.maximum(ys - hor, 0.01)
    c = (np.floor((xs - TW / 2) * z / 40.0) + np.floor(z * 6)) % 2 == 0
    img[g & c] = (230, 230, 225); img[g & ~c] = (50, 54, 70)
    return Image.fromarray(img.astype(np.uint8)).resize((TW, TH), Image.BOX)


BUILD = {'text_small': t_text_small, 'text_large': t_text_large, 'text_light': t_text_light, 'icons': t_icons, 'thin_lines': t_thin_lines, 'stems': t_stems,
         'ui_panel': t_ui_panel, 'shaded_3d': t_shaded_3d, 'foliage': t_foliage, 'gradients': t_gradients, 'anime_flat': t_anime_flat, 'checker_persp': t_checker_persp}


def corpus():
    frame = Image.new('RGB', (W, H))
    tiles = {}
    for i, n in enumerate(NAMES):
        x, y = (i % 4) * TW, (i // 4) * TH
        frame.paste(BUILD[n]().convert('RGB'), (x, y))
        tiles[n] = (x, y, x + TW, y + TH)
    sx, sy = tiles['stems'][:2]
    return frame, tiles, [(k, w, sx + cx, pol, sy + a, sy + b) for k, w, cx, pol, a, b in STEMS]
