#!/usr/bin/env python3
"""Mobile window style and density (bridge/shell/mobile.c, navbar.c), a phone simulated on the VM's compositor: APKLAYER_OUTPUT_MM / _PX tell the
shell the output is a 6.1" 1080x2340 panel (422 dpi -> bucket 420), the window style follows the output (auto). A real app (UiBench):
 1. mobile style: maximized, no title bar, 420 dpi, a navigation bar 48 dp high (126 px) told as the navigation bar inset; the app took
    420 dpi and the bar's height; its picture has the window's pixel size (screenshots in the group's output dir);
 2. the compositor's scale 300%: the density stays the panel's 420 (apps are the size they are on that phone), the bar is 42 logical = 126 px;
 3. `apklayer settings set window-mode desktop`, live: the title bar returns (46 logical), no navigation bar; then `unset`: mobile again;
 4. a tap on the bar's Back button: the shell sends Back, UiBench's root activity finishes and the app ends.
Also `--display-info` for a few simulated panels (density buckets, style): no app."""
import os
from pathlib import Path
import re
import selectors
import signal
import subprocess
import sys
import time

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
import instance  # noqa: E402  (changes into the repo root)
PKG = 'com.android.test.uibench'
ENV = dict(instance.ENV)
ENV.update(APKLAYER_OUTPUT_MM='66x143', APKLAYER_OUTPUT_PX='1080x2340', APKLAYER_DEBUG_INPUT='1')
STEPS = ((6000, 'hover:1,0'), (10000, 'hover:2,0'), (15000, 'hover:3,0'), (21000, 'hover:4,0'), (27000, 'hover:5,0'))
ENV['APKLAYER_JOPTS'] = '-Dapklayer.runms=33000 -Dapklayer.script=' + '+'.join('%d:%s' % s for s in STEPS)
CONFIGURE = re.compile(r'configure content (\d+)x(\d+) @(\d+)% \((\d+)x(\d+) px\), title bar (\d+) logical = (\d+) px(?:, maximized)?, (\w+) style, (\d+) dpi, '
                       r'navigation bar (\d+) logical = (\d+) px')
seen, checks = [], []
SHELL_LOG = instance.STATE / 'shell.log'


def shell_log():
    return SHELL_LOG.read_text(errors='replace') if SHELL_LOG.exists() else ''


def configures():
    """(w, h, scale, bar, style, dpi, nav logical, nav px) of this app's window so far."""
    text = shell_log()
    return [(int(m[1]), int(m[2]), int(m[3]), int(m[6]), m[8], int(m[9]), int(m[10]), int(m[11])) for m in CONFIGURE.finditer(text)
            if PKG in text[:m.start()].rsplit('\n', 1)[-1]]


def apklayer(*args):
    return subprocess.run(['bin/apklayer', *args], env=ENV, capture_output=True, text=True, timeout=30).stdout


def display_info(mm, px, scale_env=None, **extra):
    env = dict(ENV, APKLAYER_OUTPUT_MM=mm, APKLAYER_OUTPUT_PX=px, **extra)
    out = subprocess.run(['b/bridge/shell/apklayer-shell', '--display-info'], env=env, capture_output=True, text=True, timeout=10).stdout
    return int(re.search(r'density (\d+)', out)[1]), int(re.search(r'mobile (\d)', out)[1])


def step(name, fn):
    fn()
    checks.append(name)
    print('OK ' + name, flush=True)


# the buckets of simulated panels, without an app (the shell's --display-info against the VM's compositor)
for mm, px, density in (('66x143', '1080x2340', 420), ('60x143', '720x1560', 280), ('63x138', '1080x2400', 440), ('70x150', '1440x3088', 560)):
    got = display_info(mm, px)
    assert got == (density, 1), 'panel %s mm %s px: %s, expected density %d mobile' % (mm, px, got, density)
    checks.append('%s px %s mm -> %d dpi' % (px, mm, density))
print('OK density buckets: %s' % ', '.join(checks), flush=True)
assert display_info('530x300', '1920x1080')[1] == 0, 'a 24" monitor is not mobile'

