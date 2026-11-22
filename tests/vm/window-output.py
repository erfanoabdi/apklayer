#!/usr/bin/env python3
"""window-output: an app sees the screen of the output its window is on, and an output change reaches it at once. A headless GNOME Shell
(private socket, own D-Bus) has two virtual monitors, 800x600 first (the window opens there) and 1280x800; hello.input logs
Display.getRealMetrics at its start and at every DisplayListener.onDisplayChanged. The shell test input `output:<n>` is the compositor's
wl_surface.enter of the n-th output (-1: none: the largest output is the fallback); then the compositor's own hotplug: monitor 2 is
disabled (Mutter's ApplyMonitorsConfig), its wl_output goes. Mutter opens the window on either monitor (the first step may change nothing). Before: every app saw the largest output, and a hotplug reached it at its next configure."""
import os
import re
import selectors
import shutil
import signal
import subprocess
import sys
import tempfile
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
import instance  # noqa: E402

PKG = 'hello.input'
SMALL, LARGE = (600, 800), (800, 1280)  # the two monitors' sizes, the shorter side first
# --scale <percent>: the app's render scale (settings render-scale, bridge/shell/geometry.c): it sees every screen at that share, and each output change still reaches it
PCT = int(sys.argv[sys.argv.index('--scale') + 1]) if '--scale' in sys.argv else 100
if PCT != 100: SMALL, LARGE = (tuple(round(n * PCT / 100) for n in SMALL), tuple(round(n * PCT / 100) for n in LARGE))
if not shutil.which('gnome-shell'): sys.exit('FAIL gnome-shell is not installed')
run_dir = Path(tempfile.mkdtemp(prefix='apklayer-gs-', dir=instance.SESSION))  # beside the desktop's socket: the stack's processes see the session dir
os.chmod(run_dir, 0o700)
ENV = dict(os.environ, XDG_RUNTIME_DIR=str(run_dir))
for k in ('DISPLAY', 'WAYLAND_DISPLAY'): ENV.pop(k, None)
bus = subprocess.run(['dbus-daemon', '--session', '--fork', '--print-address=1', '--print-pid=1'], env=ENV, capture_output=True, text=True, check=True).stdout.split()
ENV['DBUS_SESSION_BUS_ADDRESS'] = bus[0]  # the shell's own session bus: Mutter's DisplayConfig is on it
TICK_MS = 500
APP_ENV = dict(instance.ENV, APKLAYER_DEBUG_INPUT='1', **({'APKLAYER_RENDER_SCALE': str(PCT)} if PCT != 100 else {}), WAYLAND_DISPLAY=str(run_dir / 'wayland-gs'), APKLAYER_DISPLAY_WIDTH='480', APKLAYER_DISPLAY_HEIGHT='600',
               APKLAYER_JOPTS='-Dhello.display=1 -Dapklayer.runms=60000 -Dapklayer.script=' + '+'.join('%d:hover:%d,0' % (i * TICK_MS, i) for i in range(1, 120)))
seen = []


def screens():
    """The sizes of the "display WxH" lines, the shorter side first (the window may be turned)."""
    return [tuple(sorted(map(int, m.groups()))) for m in (re.match(r'\[hello-input\] display (\d+)x(\d+) ', l) for l in seen) if m]


def changed():
    return sum(l.startswith('[hello-input] display changed') for l in seen)


# the compositor's own hotplug: only the smaller monitor stays
DISABLE = '''
import sys
from gi.repository import Gio, GLib
bus = Gio.bus_get_sync(Gio.BusType.SESSION)
def call(m, args):
    return bus.call_sync('org.gnome.Mutter.DisplayConfig', '/org/gnome/Mutter/DisplayConfig', 'org.gnome.Mutter.DisplayConfig', m, args, None, 0, 5000)
serial, monitors, logical, props = call('GetCurrentState', None).unpack()
keep = min(monitors, key=lambda mon: next(m[1] * m[2] for m in mon[1] if m[6].get('is-current')))
mode = next(m[0] for m in keep[1] if m[6].get('is-current'))
config = [(0, 0, 1.0, 0, True, [(keep[0][0], mode, {})])]
call('ApplyMonitorsConfig', GLib.Variant('(uua(iiduba(ssa{sv}))a{sv})', (serial, 1, config, {})))
print('disabled', [m[0][0] for m in monitors[1:]])
'''

