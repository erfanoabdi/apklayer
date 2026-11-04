#!/usr/bin/env python3
"""Real APK JNI Vulkan readback, presentation capability matching the host device and physical Back."""
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
LOGDIR = instance.LOGDIR
env = dict(instance.ENV, APKLAYER_RUNTIME_DEBUG='1', APKLAYER_LOGCAT='1',
    APKLAYER_JOPTS='-Dapklayer.runms=20000')
intent = Path('/tmp/apklayer-vulkan-intent.properties')
intent.write_text('component=hello.vulkan.Main\n')
env['APKLAYER_INTENT_FILE'] = str(intent)

def stop():
    subprocess.run(['bin/apklayer', 'stop'], env=env, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

stop()
logs = Path(LOGDIR); logs.mkdir(exist_ok=True)
libc = ctypes.CDLL(None, use_errno=True)
notify = libc.inotify_init1(os.O_NONBLOCK | os.O_CLOEXEC)
assert notify >= 0 and libc.inotify_add_watch(notify, os.fsencode(logs), 0x102) >= 0
app = None
seen = []; captured = focused = False
try:
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
            if not focused and any('[hello-vulkan] FRAME' in line for line in seen):  # the shell's test input activates the window (the desktop's
                instance.inject('hello.vulkan', 'focus')                              # focus belongs to whichever instance mapped a window last)
                focused = True
            if not captured and any('[hello-vulkan] PASS' in line for line in seen) and any('[hello-vulkan] FRAME' in line for line in seen) and any('window state active=true' in line for line in seen):
                assert sum('PASS cycle ' in line for line in seen) == 6
                assert any(('android_swapchain=1' in line and 'dma_buf=1 drm_modifier=1 semaphore_fd=1' in line) or 'android_swapchain=0' in line for line in seen)
                instance.capture('Vulkan probe result')
                instance.inject('hello.vulkan', 'key:1')  # Back into this instance's window: other instances share the desktop's focus
                captured = True
    assert captured and any('finished hello.vulkan.Main depth=0' in line for line in seen)
    print('PASS APK Vulkan readback, native capability reporting and physical Back cleanup', flush=True)
finally:
    if app is not None:
        try: os.killpg(app.pid, signal.SIGKILL)
        except ProcessLookupError: pass
        app.wait(); app.stdout.close()
    stop(); os.close(notify); intent.unlink(missing_ok=True)
    instance.kill('[a]pklayer.host.Main')

left = instance.mine('[a]pklayer.host.Main|[b]tload|[a]pklayer-shell')
assert not left, 'leaked runtime processes: ' + '\n'.join(left)
