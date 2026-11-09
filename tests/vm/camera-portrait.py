#!/usr/bin/env python3
"""Rotate-and-crop for a portrait-locked app (hello.camera's Portrait activity, -Dhello.portrait): with the fake back and front cameras the app
checks the capture result's mode and the turned YUV frame itself, and at each "shown" line a screenshot of its window (SurfaceFlinger's
picture) must show the scene upright and cropped to its centre 3:4 slice: along a row through the bars, yellow (cut), cyan, green, magenta,
red, blue (cut) left to right for the back camera, mirrored for the front one; no white or black bar. With the back camera open the app turns
its window to landscape (all eight bars across, no rotate-and-crop) and back ("back-live"): the open camera follows.
CAMERA_REAL=<description> (e.g. "MacBook Pro Camera"): that real PipeWire camera instead, whose picture is unknown: the portrait screenshots
must match the landscape one's centre slice as it is, better than turned or mirrored."""
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
REAL = os.environ.get('CAMERA_REAL')
COLOURS = {(0, 1, 1): 'cyan', (0, 1, 0): 'green', (1, 0, 1): 'magenta', (1, 0, 0): 'red', (1, 1, 0): 'yellow', (0, 0, 1): 'blue',
           (1, 1, 1): 'white', (0, 0, 0): 'black'}
# the scene's x (of 640) at fractions of the window's width: the slice is scene columns 140..500
SCENE = [(f, 140 + 360 * f) for f in (0.03, 1 / 6, 7 / 18, 11 / 18, 5 / 6, 0.97)]
BAR = ['white', 'yellow', 'cyan', 'green', 'magenta', 'red', 'blue', 'black']
SLICE = 360 / 640  # the centre slice's share of the frame's width


def name(p):
    return COLOURS[tuple(int(c > 128) for c in p)]


