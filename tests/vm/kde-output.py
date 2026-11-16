#!/usr/bin/env python3
"""kde-output: an app's window between two outputs of different scale, under a KWin of its own (kwin_wayland --virtual, headless, a private socket
and D-Bus, two 1280x800 outputs, the first at scale 2, the second at 1; the desktop is not touched). KWin moves the window to the other output and
disables the output it is on (kscreen-doctor, a hotplug); the shell's CONFIGURE scale follows the output the window is on each time (the
compositor's preferred scale, wp_fractional_scale_v1), and the app renders at it. Needs kwin_wayland, kscreen-doctor, qdbus6, gdbus, dbus-daemon."""
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
PKG = 'com.android.test.uibench'
if not shutil.which('kwin_wayland'): sys.exit('SKIP kwin_wayland is not installed')
run_dir = Path(tempfile.mkdtemp(prefix='apklayer-kwin-', dir=instance.SESSION))  # beside the desktop's socket: the stack's processes see the session dir
os.chmod(run_dir, 0o700)
host = dict(os.environ, XDG_RUNTIME_DIR=str(run_dir))
for k in ('DISPLAY', 'WAYLAND_DISPLAY'): host.pop(k, None)
bus = subprocess.run(['dbus-daemon', '--session', '--fork', '--print-address=1', '--print-pid=1'], env=host, capture_output=True, text=True, check=True).stdout.split()
host.update(DBUS_SESSION_BUS_ADDRESS=bus[0], WAYLAND_DISPLAY=str(run_dir / 'kdenest'))
os.environ.update(DBUS_SESSION_BUS_ADDRESS=bus[0])  # kwin.py's qdbus calls
kwin.LOG, kwin.ENV = run_dir / 'kwin.log', dict(host, QT_QPA_PLATFORM='wayland')  # this KWin's log and kscreen-doctor environment
OUTPUTS = {'Virtual-0': 200, 'Virtual-1': 100}
TICK = 3500
STEPS = 4
ENV = dict(instance.ENV, WAYLAND_DISPLAY=host['WAYLAND_DISPLAY'])
ENV['APKLAYER_JOPTS'] = '-Dapklayer.runms=%d -Dapklayer.script=' % (6000 + TICK * (STEPS + 1)) + '+'.join('%d:hover:%d,0' % (5000 + TICK * i, i + 1) for i in range(STEPS + 1))
results = []


def case(name, ok, detail=''):
    results.append(ok)
    print('%s %s %s' % ('OK  ' if ok else 'FAIL', name, detail), flush=True)


def on_output(expect, what):
    """The window is on an output (KWin's view) and the shell's scale is that output's."""
    info, c = kwin.info(PKG, instance.OUT), kwin.configure(instance.STATE / 'shell.log')
    case(what, bool(info and c) and info['output'] == expect and c[2] == OUTPUTS[expect], 'KWin: %s, shell @%s%% (want %s @%d%%)' % (info and info['output'], c and c[2], expect, OUTPUTS[expect]))


current = []


def step(n):
    if n == 1:
        info = kwin.info(PKG, instance.OUT)
        current.append(info['output'])
        on_output(current[0], 'the window opens on an output, at its scale')
        other = [o for o in OUTPUTS if o != current[0]][0]
        kwin.send_to_output(PKG, other, instance.OUT)
        current.append(other)
    elif n == 2:
        on_output(current[1], 'moved to the other output: its scale')
        out = subprocess.run(['kscreen-doctor', 'output.%s.disable' % current[1]], env=kwin.ENV, capture_output=True, text=True)
        case('hotplug: the output goes', out.returncode == 0, out.stderr[-300:])
    elif n == 3:
        on_output(current[0], 'its output disabled: the window and its scale move to the remaining output')
        subprocess.run(['kscreen-doctor', 'output.%s.enable' % current[1]], env=kwin.ENV, capture_output=True)


instance.stop()
(instance.STATE / 'shell.log').unlink(missing_ok=True)
kw = subprocess.Popen(['kwin_wayland', '--virtual', '--socket', 'kdenest', '--width', '1280', '--height', '800', '--output-count', '2', '--no-lockscreen'], env=host,
                      stdin=subprocess.DEVNULL, stdout=open(run_dir / 'kwin.log', 'wb'), stderr=subprocess.STDOUT, start_new_session=True)
app = None
try:
    assert subprocess.run(['gdbus', 'wait', '--session', '--timeout', '20', 'org.kde.KWin'], env=host).returncode == 0, 'KWin did not start: ' + (run_dir / 'kwin.log').read_text()[-800:]
    for name, scale in OUTPUTS.items():
        out = subprocess.run(['kscreen-doctor', 'output.%s.scale.%d' % (name, scale // 100)], env=kwin.ENV, capture_output=True, text=True)
        assert out.returncode == 0, 'kscreen-doctor: ' + out.stdout[-300:] + out.stderr[-300:]
    app = subprocess.Popen(['bin/apklayer', 'run', PKG], env=ENV, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
    selector = selectors.DefaultSelector()
    selector.register(app.stdout, selectors.EVENT_READ)
    pending, end = b'', time.monotonic() + 120
    with open(instance.OUT / 'kde-output.log', 'wb') as log:
        while selector.select(40) and time.monotonic() < end:
            data = os.read(app.stdout.fileno(), 65536)
            if not data: break
            log.write(data)
            pending += data
            *lines, pending = pending.split(b'\n')
            for raw in lines:
                m = re.search(r'script: hover:(\d+),0', raw.decode(errors='replace'))
                if m and 1 <= int(m[1]) <= STEPS:
                    try: step(int(m[1]))
                    except Exception as e: case('step %s' % m[1], False, 'error: %r' % (e,))
    print('%s KDE outputs: %d cases' % ('PASS' if results and all(results) and len(results) >= 4 else 'FAIL', len(results)), flush=True)
    sys.exit(0 if results and all(results) and len(results) >= 4 else 1)
finally:
    instance.stop()
    if app and app.poll() is None: os.killpg(app.pid, signal.SIGKILL)
    os.killpg(kw.pid, signal.SIGTERM)
    kw.wait()
    os.kill(int(bus[1]), signal.SIGTERM)
    for p in Path('/proc').iterdir():  # what KWin started in its runtime dir (Xwayland, its helpers)
        try:
            if b'XDG_RUNTIME_DIR=%s\0' % str(run_dir).encode() in (p / 'environ').read_bytes() + b'\0': os.kill(int(p.name), signal.SIGTERM)
        except (OSError, ValueError): pass
    shutil.rmtree(run_dir, ignore_errors=True)
