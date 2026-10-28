#!/usr/bin/env python3
"""Content activities across apps through the instance hub: one phase per run (basic | denied | death), every JVM owned and reaped."""
import os as _os
LOGDIR = _os.path.expanduser('~/.local/state/apklayer/default')  # the default instance's logs (apklayer tool)
import os
from pathlib import Path
import selectors
import signal
import subprocess
import sys
import time

os.chdir(Path.home() / 'apklayer-m1')
PHASE = sys.argv[1]
BASE = '-Dapklayer.realbinder=true -Dapklayer.instancehub=true -Dapklayer.runms=60000'
ENV = dict(os.environ, XDG_RUNTIME_DIR='/run/user/1000', APKLAYER_NATIVE=str(Path.home() / 'arm64-native'),
    APKLAYER_DISPLAY_WIDTH='480', APKLAYER_DISPLAY_HEIGHT='400')
ENV['APKLAYER_HOST_LIBS'] = ENV['APKLAYER_NATIVE'] + '/libapklayer-hal-graphics.so:' + ENV['APKLAYER_NATIVE'] + '/libapklayer-hal-media.so'


def stop():
    subprocess.run(['bin/apklayer', 'stop'], env=ENV, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    subprocess.run(['pkill', '-9', '-x', 'tail'], check=False)


stop()
proc = None
lines = []
try:
    package = 'hello.actcaller'
    env = dict(ENV, APKLAYER_JOPTS=BASE + ' -Dhello.act=' + PHASE)
    proc = subprocess.Popen(['bin/apklayer', 'run', package], env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
    end = time.monotonic() + 120
    pending = b''
    killed = False
    with selectors.DefaultSelector() as events:
        events.register(proc.stdout, selectors.EVENT_READ)
        while time.monotonic() < end:
            if not events.select(2): continue
            data = os.read(proc.stdout.fileno(), 65536)
            if not data: raise AssertionError('client ended (exit status %s)\n' % proc.wait() + '\n'.join(lines[-20:]))
            parts = (pending + data).split(b'\n')
            pending = parts.pop()
            for raw in parts:
                line = raw.decode(errors='replace')
                lines.append(line)
                if '[act]' in line or '[act-t]' in line or '[apklayer-hub]' in line or os.environ.get('ACT_VERBOSE'): print(line, flush=True)
                if '[act] FAIL' in line:
                    for _ in range(5):
                        if events.select(0.5): lines.extend(os.read(proc.stdout.fileno(), 65536).decode(errors='replace').split('\n'))
                    raise AssertionError(line + '\n' + '\n'.join(l for l in lines[-60:] if 'symbol may' not in l))
                if line.startswith('[act] KILL ') and not killed:
                    killed = True
                    os.kill(int(line.split()[2]), signal.SIGKILL)
                if '[act] PASS' in line:
                    if PHASE == 'private': assert 'PRIVATE STARTED' not in Path(LOGDIR + '/app-hello.acttarget.log').read_text(errors='replace') if Path(LOGDIR + '/app-hello.acttarget.log').exists() else True, 'private activity started'
                    print('PASS activities ' + PHASE, flush=True)
                    raise SystemExit(0)
    raise AssertionError('timed out\n' + '\n'.join(lines[-25:]))
finally:
    if proc is not None and proc.poll() is None: os.killpg(proc.pid, signal.SIGTERM)
    stop()
    subprocess.run(['pkill', '-f', '[a]pklayer.host.Main --hub'], check=False)
    subprocess.run(['pkill', '-f', '[c]omponent-host'], check=False)
    subprocess.run(['pkill', '-f', '[a]pklayer.host.Main'], check=False)
    subprocess.run(['pkill', '-x', 'apklayer-shell'], check=False)
