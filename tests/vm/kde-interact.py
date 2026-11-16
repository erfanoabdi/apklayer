#!/usr/bin/env python3
"""kde-interact: real pointer and keyboard (uinput, as a person's mouse and keys) on the desktop session's KWin move, resize and snap a real app's window
(UiBench) and the shell's CONFIGUREs must follow KWin's geometry each time: a drag of the title bar, the right edge, the bottom edge and the bottom-right
corner, Alt+F8 (keyboard resize), a drag to the left screen edge (quick tile) and out of it, a drag to the top edge (maximize) and back down, F11 and again.
The pointer is an absolute device over the whole screen, positioned in the device's own steps (a pixel anywhere). KWin's edge snapping needs its defaults.
Real input goes to the focused window of the session: the group runs alone; a locked screen is unlocked only with APKLY_KDE_UNLOCK=1 (a test machine). Needs the KDE session, sudo -n and gcc on the host; tests/lib/kwin.py."""
import os
from pathlib import Path
import shutil
import signal
import subprocess
import sys
import time

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
import instance  # noqa: E402  (changes into the repo root)
import kwin  # noqa: E402
PKG = 'com.android.test.uibench'
os.environ.update(DBUS_SESSION_BUS_ADDRESS=instance.ENV['DBUS_SESSION_BUS_ADDRESS'])
results = []
KEY_F8, KEY_F11, KEY_ENTER, KEY_RIGHT, KEY_DOWN = 66, 87, 28, 106, 108


def case(name, ok, detail=''):
    results.append((name, ok))
    print('%s %s %s' % ('OK  ' if ok else 'FAIL', name, detail), flush=True)


def state(done=lambda i, c: True, seconds=8):
    """KWin's window and the shell's last configure once `done(info, configure)` holds (polled, so the compositor and the shell have settled), else the last seen."""
    end = time.monotonic() + seconds
    while True:
        info, c = kwin.info(PKG, instance.OUT), kwin.configure(instance.STATE / 'shell.log')
        if (info and c and done(info, c)) or time.monotonic() > end: return info, c
        time.sleep(0.3)


def matches(i, c):
    """The shell's content size is KWin's client size (a logical pixel of rounding at a fractional scale)."""
    return abs(i['client'][2] - c[0]) <= 1 and abs(i['client'][3] - c[1]) <= 1


def keys(*steps):
    """uinput steps (tests/vm/uinput-keys.c) with the pointer in the device's own steps."""
    instance.keys('res:32768', *steps)


def at(info, x, y):
    """Logical pixel (x, y) of the output as the pointer device's coordinates (the middle of that pixel)."""
    w, h = info['outputGeometry'][2:]
    return int((x + 0.5) * 32768 / w), int((y + 0.5) * 32768 / h)


def drag(info, x1, y1, x2, y2):
    keys('drag:%d,%d,%d,%d' % (*at(info, x1, y1), *at(info, x2, y2)))


def place(info):
    """The window at a known place and size, set by KWin (a script)."""
    kwin.act(PKG, 'w.frameGeometry = {x: 300, y: 200, width: 700, height: 500};', instance.OUT)
    return state(lambda i, c: i['frame'] == [300, 200, 700, 500] and matches(i, c))


def check_size(name, info, c, grew, detail=''):
    case(name, matches(info, c) and grew, '%s KWin frame %s client %s, shell %s' % (detail, info['frame'], info['client'], c))


def kconfig(tool, *args):
    exe = next((t for t in ('%s6' % tool, '%s5' % tool) if shutil.which(t)), tool + '5')
    return subprocess.run([exe, '--file', 'kscreenlockerrc', '--group', 'Daemon', '--key', 'Autolock', *args], capture_output=True, text=True).stdout.strip()


def screen_locked():
    return subprocess.run([kwin.QDBUS, 'org.freedesktop.ScreenSaver', '/ScreenSaver', 'org.freedesktop.ScreenSaver.GetActive'], env=kwin._env(), capture_output=True, text=True).stdout.strip() == 'true'


# the locked screen takes every pointer and key event. A test machine's session may be unlocked by the test (APKLY_KDE_UNLOCK=1: logind unlocks the user's
# sessions, so never on a desktop somebody uses); no idle lock while the test runs, the setting as it was afterwards
if screen_locked():
    if os.environ.get('APKLY_KDE_UNLOCK') != '1': sys.exit('SKIP the screen is locked (the KDE lock screen takes the input; APKLY_KDE_UNLOCK=1 unlocks it on a test machine)')
    subprocess.run(['sudo', '-n', 'loginctl', 'unlock-sessions'], check=False)
    for _ in range(25):  # the lock screen goes away a moment after logind's request
        if not screen_locked(): break
        time.sleep(0.2)
    assert not screen_locked(), 'the screen is still locked after loginctl unlock-sessions'
