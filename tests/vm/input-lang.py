#!/usr/bin/env python3
"""Other scripts in a text field (docs/PLAN.md "Input / IME"): evdev keys through non-Latin xkb layouts (instance.inject layout:<names>: ru, ara, ir,
us intl's dead keys), Ctrl+A under a Cyrillic or Arabic layout (the physical key), IME commits of Arabic with harakat, Devanagari, CJK and astral
characters, deletion counted in UTF-8 bytes (text-input-v3) and glyphs on screen: two different words of one script must not render the same
(tofu boxes are all alike)."""
import os
from pathlib import Path
import selectors
import signal
import subprocess
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
import instance  # noqa: E402  (changes into the repo root)
import pixels  # noqa: E402
from PIL import ImageChops  # noqa: E402
PKG = 'hello.input'
ENV = dict(instance.ENV, APKLAYER_DEBUG_INPUT='1', APKLAYER_DISPLAY_WIDTH='480', APKLAYER_DISPLAY_HEIGHT='600')
TICK_MS = 500
ENV['APKLAYER_JOPTS'] = '-Dapklayer.runms=120000 -Dapklayer.debugkeys=true -Dapklayer.script=' + '+'.join('%d:hover:%d,0' % (i * TICK_MS, i) for i in range(1, 200))
ALL = 'combo:ctrl+30'  # Ctrl+A: the key's position, whatever the layout's letter
KEYS = lambda *codes: ['key:%d' % c for c in codes]  # noqa: E731
shots = {}
# (step, the field's text when it runs (None: at once), test input, name of a screenshot of the field to take first, ticks to wait first)
STEPS = [
    ('ru', None, ['focus', 'click:240,82', 'layout:ru'] + KEYS(34, 35, 48, 32, 20, 49), None, 0),  # п р и в е т
    ('ru2', 'привет', [ALL] + KEYS(30, 31, 32, 33, 34, 35), 'ru1', 0),  # ф ы в а п р
    ('ara', 'фывапр', ['layout:ara', ALL] + KEYS(18, 19, 20, 21, 22, 23), 'ru2', 0),
    ('ara2', 'ثقفغعه', [ALL] + KEYS(16, 17, 18, 19, 20, 21), 'ara1', 0),
    ('digits', 'ضصثقفغ', [ALL] + KEYS(2, 3, 4), 'ara2', 0),
    ('persian', '123', ['layout:ir', ALL] + KEYS(2, 3), None, 0),
    ('dead', '۱۲', ['layout:us:intl', ALL] + KEYS(40, 30), None, 0),  # dead acute, a
    ('harakat', 'á', ['layout:us', ALL, 'text:مَرْحَبًا'], None, 0),
    ('deva', 'مَرْحَبًا', [ALL, 'text:कखगघ'], None, 0),
    ('deva2', 'कखगघ', [ALL, 'text:चछजझ'], 'deva1', 0),
    ('conjunct', 'चछजझ', [ALL, 'text:हिन्दी'], 'deva2', 0),
    ('astral', 'हिन्दी', [ALL, 'text:你好𝒜😀'], None, 0),
    ('cyrillic commit', '你好𝒜😀', [ALL, 'text:привет'], None, 0),
    ('delete 1', 'привет', ['delete:2'], None, 2),  # two bytes: one Cyrillic letter
    ('delete 2', 'приве', [ALL, 'text:a😀'], None, 0),
    ('delete 3', 'a😀', ['delete:4'], None, 2),  # four bytes: the pair of units
    ('end', 'a', [], None, 0),
]
# Ctrl+A is queued on the app's main thread: the typing after it waits two ticks (as input-inject.py)
STEPS = [part for name, want, steps, shot, delay in STEPS for part in (
    [(name + ' select', want, steps[:steps.index(ALL) + 1], shot, delay), (name, want, steps[steps.index(ALL) + 1:], None, 2)] if ALL in steps
    else [(name, want, steps, shot, delay)])]


def texts(seen):
    return [line[line.index('text=') + 5:] for line in seen if '[hello-input] text=' in line]


def field(name):
    shot = instance.capture(name, PKG)
    image = pixels.rgb(shot)
    k = max(1, round(image.width / 480))  # the window is 480 px wide at scale 1 (960 at 200%, the x86 PC)
    crop = image.crop((0, 55 * k, 480 * k, 110 * k))
    ink = sum(1 for p in crop.getdata() if p[0] < 110 and p[1] < 110 and p[2] < 110)
    assert ink > 30, '%s: no text drawn in the field (%d dark pixels)' % (name, ink)
    shots[name] = crop


def compare(a, b, what):
    assert ImageChops.difference(shots[a], shots[b]).getbbox(), '%s: two different words look alike (tofu boxes?)' % what


instance.stop()
app = subprocess.Popen(['bin/apklayer', 'run', PKG], env=ENV, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
try:
    selector = selectors.DefaultSelector()
    selector.register(app.stdout, selectors.EVENT_READ)
    seen, pending, step, waited, matched = [], b'', 0, 0, 0
    with open(instance.OUT / 'input-lang.log', 'wb') as log:
        while step < len(STEPS):
            assert selector.select(15), 'silent at step ' + STEPS[step][0]
            data = os.read(app.stdout.fileno(), 65536)
            assert data, 'app ended at step ' + STEPS[step][0]
            log.write(data)
            pending += data
            *lines, pending = pending.split(b'\n')
            for raw in lines:
                line = raw.decode(errors='replace')
                if '[apklayer]' not in line and '[hello-input]' not in line: continue
                seen.append(line)
                print(line, flush=True)
                if 'script: hover:' not in line or step == len(STEPS): continue
                tick = int(line.split('script: hover:')[1].split(',')[0])
                name, want, steps, shot, delay = STEPS[step]
                got = (texts(seen) or [None])[-1]
                if want is None and tick * TICK_MS < 3000: continue
                if want is not None and got != want:
                    waited += 1
                    assert waited < 12, 'step %s: the field holds %r, wanted %r' % (name, got, want)
                    continue
                matched += 1  # ticks since the field had its text: the host sends it to the input method on its next poll
                if matched <= delay: continue
                waited = matched = 0
                if shot: field(shot)
                step += 1
                if steps: instance.inject(PKG, *steps)
    compare('ru1', 'ru2', 'Cyrillic'); compare('ara1', 'ara2', 'Arabic'); compare('deva1', 'deva2', 'Devanagari')
    print('PASS Cyrillic, Arabic, Persian, dead keys, harakat, Devanagari, CJK and astral text, byte-counted deletion and glyphs through the shell test input', flush=True)
finally:
    instance.stop()
    if app.poll() is None: os.killpg(app.pid, signal.SIGKILL)
    app.wait()
