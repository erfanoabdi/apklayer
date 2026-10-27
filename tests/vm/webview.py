#!/usr/bin/env python3
"""Content webview across apps through the instance hub: one phase per run (basic | denied | death), every JVM owned and reaped."""
import os
from pathlib import Path
import selectors
import signal
import subprocess
import sys
import time

os.chdir(Path.home() / 'apklayer-m1')
PHASE = sys.argv[1]
BASE = '-Dapklayer.realbinder=true -Dapklayer.instancehub=true -Dapklayer.runms=90000 ' + os.environ.get('WV_JOPTS', '')
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
    package = 'hello.wvprobe'
    env = dict(ENV, APKLAYER_JOPTS=BASE, APKLAYER_WEBVIEW_PROVIDER='com.android.webview', APKLAYER_LOGCAT='1',  **({'APKLAYER_RUNTIME_DEBUG': '1'} if os.environ.get('WV_RTDEBUG') else {}))
    proc = subprocess.Popen(['bin/apklayer', 'run', package], env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
    end = time.monotonic() + 25
    pending = b''
    killed = False
    with selectors.DefaultSelector() as events:
        events.register(proc.stdout, selectors.EVENT_READ)
        while time.monotonic() < end:
            if not events.select(2): continue
            data = os.read(proc.stdout.fileno(), 65536)
            if not data: raise AssertionError('client ended\n' + '\n'.join(lines[-20:]))
            parts = (pending + data).split(b'\n')
            pending = parts.pop()
            for raw in parts:
                line = raw.decode(errors='replace')
                lines.append(line)
                if '[wv]' in line or 'Exception' in line or line.startswith('\tat ') or '[apklayer]' in line or os.environ.get('WV_VERBOSE'): print(line, flush=True)
                if '[wv] FAIL' in line:
                    for _ in range(5):
                        if events.select(0.5): lines.extend(os.read(proc.stdout.fileno(), 65536).decode(errors='replace').split('\n'))
                    raise AssertionError(line + '\n' + '\n'.join(l for l in lines[-60:] if 'symbol may' not in l))
                if line.startswith('[wv] KILL ') and not killed:
                    killed = True
                    os.kill(int(line.split()[2]), signal.SIGKILL)
                if '[wv] PASS' in line:
                    time.sleep(8)  # the window stays up a moment: webview-capture.py takes a screenshot of it
                    print('PASS webview ' + PHASE, flush=True)
                    raise SystemExit(0)
    if os.environ.get('WV_DUMP'):
        for pid in subprocess.run(['pgrep', '-f', '[a]pklayer.host.Main'], capture_output=True, text=True).stdout.split():
            dump = subprocess.run(['jcmd', pid, 'Thread.print'], capture_output=True, text=True).stdout.split('\n\n')
            print('=== threads of ' + pid, *[b[:1400] for b in dump if any(k in b for k in ('Chrome_', 'ChildProcess', 'ThreadPool', 'Main Thread', 'WV.', 'org.chromium'))][:40], sep='\n', flush=True)
    raise AssertionError('timed out\n' + '\n'.join(lines[-25:]))
finally:
    for log in [Path('/tmp/apklayer-stack/hub.log')] + sorted(Path('/tmp/apklayer-stack').glob('component-com.android.webview*.log')):
        name = log.name
        if log.exists(): print('--- ' + name + ' (tail)', *[l[:200] for l in log.read_text(errors='replace').split('\n') if 'symbol may' not in l][-25:], sep='\n', flush=True)
    if proc is not None and proc.poll() is None: os.killpg(proc.pid, signal.SIGTERM)
    stop()
    subprocess.run(['pkill', '-f', '[a]pklayer.host.Main --hub'], check=False)
    subprocess.run(['pkill', '-f', '[c]omponent-host'], check=False)
    subprocess.run(['pkill', '-f', '[a]pklayer.host.Main'], check=False)
    subprocess.run(['pkill', '-x', 'apklayer-shell'], check=False)
