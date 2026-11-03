#!/usr/bin/env python3
"""VM side of tests/vm/vulkan-present.sh: hello.vulkan.Present presents solid frames through an Android Vulkan swapchain on a SurfaceView."""
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
env = dict(instance.ENV, APKLAYER_VULKAN_PRESENT='1', APKLAYER_RUNTIME_DEBUG='1', APKLAYER_LOGCAT='1', APKLAYER_JOPTS='-Dapklayer.nativemedia=true -Dapklayer.realbinder=true -Dapklayer.instancehub=true -Dapklayer.runms=25000')
intent = Path('/tmp/apklayer-vulkan-present-intent.properties')
intent.write_text('component=hello.vulkan.Present\n')
env['APKLAYER_INTENT_FILE'] = str(intent)


def stop():
    subprocess.run(['bin/apklayer', 'stop'], env=env, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    instance.kill('[b]in/apklayer hub')


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
                    shot = instance.capture('Vulkan swapchain frames')
                    magenta = pixels.count(shot, lambda r, g, b: r > 200 and g < 60 and 100 < b < 160) / (lambda im: im.width * im.height)(pixels.rgb(shot))
                    print('IMAGE %s: %.0f%% magenta' % (shot, magenta * 100), flush=True)
                    assert magenta > 0.25, 'the presented frames are not on screen (%.0f%% magenta)' % (magenta * 100)
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
    instance.kill('[a]pklayer.host.Main')
