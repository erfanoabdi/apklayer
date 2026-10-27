#!/usr/bin/env python3
"""Real MediaCodec APK, committed GPU frame, capture handshake and physical Back."""
import ctypes
import os
from pathlib import Path
import selectors
import signal
import subprocess
import sys
import time

os.chdir(Path.home() / 'apklayer-m1')
ENV = dict(os.environ, XDG_RUNTIME_DIR='/run/user/1000', APKLAYER_NATIVE=str(Path.home() / 'arm64-native'),
    APKLAYER_CODEC2='1', APKLAYER_MEDIA_SERVICES='1', APKLAYER_DEBUG_INPUT='1', APKLAYER_RUNTIME_DEBUG='1',
    APKLAYER_DISPLAY_WIDTH='480', APKLAYER_DISPLAY_HEIGHT='400',
    APKLAYER_JOPTS='-Dapklayer.nativemedia=true -Dapklayer.runms=20000')
ENV['APKLAYER_HOST_LIBS'] = ENV['APKLAYER_NATIVE'] + '/libapklayer-hal-graphics.so:' + ENV['APKLAYER_NATIVE'] + '/libapklayer-hal-media.so'

def stop():
    subprocess.run(['bin/apklayer', 'stop'], env=ENV, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    subprocess.run(['pkill', '-9', '-x', 'tail'], check=False)

stop()
logs = Path('/tmp/apklayer-stack')
logs.mkdir(exist_ok=True)
libc = ctypes.CDLL(None, use_errno=True)
notify = libc.inotify_init1(os.O_NONBLOCK | os.O_CLOEXEC)
assert notify >= 0 and libc.inotify_add_watch(notify, os.fsencode(logs), 0x102) >= 0
app = None
seen = []
committed = captured = False
try:
    subprocess.run(['gcc', '-O1', '-o', '/tmp/uinput-keys', 'tests/vm/uinput-keys.c'], check=True)
    app = subprocess.Popen(['bin/apklayer', 'run', 'hello.nativecodec'], env=ENV,
        stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
    with selectors.DefaultSelector() as events, Path('/tmp/apklayer-native-media.out').open('wb') as log:
        events.register(app.stdout, selectors.EVENT_READ)
        events.register(notify, selectors.EVENT_READ)
        pending = b''
        end = time.monotonic() + 25
        done = False
        while not done:
            ready = events.select(3)
            assert ready and time.monotonic() < end, 'native media test stalled'
            for key, _ in ready:
                if key.fd == notify:
                    os.read(notify, 65536)
                    continue
                data = os.read(app.stdout.fileno(), 65536)
                if not data:
                    assert app.wait() == 0, 'native media JVM failed'
                    done = True
                    break
                log.write(data)
                pending += data
                parts = pending.split(b'\n')
                pending = parts.pop()
                for raw in parts:
                    line = raw.decode(errors='replace')
                    seen.append(line)
                    if any(tag in line for tag in ('[hello-native-codec]', '[apklayer]', 'media JNI')):
                        print(line, flush=True)
                    assert '[hello-native-codec] FAIL' not in line, line
                    if '[hello-native-codec] FRAME PASS' in line:
                        committed = True
            # Capture after the real HWUI commit callback and a visible active window.
            if committed and not captured and any('window state active=true' in s for s in seen):
                print('CAPTURE native MediaCodec result', flush=True)
                assert sys.stdin.readline().strip() == 'captured', 'capture not acknowledged'
                subprocess.run(['sudo', '-S', '/tmp/uinput-keys', 'key:1'], input=b'1234\n',
                    stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=True)
                captured = True
    text = '\n'.join(seen)
    assert '[runtime] Android media JNI registered' in text
    assert '[hello-native-codec] startup discovery PASS' in text
    assert '[hello-native-codec] PASS native MediaCodec decoded 3 YUV frames with PTS/EOS' in text
    assert '[hello-native-codec] codec=c2.apklayer.vp9.decoder' in text
    assert text.count('[hello-native-codec] PASS native MediaCodec decoded 3 YUV frames with PTS/EOS') == 2
    assert '[hello-native-codec] PASS native MediaCodec decoded 4 YUV frames with PTS/EOS' in text
    assert '[hello-native-codec] AVC flush PASS' in text
    assert '[hello-native-codec] AVC PASS reordered 12 YUV pictures, exact PTS/EOS/release' in text
    assert '[hello-native-codec] extractor PASS MP4 tracks, seek, 12 decoded YUV pictures/PTS/EOS' in text
    assert 'media: registered extractor service' in (logs / 'extractor.log').read_text(errors='replace')
    assert '[hello-native-codec] extractor FD PASS bounded offset, track/sample/seek, caller ownership/early close' in text
    assert committed and captured
    assert 'finished hello.nativecodec.Main depth=0' in text and 'depth=0' in text
    assert (logs / 'shell.log').read_text(errors='replace').count('dmabuf frame') > 0
    print('PASS APK MediaCodec JNI, real YUV/PTS/EOS, frame commit and physical Back', flush=True)
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
    left = subprocess.run(['pgrep', '-af', '[a]pklayer.host.Main|[b]tload|[a]pklayer-shell'], capture_output=True, text=True)
    assert left.returncode != 0, left.stdout