autolock = kconfig('kreadconfig')  # '' = not set
kconfig('kwriteconfig', 'false')
instance.stop()
(instance.STATE / 'shell.log').unlink(missing_ok=True)
app = subprocess.Popen(['bin/apklayer', 'run', PKG], env=dict(instance.ENV, APKLAYER_JOPTS='-Dapklayer.runms=150000'), stdout=open(instance.OUT / 'kde-interact-app.log', 'wb'), stderr=subprocess.STDOUT, start_new_session=True)
try:
    info, c = state(lambda i, c: i['frame'][2] > 0 and c, 60)
    assert info, 'the window did not appear in KWin'
    out = info['outputGeometry']
    info, c = place(info)
    assert info, 'the window is gone from KWin after being placed (shell: %s; KWin has: %s)' % (kwin.configure(instance.STATE / 'shell.log'),
        kwin.script('var c = workspace.clientList(); for (var i = 0; i < c.length; i++) P(c[i].resourceClass + "|" + c[i].desktopFileName + "|" + c[i].caption + "|" + c[i].frameGeometry.x);', instance.OUT))
    bar = info['client'][1] - info['frame'][1]  # KWin's title bar height, logical
    case('server-side title bar, placed by KWin', not info['noBorder'] and c[3] == 0 and bar > 0 and matches(info, c), 'bar %d, KWin frame %s, shell %s' % (bar, info['frame'], c))

    # move by the title bar
    fx, fy, fw, fh = info['frame']
    drag(info, fx + fw // 2, fy + bar // 2, fx + fw // 2 + 120, fy + bar // 2 + 60)
    i2, c2 = state(lambda i, c: i['frame'][0] != fx and matches(i, c))
    case('title bar drag moves the window, the size stays', abs(i2['frame'][0] - (fx + 120)) <= 3 and abs(i2['frame'][1] - (fy + 60)) <= 3 and i2['frame'][2:] == [fw, fh] and matches(i2, c2),
         'KWin frame %s (was %s), shell %s' % (i2['frame'], info['frame'], c2))
    info, c = place(i2)

    # edges and the corner: the invisible resize handle is just outside the frame, or on its last pixel: the first position that grabs it is kept
    def resize(name, dx, dy, edge_x, edge_y):
        global info, c
        fx, fy, fw, fh = info['frame']
        for off in (3, 1, -1):
            drag(info, fx + edge_x(fw, off), fy + edge_y(fh, off), fx + edge_x(fw, off) + dx, fy + edge_y(fh, off) + dy)
            i2, c2 = state(lambda i, c: i['frame'][2:] != [fw, fh] and matches(i, c), 4)
            if i2['frame'][2:] != [fw, fh]: break
        case(name, i2['frame'][2:] != [fw, fh] and abs(i2['frame'][2] - (fw + dx)) <= 4 and abs(i2['frame'][3] - (fh + dy)) <= 4 and matches(i2, c2),
             'grab %d px off the edge, KWin frame %s (was %s), shell %s' % (off, i2['frame'], info['frame'], c2))
        info, c = place(i2)

    resize('right edge drag: the width follows, the app takes the size', 100, 0, lambda fw, off: fw + off - 1, lambda fh, off: fh // 2)
    resize('bottom edge drag: the height follows', 0, 80, lambda fw, off: fw // 2, lambda fh, off: fh + off - 1)
    resize('bottom-right corner drag: both follow', 90, 70, lambda fw, off: fw + off - 1, lambda fh, off: fh + off - 1)

    # Alt+F8: KWin's keyboard resize (arrows, Enter). Plasma 5.27 binds no key to "Window Resize" by default: then its global shortcut is invoked as the binding would
    fx, fy, fw, fh = info['frame']
    resize_keys = ['sleep:700', *['key:%d' % KEY_RIGHT] * 8, *['key:%d' % KEY_DOWN] * 5, 'key:%d' % KEY_ENTER, 'sleep:300']
    keys('combo:alt+%d' % KEY_F8, *resize_keys)
    i2, c2 = state(lambda i, c: i['frame'][2:] != [fw, fh] and matches(i, c), 4)
    bound = i2['frame'][2:] != [fw, fh]
    if not bound:
        subprocess.run([kwin.QDBUS, 'org.kde.kglobalaccel', '/component/kwin', 'org.kde.kglobalaccel.Component.invokeShortcut', 'Window Resize'], env=kwin._env(), check=True, capture_output=True)
        keys(*resize_keys)
        i2, c2 = state(lambda i, c: i['frame'][2:] != [fw, fh] and matches(i, c))
    check_size('keyboard resize (Window Resize, arrows, Enter) grows the window', i2, c2, i2['frame'][2] > fw and i2['frame'][3] > fh, 'Alt+F8 %s, was %s;' % ('bound' if bound else 'not bound by default', info['frame']))
    info, c = place(i2)

    # snap by dragging: left edge = quick tile left, out of it = the old size back
    fx, fy, fw, fh = info['frame']
    drag(info, fx + fw // 2, fy + bar // 2, 0, out[3] // 2)
    i2, c2 = state(lambda i, c: i['tiled'] and c[4] == 'tiled' and matches(i, c))
    area = out[2]
    case('drag to the left screen edge: tiled left, half the width', i2['tiled'] and c2[4] == 'tiled' and abs(c2[0] * 2 - area) <= area // 10 and i2['client'][0] == 0 and matches(i2, c2),
         'KWin tile %s client %s, shell %s, output width %d' % (i2['tiled'], i2['client'], c2, area))
    tfx, tfy, tfw, tfh = i2['frame']
    drag(i2, tfx + tfw // 2, tfy + bar // 2, area // 2, tfy + bar // 2 + 200)
    i3, c3 = state(lambda i, c: not i['tiled'] and c[4] == '' and matches(i, c))
    case('drag out of the tile: floating again, the size it had', not i3['tiled'] and c3[4] == '' and matches(i3, c3) and abs(i3['frame'][2] - fw) <= 2,
         'KWin frame %s (before tiling %s), shell %s' % (i3['frame'], info['frame'], c3))
    info, c = place(i3)

    # top edge = maximize, dragged down = restore
    fx, fy, fw, fh = info['frame']
    drag(info, fx + fw // 2, fy + bar // 2, out[2] // 2, 0)
    i2, c2 = state(lambda i, c: i['maximize'] == 3 and c[4] == 'maximized' and matches(i, c))
    case('drag to the top screen edge: maximized', i2['maximize'] == 3 and c2[4] == 'maximized' and matches(i2, c2), 'KWin mode %s client %s, shell %s' % (i2['maximize'], i2['client'], c2))
    mfx, mfy, mfw, mfh = i2['frame']
    drag(i2, mfx + mfw // 2, mfy + bar // 2, mfx + mfw // 2, mfy + bar // 2 + 300)
    i3, c3 = state(lambda i, c: i['maximize'] == 0 and c[4] == '' and matches(i, c))
    case('drag down from the maximized title bar: restored to the old size', i3['maximize'] == 0 and c3[4] == '' and matches(i3, c3) and abs(i3['frame'][2] - fw) <= 2,
         'KWin frame %s (before %s), shell %s' % (i3['frame'], info['frame'], c3))
    info, c = place(i3)

    # F11, the real key: the shell makes the window fullscreen, again leaves it
    keys('key:%d' % KEY_F11)
    i2, c2 = state(lambda i, c: i['fullScreen'] and matches(i, c))
    case('F11: fullscreen, the whole output, no bar', i2['fullScreen'] and abs(c2[0] - out[2]) <= 1 and abs(c2[1] - out[3]) <= 1 and c2[3] == 0 and matches(i2, c2),
         'KWin client %s, shell %s, output %s' % (i2['client'], c2, out))
    keys('key:%d' % KEY_F11)
    i3, c3 = state(lambda i, c: not i['fullScreen'] and matches(i, c))
    case('F11 again: back to the floating size', not i3['fullScreen'] and c3[:2] == c[:2] and matches(i3, c3), 'KWin client %s, shell %s, was %s' % (i3['client'], c3, c))
    failed = [n for n, ok in results if not ok]
    print('%s KDE interactive: %d cases, %d failed %s' % ('FAIL' if failed or not results else 'PASS', len(results), len(failed), failed), flush=True)
    sys.exit(1 if failed or not results else 0)
finally:
    kconfig('kwriteconfig', autolock) if autolock else kconfig('kwriteconfig', '--delete')
    instance.stop()
    if app.poll() is None: os.killpg(app.pid, signal.SIGKILL)
    app.wait()
