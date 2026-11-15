#!/usr/bin/env python3
"""Wheel and stylus input of one instance through the shell's test input (instance.inject wheel:/pen:, bridge/shell/pointer.c and tablet.c, the
paths of wl_pointer's axis/axis_discrete/axis_value120 and zwp_tablet_tool_v2): hello.input prints the MotionEvent axes it gets. The wheel's
notches are AXIS_VSCROLL/AXIS_HSCROLL (+ = up/right, hi-res and touchpad scrolls as fractions, Shift in the meta state); the pen's pressure, distance,
tilt, orientation (TouchInputMapper's formulas), barrel rotation (AXIS_GENERIC_1), stylus buttons, eraser and hover/touch actions."""
import math
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
PKG = 'hello.input'
ENV = dict(instance.ENV, APKLAYER_DEBUG_INPUT='1', APKLAYER_DISPLAY_WIDTH='480', APKLAYER_DISPLAY_HEIGHT='600',
           APKLAYER_JOPTS='-Dapklayer.runms=20000 -Dapklayer.debugkeys=true')
seen = []

instance.stop()
app = subprocess.Popen(['bin/apklayer', 'run', PKG], env=ENV, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
selector = selectors.DefaultSelector()
selector.register(app.stdout, selectors.EVENT_READ)
pending = b''
log = open(instance.OUT / 'input-wheel-pen.log', 'wb')


def expect(what, after=0):
    """Reads the app's output until a line from index `after` on contains `what`; returns it."""
    global pending
    end = time.monotonic() + 30
    while not any(what in l for l in seen[after:]):
        assert selector.select(10) and time.monotonic() < end, 'no "%s"; last lines: %s' % (what, seen[-6:])
        data = os.read(app.stdout.fileno(), 65536)
        assert data, 'the app ended without "%s"' % what
        log.write(data)
        pending += data
        *lines, pending = pending.split(b'\n')
        for raw in lines:
            line = raw.decode(errors='replace')
            if '[apklayer]' in line or '[hello-input]' in line:
                seen.append(line)
                print(line, flush=True)
    return next(l for l in seen[after:] if what in l)


def fields(line):
    return {k: v for k, v in re.findall(r'(\w+)=(\S+)', line)}


def close(got, want, what, eps=1e-3):
    assert abs(float(got) - want) < eps, '%s: %s, expected %s' % (what, got, want)


def wheel(step, v, h, shift=False):
    n = len(seen)
    instance.inject(PKG, step)
    f = fields(expect('[hello-input] wheel', n))
    close(f['v'], v, step + ' v')
    close(f['h'], h, step + ' h')
    assert f['shift'] == str(shift).lower() and f['source'] == '0x2002', (step, f)  # SOURCE_MOUSE


def pen(step, action, tool=2, **want):
    n = len(seen)
    instance.inject(PKG, step)
    f = fields(expect('[hello-input] pen ACTION_' + action + ' ', n))
    assert f['tool'] == str(tool) and f['source'] == '0x4002', (step, f)  # SOURCE_STYLUS
    for key, value in want.items(): close(f[key], value, step + ' ' + key)
    return f


try:
    expect('[apklayer] text input on, type 0x')
    # a wheel notch: Wayland + = down, Android AXIS_VSCROLL - = down
    wheel('wheel:0,1', -1, 0)
    wheel('wheel:-2,0', 0, -2)
    wheel('wheel:1,3', -3, 1)
    wheel('wheel:0,60,hires', -0.5, 0)        # a hi-res wheel: 60/120 of a notch
    wheel('wheel:30,-1,hires', 0.0083, 0.25)  # fractions of both axes in one frame
    wheel('wheel:0,-5,finger', 0.3333, 0)     # a touchpad's continuous scroll, 15 units a notch
    wheel('wheel:0,1,shift', -1, 0, shift=True)  # Shift+wheel: the meta state, the view (HorizontalScrollView) decides
    # a pen: hover, touch with tilt and rotation, a button held, lifted, the eraser end
    f = pen('pen:100,200,0,0,0,0,0.5', 'HOVER_MOVE', distance=0.5, pressure=0)
    scale = float(f['x']) / 100  # the shell converts surface coordinates by the output's scale (200% on a HiDPI desktop)
    assert scale >= 1 and abs(float(f['y']) - 200 * scale) < 1e-3, 'pen position %s,%s at 100,200 (scale %s)' % (f['x'], f['y'], scale)
    pen('pen:100,200,0.6,30,0,90,0', 'DOWN', pressure=0.6, tilt=math.radians(30), orientation=math.atan2(-math.sin(math.radians(30)), 0),
        rotation=math.pi / 2, buttons=0)
    pen('pen:110,210,0.8,0,45,0,0,1', 'MOVE', pressure=0.8, tilt=math.radians(45), orientation=0, buttons=32)  # BUTTON_STYLUS_PRIMARY
    pen('pen:110,210,0.8,0,45,0,0,2', 'MOVE', buttons=64)  # BUTTON_STYLUS_SECONDARY
    pen('pen:110,210,0,0,0,200,0,0', 'UP', pressure=0, tilt=0, orientation=math.radians(-160), rotation=math.radians(-160), buttons=0)  # no tilt: the rotation
    instance.inject(PKG, 'pen:out')
    pen('pen:120,120,0.3,0,0,0,0,0,1', 'DOWN', tool=4, pressure=0.3)  # TOOL_TYPE_ERASER
    pen('pen:out', 'UP', tool=4)
    print('PASS wheel notches, hi-res and touchpad fractions on both axes, Shift, and stylus pressure, tilt, orientation, rotation, buttons, eraser, hover and touch', flush=True)
finally:
    instance.stop()
    if app.poll() is None: os.killpg(app.pid, signal.SIGKILL)
    app.wait()
    log.close()
    left = instance.mine('[a]pklayer.host.Main|[b]tload|[a]pklayer-shell')
    assert not left, left
