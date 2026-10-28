#!/usr/bin/env python3
"""Real APK JNI Vulkan readback, presentation capability matching the host device and physical Back."""
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
    APKLAYER_JOPTS='-Dapklayer.realbinder=true -Dapklayer.instancehub=true -Dapklayer.runms=20000')
intent = Path('/tmp/apklayer-vulkan-intent.properties')
intent.write_text('component=hello.vulkan.Main\n')
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
seen = []; captured = False
try:
    subprocess.run(['gcc', '-O1', '-o', '/tmp/uinput-keys', 'tests/vm/uinput-keys.c'], check=True)
    app = subprocess.Popen(['bin/apklayer', 'run', 'hello.vulkan'], env=env,
        stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
    end = time.monotonic() + 25
    with selectors.DefaultSelector() as events, open('/tmp/apklayer-vulkan.out', 'wb') as log:
        events.register(app.stdout, selectors.EVENT_READ); events.register(notify, selectors.EVENT_READ)
        pending = b''
        while True:
            ready = events.select(3)
            assert ready and time.monotonic() < end, 'Vulkan probe stalled: ' + '\n'.join(seen[-15:])
            finished = False
            for key, _ in ready:
                if key.fd == notify:
                    os.read(notify, 65536); continue
                chunk = os.read(app.stdout.fileno(), 65536)
                if not chunk:
                    assert app.wait() == 0, 'Vulkan probe JVM failed: ' + '\n'.join(seen[-20:])
                    finished = True; break
                log.write(chunk); log.flush()
                lines = (pending + chunk).split(b'\n'); pending = lines.pop()
                for raw in lines:
                    line = raw.decode(errors='replace'); seen.append(line)
                    if any(tag in line for tag in ('[apklayer]', 'vktest:', '[hello-vulkan]', 'Exception', 'failed', 'SIGSEGV', 'fatal error')):
                        print(line, flush=True)
                    assert not any(tag in line for tag in ('FATAL EXCEPTION', 'fatal error has been detected')), line
            if finished: break
            if not captured and any('[hello-vulkan] PASS' in line for line in seen) and any('[hello-vulkan] FRAME' in line for line in seen) and any('window state active=true' in line for line in seen):
                assert sum('PASS cycle ' in line for line in seen) == 6
                assert any(('android_swapchain=1' in line and 'dma_buf=1 drm_modifier=1 semaphore_fd=1' in line) or 'android_swapchain=0' in line for line in seen)
                print('CAPTURE Vulkan probe result', flush=True)
                assert sys.stdin.readline().strip() == 'captured'
                subprocess.run(['sudo', '-S', '/tmp/uinput-keys', 'key:1'], input=b'1234\n', check=True,
                    stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
                captured = True
    assert captured and any('finished hello.vulkan.Main depth=0' in line for line in seen)
    print('PASS APK Vulkan readback, native capability reporting and physical Back cleanup', flush=True)
finally:
    if app is not None:
        try: os.killpg(app.pid, signal.SIGKILL)
        except ProcessLookupError: pass
        app.wait(); app.stdout.close()
    stop(); os.close(notify); intent.unlink(missing_ok=True)
    subprocess.run(['pkill', '-9', '-f', '[a]pklayer.host.Main'], check=False)

left = subprocess.run(['pgrep', '-af', '[a]pklayer.host.Main|[b]tload|[a]pklayer-shell'], capture_output=True, text=True)
assert left.returncode == 1, 'leaked runtime processes: ' + left.stdout
