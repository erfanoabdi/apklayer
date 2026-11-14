#!/usr/bin/env python3
"""Real widget typing, clipboard and IBus -> Mutter -> proxied text-input-v3."""
import gi
gi.require_version('IBus', '1.0')
from gi.repository import IBus, Gio
import os
import re
from pathlib import Path
import selectors
import signal
import subprocess
import sys
import time

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
import instance  # noqa: E402  (changes into the repo root)
LOGDIR = instance.LOGDIR
ENV = dict(instance.ENV, DBUS_SESSION_BUS_ADDRESS='unix:path=%s/bus' % instance.SESSION,
    APKLAYER_RUNTIME_DEBUG='1',
    APKLAYER_DEBUG_INPUT='1', APKLAYER_DISPLAY_WIDTH='480', APKLAYER_DISPLAY_HEIGHT='600')
os.environ['DBUS_SESSION_BUS_ADDRESS'] = ENV['DBUS_SESSION_BUS_ADDRESS']
# IBus finds its daemon by a file named after the Wayland display (~/.config/ibus/bus/<machine>-unix-<WAYLAND_DISPLAY>): the desktop's
# name, not the absolute path an instance's environment carries
IBUS_ENV = dict(ENV, WAYLAND_DISPLAY=Path(ENV['WAYLAND_DISPLAY']).name)
os.environ['WAYLAND_DISPLAY'] = IBUS_ENV['WAYLAND_DISPLAY']
IBus.init()
bus = IBus.Bus.new()
assert bus.is_connected(), 'no IBus daemon for display ' + IBUS_ENV['WAYLAND_DISPLAY']
baseline = bus.get_global_engine()
if baseline:
    previous = baseline.get_name()
else:
    # IBus can be idle without a global engine when no editor is focused.
    # Restore GNOME's configured source rather than leaving the test engine.
    sources = Gio.Settings.new('org.gnome.desktop.input-sources').get_value('sources').unpack()
    kind, name = sources[0]
    if kind == 'ibus':
        previous = name
    else:
        layout, _, variant = name.partition('+')
        prefix = 'xkb:' + layout + ':' + variant + ':'
        previous = next(e.get_name() for e in bus.list_engines() if e.get_name().startswith(prefix))
# ticks are on the app's clock: each step's real input (a uinput call is ~2 s) must be done before the next tick
steps = [(1000, 1), (4000, 2), (6000, 3), (9000, 4), (12000, 5),
         (13000, 6), (16000, 0), (17000, 7), (20000, 8), (22000, 9)]
ENV['APKLAYER_JOPTS'] = ('-Dapklayer.runms=48000 -Dapklayer.debugkeys=true -Dapklayer.script='
    + '+'.join(f'{2 * t}:hover:{step},0' for t, step in steps))
selector = selectors.DefaultSelector()
children = []
log = open('/tmp/input-test.log', 'wb')
seen = []
pending = {}
checks = set()