def check(shot, front):
    im = Image.open(shot).convert('RGB')
    print('%s: %dx%d' % (shot.name, im.width, im.height), flush=True)
    assert im.height > im.width, 'not a portrait window: %dx%d' % im.size
    if REAL: return
    for row in (0.3, 0.5):  # the bars fill the scene's top three quarters
        seen = [name(im.getpixel((int(f * (im.width - 1)), int(row * im.height)))) for f, _ in SCENE]
        want = [BAR[int((640 - x if front else x) // 80)] for _, x in SCENE]
        print('  row %.0f%%: %s (want %s)' % (row * 100, ' '.join(seen), ' '.join(want)), flush=True)
        assert seen == want, 'the preview is not upright and cropped'


def check_landscape(shot):
    im = Image.open(shot).convert('RGB')
    print('%s: %dx%d' % (shot.name, im.width, im.height), flush=True)
    assert im.width > im.height, 'not a landscape window: %dx%d' % im.size
    if REAL: return
    seen = [name(im.getpixel((int((i + 0.5) / 8 * (im.width - 1)), int(0.3 * im.height)))) for i in range(8)]
    print('  row 30%%: %s (want %s)' % (' '.join(seen), ' '.join(BAR)), flush=True)
    assert seen == BAR, 'the landscape preview is not the whole upright scene'


def grid(im):
    return list(im.convert('L').resize((18, 32), Image.BILINEAR).getdata())


def correlation(a, b):
    ma, mb = sum(a) / len(a), sum(b) / len(b)
    num = sum((x - ma) * (y - mb) for x, y in zip(a, b))
    den = (sum((x - ma) ** 2 for x in a) * sum((y - mb) ** 2 for y in b)) ** 0.5
    return num / den if den else 0.0


def untitled(im):
    """The window without its title bar: the rows that keep the top right pixel's colour."""
    top = 0
    while top < im.height // 4 and im.getpixel((im.width - 1, top)) == im.getpixel((im.width - 1, 0)): top += 1
    return im.crop((0, top, im.width, im.height))


def compare(landscape, portrait):
    """A real camera: the portrait window shows the landscape picture's centre slice as it is (rotate-and-crop undoes the app's turn)."""
    land, port = untitled(Image.open(landscape).convert('RGB')), untitled(Image.open(portrait).convert('RGB'))
    w = land.width
    centre = grid(land.crop((int(w * (1 - SLICE) / 2), 0, int(w * (1 + SLICE) / 2), land.height)))
    scores = {k: correlation(centre, grid(port.transpose(t) if t is not None else port)) for k, t in
              (('upright', None), ('turned 90', Image.Transpose.ROTATE_90), ('turned 180', Image.Transpose.ROTATE_180),
               ('turned 270', Image.Transpose.ROTATE_270), ('mirrored', Image.Transpose.FLIP_LEFT_RIGHT))}
    print('  %s against %s: %s' % (portrait.name, landscape.name, ', '.join('%s %.2f' % kv for kv in scores.items())), flush=True)
    upright = scores.pop('upright')
    assert upright > 0.5 and upright > max(scores.values()) + 0.1, 'the portrait preview is not the upright centre of the landscape one'


def real_camera_id():
    """The provider numbers PipeWire cameras in the backend's listing order (tests/vm/camera-pipewire.sh)."""
    env = dict(os.environ, PIPEWIRE_RUNTIME_DIR=os.environ.get('PIPEWIRE_RUNTIME_DIR', '/run/user/%d' % os.getuid()))
    listing = subprocess.run(['b/bridge/hals/camera/camera-pipewire-test'], env=env, capture_output=True, text=True, timeout=30).stdout
    cameras = [line for line in listing.splitlines() if line.startswith('camera ')]
    found = [i for i, line in enumerate(cameras) if REAL in line]
    assert found, 'no camera "%s" in:\n%s' % (REAL, listing)
    print(cameras[found[0]], flush=True)
    return found[0]


jopts = '-Dapklayer.runms=60000 -Dhello.portrait=true'
instance.stop()
if REAL:
    jopts += ' -Dhello.real=%d' % real_camera_id()
    subprocess.run(['bin/apklayer', 'providers', 'set', 'camera', 'pipewire'], env=instance.ENV, check=True, stdout=subprocess.DEVNULL)
else:
    subprocess.run(['bin/apklayer', 'providers', 'set', 'camera', 'fake', 'cameras=back,front', 'sizes=640x480,1280x720'], env=instance.ENV,
                   check=True, stdout=subprocess.DEVNULL)
ENV = dict(instance.ENV, APKLAYER_JOPTS=jopts)
subprocess.run(['bin/apklayer', 'permissions', 'grant', PKG, 'android.permission.CAMERA'], env=ENV, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
app = subprocess.Popen(['bin/apklayer', 'run', PKG], env=ENV, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
shots, result = {}, None
try:
    selector = selectors.DefaultSelector()
    selector.register(app.stdout, selectors.EVENT_READ)
    end, pending = time.monotonic() + 90, b''
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
                    shape, camera = line.split()[-3:-1]
                    shot = instance.capture('camera-%s-%s' % (shape, camera), PKG)
                    check_landscape(shot) if shape == 'landscape' else check(shot, camera == 'front')
                    shots[shape + ' ' + camera] = shot
                elif '[cam] PASS' in line or '[cam] FAIL' in line: result = line
    assert result and 'PASS' in result, result or 'the app ended without a result'
    first = next(iter(shots), ' ').split()[-1]  # the real camera's facing ("external" for a webcam), else back
    want = ['portrait ' + first, 'landscape ' + first, 'portrait %s-live' % first] + ([] if REAL else ['portrait front'])
    assert list(shots) == want, list(shots)
    if REAL:
        # the shot right after the landscape one (the first portrait one is a few seconds earlier, while the camera's exposure still settles)
        compare(shots['landscape ' + first], shots['portrait %s-live' % first])
    print('PASS camera-portrait: rotate-and-crop for the %s, upright on screen, following the window while open'
          % ('real camera "%s"' % REAL if REAL else 'back and front cameras'), flush=True)
finally:
    instance.stop()
    if app.poll() is None: os.killpg(app.pid, signal.SIGKILL)
    app.wait()
    subprocess.run(['bin/apklayer', 'providers', 'unset', 'camera'], env=instance.ENV, stdout=subprocess.DEVNULL)
