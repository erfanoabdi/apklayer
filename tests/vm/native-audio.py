#!/usr/bin/env python3
"""Native attribution/Parcel/audio APK, committed GPU frame and physical Back."""
import ctypes
import os
from pathlib import Path
import selectors
import signal
import subprocess
import sys
import time
from audio_capture import AudioCapture

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
import instance  # noqa: E402  (changes into the repo root)
LOGDIR = instance.LOGDIR
ENV = dict(instance.ENV, APKLAYER_CODEC2='0', APKLAYER_MEDIA_SERVICES='0', APKLAYER_DEBUG_INPUT='1', APKLAYER_RUNTIME_DEBUG='1',
    APKLAYER_DISPLAY_WIDTH='480', APKLAYER_DISPLAY_HEIGHT='400',
    APKLAYER_JOPTS='-Dapklayer.nativeattribution=true -Dapklayer.runms=90000')
extra = Path.home() / '.apklayer-extra-jopts'  # regression runs with a feature flag (e.g. -Dapklayer.realbinder=true): the file holds the extra options
if extra.exists(): ENV['APKLAYER_JOPTS'] += ' ' + extra.read_text().strip()
ENV['APKLAYER_HOST_LIBS'] = ENV['APKLAYER_NATIVE'] + '/libapklayer-hal-graphics.so:' + ENV['APKLAYER_NATIVE'] + '/libapklayer-hal-media.so'

audio_mode = sys.argv[1:] == ['audio']
capture = None
if audio_mode:
    ENV.update(APKLAYER_AUDIO='1', APKLAYER_AUDIO_SERVICES='1', APKLAYER_MEDIA_SERVICES='1', APKLAYER_CODEC2='1')  # the recorder's encoder is a Codec2 component
    ENV['APKLAYER_JOPTS'] += ' -Dapklayer.nativemedia=true -Dapklayer.nativeaudio=true -Dapklayer.nativeparcels=true -Dhello.rate=1 -Dhello.record=1'
    ENV['APKLAYER_HOST_LIBS'] += ':' + ENV['APKLAYER_NATIVE'] + '/libapklayer-hal-audio.so'


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
    if audio_mode:
        capture = AudioCapture(ENV)
        ENV['APKLAYER_AUDIO_TARGET'] = capture.name
        ENV['APKLAYER_AUDIO_TARGET_IN'] = capture.name
    app = subprocess.Popen(['bin/apklayer', 'run', 'hello.nativeaudio'], env=ENV,
        stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
    with selectors.DefaultSelector() as events, Path('/tmp/apklayer-native-audio.out').open('wb') as log:
        events.register(app.stdout, selectors.EVENT_READ)
        events.register(notify, selectors.EVENT_READ)
        if capture: capture.register(events)
        pending = b''
        end = time.monotonic() + 100
        done = False
        while not done:
            ready = events.select(3)
            assert ready and time.monotonic() < end, 'native media test stalled'
            for key, _ in ready:
                if capture and capture.owns(key):
                    capture.read(key)
                    continue
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
                    if any(tag in line for tag in ('[hello-native-audio]', '[apklayer]', 'attribution Parcel', 'audio JNI')):
                        print(line, flush=True)
                    assert '[hello-native-audio] FAIL' not in line, line
                    if '[hello-native-audio] FRAME PASS' in line:
                        committed = True
            # Capture after the real HWUI commit callback and a visible active window.
            if committed and not captured and any('window state active=true' in s for s in seen):
                shot = instance.capture(('native AudioTrack JNI result' if audio_mode else 'native attribution result'))
                instance.keys(*['key:1'],
                    stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=True)
                captured = True
    text = '\n'.join(seen)
    assert '[runtime] native attribution Parcel adapter registered' in text
    assert '[hello-native-audio] attribution PASS fields/Unicode/chain/tokens/scoped release' in text
    if audio_mode:
        assert '[runtime] original Android audio JNI registered' in text
        assert '[hello-native-audio] native Parcel PASS' in text
        assert '[hello-native-audio] startup AudioTrack PASS' in text
        assert '[hello-native-audio] AudioTrack PASS PCM/marker/timestamp=' in text
        assert '[hello-native-audio] RATE PASS' in text
        assert '[hello-native-audio] RECORD PASS' in text
        assert '[hello-native-audio] RECORDER PASS' in text
        capture.verify()
    assert committed and captured
    assert 'finished hello.nativeaudio.Main depth=0' in text and 'depth=0' in text
    assert (logs / 'shell.log').read_text(errors='replace').count('dmabuf frame') > 0
    print('PASS APK ' + ('AudioTrack JNI/PCM/clock/callbacks and attribution' if audio_mode else 'real attribution Parcel fields/identity/ownership') + ', frame commit and physical Back', flush=True)
finally:
    stop()
    if app is not None:
        try:
            os.killpg(app.pid, signal.SIGKILL)
        except ProcessLookupError:
            pass
        app.wait()
        app.stdout.close()
    if capture: capture.close()
    os.close(notify)
    for name in ('apklayer-media.xml', 'apklayer-audio.xml', 'apklayer-audio-effects.xml'):
        (Path(ENV['APKLAYER_NATIVE']) / 'android-root/vendor/etc/vintf/manifest' / name).unlink(missing_ok=True)
    instance.kill('[a]pklayer.host.Main --hub')  # the instance hub (auto-started with the hub flags) also ends by itself when idle
    left = instance.mine('[a]pklayer.host.Main|[b]tload|[a]pklayer-shell')
    assert not left, left
