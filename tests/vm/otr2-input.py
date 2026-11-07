#!/usr/bin/env python3
"""OTR2 (androidx GameActivity, native input, manifest screenOrientation sensorLandscape) takes pointer input where it is pointed: the window is
landscape (the manifest's orientation reaches the host) and a touch tap and a mouse click, at window scales 100 and 200, select a character
portrait on the title screen (a green frame moves to it; screenshots before and after in OUT), also after the window changed size: a drag
resize, maximize and restore (tests/run otr2-input), or a scale change of the running game, 100 -> 200 -> 100 % (`otr2-input.py scale`, group
otr2-probe). OTR2 reads its window size only when it gets a window, so before NativeWindowResize a press after a resize landed at old/new of its
position. The same path dismisses the "WHAT'S NEW" dialog of a profile that has not seen it. OTR2 installed by hand."""
import os
from pathlib import Path
import re
import selectors
import signal
import subprocess
import sys
import time

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
import instance  # noqa: E402  (changes into the repo root)
import pixels  # noqa: E402
PKG = 'com.dogbytegames.otrnext'
LOGDIR = Path(instance.LOGDIR)
# the second portrait of the title screen and the two portraits' frames, as fractions of the window (measured in a 797x448 screenshot)
PORTRAITS = {1: (366 / 797, 348 / 448), 2: (430 / 797, 348 / 448)}
MAXIMIZE = (1194, 718)  # window geometry: 16:9 content of 1194x672 under the 46 px title bar
FRAMES = {1: (343 / 797, 326 / 448, 392 / 797, 372 / 448), 2: (407 / 797, 326 / 448, 456 / 797, 372 / 448)}
DRAG = ['resize:880,495', 'resize:960,540', 'resize:1040,585']  # a drag resize (content sizes, 16:9 so the same fractions hit the same buttons)
RESIZE = [('press', 2), ('resize', DRAG), ('press', 1), ('resize', ['maximize:%d,%d' % MAXIMIZE]), ('press', 2), ('resize', ['unmaximize']), ('press', 1)]
SCALE = [('press', 2), ('resize', ['scale:200']), ('press', 1), ('resize', ['scale:100']), ('press', 2)]
# windows of another shape than the game's 16:9 (`otr2-input.py aspect`, group otr2-aspect): each reached by a drag resize, then the portrait
# that is not selected is located from the drawn frame in a screenshot (not from fractions of the window) and pressed
ASPECTS = [(600, 1000), (1600, 500), (900, 850), (1137, 613)]


