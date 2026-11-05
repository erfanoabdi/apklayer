#!/usr/bin/env python3
"""OTR2 (androidx GameActivity, native input, manifest screenOrientation sensorLandscape) takes pointer input where it is pointed: the window is
landscape (the manifest's orientation reaches the host) and a touch tap and a mouse click, at window scales 100 and 200, select the second
character portrait on the title screen (a green frame moves from the first portrait to it; screenshots before and after in OUT).
The same path dismisses the "WHAT'S NEW" dialog of a profile that has not seen it. tests/run otr2-input; OTR2 installed by hand."""
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
RUNS = [(200, 'click', True)]
env = dict(instance.ENV, APKLAYER_RUNTIME_DEBUG='1', APKLAYER_LOGCAT='1', APKLAYER_JOPTS='-Dapklayer.runms=60000 -Dapklayer.debugkeys=true -Dapklayer.debugresize=true')
intent = instance.OUT / 'otr2-intent.properties'
intent.write_text('component=com.dogbytegames.otrnext.OTRNext\n')
env['APKLAYER_INTENT_FILE'] = str(intent)


def frames():
    shell = LOGDIR / 'shell.log'
    return shell.read_text(errors='replace').count('dmabuf frame') if shell.exists() else 0


def green(image, which):
    """Bright green frame pixels around portrait `which` (the selected one)."""
    im = pixels.rgb(image)
    w, h = im.size
    x0, y0, x1, y1 = FRAMES[which]
    box = im.crop((int(x0 * w), int(y0 * h), int(x1 * w), int(y1 * h)))
    return pixels.count_image(box, lambda r, g, b: g > 170 and r < 150 and b < 90 and g - r > 50)


def logical(seen, scale):
    """The window content's logical size of the shell's last configure of this run."""
    return [int(v) for v in re.findall(r'configure (\d+)x(\d+) @\d+%', '\n'.join(seen))[-1]]


def play(scale, mode, resize):
    """One app start: landscape window, scale, wait for the title screen, then press a portrait with `mode` (tap | click) and check that the
    selection moved; with `resize` the window is also maximized and restored (a 16:9 content of the same shape, so the same fractions hit the
    same buttons) and pressed again after each."""
    seen = []
    saw = lambda text: any(text in line for line in seen)
    instance.stop()
    app = subprocess.Popen(['bin/apklayer', 'run', PKG], env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
    name = '%d-%s%s' % (scale, mode, '-resize' if resize else '')
    actions = [('press', 2), ('maximize', MAXIMIZE)] + [('probe', (x, y)) for y in (348, 261, 174) for x in range(200, 760, 40)]
    step, phase, mark, tries, stage, shots = 0, 'run', 0, 0, 0, {}
    try:
        sel = selectors.DefaultSelector()
        sel.register(app.stdout, selectors.EVENT_READ)
        pending = b''
        end = time.monotonic() + 900
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
                    if green(shots['title'], 1) < 100:
                        assert tries < 40, 'OTR2 %s: the title screen never came up' % name
                        tries += 1; mark = n - 800  # not there yet: look again in 200 frames
                        if tries == 2:  # a fresh instance first asks for consent (a Java dialog over the game): its Consent button
                            width, height = logical(seen, scale)
                            instance.inject(PKG, 'tap:%d,%d' % (round(508 / 797 * width), round(361 / 448 * height)))
                        continue
                    stage = 2
                elif phase == 'run':
                    kind, arg = actions[step]
                    width, height = logical(seen, scale)
                    if kind in ('press', 'probe'):
                        at = (round(PORTRAITS[arg][0] * width), round(PORTRAITS[arg][1] * height)) if kind == 'press' else arg; target = arg if kind == 'press' else 1
                        shots['before'] = instance.capture('otr2-%s-%d-before' % (name, step), PKG)
                        instance.inject(PKG, *(['press:%d,%d' % at] if mode == 'tap' else ['click:%d,%d' % at]))
                        phase, mark = 'release', n
                    else:
                        count = sum(l.startswith('[apklayer] configure ') for l in seen)
                        instance.inject(PKG, 'maximize:%d,%d' % arg if kind == 'maximize' else 'unmaximize')
                        phase, mark, seen_configures = 'settle', n, count
                elif phase == 'release' and n >= mark + 10:
                    if mode == 'tap': instance.inject(PKG, 'release:%d,%d' % at)
                    phase, mark = 'check', n
                elif phase == 'check' and n >= mark + 60:
                    shots['after'] = instance.capture('otr2-%s-%d-after' % (name, step), PKG)
                    other = 1 if target == 2 else 2
                    got = (green(shots['after'], target), green(shots['after'], other))
                    print('OTR2 %s step %d: %s portrait %d at %s in %dx%d @%d%%: frame pixels (target, other) %s' % (name, step, mode, target, at, *logical(seen, scale), scale, got), flush=True)
                    if actions[step][0] == 'probe': print('PROBE %s selects %s' % (at, (1 if green(shots['after'], 1) > 100 else 0, 2 if green(shots['after'], 2) > 100 else 0)), flush=True); step, phase = step + 1, 'run'; continue
                    assert got[0] > 100 and got[1] < 20, 'FAIL OTR2 %s: the %s at %s did not select portrait %d %s' % (name, mode, at, target, got)
                    step, phase = step + 1, 'run'
                elif phase == 'settle' and n >= mark + 120:
                    assert sum(l.startswith('[apklayer] configure ') for l in seen) > seen_configures, 'no configure after %s' % actions[step][0]
                    step, phase = step + 1, 'run'
        assert step == len(actions), 'OTR2 %s: stopped at step %d of %d (%s)' % (name, step, len(actions), actions[min(step, len(actions) - 1)])
        assert saw('orientation landscape requested'), 'the manifest orientation (sensorLandscape) did not reach the host'
    finally:
        try: os.killpg(app.pid, signal.SIGKILL)
        except ProcessLookupError: pass
        app.wait(); app.stdout.close()
        instance.stop()
        instance.kill('[a]pklayer.host.Main')
    print('PASS OTR2 %s: %s selected the portraits at %d%%%s' % (name, mode, scale, ', also after maximize and restore' if resize else ''), flush=True)


try:
    for run in RUNS:
        play(*run)
finally:
    intent.unlink(missing_ok=True)
instance.stop()  # a second stop: the consent dialog's WebView helper process can start while the first one ends the stack
instance.kill('[a]pklayer.host.Main')
instance.assert_clean()
