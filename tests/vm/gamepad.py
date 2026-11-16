#!/usr/bin/env python3
"""Game controllers (docs/GAMEPAD.md): a virtual Xbox 360 pad made through /dev/uinput (tests/lib/uinput_pad.py, as root) in a directory of its own
(APKLAYER_INPUT_DIR, so no other instance sees it) is read by the shell (bridge/shell/gamepad.c), mapped by the host (HostGamepad.java) and seen by
hello-gamepad as an Android game sees it: the device list and InputDeviceListener (hot-plug and unplug), SOURCE_GAMEPAD|JOYSTICK|DPAD, the buttons from
Xbox 360's .kl layout, sticks, triggers and the hat, DPAD keys from the hat, and rumble back to the kernel's force feedback."""
import os
from pathlib import Path
import re
import selectors
import shutil
import signal
import subprocess
import sys
import tempfile
import time

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
import instance  # noqa: E402  (changes into the repo root)
PKG = 'hello.gamepad'
SOURCE_GAMEPAD, SOURCE_JOYSTICK, SOURCE_DPAD = 0x401, 0x1000010, 0x201
KEYCODE = {'BUTTON_A': 96, 'BUTTON_B': 97, 'BUTTON_X': 99, 'BUTTON_Y': 100, 'BUTTON_L1': 102, 'BUTTON_R1': 103, 'BUTTON_THUMBL': 106, 'BUTTON_THUMBR': 107,
           'BUTTON_START': 108, 'BUTTON_SELECT': 109, 'BUTTON_MODE': 110, 'DPAD_DOWN': 20, 'DPAD_LEFT': 21}  # android.view.KeyEvent
DIR = tempfile.mkdtemp(prefix='apklayer-gamepad-', dir=instance.ROOT)  # not /tmp: nodev there, a device node in it cannot be opened
ENV = dict(instance.ENV, APKLAYER_DEBUG_INPUT='1', APKLAYER_DISPLAY_WIDTH='480', APKLAYER_DISPLAY_HEIGHT='600', APKLAYER_INPUT_DIR=DIR,
           APKLAYER_JOPTS='-Dapklayer.runms=40000 -Dapklayer.debugkeys=true')


class Stream:
    """The lines of one process that start with a tag, read until one has what a step expects."""

    def __init__(self, proc, tags):
        self.proc, self.tags, self.seen, self.pending = proc, tags, [], b''
        self.selector = selectors.DefaultSelector()
        self.selector.register(proc.stdout, selectors.EVENT_READ)

    def expect(self, what, after=0):
        end = time.monotonic() + 90
        while not any(what in l for l in self.seen[after:]):
            assert self.selector.select(30) and time.monotonic() < end, 'no "%s"; last lines: %s' % (what, self.seen[-6:])
            data = os.read(self.proc.stdout.fileno(), 65536)
            assert data, 'the process ended without "%s"; last lines: %s' % (what, self.seen[-6:])
            self.pending += data
            *lines, self.pending = self.pending.split(b'\n')
            for raw in lines:
                line = raw.decode(errors='replace')
                if any(t in line for t in self.tags):
                    self.seen.append(line)
                    print(line, flush=True)
        return next(l for l in self.seen[after:] if what in l)


def fields(line):
    return dict(re.findall(r'(\w+)=(\S+)', line))