instance.stop()
SHELL_LOG.unlink(missing_ok=True)
app = subprocess.Popen(['bin/apklayer', 'run', PKG], env=ENV, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
try:
    selector = selectors.DefaultSelector()
    selector.register(app.stdout, selectors.EVENT_READ)
    end = time.monotonic() + 70
    pending = b''
    with open(instance.OUT / 'mobile.log', 'wb') as log:
        while True:
            assert selector.select(10) and time.monotonic() < end, 'mobile test stalled'
            data = os.read(app.stdout.fileno(), 65536)
            if not data: break
            log.write(data)
            pending += data
            *lines, pending = pending.split(b'\n')
            for raw in lines:
                line = raw.decode(errors='replace')
                if '[apklayer]' not in line: continue
                seen.append(line)
                if 'script: hover:1,0' in line:
                    w, h, scale, bar, style, dpi, nav_l, nav_px = configures()[-1]
                    assert (style, bar, dpi) == ('mobile', 0, 420), 'first configure %s' % (configures()[-1],)
                    assert nav_px == 126 and nav_l * scale // 100 in range(nav_px - 1, nav_px + 2), 'navigation bar %s' % (configures()[-1],)
                    assert any('@420dpi, navigation bar %d px' % nav_px in l for l in seen), 'the app did not take 420 dpi and the bar'
                    shot = Image.open(instance.capture('mobile', PKG)).size
                    assert abs(shot[0] - w * scale // 100) <= 1 and abs(shot[1] - h * scale // 100) <= 1, 'picture %s px for %dx%d @%d%%' % (shot, w, h, scale)
                    first = (w, h, scale)
                    checks.append('mobile %dx%d@%d%%, 420 dpi, navigation bar %d px' % (w, h, scale, nav_px))
                    assert 'mobile style' in shell_log(), 'no mobile style in the shell log'
                    instance.inject(PKG, 'scale:300')
                elif 'script: hover:2,0' in line:
                    w, h, scale, bar, style, dpi, nav_l, nav_px = configures()[-1]
                    assert (scale, style, bar, dpi, nav_px) == (300, 'mobile', 0, 420, 126), 'at 300%%: %s' % (configures()[-1],)
                    assert nav_l == 42, 'navigation bar %d logical, expected 42' % nav_l
                    assert any('@420dpi' in l and ' @300%' in l for l in seen), 'the app did not take 420 dpi at 300%'
                    shot = Image.open(instance.capture('mobile300', PKG)).size
                    assert abs(shot[0] - w * 3) <= 1 and abs(shot[1] - h * 3) <= 1, 'picture %s px for %dx%d @300%%' % (shot, w, h)
                    checks.append('scale 300%: still 420 dpi, bar 42 logical')
                    try: instance.capture('mobile-desktop')  # the whole desktop: the shell's navigation bar over the window (a human check of its look)
                    except Exception as e: print('no desktop screenshot: %s' % e, flush=True)
                    print(apklayer('settings', 'set', 'window-mode', 'desktop').strip(), flush=True)
                elif 'script: hover:3,0' in line:
                    w, h, scale, bar, style, dpi, nav_l, nav_px = configures()[-1]
                    assert (style, bar, nav_l) == ('desktop', 46, 0), 'desktop style live: %s' % (configures()[-1],)
                    instance.capture('mobile-desktop-style', PKG)
                    checks.append('live switch to desktop style: title bar %d, dpi %d' % (bar, dpi))
                    print(apklayer('settings', 'unset', 'window-mode').strip(), flush=True)
                elif 'script: hover:4,0' in line:
                    w, h, scale, bar, style, dpi, nav_l, nav_px = configures()[-1]
                    assert (style, bar, dpi) == ('mobile', 0, 420), 'back to mobile: %s' % (configures()[-1],)
                    checks.append('live switch back to mobile (auto)')
                    instance.inject(PKG, 'tap:%d,%d' % (w // 4, h - nav_l // 2))  # Back: UiBench's root activity finishes, the app ends
    assert app.wait() == 0, 'app failed'
    assert 'navigation bar back' in shell_log(), 'a tap on Back did not reach the shell as Back'
    checks.append('Back on the navigation bar ends the app')
    assert len(checks) >= 9, checks
    print('PASS mobile style and density: %s' % '; '.join(checks), flush=True)
finally:
    apklayer('settings', 'unset', 'window-mode')
    instance.stop()
    if app.poll() is None: os.killpg(app.pid, signal.SIGKILL)
    app.wait()
    left = instance.mine('[a]pklayer.host.Main|[b]tload|[a]pklayer-shell')
    assert not left, left