def stop():
    subprocess.run(['bin/apklayer', 'stop'], env=ENV, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


def keys(*steps):
    print('INPUT ' + ' '.join(steps), flush=True)
    instance.keys(*[*steps],
                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=True)


_origin = []


def locate(content_y):
    """Desktop permille of (the window's content middle, content_y): where the shell reports the pointer, not a desktop screenshot (flameshot
    hangs or aborts on GNOME 46): the absolute pointer goes from the top panel to a probe point, the shell logs 'pointer enter content at X,Y' in
    the window's own coordinates, so the window's origin is the probe minus that."""
    if not _origin:
        state = Gio.DBusProxy.new_for_bus_sync(Gio.BusType.SESSION, 0, None, 'org.gnome.Mutter.DisplayConfig', '/org/gnome/Mutter/DisplayConfig',
                                               'org.gnome.Mutter.DisplayConfig', None).call_sync('GetCurrentState', None, 0, -1, None).unpack()
        mode = next(m for m in state[1][0][1] if m[6].get('is-current'))
        width, height = mode[1], mode[2]  # shortcut: one monitor at scale 1 (the VM); read the logical monitor if a test host differs
        shell_log = Path(LOGDIR + '/shell.log')
        for px, py in ((300, 300), (500, 400), (400, 600), (700, 300), (250, 450), (600, 650)):
            seen_before = shell_log.read_text(errors='replace').count('pointer enter content')
            instance.keys('abs:500,5', f'abs:{px * 1000 // width},{py * 1000 // height}', stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            enters = re.findall(r'pointer enter content at (-?\d+),(-?\d+)', shell_log.read_text(errors='replace'))
            if len(enters) > seen_before:
                _origin[:] = [px - int(enters[-1][0]), py - int(enters[-1][1]), width, height]
                break
        assert _origin, 'the shell saw no pointer enter on the window at any probe point'
    ox, oy, width, height = _origin
    return round((ox + 231) * 1000 / width), round((oy + content_y) * 1000 / height)


def paste():
    return subprocess.run(['wl-paste', '--no-newline'], env=ENV, capture_output=True, check=True).stdout


stop()
try:
    engine = subprocess.Popen(['python3', '-u', 'tests/vm/ibus-engine.py'], env=IBUS_ENV,
                              stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    children.append(engine)
    assert engine.stdout.readline().strip() == b'ENGINE ready'
    selector.register(engine.stdout, selectors.EVENT_READ, 'engine')
    pending['engine'] = b''
    app = subprocess.Popen(['bin/apklayer', 'run', 'hello.input'], env=ENV,
                           stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
    children.append(app)
    selector.register(app.stdout, selectors.EVENT_READ, 'app')
    pending['app'] = b''
    end = time.monotonic() + 80
    app_done = False
    while not app_done:
        events = selector.select(12)
        assert events and time.monotonic() < end, 'input test stalled'
        for key, _ in events:
            data = os.read(key.fd, 65536)
            if not data:
                selector.unregister(key.fileobj)
                assert key.data == 'app' and app.wait() == 0, 'input process failed'
                app_done = True
                continue
            log.write(data)
            pending[key.data] += data
            parts = pending[key.data].split(b'\n')
            pending[key.data] = parts.pop()
            for raw in parts:
                line = raw.decode(errors='replace')
                if not any(s in line for s in ('[apklayer]', '[hello-input]', 'ENGINE ')):
                    continue
                seen.append(line)
                print(line, flush=True)
                if 'script: hover:1,0' in line:
                    x, y = locate(96)
                    keys(f'abs:{x},{y}', 'click', 'key:35', 'key:23')  # real h, i
                elif 'script: hover:2,0' in line:
                    assert '[hello-input] text=hi' in '\n'.join(seen), 'keyboard did not type hi'
                    checks.add('typing')
                    owner = subprocess.Popen(['wl-copy', '--foreground'], env=ENV, stdin=subprocess.PIPE)
                    children.append(owner)
                    owner.stdin.write(b'host-copy')
                    owner.stdin.close()
                elif 'script: hover:3,0' in line:
                    keys('combo:ctrl+30', 'combo:ctrl+47')  # select all, real paste
                elif 'script: hover:4,0' in line:
                    assert '[hello-input] text=host-copy' in '\n'.join(seen), 'host selection did not reach field'
                    checks.add('host-clipboard')
                    x, y = locate(168)
                    keys(f'abs:{x},{y}', 'click')
                elif 'script: hover:5,0' in line:
                    assert paste() == b'android-copy', 'Android selection did not reach host'
                    checks.add('android-clipboard')
                    assert bus.set_global_engine('apklayer-test'), 'could not select test IME'
                elif 'script: hover:6,0' in line:
                    x, y = locate(96)
                    keys(f'abs:{x},{y}', 'click', 'combo:ctrl+30', 'key:49', 'key:23', 'key:57')
                elif 'script: hover:7,0' in line:
                    assert '[hello-input] text=你好' in '\n'.join(seen), 'real IME commit did not reach field'
                    assert 'ENGINE committed 你好' in '\n'.join(seen), 'IBus did not process real input'
                    checks.add('ime')
                    assert 'ENGINE surrounding 你好 2 2' in '\n'.join(seen), 'IBus did not get the field text and cursor (surrounding text)'
                    x, y = locate(296)
                    keys(f'abs:{x},{y}', 'click')
                elif 'script: hover:8,0' in line:
                    assert paste() == b'L' * 20000, 'long clipboard transfer was truncated or stalled'
                    checks.add('long-clipboard')
                elif 'script: hover:9,0' in line:
                    instance.capture('verified input', 'hello.input')  # the window's own picture
    assert len(checks) == 5, checks
    shell = Path(LOGDIR + '/shell.log').read_text(errors='replace')
    assert '[shell] text input enabled' in shell, 'proxied field did not enable text-input-v3'
    print('PASS real typing, host/app clipboard, 20KB transfer and IBus preedit/commit through proxy', flush=True)
finally:
    print('RESTORE IBus ' + previous, flush=True)
    bus.set_global_engine(previous)
    stop()
    if 'app' in globals():
        try:
            os.killpg(app.pid, signal.SIGKILL)
        except ProcessLookupError:
            pass
    for child in children:
        if child.poll() is None:
            child.terminate()
        child.wait()
    log.close()
    selector.close()
    left = instance.mine('[a]pklayer.host.Main|[b]tload|[a]pklayer-shell|[i]bus-engine.py')
    assert not left, left