def drag(w, h, to):
    return ['resize:%d,%d' % (w + (to[0] - w) * i // 3, h + (to[1] - h) * i // 3) for i in (1, 2, 3)]


ASPECT = []
for i, size in enumerate(ASPECTS):
    ASPECT += [('resize', drag(*([797, 448] if i == 0 else ASPECTS[i - 1]), size)), ('other', size)]
MODES = {'scale': [(100, 'click', SCALE), (100, 'tap', SCALE)], 'aspect': [(100, 'tap', ASPECT), (200, 'click', ASPECT)]}
RUNS = MODES.get(sys.argv[1] if sys.argv[1:] else '', [(100, 'tap', RESIZE), (200, 'click', RESIZE), (200, 'tap', RESIZE)])
TABLE = []
env = dict(instance.ENV, APKLAYER_RUNTIME_DEBUG='1', APKLAYER_LOGCAT='1', APKLAYER_JOPTS='-Dapklayer.runms=300000 -Dapklayer.debugkeys=true')
intent = instance.OUT / 'otr2-intent.properties'
intent.write_text('component=com.dogbytegames.otrnext.OTRNext\n')
env['APKLAYER_INTENT_FILE'] = str(intent)


def frames():
    shell = LOGDIR / 'shell.log'
    return shell.read_text(errors='replace').count('dmabuf frame') if shell.exists() else 0


def green(image, which):
    """The share of green frame pixels around portrait `which` (the selected one): a share, not a count, since the window's pixel size
    differs between hosts (Parallels: 579x325, UTM: 797x448 or twice that)."""
    im = pixels.rgb(image)
    w, h = im.size
    x0, y0, x1, y1 = FRAMES[which]
    box = im.crop((int(x0 * w), int(y0 * h), int(x1 * w), int(y1 * h)))
    return pixels.count_image(box, lambda r, g, b: g > 120 and g - r > 40 and g - b > 40) / (box.size[0] * box.size[1])


def frame_box(image):
    """The selected portrait's green frame in a screenshot (pixels): the largest hollow cluster (stretched with the window: the game scales its 16:9 layout to any shape) of frame-green pixels."""
    im = pixels.rgb(image)
    w, h = im.size
    px = im.load()
    step = max(1, w // 800)  # sampled: a 2x window has twice the pixels, the frame twice the width
    green = {(x, y) for y in range(0, h, step) for x in range(0, w, step)
             if (lambda r, g, b: g > 120 and g - r > 40 and g - b > 40)(*px[x, y])}
    best, seen = None, set()
    for start in green:
        if start in seen: continue
        todo, part = [start], []
        seen.add(start)
        while todo:
            x, y = todo.pop()
            part.append((x, y))
            for q in ((x + dx * step, y + dy * step) for dx in range(-3, 4) for dy in range(-3, 4)):  # bridges the frame's anti-aliased sides
                if q in green and q not in seen:
                    seen.add(q); todo.append(q)
        xs, ys = [q[0] for q in part], [q[1] for q in part]
        bw, bh = max(xs) - min(xs) + step, max(ys) - min(ys) + step
        fill = len(part) * step * step / (bw * bh)
        if bw >= 12 and 0.25 < bw / bh < 4 and fill < 0.6 and (best is None or bw * bh > best[2] * best[3]):
            best = (min(xs), min(ys), bw, bh)
    return best


def logical(seen, scale):
    """The window content's logical size of the shell's last configure of this run."""
    return [int(v) for v in re.findall(r'configure (\d+)x(\d+) @\d+%', '\n'.join(seen))[-1]]


def consent_dialog(image):
    """The UMP consent dialog of a fresh instance: a large white card in the middle of the window."""
    im = pixels.rgb(image)
    w, h = im.size
    box = im.crop((int(0.25 * w), int(0.2 * h), int(0.75 * w), int(0.8 * h)))
    return pixels.count_image(box, lambda r, g, b: r > 235 and g > 235 and b > 235) > 0.4 * box.size[0] * box.size[1]


def play(scale, mode, actions):
    """One app start: landscape window, scale, wait for the title screen, then the actions: ('press', portrait) presses it with `mode`
    (tap | click) and checks that the selection moved to it; ('resize', steps) injects the steps (a resize, maximize, scale change) and waits
    until the host handed the game its resized surface (NativeWindowResize)."""
    seen = []
    saw = lambda text: any(text in line for line in seen)
    instance.stop()
    app = subprocess.Popen(['bin/apklayer', 'run', PKG], env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
    name = '%d-%s-%s' % (scale, mode, 'scale' if actions is SCALE else 'aspect' if actions is ASPECT else 'resize')
    step, phase, mark, tries, stage, shots, selected = 0, 'run', 0, 0, 0, {}, 1
    try:
        sel = selectors.DefaultSelector()
        sel.register(app.stdout, selectors.EVENT_READ)
        pending = b''
        end = time.monotonic() + 420
        with open(instance.OUT / ('otr2-input-%s.log' % name), 'wb') as log:
            while step < len(actions) and time.monotonic() < end:
                if sel.select(3):
                    data = os.read(app.stdout.fileno(), 65536)
                    if not data: break
                    log.write(data); log.flush()
                    *lines, pending = (pending + data).split(b'\n')
                    seen += [l.decode(errors='replace') for l in lines]
                n = frames()
                if stage == 0:
                    if saw('started ' + PKG) and n >= 40:
                        instance.inject(PKG, 'focus', 'scale:%d' % scale); stage, mark = 1, n
                elif stage == 1:  # the title screen shows after the shaders are compiled (longer on a loaded VM)
                    if not (saw('@%d%%' % scale) and n >= mark + 1000): continue
                    shots['title'] = instance.capture('otr2-%s-title' % name, PKG)
                    selected = 1 if green(shots['title'], 1) >= green(shots['title'], 2) else 2  # the profile keeps the last run's choice
                    if green(shots['title'], selected) < 0.04:
                        assert tries < 40, 'OTR2 %s: the title screen never came up' % name
                        tries += 1; mark = n - 800  # not there yet: look again in 200 frames
                        if consent_dialog(shots['title']):  # a fresh instance first asks for consent (a Java dialog over the game): its Consent button
                            width, height = logical(seen, scale)
                            instance.inject(PKG, 'tap:%d,%d' % (round(508 / 797 * width), round(361 / 448 * height)))
                        continue
                    stage = 2
                elif phase == 'run':
                    kind, arg = actions[step]
                    width, height = logical(seen, scale)
                    if kind == 'other':  # the other portrait, found from the drawn frame: frames are 49 px wide, 64 px apart at 797x448
                        shots['before'] = instance.capture('otr2-%s-%d-before' % (name, step), PKG)
                        box = frame_box(shots['before'])
                        assert box, 'OTR2 %s: no portrait frame in %s' % (name, shots['before'])
                        sx, sy = pixels.rgb(shots['before']).size[0] / width, pixels.rgb(shots['before']).size[1] / height
                        direction = 1 if selected == 1 else -1
                        aim = (box[0] + box[2] / 2 + direction * box[2] * 64 / 49, box[1] + box[3] / 2)
                        at = (round(aim[0] / sx), round(aim[1] / sy))
                        instance.inject(PKG, *(['press:%d,%d' % at] if mode == 'tap' else ['click:%d,%d' % at]))
                        phase, mark, target = 'release', n, 3 - selected
                    elif kind == 'press':
                        at = (round(PORTRAITS[arg][0] * width), round(PORTRAITS[arg][1] * height))
                        shots['before'] = instance.capture('otr2-%s-%d-before' % (name, step), PKG)
                        instance.inject(PKG, *(['press:%d,%d' % at] if mode == 'tap' else ['click:%d,%d' % at]))
                        phase, mark, target = 'release', n, arg
                    else:
                        handovers = sum('surface handed over again' in l for l in seen)
                        instance.inject(PKG, *arg)
                        phase, mark = 'settle', n
                elif phase == 'release' and n >= mark + 10:
                    if mode == 'tap': instance.inject(PKG, 'release:%d,%d' % at)
                    phase, mark = 'check', n
                elif phase == 'check' and n >= mark + 60:
                    shots['after'] = instance.capture('otr2-%s-%d-after' % (name, step), PKG)
                    if actions[step][0] == 'other':
                        moved = frame_box(shots['after'])
                        hit = bool(moved) and abs(moved[0] + moved[2] / 2 - aim[0]) < box[2] / 2 and abs(moved[1] + moved[3] / 2 - aim[1]) < box[3] / 2
                        TABLE.append('%dx%d @%d%% %s: frame at %s, tapped portrait %d at %s (screen %d,%d) -> %s' % (*logical(seen, scale), scale, mode,
                                     tuple(round(v) for v in box), target, at, *map(round, aim), 'hit' if hit else 'MISS (frame now %s)' % (moved,)))
                        print('TABLE ' + TABLE[-1], flush=True)
                        if hit: selected = target
                        step, phase = step + 1, 'run'
                        continue
                    other = 1 if target == 2 else 2
                    got = (green(shots['after'], target), green(shots['after'], other))
                    print('OTR2 %s step %d: %s portrait %d at %s in %dx%d @%d%%: green share (target, other) %.3f %.3f' % (name, step, mode, target, at, *logical(seen, scale), scale, *got), flush=True)
                    assert got[0] > 0.04 and got[1] < 0.01, 'FAIL OTR2 %s: the %s at %s did not select portrait %d %s' % (name, mode, at, target, got)
                    step, phase, selected = step + 1, 'run', target
                elif phase == 'settle' and n >= mark + 150 and sum('surface handed over again' in l for l in seen) > handovers:
                    step, phase = step + 1, 'run'
        assert step == len(actions), 'OTR2 %s: stopped at step %d of %d (%s)' % (name, step, len(actions), actions[min(step, len(actions) - 1)])
        assert saw('orientation landscape requested'), 'the manifest orientation (sensorLandscape) did not reach the host'
    finally:
        try: os.killpg(app.pid, signal.SIGKILL)
        except ProcessLookupError: pass
        app.wait(); app.stdout.close()
        instance.stop()
        instance.kill('[a]pklayer.host.Main')
    print('PASS OTR2 %s: %s selected the portraits at %d%%, also after %s' % (name, mode, scale, ', '.join(' '.join(a) for k, a in actions if k == 'resize')), flush=True)


try:
    for run in RUNS:
        play(*run)
finally:
    intent.unlink(missing_ok=True)
    if TABLE: print('window -> tap -> result\n' + '\n'.join(TABLE), flush=True)
assert all(t.endswith('hit') for t in TABLE), 'FAIL OTR2: a press on a located portrait missed'
instance.stop()  # a second stop: the consent dialog's WebView helper process can start while the first one ends the stack
instance.kill('[a]pklayer.host.Main')
instance.assert_clean()
