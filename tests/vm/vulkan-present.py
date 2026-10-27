#!/usr/bin/env python3
"""VM side of tests/vm/vulkan-present.sh: hello.vulkan.Present presents solid frames through an Android Vulkan swapchain on a SurfaceView."""
import os
from pathlib import Path
import selectors
import signal
import subprocess
import sys
import time

os.chdir(Path.home() / 'apklayer-m1')
env = dict(os.environ, APKLAYER_VULKAN_PRESENT='1', XDG_RUNTIME_DIR='/run/user/1000', APKLAYER_NATIVE=str(Path.home() / 'arm64-native'),
    APKLAYER_RUNTIME_DEBUG='1', APKLAYER_LOGCAT='1', APKLAYER_JOPTS='-Dapklayer.nativemedia=true -Dapklayer.realbinder=true -Dapklayer.instancehub=true -Dapklayer.runms=25000')
intent = Path('/tmp/apklayer-vulkan-present-intent.properties')
intent.write_text('component=hello.vulkan.Present\n')
env['APKLAYER_INTENT_FILE'] = str(intent)


def stop():
    subprocess.run(['bin/apklayer', 'stop'], env=env, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    subprocess.run(['pkill', '-9', '-x', 'tail'], check=False)
    subprocess.run(['pkill', '-f', '[b]in/apklayer hub'], check=False)


stop()
app = None
seen = []
captured = False
try:
    app = subprocess.Popen(['bin/apklayer', 'run', 'hello.vulkan'], env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
    end = time.monotonic() + 60
    with selectors.DefaultSelector() as events, open('/tmp/apklayer-vulkan-present.out', 'wb') as log:
        events.register(app.stdout, selectors.EVENT_READ)
        pending = b''
        while True:
            ready = events.select(10)
            assert ready and time.monotonic() < end, 'Vulkan present stalled: ' + '\n'.join(seen[-15:])
            chunk = os.read(app.stdout.fileno(), 65536)
            if not chunk:
                break
            log.write(chunk); log.flush()
            lines = (pending + chunk).split(b'\n'); pending = lines.pop()
            for raw in lines:
                line = raw.decode(errors='replace'); seen.append(line)
                if any(tag in line for tag in ('vkswap:', '[hello-vulkan]', 'apklayer.vulkan', 'SIGSEGV', 'fatal error')):
                    print(line, flush=True)
                assert not any(tag in line for tag in ('FATAL EXCEPTION', 'fatal error has been detected')), line
                if not captured and 'vkswap: presented 60 frames' in line:
                    print('CAPTURE Vulkan swapchain frames', flush=True)
                    assert sys.stdin.readline().strip() == 'captured'
                    captured = True
    app.wait()
    assert captured, 'the swapchain never presented 60 frames'
    assert any('vkswap: PASS' in line for line in seen), 'presentation did not finish'
    print('PASS Vulkan swapchain presented frames on a SurfaceView', flush=True)
finally:
    if app is not None:
        try: os.killpg(app.pid, signal.SIGKILL)
        except ProcessLookupError: pass
        app.wait(); app.stdout.close()
    stop(); intent.unlink(missing_ok=True)
    subprocess.run(['pkill', '-9', '-f', '[a]pklayer.host.Main'], check=False)
