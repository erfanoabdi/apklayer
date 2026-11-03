#!/usr/bin/env python3
"""Foreground OTR2 (Off The Road 2) on the GPU route: real GPU frames, screenshots with game content and physical Back (tests/run otr2; OTR2 installed by hand)."""
import ctypes
import os
from pathlib import Path
import selectors
import signal
import subprocess
import sys
import time

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
import instance  # noqa: E402  (changes into the repo root)
import pixels  # noqa: E402
LOGDIR = instance.LOGDIR
env = dict(instance.ENV, APKLAYER_RUNTIME_DEBUG='1', APKLAYER_LOGCAT='1',
    APKLAYER_JOPTS='-Dapklayer.nativeresources=true -Dapklayer.nativemedia=true -Dapklayer.realbinder=true -Dapklayer.instancehub=true -Dapklayer.runms=20000')
intent = Path('/tmp/apklayer-otr2-intent.properties')
intent.write_text('component=com.dogbytegames.otrnext.OTRNext\n')
env['APKLAYER_INTENT_FILE'] = str(intent)

def stop():
    subprocess.run(['bin/apklayer', 'stop'], env=env, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

stop()
logs = Path(LOGDIR); logs.mkdir(exist_ok=True)
libc = ctypes.CDLL(None, use_errno=True)
notify = libc.inotify_init1(os.O_NONBLOCK | os.O_CLOEXEC)
assert notify >= 0 and libc.inotify_add_watch(notify, os.fsencode(logs), 0x102) >= 0
app = None
seen = []; captured = False; captures = 0; best = 0.0
try:
    app = subprocess.Popen(['bin/apklayer', 'run', 'com.dogbytegames.otrnext'], env=env,
        stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
    end = time.monotonic() + 25
    with selectors.DefaultSelector() as events, open('/tmp/apklayer-otr2.out', 'wb') as log:
        events.register(app.stdout, selectors.EVENT_READ); events.register(notify, selectors.EVENT_READ)
        pending = b''
        while True:
            ready = events.select(3)
            if not ready or time.monotonic() >= end:
                best = max(best, pixels.game_content(instance.capture('OTR2 quiet', 'com.dogbytegames.otrnext')))
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
                    best = max(best, pixels.game_content(instance.capture('OTR2 GPU output ' + str(captures), 'com.dogbytegames.otrnext')))
                finally: os.kill(pid, signal.SIGCONT)
                if captures < 2: continue
                instance.keys(*['key:1'], check=True,
                    stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
                captured = True
    print('OTR2 captured stages:', captures, 'Back returned to depth 0:', any('finished com.dogbytegames.otrnext.OTRNext depth=0' in line for line in seen), flush=True)
    print('OTR2 observation complete', flush=True)
    assert best >= 0.5, 'FAIL OTR2 drew no game content'
    print('PASS OTR2 renders game frames (%.0f%% of the window lit)' % (best * 100), flush=True)
finally:
    if app is not None:
        try: os.killpg(app.pid, signal.SIGKILL)
        except ProcessLookupError: pass
        app.wait(); app.stdout.close()
    stop(); os.close(notify); intent.unlink(missing_ok=True)
    instance.kill('[a]pklayer.host.Main')

left = instance.mine('[a]pklayer.host.Main|[b]tload|[a]pklayer-shell')
assert not left, 'leaked runtime processes: ' + '\n'.join(left)
