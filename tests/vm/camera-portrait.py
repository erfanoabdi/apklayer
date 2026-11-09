#!/usr/bin/env python3
"""Rotate-and-crop for a portrait-locked app (hello.camera's Portrait activity, -Dhello.portrait): with the fake back and front cameras the app
checks the capture result's mode and the turned YUV frame itself, and at each "shown" line a screenshot of its window (SurfaceFlinger's
picture) must show the scene upright and cropped to its centre 3:4 slice: along a row through the bars, yellow (cut), cyan, green, magenta,
red, blue (cut) left to right for the back camera, mirrored for the front one; no white or black bar."""
import os
from pathlib import Path
import selectors
import signal
import subprocess
import sys
import time

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
import instance  # noqa: E402  (changes into the repo root)
from PIL import Image  # noqa: E402

PKG = 'hello.camera'
ENV = dict(instance.ENV, APKLAYER_JOPTS='-Dapklayer.runms=40000 -Dhello.portrait=true')
COLOURS = {(0, 1, 1): 'cyan', (0, 1, 0): 'green', (1, 0, 1): 'magenta', (1, 0, 0): 'red', (1, 1, 0): 'yellow', (0, 0, 1): 'blue',
           (1, 1, 1): 'white', (0, 0, 0): 'black'}
# the scene's x (of 640) at fractions of the window's width: the slice is scene columns 140..500
SCENE = [(f, 140 + 360 * f) for f in (0.03, 1 / 6, 7 / 18, 11 / 18, 5 / 6, 0.97)]
BAR = ['white', 'yellow', 'cyan', 'green', 'magenta', 'red', 'blue', 'black']


def name(p):
    return COLOURS[tuple(int(c > 128) for c in p)]


def check(shot, front):
    im = Image.open(shot).convert('RGB')
    print('%s: %dx%d' % (shot.name, im.width, im.height), flush=True)
    assert im.height > im.width, 'not a portrait window: %dx%d' % im.size
    for row in (0.3, 0.5):  # the bars fill the scene's top three quarters
        seen = [name(im.getpixel((int(f * (im.width - 1)), int(row * im.height)))) for f, _ in SCENE]
        want = [BAR[int((640 - x if front else x) // 80)] for _, x in SCENE]
        print('  row %.0f%%: %s (want %s)' % (row * 100, ' '.join(seen), ' '.join(want)), flush=True)
        assert seen == want, 'the preview is not upright and cropped'


instance.stop()
subprocess.run(['bin/apklayer', 'providers', 'set', 'camera', 'fake', 'cameras=back,front', 'sizes=640x480,1280x720'], env=ENV, check=True,
               stdout=subprocess.DEVNULL)
subprocess.run(['bin/apklayer', 'permissions', 'grant', PKG, 'android.permission.CAMERA'], env=ENV, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
app = subprocess.Popen(['bin/apklayer', 'run', PKG], env=ENV, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
shots, result = [], None
try:
    selector = selectors.DefaultSelector()
    selector.register(app.stdout, selectors.EVENT_READ)
    end, pending = time.monotonic() + 60, b''
    with open(instance.OUT / 'camera-portrait.log', 'wb') as log:
        while result is None:
            assert selector.select(15) and time.monotonic() < end, 'camera-portrait stalled'
            data = os.read(app.stdout.fileno(), 65536)
            if not data: break
            log.write(data)
            pending += data
            *lines, pending = pending.split(b'\n')
            for raw in lines:
                line = raw.decode(errors='replace')
                if '[cam]' not in line: continue
                print(line, flush=True)
                if line.endswith(' shown'):
                    facing = line.split()[-2]
                    check(instance.capture('camera-portrait-' + facing, PKG), facing == 'front')
                    shots.append(facing)
                elif '[cam] PASS' in line or '[cam] FAIL' in line: result = line
    assert result and 'PASS' in result, result or 'the app ended without a result'
    assert shots == ['back', 'front'], shots
    print('PASS camera-portrait: rotate-and-crop for the back and front cameras, upright on screen', flush=True)
finally:
    instance.stop()
    if app.poll() is None: os.killpg(app.pid, signal.SIGKILL)
    app.wait()
    subprocess.run(['bin/apklayer', 'providers', 'unset', 'camera'], env=ENV, stdout=subprocess.DEVNULL)
