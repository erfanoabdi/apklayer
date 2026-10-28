#!/usr/bin/env python3
"""Two native app JVMs, one cold-started stack; real focus and close routing."""
import os as _os
LOGDIR = _os.path.expanduser('~/.local/state/apklayer/default')  # the default instance's logs (apklayer tool)
import os
import json
from pathlib import Path
import selectors
import signal
import re
import subprocess
import time

ROOT = Path.home() / 'apklayer-m1'
os.chdir(ROOT)
ENV = dict(os.environ, XDG_RUNTIME_DIR='/run/user/1000',
           APKLAYER_NATIVE=str(Path.home() / 'arm64-native'), APKLAYER_RUNTIME_DEBUG='1',
           APKLAYER_DISPLAY_WIDTH='420', APKLAYER_DISPLAY_HEIGHT='600')
PACKAGES = ('hello.share.dst', 'hello.share.dst2')
processes = {}
logs = {}
lines = {p: [] for p in PACKAGES}
buffers = {p: b'' for p in PACKAGES}
ready = set()
late = set()
selector = selectors.DefaultSelector()
focus_checked = closed = False
close_at = None


def stop():
    subprocess.run(['bin/apklayer', 'stop'], env=ENV, stdout=subprocess.DEVNULL,
                   stderr=subprocess.DEVNULL)
    subprocess.run(['pkill', '-9', '-x', 'tail'], check=False)


def keys(*steps):
    print('INPUT ' + ' '.join(steps), flush=True)
    subprocess.run(['sudo', '-n', '/tmp/uinput-keys', *steps],
                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=True)


def layers():
    r = subprocess.run(['b/cli/apklayer', 'exec', 'libapklayer_servicetool.so', 'dump', 'SurfaceFlinger', '--list'],
                       env=ENV, capture_output=True, text=True, check=True)
    return r.stdout


stop()
try:
    subprocess.run(['gcc', '-O1', '-o', '/tmp/uinput-keys', 'tests/vm/uinput-keys.c'], check=True)
    for package in PACKAGES:
        steps = '+'.join(f'{t}:clipcopy:{package}-{t}' for t in range(1000, 21000, 2000))
        env = dict(ENV, APKLAYER_JOPTS='-Dapklayer.runms=22000 -Dapklayer.script=' + steps)
        p = subprocess.Popen(['bin/apklayer', 'run', package], env=env, stdout=subprocess.PIPE,
                             stderr=subprocess.STDOUT, start_new_session=True)
        processes[package] = p
        logs[package] = open('/tmp/multi-' + package + '.log', 'wb')
        selector.register(p.stdout, selectors.EVENT_READ, package)
    deadline = time.monotonic() + 35
    gone = None
    survivor = None
    while selector.get_map():
        events = selector.select(3)
        assert events and time.monotonic() < deadline, 'silent or stalled multi-app run'
        for key, _ in events:
            package = key.data
            data = os.read(key.fd, 65536)
            if not data:
                selector.unregister(key.fileobj)
                assert processes[package].wait() == 0, package + ' crashed'
                if not closed:
                    assert focus_checked and close_at is not None and time.monotonic() - close_at < 3, 'app did not exit in response to real Back'
                    gone = package
                    survivor = next(p for p in PACKAGES if p != gone)
                    assert processes[survivor].poll() is None, 'closing one app stopped the other'
                    remaining = layers()
                    assert 'VRI-' + survivor + '/' in remaining, 'survivor layer disappeared'
                    assert 'VRI-' + gone + '/' not in remaining, 'closed app layer remains'
                    closed = True
                    print('PASS close routed to ' + gone + '; ' + survivor + ' remains', flush=True)
                    print('RESIZE surviving window', flush=True)
                    points = json.loads(input())
                    keys(*(step for x, y in points for step in (f'abs:{x},{y}', 'click')))
                continue
            logs[package].write(data)
            buffers[package] += data
            parts = buffers[package].split(b'\n')
            buffers[package] = parts.pop()
            for raw in parts:
                line = raw.decode(errors='replace')
                if '[apklayer]' not in line:
                    continue
                lines[package].append(line)
                print(package + ': ' + line, flush=True)
                if 'script: clipcopy:' + package + '-1000' in line:
                    ready.add(package)
                if 'script: clipcopy:' + package + '-11000' in line:
                    late.add(package)
                if ready == set(PACKAGES) and not focus_checked:
                    snapshot = layers()
                    assert all('VRI-' + p + '/' in snapshot for p in PACKAGES), 'missing app layer'
                    print('LOCATE app windows', flush=True)
                    points = json.loads(input())
                    keys(*(step for x, y in points for step in (f'abs:{x},{y}', 'click')))
                    focus_checked = True
                    print('CAPTURE two app windows', flush=True)
                if late == set(PACKAGES) and focus_checked and not closed and gone is None:
                    gone = 'pending'  # send just once; EOF identifies the actual focused app
                    close_at = time.monotonic()
                    keys('key:1')
                if closed and package == survivor and 'script: clipcopy:' + package + '-17000' in line:
                    assert processes[package].poll() is None
                    print('PASS surviving app main loop remains responsive', flush=True)
    assert focus_checked and closed, 'real close did not reach either app'
    shell = Path(LOGDIR + '/shell.log').read_text(errors='replace')
    assert shell.count('[shell] listening on ') == 1, 'concurrent launches restarted the shell'
    service = subprocess.run(['pgrep', '-af', '[l]ibapklayer_servicemanager.so'], capture_output=True, text=True)
    assert len(service.stdout.splitlines()) == 1, 'more than one service stack'
    assert '[shell] apklayer: window ' not in shell, 'primary window exists'
    assert all('[shell] ' + p + ' takes the input of window' in shell for p in PACKAGES)
    displays = [re.search(r'virtual display \"([^\"]+)\": layer stack (\d+)',
        Path(LOGDIR + '/vdisplay-' + p + '.log').read_text(errors='replace')).groups() for p in PACKAGES]
    assert len({d[0] for d in displays}) == len({d[1] for d in displays}) == 2, displays
    assert any('configure 420x600' not in s and 'configure ' in s for s in lines[survivor]), 'survivor did not resize'
    final_size = re.findall(r'configure (\d+x\d+)', '\n'.join(lines[survivor]))[-1]
    assert final_size in shell, 'survivor produced no frame after its neighbor closed'
    assert all(any('active=true' in s for s in lines[p]) for p in PACKAGES), 'focus never reached both apps'
    assert 'resumed' in '\n'.join(sum(lines.values(), [])), 'focus restore was not delivered'
    print('PASS two app JVMs, distinct displays, real focus/close, one shared stack', flush=True)
finally:
    stop()
    for package, proc in processes.items():
        if proc.poll() is None:
            os.killpg(proc.pid, signal.SIGKILL)
        proc.wait()
        logs[package].close()
    selector.close()
    left = subprocess.run(['pgrep', '-af', '[a]pklayer.host.Main|[b]tload|[a]pklayer-shell'],
                          capture_output=True, text=True)
    assert left.returncode != 0, left.stdout
