#!/usr/bin/env python3
"""Foreground OTR2 (Off The Road 2) on the GPU route: real GPU frames, capture handshake and physical Back. Driven by tests/vm/otr2-driver.py."""
import os as _os
LOGDIR = _os.path.expanduser('~/.local/state/apklayer/default')  # the default instance's logs (apklayer tool)
import ctypes
import os
from pathlib import Path
import selectors
import signal
import subprocess
import sys
import time

os.chdir(Path.home() / 'apklayer-m1')
env = dict(os.environ, XDG_RUNTIME_DIR='/run/user/1000', APKLAYER_NATIVE=str(Path.home() / 'arm64-native'),
    APKLAYER_RUNTIME_DEBUG='1', APKLAYER_LOGCAT='1',
    APKLAYER_JOPTS='-Dapklayer.nativeresources=true -Dapklayer.nativemedia=true -Dapklayer.realbinder=true -Dapklayer.instancehub=true -Dapklayer.runms=20000')
intent = Path('/tmp/apklayer-otr2-intent.properties')
intent.write_text('component=com.dogbytegames.otrnext.OTRNext\n')
env['APKLAYER_INTENT_FILE'] = str(intent)

def stop():
    subprocess.run(['bin/apklayer', 'stop'], env=env, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    subprocess.run(['pkill', '-9', '-x', 'tail'], check=False)

stop()
logs = Path(LOGDIR); logs.mkdir(exist_ok=True)
libc = ctypes.CDLL(None, use_errno=True)
notify = libc.inotify_init1(os.O_NONBLOCK | os.O_CLOEXEC)
assert notify >= 0 and libc.inotify_add_watch(notify, os.fsencode(logs), 0x102) >= 0
app = None
seen = []; captured = False; captures = 0
try:
    subprocess.run(['gcc', '-O1', '-o', '/tmp/uinput-keys', 'tests/vm/uinput-keys.c'], check=True)
    app = subprocess.Popen(['bin/apklayer', 'run', 'com.dogbytegames.otrnext'], env=env,
        stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
    end = time.monotonic() + 25
    with selectors.DefaultSelector() as events, open('/tmp/apklayer-otr2.out', 'wb') as log:
        events.register(app.stdout, selectors.EVENT_READ); events.register(notify, selectors.EVENT_READ)
        pending = b''
        while True:
            ready = events.select(3)
            if not ready or time.monotonic() >= end:
                print('CAPTURE OTR2 quiet', flush=True)
                assert sys.stdin.readline().strip() == 'captured'
                print('OTR2 observation ended (no log events for 3s or test budget reached)', flush=True)
                break
            finished = False
            for key, _ in ready:
                if key.fd == notify:
                    os.read(notify, 65536); continue
                chunk = os.read(app.stdout.fileno(), 65536)
                if not chunk:
                    assert app.wait() == 0, 'OTR2 JVM failed: ' + '\n'.join(seen[-20:])
                    finished = True; break
                log.write(chunk); log.flush()
                lines = (pending + chunk).split(b'\n'); pending = lines.pop()
                for raw in lines:
                    line = raw.decode(errors='replace'); seen.append(line)
                    if any(tag in line for tag in ('[apklayer]', 'GLOTR2', 'Exception', 'failed', 'SIGSEGV', 'fatal error')):
                        print(line, flush=True)

            if finished: break
            shell = (logs / 'shell.log').read_text(errors='replace') if (logs / 'shell.log').exists() else ''
            if not captured and any('started com.dogbytegames.otrnext.OTRNext' in line for line in seen) and shell.count('dmabuf frame') >= 5 + captures * 45:
                captures += 1
                pid = int(next(s.rsplit('pid=', 1)[1] for s in seen if 'hub registered com.dogbytegames.otrnext pid=' in s))
                # Stop submissions while the host reads the virgl scanout; otherwise
                # its capture can contain partially black rows during animation.
                os.kill(pid, signal.SIGSTOP)
                try:
                    print('CAPTURE OTR2 GPU output ' + str(captures), flush=True)
                    assert sys.stdin.readline().strip() == 'captured'
                finally: os.kill(pid, signal.SIGCONT)
                if captures < 2: continue
                subprocess.run(['sudo', '-n', '/tmp/uinput-keys', 'key:1'], check=True,
                    stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
                captured = True
    print('OTR2 captured stages:', captures, 'Back returned to depth 0:', any('finished com.dogbytegames.otrnext.OTRNext depth=0' in line for line in seen), flush=True)
    print('OTR2 observation complete', flush=True)
finally:
    if app is not None:
        try: os.killpg(app.pid, signal.SIGKILL)
        except ProcessLookupError: pass
        app.wait(); app.stdout.close()
    stop(); os.close(notify); intent.unlink(missing_ok=True)
    subprocess.run(['pkill', '-9', '-f', '[a]pklayer.host.Main'], check=False)

left = subprocess.run(['pgrep', '-af', '[a]pklayer.host.Main|[b]tload|[a]pklayer-shell'], capture_output=True, text=True)
assert left.returncode == 1, 'leaked runtime processes: ' + left.stdout
