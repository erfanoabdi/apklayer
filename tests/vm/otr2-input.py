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
RUNS = [(100, 'click', SCALE), (100, 'tap', SCALE)] if sys.argv[1:] == ['scale'] else [(100, 'tap', RESIZE), (200, 'click', RESIZE), (200, 'tap', RESIZE)]
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
    name = '%d-%s-%s' % (scale, mode, 'scale' if actions is SCALE else 'resize')
    step, phase, mark, tries, stage, shots = 0, 'run', 0, 0, 0, {}
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
                    if green(shots['title'], 1) < 0.04:
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
                    if kind == 'press':
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
                    other = 1 if target == 2 else 2
                    got = (green(shots['after'], target), green(shots['after'], other))
                    print('OTR2 %s step %d: %s portrait %d at %s in %dx%d @%d%%: green share (target, other) %.3f %.3f' % (name, step, mode, target, at, *logical(seen, scale), scale, *got), flush=True)
                    assert got[0] > 0.04 and got[1] < 0.01, 'FAIL OTR2 %s: the %s at %s did not select portrait %d %s' % (name, mode, at, target, got)
                    step, phase = step + 1, 'run'
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
instance.stop()  # a second stop: the consent dialog's WebView helper process can start while the first one ends the stack
instance.kill('[a]pklayer.host.Main')
instance.assert_clean()