# (the injected step, what the app must then see, whether it must change with a DisplayListener callback: not the first, the window may open there)
STEPS = [('output:1', LARGE, False), ('output:0', SMALL, True), ('output:-1', LARGE, True), ('hotplug', SMALL, True)]

instance.stop()
gs_log = run_dir / 'gs.log'
gs = subprocess.Popen(['gnome-shell', '--headless', '--wayland', '--no-x11', '--wayland-display=wayland-gs',
                       '--virtual-monitor', '800x600', '--virtual-monitor', '1280x800'], env=ENV, stdin=subprocess.DEVNULL,
                      stdout=gs_log.open('wb'), stderr=subprocess.STDOUT, start_new_session=True)
app = None
try:
    instance.wait_file(gs_log, 'Using Wayland display name', 20)
    assert 'Added virtual monitor Meta-1' in gs_log.read_text(), 'GNOME Shell has no second virtual monitor'
    app = subprocess.Popen(['bin/apklayer', 'run', PKG], env=APP_ENV, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
    selector = selectors.DefaultSelector()
    selector.register(app.stdout, selectors.EVENT_READ)
    end, pending, step, waiting, early = time.monotonic() + 100, b'', 0, None, 0
    with open(instance.OUT / 'window-output.log', 'wb') as log:
        while step < len(STEPS) or waiting:
            assert selector.select(15) and time.monotonic() < end, 'window-output test stalled at step %d: %s' % (step, seen[-5:])
            data = os.read(app.stdout.fileno(), 65536)
            assert data, 'the app ended at step %d' % step
            log.write(data)
            pending += data
            *lines, pending = pending.split(b'\n')
            for raw in lines:
                line = raw.decode(errors='replace')
                if '[hello-input]' not in line and 'script: hover:' not in line: continue
                seen.append(line)
                print(line, flush=True)
                if waiting:  # the screen after the step arrives with the DisplayListener callback
                    name, want, strict, before = waiting
                    if screens()[-1] == want and (changed() > before or not strict):
                        print('PASS %s: the app sees %dx%d%s' % (name, *want, ' at once (onDisplayChanged)' if changed() > before else ''), flush=True)
                        waiting = None
                elif 'script: hover:' in line and step < len(STEPS) and len(screens()) > 0:
                    early += 1
                    if early < 6: continue  # the first frames: the compositor's wl_surface.enter comes after them
                    name, want, strict = STEPS[step]
                    step += 1
                    waiting = (name, want, strict, changed())
                    if name == 'hotplug':
                        out = subprocess.run(['python3', '-I', '-c', DISABLE], env=ENV, capture_output=True, text=True)
                        assert out.returncode == 0, 'monitor config failed: ' + out.stderr[-800:]
                    else:
                        instance.inject(PKG, name)
finally:
    instance.stop()
    if app and app.poll() is None: os.killpg(app.pid, signal.SIGKILL)
    for p in Path('/proc').iterdir():  # the shell, its bus, the portal and the rest it started: the processes whose runtime dir this is
        try:
            if b'XDG_RUNTIME_DIR=%s\0' % str(run_dir).encode() in (p / 'environ').read_bytes() + b'\0': os.kill(int(p.name), signal.SIGTERM)
        except (OSError, ValueError): pass
    gs.wait()
    subprocess.run(['fusermount3', '-uz', str(run_dir / 'doc')], capture_output=True)
    shutil.rmtree(run_dir, ignore_errors=True)
