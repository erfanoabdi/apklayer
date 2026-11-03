#!/usr/bin/env python3
"""Real MediaCodec Surface output, SurfaceFlinger layer, pixels and physical Back."""
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
ENV = dict(instance.ENV, APKLAYER_CODEC2='1', APKLAYER_MEDIA_SERVICES='1', APKLAYER_DEBUG_INPUT='1', APKLAYER_RUNTIME_DEBUG='1',
    APKLAYER_DISPLAY_WIDTH='480', APKLAYER_DISPLAY_HEIGHT='400',
    APKLAYER_JOPTS='-Dapklayer.nativemedia=true -Dapklayer.runms=20000')
ENV['APKLAYER_HOST_LIBS'] = ENV['APKLAYER_NATIVE'] + '/libapklayer-hal-graphics.so:' + ENV['APKLAYER_NATIVE'] + '/libapklayer-hal-media.so'

def stop():
    subprocess.run(['bin/apklayer', 'stop'], env=ENV, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

stop()
logs = Path(LOGDIR)
logs.mkdir(exist_ok=True)
libc = ctypes.CDLL(None, use_errno=True)
notify = libc.inotify_init1(os.O_NONBLOCK | os.O_CLOEXEC)
assert notify >= 0 and libc.inotify_add_watch(notify, os.fsencode(logs), 0x102) >= 0
app = None
seen = []
committed = captured = False
try:
    app = subprocess.Popen(['bin/apklayer', 'run', 'hello.nativesurface'], env=ENV,
        stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
    with selectors.DefaultSelector() as events, Path('/tmp/apklayer-native-surface.out').open('wb') as log:
        events.register(app.stdout, selectors.EVENT_READ)
        events.register(notify, selectors.EVENT_READ)
        pending = b''
        end = time.monotonic() + 25
        done = False
        while not done:
            ready = events.select(3)
            assert ready and time.monotonic() < end, 'native surface test stalled'
            for key, _ in ready:
                if key.fd == notify:
                    os.read(notify, 65536)
                    continue
                data = os.read(app.stdout.fileno(), 65536)
                if not data:
                    assert app.wait() == 0, 'native surface JVM failed'
                    done = True
                    break
                log.write(data)
                pending += data
                parts = pending.split(b'\n')
                pending = parts.pop()
                for raw in parts:
                    line = raw.decode(errors='replace')
                    seen.append(line)
                    if any(tag in line for tag in ('[hello-native-surface]', '[apklayer]', 'media JNI')):
                        print(line, flush=True)
                    assert '[hello-native-surface] FAIL' not in line, line
                    if '[hello-native-surface] AVC rendered pts=3600000366666' in line:
                        committed = True
            # Capture after the real HWUI commit callback and a visible active window.
            if committed and not captured and any('window state active=true' in s for s in seen):
                with open('/tmp/apklayer-native-surface-layers.txt', 'wb') as dump:
                    subprocess.run(['bin/apklayer', 'exec', 'libapklayer_servicetool.so', 'dump', 'SurfaceFlinger'], env=ENV, stdout=dump, stderr=subprocess.STDOUT, check=True)
                shot = instance.capture('native SurfaceView result')
                instance.keys(*['key:1'],
                    stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=True)
                captured = True
    text = '\n'.join(seen)
    assert '[runtime] Android media JNI registered' in text
    assert '[hello-native-surface] DECODE PASS' in text
    assert '[hello-native-surface] created valid=true' in text
    assert '[hello-native-surface] AVC PASS reordered 12 Surface pictures, exact PTS/EOS' in text
    assert '[hello-native-surface] AVC rendered pts=3600000366666' in text
    assert 'SurfaceView[hello.nativesurface' in Path('/tmp/apklayer-native-surface-layers.txt').read_text()
    assert committed and captured
    assert 'finished hello.nativesurface.Main depth=0' in text and 'depth=0' in text
    assert (logs / 'shell.log').read_text(errors='replace').count('dmabuf frame') > 0
    print('PASS native SurfaceView decode and physical Back', flush=True)
finally:
    stop()
    if app is not None:
        try:
            os.killpg(app.pid, signal.SIGKILL)
        except ProcessLookupError:
            pass
        app.wait()
        app.stdout.close()
    os.close(notify)
    (Path(ENV['APKLAYER_NATIVE']) / 'android-root/vendor/etc/vintf/manifest/apklayer-media.xml').unlink(missing_ok=True)
    left = instance.mine('[a]pklayer.host.Main|[b]tload|[a]pklayer-shell')
    assert not left, left
