#!/usr/bin/env python3
"""Android's fullscreen on the desktop's compositor (bridge/shell/mobile.c window_set_fullscreen, geometry.c): a real app (UiBench) hides the
system bars with WindowInsetsController.hide(systemBars()) (script step fullscreen:1) and with the legacy flags (SYSTEM_UI_FLAG_FULLSCREEN,
HIDE_NAVIGATION, IMMERSIVE_STICKY and FLAG_FULLSCREEN: fullscreen:3), and shows them again (fullscreen:0, fullscreen:2). Checked: the shell asks
the compositor for a fullscreen window, whose configure is the whole output with no title bar (desktop) or no navigation bar inset (mobile), the
app's picture has that size, leaving restores the window's size, title bar and navigation bar; with a landscape request before (NewPipe's
player: SENSOR_LANDSCAPE, then fullscreen, then portrait and leave) the same: the requested turn recreates the activity (a rotation), and the
portrait request in fullscreen leaves fullscreen so the desktop window can turn back.
--mobile: a phone simulated on the compositor as tests/vm/mobile.py does (maximized, navigation bar 126 px)."""
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
DECOR_H = 46  # bridge/shell/decor.h
MOBILE = '--mobile' in sys.argv
ENV = dict(instance.ENV)
if MOBILE: ENV.update(APKLAYER_OUTPUT_MM='66x143', APKLAYER_OUTPUT_PX='1080x2340')
STEPS = ((5000, 'navinset'), (5200, 'hover:1,0'), (6000, 'fullscreen:1'), (8500, 'navinset'), (8700, 'hover:2,0'),
         (9500, 'fullscreen:0'), (12000, 'navinset'), (12200, 'hover:3,0'), (12500, 'fullscreen:3'), (15000, 'hover:4,0'),
         (15500, 'fullscreen:2'), (18000, 'hover:5,0'), (18500, 'orient:6'), (20500, 'fullscreen:1'), (23000, 'hover:6,0'),
         (23500, 'orient:1'), (23700, 'fullscreen:0'), (26500, 'navinset'), (26700, 'hover:7,0'))
ENV['APKLAYER_JOPTS'] = '-Dapklayer.runms=28000 -Dapklayer.script=' + '+'.join('%d:%s' % s for s in STEPS)
CONFIGURE = re.compile(r'configure content (\d+)x(\d+) @(\d+)% \((\d+)x(\d+) px\), title bar (\d+) logical')
SHELL_LOG = instance.STATE / 'shell.log'
seen, checks = [], []


def shell_log():
    return SHELL_LOG.read_text(errors='replace') if SHELL_LOG.exists() else ''


def configure():
    """The shell's last CONFIGURE of this app's window: (w, h, scale, bar logical)."""
    text = shell_log()
    c = [(int(m[1]), int(m[2]), int(m[3]), int(m[6])) for m in CONFIGURE.finditer(text) if PKG in text[:m.start()].rsplit('\n', 1)[-1]]
    return c[-1]


def insets():
    return [int(l.split('navinset ')[1]) for l in seen if 'navinset ' in l]


def picture(name, w, h, scale):
    shot = Image.open(instance.capture(name, PKG)).size
    assert abs(shot[0] - w * scale // 100) <= 1 and abs(shot[1] - h * scale // 100) <= 1, 'picture %s px for %dx%d @%d%%' % (shot, w, h, scale)


def fullscreen(name):
    w, h, scale, bar = configure()
    ow, oh = (int(n) for n in re.search(r'first configure: .* output (\d+)x(\d+) @', shell_log()).groups())
    assert (w, h, bar) == (ow, oh, 0), '%s: configure %s, expected the whole %dx%d output, no title bar' % (name, configure(), ow, oh)
    picture('fullscreen-' + name, w, h, scale)
    checks.append('%s: %dx%d, no bar' % (name, w, h))


def restored(name, shape):
    w, h, scale, bar = configure()
    assert (w, h, bar) == shape, '%s: configure %s, expected %s' % (name, configure(), shape)
    picture('restored-' + name, w, h, scale)
    checks.append('%s restored' % name)


instance.stop()
SHELL_LOG.unlink(missing_ok=True)
app = subprocess.Popen(['bin/apklayer', 'run', PKG], env=ENV, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
try:
    selector = selectors.DefaultSelector()
    selector.register(app.stdout, selectors.EVENT_READ)
    end = time.monotonic() + 70
    pending, first = b'', None
    with open(instance.OUT / 'fullscreen.log', 'wb') as log:
        while True:
            assert selector.select(10) and time.monotonic() < end, 'fullscreen test stalled'
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
                    first = configure()[:2] + configure()[3:]
                    assert first[2] == (0 if MOBILE else DECOR_H), 'first configure %s' % (configure(),)
                elif 'script: hover:2,0' in line:
                    assert 'fullscreen on' in shell_log(), 'the shell was not asked for fullscreen'
                    fullscreen('insets-controller')
                    try: instance.capture('fullscreen-desktop')  # the whole desktop: no title bar, no panel (a human check of its look)
                    except Exception as e: print('no desktop screenshot: %s' % e, flush=True)
                    if MOBILE: assert insets() == [126, 0], 'navigation bar inset: %s' % insets()
                elif 'script: hover:3,0' in line:
                    assert 'fullscreen off' in shell_log(), 'the shell was not asked to leave fullscreen'
                    restored('insets-controller', first)
                    if MOBILE: assert insets() == [126, 0, 126], 'navigation bar inset: %s' % insets()
                elif 'script: hover:4,0' in line:
                    fullscreen('legacy-flags')
                elif 'script: hover:5,0' in line:
                    restored('legacy-flags', first)
                elif 'script: hover:6,0' in line:
                    # the turn the app asked for is a rotation: UiBench's activity (no configChanges) is recreated, as on Android
                    if not MOBILE: assert any('configuration change: recreated' in l for l in seen), 'the landscape turn did not recreate the activity'
                    fullscreen('landscape')
                elif 'script: hover:7,0' in line:
                    w, h, scale, bar = configure()
                    # a maximized (mobile) window keeps the work area's size; a floating one takes the portrait shape asked for
                    assert (w, h, bar) == first if MOBILE else bar == first[2] and h > w, 'portrait after fullscreen: %s' % (configure(),)
                    picture('restored-portrait', w, h, scale)
                    if MOBILE: assert insets()[-1] == 126, 'navigation bar inset: %s' % insets()
                    checks.append('portrait again %dx%d' % (w, h))
    assert app.wait() == 0, 'app failed'
    assert len(checks) == 6, checks
    print('PASS fullscreen%s: %s' % (' (mobile)' if MOBILE else '', ', '.join(checks)), flush=True)
finally:
    instance.stop()
    if app.poll() is None: os.killpg(app.pid, signal.SIGKILL)
    app.wait()
    left = instance.mine('[a]pklayer.host.Main|[b]tload|[a]pklayer-shell')
    assert not left, left