instance.stop()
app = subprocess.Popen(['bin/apklayer', 'run', PKG], env=ENV, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
helper = subprocess.Popen(['sudo', '-n', 'python3', 'tests/lib/uinput_pad.py', DIR], stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=False, start_new_session=True)
out, ff = Stream(app, ['[hello-gamepad]', '[apklayer] gamepad']), Stream(helper, ['FF ', 'created', 'destroyed', 'Error', 'Traceback'])


def pad(*lines):
    helper.stdin.write(''.join(l + '\n' for l in lines).encode())
    helper.stdin.flush()


def press(code, expect_key, source=SOURCE_GAMEPAD):
    n = len(out.seen)
    pad('key %d 1' % code, 'sync')
    line = out.expect('key ACTION_DOWN %d ' % KEYCODE[expect_key], n)
    assert fields(line)['source'] == hex(source), line
    n = len(out.seen)
    pad('key %d 0' % code, 'sync')
    out.expect('key ACTION_UP %d ' % KEYCODE[expect_key], n)


def motion(step, **want):
    n = len(out.seen)
    pad(*step, 'sync')
    f = fields(out.expect('[hello-gamepad] motion ', n))
    for key, value in want.items():
        assert abs(float(f[key]) - value) < 2e-3, '%s: %s expected %s in %s' % (key, f[key], value, f)
    assert f['source'] == hex(SOURCE_JOYSTICK)


try:
    out.expect('[hello-gamepad] ready')
    instance.inject(PKG, 'focus')
    # hot-plug: the device list and the listener (the shell finds the node by inotify, the host announces it to InputManager)
    pad('create Xbox_360_Test_Pad 045e 028e')
    ff.expect('created')
    line = out.expect('added id=')
    f = fields(line)
    assert f['name'] == 'Xbox_360_Test_Pad' and f['vendor'] == '45e' and f['product'] == '28e' and f['external'] == 'true', line
    assert int(f['sources'], 16) == SOURCE_GAMEPAD | SOURCE_JOYSTICK | SOURCE_DPAD and f['controller'] == '1' and f['vibrators'] == '1', line
    for rng in ('AXIS_X[-1.0,1.0,', 'AXIS_Y[-1.0,1.0,', 'AXIS_Z[-1.0,1.0,', 'AXIS_RZ[-1.0,1.0,', 'AXIS_LTRIGGER[0.0,1.0,', 'AXIS_RTRIGGER[0.0,1.0,', 'AXIS_HAT_X[-1.0,1.0,', 'AXIS_HAT_Y[-1.0,1.0,'):
        assert rng in line, '%s not in %s' % (rng, line)
    # the buttons of Vendor_045e_Product_028e.kl
    for code, key in ((304, 'BUTTON_A'), (305, 'BUTTON_B'), (307, 'BUTTON_X'), (308, 'BUTTON_Y'), (310, 'BUTTON_L1'),
                      (311, 'BUTTON_R1'), (314, 'BUTTON_SELECT'), (315, 'BUTTON_START'), (316, 'BUTTON_MODE'),
                      (317, 'BUTTON_THUMBL'), (318, 'BUTTON_THUMBR')):
        if key == 'BUTTON_A' or key == 'BUTTON_B': continue  # they rumble, below
        press(code, key)
    # sticks -1..1 over the device's range (ABS_X/Y: AXIS_X/Y; ABS_RX/RY: AXIS_Z/RZ), triggers 0..1 (ABS_Z/RZ), the hat as it is
    motion(['abs 0 16384'], x=0.5, y=0, z=0)
    motion(['abs 1 -32768', 'abs 3 32767'], x=0.5, y=-1, z=1)
    motion(['abs 4 -16384'], rz=-0.5, z=1)
    motion(['abs 2 255'], lt=1, rt=0)
    motion(['abs 5 128'], rt=0.502)
    motion(['abs 0 0', 'abs 1 0', 'abs 3 0', 'abs 4 0', 'abs 2 0', 'abs 5 0'], x=0, y=0, z=0, rz=0, lt=0, rt=0)
    # the hat: AXIS_HAT_X/Y, and the window's DPAD keys from it (ViewRootImpl's SyntheticJoystickHandler, as on Android)
    n = len(out.seen)
    motion(['abs 16 -1'], hx=-1, hy=0)
    out.expect('key ACTION_DOWN %d ' % KEYCODE['DPAD_LEFT'], n)
    motion(['abs 16 0', 'abs 17 1'], hx=0, hy=1)
    out.expect('key ACTION_DOWN %d ' % KEYCODE['DPAD_DOWN'], n)
    motion(['abs 17 0'], hy=0)
    # rumble: A in the app asks for 300 ms at amplitude 128/255 of the vibrator manager; the shell uploads and plays it on the kernel's FF
    n, m = len(out.seen), len(ff.seen)
    pad('key 304 1', 'sync')
    out.expect('key ACTION_DOWN %d ' % KEYCODE['BUTTON_A'], n)
    out.expect('rumble requested', n)
    play = fields(ff.expect('FF play', m))
    assert abs(int(play['strong']) - 128 * 257) < 300 and play['strong'] == play['weak'] and play['ms'] == '300', play
    pad('key 304 0', 'sync')
    n, m = len(out.seen), len(ff.seen)
    pad('key 305 1', 'sync')
    out.expect('rumble cancelled', n)
    ff.expect('FF stop', m)
    pad('key 305 0', 'sync')
    # hot-unplug: the listener, and the device is gone from the list
    n = len(out.seen)
    pad('destroy')
    ff.expect('destroyed')
    assert 'device=null' in out.expect('removed id=', n)
    n = len(out.seen)
    pad('create Xbox_360_Test_Pad 045e 028e')
    again = fields(out.expect('added id=', n))
    assert again['id'] != f['id'] and again['controller'] == '1', again
    print('PASS gamepad hot-plug and unplug, InputDevice sources/ranges, 9 buttons from the Xbox 360 layout, sticks, triggers, hat and DPAD keys, rumble through force feedback', flush=True)
finally:
    try:
        pad('quit')
        helper.stdin.close()
        helper.wait(10)
    except Exception:
        pass
    instance.stop()
    if app.poll() is None: os.killpg(app.pid, signal.SIGKILL)
    app.wait()
    if helper.poll() is None: subprocess.run(['sudo', '-n', 'kill', '-9', str(helper.pid)])  # root's: the sudo wrapper and its python
    helper.wait()
    shutil.rmtree(DIR, ignore_errors=True)
    left = instance.mine('[a]pklayer.host.Main|[b]tload|[a]pklayer-shell|[u]input_pad')
    assert not left, left
