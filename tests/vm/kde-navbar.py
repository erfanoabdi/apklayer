#!/usr/bin/env python3
"""kde-navbar: under server-side decorations (KDE's KWin, a KWin of its own: kwin_wayland --virtual, headless, private socket and D-Bus; the desktop
is not touched) the compositor draws the title bar, which has no Back button, so the shell gives the window the mobile navigation bar (navbar.c,
setting `navbar`): a strip over the bottom of the content, Back only, told to the app as its navigation bar inset. Cases, in one window of
hello-input (Back is counted there, -Dhello.backlog):
  the bar is on (shell log), the content is KWin's client area and the app's inset is the bar (48 logical, at every scale), a click on the Back
  region sends Back, one beside it or above the bar does not; maximize, tiling, fullscreen (no bar, no inset, the click goes to the content),
  scales 150% 200% 300% (the output's); the compositor drops its title bar (client-side: ours, no bar); `navbar off` and back to auto, live.
The shell's own pixels are read from the compositor's screen (spectacle, KWin's ScreenShot2) when it can capture. Needs kwin_wayland, kscreen-doctor,
qdbus6, gdbus, dbus-daemon."""
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
import kwin  # noqa: E402
PKG = 'hello.input'
if not shutil.which('kwin_wayland'): sys.exit('SKIP kwin_wayland is not installed')
run_dir = Path(tempfile.mkdtemp(prefix='apklayer-kwin-', dir=instance.SESSION))  # beside the desktop's socket: the stack's processes see the session dir
os.chmod(run_dir, 0o700)
host = dict(os.environ, XDG_RUNTIME_DIR=str(run_dir))
for k in ('DISPLAY', 'WAYLAND_DISPLAY'): host.pop(k, None)
for k in ('XDG_CONFIG_HOME', 'XDG_DATA_HOME', 'XDG_CACHE_HOME', 'XDG_STATE_HOME'): host[k] = str(run_dir / k.lower())  # KWin remembers outputs' scale and windows' state: every run starts clean
bus = subprocess.run(['dbus-daemon', '--session', '--fork', '--print-address=1', '--print-pid=1'], env=host, capture_output=True, text=True, check=True).stdout.split()
host.update(DBUS_SESSION_BUS_ADDRESS=bus[0], WAYLAND_DISPLAY=str(run_dir / 'kdenest'))
host["KWIN_SCREENSHOT_NO_PERMISSION_CHECKS"] = "1"  # spectacle may capture this KWin (5.27 refuses a process its desktop file does not name)
os.environ.update(DBUS_SESSION_BUS_ADDRESS=bus[0])  # kwin.py's qdbus calls
kwin.LOG, kwin.ENV = run_dir / 'kwin.log', dict(host, QT_QPA_PLATFORM='wayland')  # this KWin's log and kscreen-doctor environment
TICK = 3500
NAV = 48  # logical pixels (navbar.h: Android's 48 dp at the density 160 x scale)
ENV = dict(instance.ENV, WAYLAND_DISPLAY=host['WAYLAND_DISPLAY'])
results = []
back_count = [0]  # "back pressed" lines of the app so far
steps = {}


def case(name, ok, detail=''):
    results.append((name, ok))
    print('%s %s %s' % ('OK  ' if ok else 'FAIL', name, detail), flush=True)


SIZE = re.compile(r'configure content (\d+)x(\d+) @(\d+)% .*?title bar (\d+) logical = (\d+) px(, maximized|, tiled)?.*?navigation bar (\d+) logical = (\d+) px')


def cfg():
    """The shell's last CONFIGURE of the app: dict w, h, scale, bar, state ('', maximized, tiled), nav (logical), nav_px."""
    text = (instance.STATE / 'shell.log').read_text(errors='replace')
    last = [m for m in SIZE.finditer(text) if PKG in text[:m.start()].rsplit('\n', 1)[-1]][-1]
    g = last.groups()
    return dict(w=int(g[0]), h=int(g[1]), scale=int(g[2]), bar=int(g[3]), state=(g[5] or '').strip(', '), nav=int(g[6]), nav_px=int(g[7]))


def shell_log():
    return (instance.STATE / 'shell.log').read_text(errors='replace')


def kw():
    return kwin.info(PKG, instance.OUT)


