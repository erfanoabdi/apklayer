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
PORTRAIT2 = (430 / 797, 348 / 448)
FRAMES = {1: (343 / 797, 326 / 448, 392 / 797, 372 / 448), 2: (407 / 797, 326 / 448, 456 / 797, 372 / 448)}
RUNS = [(100, 'tap'), (200, 'click'), (200, 'tap')]
env = dict(instance.ENV, APKLAYER_RUNTIME_DEBUG='1', APKLAYER_LOGCAT='1', APKLAYER_JOPTS='-Dapklayer.runms=60000 -Dapklayer.debugkeys=true')
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


def play(scale, mode):
    """One app start: landscape window, scale, wait for the title screen, press portrait 2 with `mode` (tap | click), compare."""
    seen = []
    saw = lambda text: any(text in line for line in seen)
    instance.stop()
    app = subprocess.Popen(['bin/apklayer', 'run', PKG], env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
    name = '%d-%s' % (scale, mode)
    result = {}
    try:
        sel = selectors.DefaultSelector()
        sel.register(app.stdout, selectors.EVENT_READ)
        pending = b''
        stage, mark = 0, 0
        end = time.monotonic() + 100
        with open(instance.OUT / ('otr2-input-%s.log' % name), 'wb') as log:
            while stage < 9 and time.monotonic() < end:
                if sel.select(3):
                    data = os.read(app.stdout.fileno(), 65536)
                    if not data: break
                    log.write(data); log.flush()
                    *lines, pending = (pending + data).split(b'\n')
                    seen += [l.decode(errors='replace') for l in lines]
                n = frames()
                if stage == 0 and saw('started ' + PKG) and n >= 40:
                    instance.inject(PKG, 'focus', 'scale:%d' % scale); stage, mark = 1, n
                elif stage == 1 and saw('@%d%%' % scale) and n >= mark + 1000:  # the title screen shows after the shaders are compiled
                    result['before'] = instance.capture('otr2-%s-before' % name, PKG)
                    width, height = [int(v) for v in re.findall(r'configure (\d+)x(\d+) @%d%%' % scale, '\n'.join(seen))[-1]]
                    result['size'] = (width, height)
                    x, y = round(PORTRAIT2[0] * width), round(PORTRAIT2[1] * height)
                    if mode == 'tap': instance.inject(PKG, 'press:%d,%d' % (x, y))
                    else: instance.inject(PKG, 'click:%d,%d' % (x, y))
                    stage, mark = 2, n
                    result['at'] = (x, y)
                elif stage == 2 and n >= mark + 10:
                    if mode == 'tap': instance.inject(PKG, 'release:%d,%d' % result['at'])
                    stage, mark = 3, n
                elif stage == 3 and n >= mark + 60:
                    result['after'] = instance.capture('otr2-%s-after' % name, PKG); stage = 9
    finally:
        try: os.killpg(app.pid, signal.SIGKILL)
        except ProcessLookupError: pass
        app.wait(); app.stdout.close()
        instance.stop()
        instance.kill('[a]pklayer.host.Main')
    assert 'after' in result, 'OTR2 %s: the title screen never came up' % name
    assert saw('orientation landscape requested'), 'the manifest orientation (sensorLandscape) did not reach the host'
    assert result['size'][0] > result['size'][1], 'OTR2 window is not landscape: %s' % (result['size'],)
    first = (green(result['before'], 1), green(result['before'], 2))
    last = (green(result['after'], 1), green(result['after'], 2))
    print('OTR2 %s: at %s in %dx%d; frame pixels (portrait 1, 2) before %s after %s' % (name, result['at'], *result['size'], first, last), flush=True)
    assert first[0] > 100 and first[1] < 20, 'OTR2 %s: portrait 1 is not the selected one at the start %s' % (name, first)
    assert last[1] > 100 and last[0] < 20, 'FAIL OTR2 %s: the %s did not select portrait 2 %s' % (name, mode, last)
    print('PASS OTR2 %s selected portrait 2 at %d%%' % (mode, scale), flush=True)


try:
    for scale, mode in RUNS:
        play(scale, mode)
finally:
    intent.unlink(missing_ok=True)
instance.assert_clean()
