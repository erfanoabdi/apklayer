#!/usr/bin/env python3
"""kde-window: KDE Plasma (KWin 6) drives a real app's window (UiBench) through a KWin script, and the shell's CONFIGUREs and KWin's own view of the
window must agree: server-side title bar (the shell's bar is 0), the content size the app got = KWin's client geometry, maximize, restore,
fullscreen (the whole output, 0 bar), quick tile (tiled state, half the work area), untile, minimize, the compositor's close. The compositor's
events are real here; window-geometry injects them (no desktop). Cases are reported one by one: a failed one does not stop the rest.
Needs a KWin session (qdbus6, journalctl --user); tests/lib/kwin.py."""
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
import kwin  # noqa: E402
PKG = 'com.android.test.uibench'
ENV = dict(instance.ENV)
TICK = 3500
DECOR_H = 46  # bridge/shell/decor.h
results = []


def configure():
    return kwin.configure(instance.STATE / 'shell.log')


def case(name, ok, detail=''):
    results.append((name, ok))
    print('%s %s %s' % ('OK  ' if ok else 'FAIL', name, detail), flush=True)


def window():
    return kwin.info(PKG, instance.OUT)


def close(a, b):
    """KWin's geometry is fractional at a fractional scale (150%: 1280.67 for the shell's 1281): within one logical pixel."""
    return all(abs(x - y) <= 1 for x, y in zip(a, b))


def same_size(info, c):
    return close(info['client'][2:], c[:2])


def at_start():
    info, c = window(), configure()
    case('server-side title bar', bool(info) and not info['noBorder'] and c and c[3] == 0, '%s' % ({k: info[k] for k in ('noBorder', 'frame', 'client', 'scale')} if info else info))
    case('content = KWin client geometry', bool(info and c) and same_size(info, c), 'KWin %s, shell %s' % (info and info['client'], c))
    case('scale = output scale', bool(info and c) and c[2] == int(info['scale'] * 100), 'KWin %s, shell @%s%%' % (info and info['scale'], c and c[2]))
    case('minimum size', bool(info) and info['min'][0] >= 160, 'min %s max %s' % (info and info['min'], info and info['max']))
    steps['float'] = c


def check_maximized():
    info, c = window(), configure()
    case('maximize', info['maximize'] == 3 and c[4] == 'maximized' and same_size(info, c), 'KWin mode %s client %s, shell %s' % (info['maximize'], info['client'], c))


def check_restored():
    info, c = window(), configure()
    case('unmaximize: floating size back', info['maximize'] == 0 and c[4] == '' and c[:2] == steps['float'][:2] and same_size(info, c), 'KWin %s, shell %s, was %s' % (info['client'], c, steps['float']))


def check_fullscreen():
    info, c = window(), configure()
    out = info['outputGeometry']
    case('fullscreen: the whole output, no bar', info['fullScreen'] and close(c[:2], out[2:]) and c[3] == 0, 'KWin client %s output %s, shell %s' % (info['client'], out, c))


def check_unfullscreen():
    info, c = window(), configure()
    case('leave fullscreen: floating size back', not info['fullScreen'] and c[:2] == steps['float'][:2] and same_size(info, c), 'KWin %s, shell %s' % (info['client'], c))


def check_tiled():
    info, c = window(), configure()
    area = info['outputGeometry'][2]
    case('quick tile left: tiled state, about half the width', info['tiled'] and c[4] == 'tiled' and same_size(info, c) and abs(c[0] * 2 - area) <= area // 10,
         'KWin tile %s client %s, shell %s, output width %s' % (info['tiled'], info['client'], c, area))


def check_untiled():
    info, c = window(), configure()
    case('untile: floating size back', not info['tiled'] and c[4] == '' and c[:2] == steps['float'][:2], 'KWin %s, shell %s' % (info['client'], c))


def check_resized():
    info, c = window(), configure()
    case('frame set by a script: the app takes the new size', same_size(info, c) and c[:2] != steps['float'][:2] and abs(info['client'][2] - 700) <= 1, 'KWin client %s frame %s, shell %s (asked: frame 700x560)' % (info['client'], info['frame'], c))
    steps['resized'] = c


def check_tiled_right():
    info, c = window(), configure()
    area = info['outputGeometry'][2]
    case('quick tile right', info['tiled'] and c[4] == 'tiled' and same_size(info, c) and info['client'][0] > 0 and abs(c[0] * 2 - area) <= area // 10,
         'KWin client %s, shell %s' % (info['client'], c))