def shot(name):
    """The virtual output as KWin composes it (spectacle, KWin's ScreenShot2): a PIL image in output pixels, None when the capture is not possible."""
    from PIL import Image
    path = instance.OUT / (name + '.png')
    path.unlink(missing_ok=True)
    out = subprocess.run(['spectacle', '-b', '-n', '-f', '-o', str(path)], env=dict(host, QT_QPA_PLATFORM='wayland'), capture_output=True, text=True, timeout=30)
    if not path.exists():
        print('no screenshot: %s' % (out.stderr[-300:] or out.stdout[-300:]), flush=True)
        return None
    print('CAPTURED %s' % path, flush=True)
    return Image.open(path).convert('RGB')


def pixels(name, check_bar=True):
    """The window's bottom strip in a screenshot: (bar background seen at 3/4 of the width in the bar's middle, a darker icon pixel in Back's region, the content pixel above the bar).
    Positions: KWin's client area (logical) times the output's scale."""
    info, c = kw(), cfg()
    img = shot(name)
    if img is None: return None
    f = img.width / info['outputGeometry'][2]
    x, y, w, h = info['client']
    px = lambda lx, ly: img.getpixel((min(img.width - 1, int((x + lx) * f)), min(img.height - 1, int((y + ly) * f))))
    bar = px(w * 3 // 4, h - NAV // 2)
    above = px(w * 3 // 4, h - NAV - 6)
    cx, cy = w // 4, h - NAV // 2
    icon = max((px(cx + dx, cy + dy) for dx in range(-14, 15) for dy in range(-14, 15)), key=lambda p: abs(sum(p) - sum(bar)))  # the triangle's stroke: the pixel farthest from the bar's colour
    return bar, icon, above


def backs():
    return back_count[0]


def click_back_region(c):
    """A click on the bar's Back button (a quarter of the width, the bar's middle) as the pointer; returns whether the app counted a Back."""
    before = backs()
    instance.inject(PKG, 'click:%d,%d' % (c['w'] // 4, c['h'] - NAV // 2))
    return before


def pixel_case(name, shot_name, want_bar=True):
    """The picture KWin composes: the bar's colour fills the bottom NAV px at 3/4 of the width, Back's triangle is darker (lighter in the dark scheme) in its
    region, and the content above is not the bar's colour; without a bar (fullscreen) the bottom is not the bar's colour."""
    p = pixels(shot_name)
    if p is None: return case(name + ' (screenshot)', False, 'no capture of the virtual output')
    bar, icon, above = p
    is_bar = bar in ((244, 244, 244), (0, 0, 0))
    if want_bar: ok = is_bar and abs(sum(icon) - sum(bar)) > 150 and above != bar
    else: ok = not is_bar
    case(name, ok, 'bottom %s, Back region extreme %s, above the bar %s' % (bar, icon, above))


def title_case(name):
    """KWin's own title bar (Breeze) in the picture it composes: bar rows above the client area, filled with a colour that is neither the backdrop nor the app's content."""
    info = kw()
    img = shot(name)
    if img is None: return case(name + ' (screenshot)', False, 'no capture of the virtual output')
    f = img.width / info['outputGeometry'][2]
    fx, fy, fw, fh = info['frame']
    bar = info['client'][1] - fy
    px = lambda lx, ly: img.getpixel((min(img.width - 1, int(lx * f)), min(img.height - 1, int(ly * f))))
    title, content, outside = px(fx + fw // 4, fy + bar // 2), px(fx + fw // 4, info['client'][1] + bar), px(fx - 20, fy + bar // 2)
    case(name, 15 <= bar <= 60 and title not in (outside, content) and sum(title) > 600, 'title bar %d logical, its colour %s, the content below %s, the backdrop %s' % (bar, title, content, outside))


def check_start():
    info, c = kw(), cfg()
    log = shell_log()
    case('server-side decorations: the shell has no title bar of its own', 'hello.input: server-side decorations' in log and c['bar'] == 0, 'bar %s' % c['bar'])
    case('the navigation bar is on under them', 'navigation bar on' in log, '')
    case('content = KWin client area (the bar is over its bottom)', info['client'][2:] == [c['w'], c['h']], 'KWin %s, shell %dx%d' % (info['client'], c['w'], c['h']))
    case('the app is told the bar as its inset: %d logical = %d px @%d%%' % (NAV, NAV * c['scale'] // 100, c['scale']), c['nav'] == NAV and c['nav_px'] == (NAV * c['scale'] + 50) // 100, str(c))
    steps['float'] = c
    pixel_case('the bar is drawn: its colour at the bottom, the Back triangle in its region', 'start')
    title_case('the title bar is KWin\'s: a light bar above the content, not our own')
    steps['back_before'] = click_back_region(c)  # Back


def check_clicked():
    case('a click on Back sends KEYCODE_BACK to the app', backs() == steps['back_before'] + 1, 'Back count %d -> %d' % (steps['back_before'], backs()))
    c = cfg()
    steps['back_before'] = backs()
    instance.inject(PKG, 'click:%d,%d' % (c['w'] * 3 // 4, c['h'] - NAV // 2), 'click:%d,%d' % (c['w'] // 4, c['h'] - NAV - 8))  # beside Back, above the bar
    steps['ignored'] = 1


def check_ignored():
    case('a click beside Back or above the bar is not Back', backs() == steps['back_before'], 'Back count %d -> %d' % (steps['back_before'], backs()))


def check_maximized():
    info, c = kw(), cfg()
    pixel_case('maximized: the bar at the bottom of the maximized window', 'maximized')
    case('maximize: the bar stays, content = KWin client area', info['maximize'] == 3 and c['state'] == 'maximized' and c['nav'] == NAV and info['client'][2:] == [c['w'], c['h']],
         'KWin %s mode %s, shell %s' % (info['client'], info['maximize'], c))
    steps['back_before'] = click_back_region(c)


def check_max_clicked():
    case('maximized: Back at the bottom of the window', backs() == steps['back_before'] + 1, 'Back count %d -> %d' % (steps['back_before'], backs()))


def check_restored():
    info, c = kw(), cfg()
    case('unmaximize: floating size and the bar back', info['maximize'] == 0 and c['state'] == '' and c['nav'] == NAV and c[('w')] == steps['float']['w'] and c['h'] == steps['float']['h'], 'shell %s, was %s' % (c, steps['float']))


def check_fullscreen():
    info, c = kw(), cfg()
    case('fullscreen: the whole output, no bar, no inset', info['fullScreen'] and [c['w'], c['h']] == info['outputGeometry'][2:] and c['nav_px'] == 0, 'KWin client %s, shell %s' % (info['client'], c))
    pixel_case('fullscreen: no bar in the picture', 'fullscreen', want_bar=False)
    steps['back_before'] = backs()
    instance.inject(PKG, 'click:%d,%d' % (c['w'] // 4, c['h'] - NAV // 2))  # no bar there: the content takes it


def check_fullscreen_click():
    case('fullscreen: the bottom of the window is the content, not Back', backs() == steps['back_before'], 'Back count %d -> %d' % (steps['back_before'], backs()))


def check_unfullscreen():
    info, c = kw(), cfg()
    case('leave fullscreen: the bar and the inset are back', not info['fullScreen'] and c['nav'] == NAV and c['w'] == steps['float']['w'] and c['h'] == steps['float']['h'], 'shell %s' % c)


def check_tiled():
    info, c = kw(), cfg()
    area = info['outputGeometry'][2]
    case('quick tile left: tiled, half the width, the bar over the content bottom', info['tiled'] and c['state'] == 'tiled' and c['nav'] == NAV and info['client'][2:] == [c['w'], c['h']] and abs(c['w'] * 2 - area) <= area // 10,
         'KWin %s, shell %s' % (info['client'], c))
    steps['back_before'] = click_back_region(c)


def check_tiled_clicked():
    case('tiled: Back works', backs() == steps['back_before'] + 1, 'Back count %d -> %d' % (steps['back_before'], backs()))


def scale_step(percent):
    def check():
        c = cfg()
        case('scale %d%%: the bar is %d logical = %d px, the inset follows' % (percent, NAV, (NAV * percent + 50) // 100), c['scale'] == percent and c['nav'] == NAV and c['nav_px'] == (NAV * percent + 50) // 100, str(c))
        if percent in (200, 300): pixel_case('scale %d%%: the bar in the picture' % percent, 'scale%d' % percent)
        steps['back_before'] = click_back_region(c)
    check.__name__ = 'check_scale_%d' % percent
    return check


def check_scaled_clicked():
    case('Back works at that scale', backs() == steps['back_before'] + 1, 'Back count %d -> %d' % (steps['back_before'], backs()))


def scheme_step(name, want):
    """`settings set night`: the bar's colours in the picture KWin composes are the scheme's (light #f4f4f4 with dark icons, dark black with light icons), live."""
    def check():
        p = pixels('scheme-' + name)
        if p is None: return case('%s scheme: the bar in the picture (screenshot)' % name, False, 'no capture of the virtual output')
        bar, icon, above = p
        light = want[0] > 128
        case('%s scheme: the bar is %s, the Back icon %s' % (name, want, 'darker' if light else 'lighter'), bar == want and (sum(icon) < sum(bar) - 150 if light else sum(icon) > sum(bar) + 150),
             'bar %s, icon %s, above %s; shell: %s' % (bar, icon, above, [l.split('] ', 1)[-1] for l in shell_log().splitlines() if 'navigation bar' in l][-1:]))
    check.__name__ = 'check_scheme_' + name
    return check


def check_no_border():
    info, c = kw(), cfg()
    csd = c['bar'] == 46
    case('KWin drops its title bar (client-side): ours returns, no navigation bar', info['noBorder'] and (csd and c['nav'] == 0 or c['bar'] == 0), 'shell %s' % c)


def check_border():
    info, c = kw(), cfg()
    case('the compositor draws the title bar again: ours is gone, the bar is back', not info['noBorder'] and c['bar'] == 0 and c['nav'] == NAV, 'shell %s' % c)


def settings(*args):
    return subprocess.run(['bin/apklayer', 'settings', *args], env=ENV, capture_output=True, text=True, timeout=30)


def check_off():
    c = cfg()
    case('navbar off, live: no bar, no inset', 'navigation bar off' in shell_log() and c['nav'] == 0 and c['nav_px'] == 0 and c['bar'] == 0, 'shell %s' % c)
    steps['back_before'] = backs()
    instance.inject(PKG, 'click:%d,%d' % (c['w'] // 4, c['h'] - NAV // 2))


def check_off_click():
    case('navbar off: the bottom of the window is the content', backs() == steps['back_before'], 'Back count %d -> %d' % (steps['back_before'], backs()))


def check_auto():
    c = cfg()
    case('navbar auto again: the bar and the inset are back', c['nav'] == NAV and shell_log().rstrip().count('navigation bar on') >= 2, 'shell %s' % c)


def check_values():
    case('settings: on and off are accepted, other values refused', settings('set', 'navbar', 'on').returncode == 0 and settings('set', 'navbar', 'maybe').returncode != 0, '')
    settings('unset', 'navbar')


SEQUENCE = [  # (what happens at this tick, the check at the next one)
    ('', check_start),
    ('', check_clicked),
    ('', check_ignored),
    ('w.setMaximize(true, true);', check_maximized),
    ('', check_max_clicked),
    ('w.setMaximize(false, false);', check_restored),
    ('w.fullScreen = true;', check_fullscreen),
    ('', check_fullscreen_click),
    ('w.fullScreen = false;', check_unfullscreen),
    ('workspace.slotWindowQuickTileLeft();', check_tiled),
    ('', check_tiled_clicked),
    ('untile(w);', lambda: None),
    ('w.setMaximize(false, false);', lambda: None),
    ('scale:1.5', scale_step(150)),
    ('', check_scaled_clicked),
    ('scale:2', scale_step(200)),
    ('', check_scaled_clicked),
    ('scale:3', scale_step(300)),
    ('', check_scaled_clicked),
    ('scale:1', lambda: None),
    ('set:night:no', scheme_step('light', (244, 244, 244))),
    ('set:night:yes', scheme_step('dark', (0, 0, 0))),
    ('unset:night', lambda: None),
    ('w.noBorder = true;', check_no_border),
    ('w.noBorder = false;', check_border),
    ('set:navbar:off', check_off),
    ('', check_off_click),
    ('unset:navbar', check_auto),
    ('', check_values),
]
ticks = [(5000 + TICK * i, 'hover:%d,0' % (i + 1)) for i in range(len(SEQUENCE) + 1)]
ENV['APKLAYER_JOPTS'] = '-Dhello.backlog=1 -Dapklayer.runms=%d -Dapklayer.script=' % (ticks[-1][0] + 4000) + '+'.join('%d:%s' % s for s in ticks)
ENV['APKLAYER_DEBUG_INPUT'] = '1'


def do(action):
    if action.startswith('scale:'):
        out = subprocess.run(['kscreen-doctor', 'output.Virtual-0.scale.' + action[6:]], env=kwin.ENV, capture_output=True, text=True)
        assert out.returncode == 0, 'kscreen-doctor: ' + out.stdout[-300:] + out.stderr[-300:]
    elif action.startswith('set:') or action.startswith('unset:'):
        assert settings(*action.split(':')).returncode == 0, action
    elif action:
        kwin.act(PKG, action, instance.OUT)


def startup_off():
    """`navbar off` set before the app starts: the window is created without the bar (the display HAL's first message carries the setting)."""
    settings('set', 'navbar', 'off')
    env = dict(ENV, APKLAYER_JOPTS='-Dhello.backlog=1 -Dapklayer.runms=9000 -Dapklayer.script=5000:hover:1,0')
    p = subprocess.Popen(['bin/apklayer', 'run', PKG], env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
    sel, seen, got, end = selectors.DefaultSelector(), b'', [], time.monotonic() + 60
    sel.register(p.stdout, selectors.EVENT_READ)
    try:
        while sel.select(30) and time.monotonic() < end:
            data = os.read(p.stdout.fileno(), 65536)
            if not data: break
            seen += data
            if b'script: hover:1,0' in seen and not got:
                got.append(cfg())
                instance.inject(PKG, 'click:%d,%d' % (got[0]['w'] // 4, got[0]['h'] - NAV // 2))
        c = got[0] if got else None
        case('navbar off before the launch: the window starts without the bar and the inset', bool(c) and c['nav'] == 0 and c['nav_px'] == 0 and seen.count(b'back pressed') == 0,
             'first configure %s, Backs %d; shell: %s' % (c, seen.count(b'back pressed'), [l for l in shell_log().splitlines() if 'navigation bar' in l or 'decorations' in l or ' style' in l][:6]))
    finally:
        settings('unset', 'navbar')
        instance.stop()
        if p.poll() is None: os.killpg(p.pid, signal.SIGKILL)
        (instance.STATE / 'shell.log').unlink(missing_ok=True)


instance.stop()
(instance.STATE / 'shell.log').unlink(missing_ok=True)
kwp = subprocess.Popen(['kwin_wayland', '--virtual', '--socket', 'kdenest', '--width', '1280', '--height', '800', '--no-lockscreen'], env=host,
                       stdin=subprocess.DEVNULL, stdout=open(run_dir / 'kwin.log', 'wb'), stderr=subprocess.STDOUT, start_new_session=True)
app = None
try:
    assert subprocess.run(['gdbus', 'wait', '--session', '--timeout', '20', 'org.kde.KWin'], env=host).returncode == 0, 'KWin did not start: ' + (run_dir / 'kwin.log').read_text()[-800:]
    startup_off()
    app = subprocess.Popen(['bin/apklayer', 'run', PKG], env=ENV, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
    selector = selectors.DefaultSelector()
    selector.register(app.stdout, selectors.EVENT_READ)
    pending, end = b'', time.monotonic() + 180
    with open(instance.OUT / 'kde-navbar.log', 'wb') as log:
        while selector.select(40) and time.monotonic() < end:
            data = os.read(app.stdout.fileno(), 65536)
            if not data: break
            log.write(data)
            pending += data
            *lines, pending = pending.split(b'\n')
            for raw in lines:
                line = raw.decode(errors='replace')
                if '[hello-input] back pressed' in line: back_count[0] += 1
                m = re.search(r'script: hover:(\d+),0', line)
                if not m: continue
                step = int(m[1])
                if step >= 2:
                    check = SEQUENCE[step - 2][1]
                    try: check()
                    except Exception as e: case(getattr(check, '__name__', 'check') + ' (tick %d)' % step, False, 'error: %r' % (e,))
                if step - 1 < len(SEQUENCE) and SEQUENCE[step - 1][0]:
                    try: do(SEQUENCE[step - 1][0])
                    except Exception as e: case('action ' + SEQUENCE[step - 1][0], False, 'error: %r' % (e,))
    failed = [n for n, ok in results if not ok]
    ok = results and not failed and len(results) >= 25
    print('%s KDE navigation bar: %d cases, %d failed %s' % ('PASS' if ok else 'FAIL', len(results), len(failed), failed), flush=True)
    sys.exit(0 if ok else 1)
finally:
    instance.stop()
    if app and app.poll() is None: os.killpg(app.pid, signal.SIGKILL)
    os.killpg(kwp.pid, signal.SIGTERM)
    kwp.wait()
    os.kill(int(bus[1]), signal.SIGTERM)
    for p in Path('/proc').iterdir():  # what KWin started in its runtime dir (Xwayland, its helpers)
        try:
            if b'XDG_RUNTIME_DIR=%s\0' % str(run_dir).encode() in (p / 'environ').read_bytes() + b'\0': os.kill(int(p.name), signal.SIGTERM)
        except (OSError, ValueError): pass
    shutil.rmtree(run_dir, ignore_errors=True)
