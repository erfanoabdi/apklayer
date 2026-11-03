#!/usr/bin/env python3
"""Foreground UiBench TextureView: real GPU frames, capture handshake and physical Back."""
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
    APKLAYER_JOPTS='-Dapklayer.realbinder=true -Dapklayer.instancehub=true -Dapklayer.runms=20000')
intent = Path('/tmp/apklayer-texture-intent.properties')
intent.write_text('component=com.android.test.uibench.GlTextureViewActivity\n')
env['APKLAYER_INTENT_FILE'] = str(intent)

def stop():
    subprocess.run(['bin/apklayer', 'stop'], env=env, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

stop()
logs = Path(LOGDIR); logs.mkdir(exist_ok=True)
libc = ctypes.CDLL(None, use_errno=True)
notify = libc.inotify_init1(os.O_NONBLOCK | os.O_CLOEXEC)
assert notify >= 0 and libc.inotify_add_watch(notify, os.fsencode(logs), 0x102) >= 0
app = None
seen = []; captured = False; captures = 0; shots = []
try:
    app = subprocess.Popen(['bin/apklayer', 'run', 'com.android.test.uibench'], env=env,
        stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
    end = time.monotonic() + 25
    with selectors.DefaultSelector() as events, open('/tmp/apklayer-texture.out', 'wb') as log:
        events.register(app.stdout, selectors.EVENT_READ); events.register(notify, selectors.EVENT_READ)
        pending = b''
        while True:
            ready = events.select(3)
            assert ready and time.monotonic() < end, 'TextureView stalled: ' + '\n'.join(seen[-15:])
            finished = False
            for key, _ in ready:
                if key.fd == notify:
                    os.read(notify, 65536); continue
                chunk = os.read(app.stdout.fileno(), 65536)
                if not chunk:
                    assert app.wait() == 0, 'TextureView JVM failed: ' + '\n'.join(seen[-20:])
                    finished = True; break
                log.write(chunk); log.flush()
                lines = (pending + chunk).split(b'\n'); pending = lines.pop()
                for raw in lines:
                    line = raw.decode(errors='replace'); seen.append(line)
                    if any(tag in line for tag in ('[apklayer]', 'GLTextureView', 'Exception', 'failed', 'SIGSEGV', 'fatal error')):
                        print(line, flush=True)
                    assert not any(tag in line for tag in ('FATAL EXCEPTION', 'fatal error has been detected')), line
            if finished: break
            shell = (logs / 'shell.log').read_text(errors='replace') if (logs / 'shell.log').exists() else ''
            if not captured and any('started com.android.test.uibench.GlTextureViewActivity' in line for line in seen) and shell.count('dmabuf frame') >= 40 + captures * 40:
                captures += 1
                pid = int(next(s.rsplit('pid=', 1)[1] for s in seen if 'hub registered com.android.test.uibench pid=' in s))
                # Stop submissions while the host reads the virgl scanout; otherwise
                # its capture can contain partially black rows during animation.
                os.kill(pid, signal.SIGSTOP)
                try:
                    shot = instance.capture('TextureView GPU output ' + str(captures), 'com.android.test.uibench')
                    orange = pixels.count(shot, lambda r, g, b: r > 80 and r > g * 1.2 and g > b * 1.2)
                    assert orange > 60000, ('missing canyon texture', shot, orange)
                    print('PASS canyon texture pixels:', orange, flush=True)
                    shots.append(shot)
                finally: os.kill(pid, signal.SIGCONT)
                if captures < 2: continue
                instance.keys(*['key:1'], check=True,
                    stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
                captured = True
    assert captured and any('finished com.android.test.uibench.GlTextureViewActivity depth=0' in line for line in seen)
    assert len(shots) == 2
    moved = pixels.count_image(pixels.difference(*shots), lambda *p: max(p) > 30)
    assert moved > 20000, ('TextureView animation did not change', moved)
    print('PASS animated TextureView pixels:', moved, flush=True)
    print('PASS UiBench GL TextureView GPU frames and physical Back cleanup', flush=True)
finally:
    if app is not None:
        try: os.killpg(app.pid, signal.SIGKILL)
        except ProcessLookupError: pass
        app.wait(); app.stdout.close()
    stop(); os.close(notify); intent.unlink(missing_ok=True)
    instance.kill('[a]pklayer.host.Main')

left = instance.mine('[a]pklayer.host.Main|[b]tload|[a]pklayer-shell')
assert not left, 'leaked runtime processes: ' + '\n'.join(left)