def check_untiled_after_resize():
    info, c = window(), configure()
    case('untile: the size it had before tiling', not info['tiled'] and c[4] == '' and same_size(info, c) and c[:2] == steps['resized'][:2], 'KWin %s, shell %s, before %s' % (info['client'], c, steps['resized']))


def check_no_border():
    info, c = window(), configure()
    csd = c[3] == DECOR_H
    case('no border: server bar gone, ours (46) back if KWin says client-side', info['noBorder'] and (csd or c[3] == 0), 'KWin noBorder %s, shell bar %s' % (info['noBorder'], c[3]))


def check_border():
    info, c = window(), configure()
    case('border again: the compositor draws the title bar, ours is gone', not info['noBorder'] and c[3] == 0 and same_size(info, c), 'KWin %s, shell %s' % (info['client'], c))


def check_minimized():
    case('minimize', window()['minimized'])


def check_shown():
    info, c = window(), configure()
    case('minimize, then show again', not info['minimized'] and same_size(info, c), 'KWin %s, shell %s' % (info['client'], c))


def check_closed():
    log = (instance.STATE / 'shell.log').read_text(errors='replace')
    case('close from the compositor reaches the shell', 'closed by the compositor' in log)


steps = {}
SEQUENCE = [  # (what KWin does at this tick, the check at the next one)
    ('', at_start),
    ('w.setMaximize(true, true);', check_maximized),
    ('w.setMaximize(false, false);', check_restored),
    ('w.fullScreen = true;', check_fullscreen),
    ('w.fullScreen = false;', check_unfullscreen),
    ('workspace.slotWindowQuickTileLeft();', check_tiled),
    ('w.tile = null;', check_untiled),
    ('w.frameGeometry = {x: w.frameGeometry.x, y: w.frameGeometry.y, width: 700, height: 560};', check_resized),  # not an interactive resize
    ('workspace.slotWindowQuickTileRight();', check_tiled_right),
    ('w.tile = null;', check_untiled_after_resize),
    ('w.noBorder = true;', check_no_border),  # the decoration goes: KWin may tell the client to draw its own bar
    ('w.noBorder = false;', check_border),
    ('w.minimized = true;', check_minimized),
    ('w.minimized = false;', check_shown),
    ('w.closeWindow();', check_closed),
]
ticks = [(5000 + TICK * i, 'hover:%d,0' % (i + 1)) for i in range(len(SEQUENCE) + 1)]
ENV['APKLAYER_JOPTS'] = '-Dapklayer.runms=%d -Dapklayer.script=' % (ticks[-1][0] + 4000) + '+'.join('%d:%s' % s for s in ticks)
ENV['APKLAYER_DEBUG_INPUT'] = '1'
instance.stop()
(instance.STATE / 'shell.log').unlink(missing_ok=True)
app = subprocess.Popen(['bin/apklayer', 'run', PKG], env=ENV, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
try:
    selector = selectors.DefaultSelector()
    selector.register(app.stdout, selectors.EVENT_READ)
    end = time.monotonic() + 120
    pending, done = b'', set()
    os.environ.update(DBUS_SESSION_BUS_ADDRESS=ENV['DBUS_SESSION_BUS_ADDRESS'])
    with open(instance.OUT / 'kde-window.log', 'wb') as log:
        while True:
            if not selector.select(40) or time.monotonic() > end: break
            data = os.read(app.stdout.fileno(), 65536)
            if not data: break
            log.write(data)
            pending += data
            *lines, pending = pending.split(b'\n')
            for raw in lines:
                m = re.search(r'script: hover:(\d+),0', raw.decode(errors='replace'))
                if not m: continue
                step = int(m[1])
                if step >= 2:
                    check = SEQUENCE[step - 2][1]
                    try: check(); done.add(check.__name__)
                    except Exception as e: case(check.__name__, False, 'error: %r' % (e,))
                if step - 1 < len(SEQUENCE) and SEQUENCE[step - 1][0]:
                    try: kwin.act(PKG, SEQUENCE[step - 1][0], instance.OUT)
                    except Exception as e: case('KWin action ' + SEQUENCE[step - 1][0], False, 'error: %r' % (e,))
    if 'check_closed' not in done: check_closed()  # the window is gone with its app: no later tick
    failed = [n for n, ok in results if not ok]
    print('%s KDE window: %d cases, %d failed %s' % ('FAIL' if failed or not results else 'PASS', len(results), len(failed), failed), flush=True)
    sys.exit(1 if failed or not results else 0)
finally:
    instance.stop()
    if app.poll() is None: os.killpg(app.pid, signal.SIGKILL)
    app.wait()
