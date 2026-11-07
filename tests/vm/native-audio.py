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
ENV = dict(instance.ENV, APKLAYER_DEBUG_INPUT='1', APKLAYER_RUNTIME_DEBUG='1',
    APKLAYER_DISPLAY_WIDTH='480', APKLAYER_DISPLAY_HEIGHT='400',
    APKLAYER_JOPTS='-Dapklayer.nativeattribution=true -Dapklayer.runms=90000')
extra = Path.home() / '.apklayer-extra-jopts'  # regression runs with a feature flag (e.g. -Dapklayer.realbinder=true): the file holds the extra options
if extra.exists(): ENV['APKLAYER_JOPTS'] += ' ' + extra.read_text().strip()
ENV['APKLAYER_HOST_LIBS'] = ENV['APKLAYER_NATIVE'] + '/libapklayer-hal-graphics.so:' + ENV['APKLAYER_NATIVE'] + '/libapklayer-hal-media.so'

late = sys.argv[1:] == ['late']  # installed while the hub runs: audioserver must learn the package (HubAudioPermissions.registered)
audio_mode = late or sys.argv[1:] == ['audio']
capture = None
if audio_mode:
    ENV['APKLAYER_JOPTS'] += ' -Dapklayer.nativemedia=true -Dapklayer.nativeaudio=true -Dapklayer.nativeparcels=true -Dhello.rate=1 -Dhello.record=1'
    ENV['APKLAYER_HOST_LIBS'] += ':' + ENV['APKLAYER_NATIVE'] + '/libapklayer-audio-pipewire.so'


def stop():
    subprocess.run(['bin/apklayer', 'stop'], env=ENV, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

stop()
logs = Path(LOGDIR)
logs.mkdir(exist_ok=True)
libc = ctypes.CDLL(None, use_errno=True)
notify = libc.inotify_init1(os.O_NONBLOCK | os.O_CLOEXEC)
assert notify >= 0 and libc.inotify_add_watch(notify, os.fsencode(logs), 0x102) >= 0
app = hub = None
seen = []
committed = captured = focused = False
try:
    if audio_mode:
        capture = AudioCapture(ENV)
        ENV['APKLAYER_AUDIO_TARGET'] = capture.name
        ENV['APKLAYER_AUDIO_TARGET_IN'] = capture.name
    if late:
        subprocess.run(['bin/apklayer', 'uninstall', 'hello.nativeaudio'], env=ENV, stdout=subprocess.DEVNULL, check=True)
        hub_log = Path(os.environ.get('APKLY_OUT', '/tmp')) / 'late-hub.log'  # not in the logs: the stack's start renews them
        hub_log.parent.mkdir(parents=True, exist_ok=True)
        with hub_log.open('wb') as out:
            hub = subprocess.Popen(['bin/apklayer', 'hub'], env=ENV, stdout=out, stderr=subprocess.STDOUT, start_new_session=True)
        subprocess.run(['bin/apklayer', 'wait', 'hub'], env=ENV, check=True, timeout=30)
        out = subprocess.run(['bin/apklayer', 'install', '.tests/apks/hello-native-audio.apk'], env=ENV, capture_output=True, text=True)
        assert 'installed' in out.stdout + out.stderr, out.stdout[-2000:] + out.stderr[-2000:]
    app = subprocess.Popen(['bin/apklayer', 'run', 'hello.nativeaudio'], env=ENV,
        stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
    with selectors.DefaultSelector() as events, Path(os.environ.get('APKLY_OUT', '/tmp') + '/apklayer-native-audio.out').open('wb') as log:
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
                    rc = app.wait()
                    assert rc == 0, 'native media JVM failed: status %d' % rc
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
            # the shell's test input activates the window (the desktop's focus belongs to whichever instance mapped a window last)
            if committed and not focused:
                instance.inject('hello.nativeaudio', 'focus')
                focused = True
            if committed and not captured and any('window state active=true' in s for s in seen):
                instance.capture('native AudioTrack JNI result' if audio_mode else 'native attribution result', 'hello.nativeaudio')
                instance.inject('hello.nativeaudio', 'key:1')  # Back into this instance's window: other instances share the desktop's focus
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
        assert '[hello-native-audio] COMM PASS' in text  # devices, communication device, a call's playback and capture
        assert '[hello-native-audio] MUTE PASS silent while muted' in text  # setMicrophoneMute: AudioFlinger's mute, the HAL's silent capture
        assert '[hello-native-audio] CALL PASS' in text  # setMode and setCommunicationDevice reach the policy through the hub (HubCallAudio)
        assert '[hello-native-audio] VOIP PASS' in text  # WebRTC's call audio: direct buffers with an array, mono capture, effects, ByteBuffer I/O
        capture.verify()
    if late:
        hub_text = hub_log.read_text(errors='replace')  # fed again when the app registered (the hub's start knew no hello.nativeaudio)
        assert 'audio permissions fed' in hub_text[hub_text.index('registered hello.nativeaudio'):], 'the late install was not fed to audioserver'
    assert committed and captured
    assert 'finished hello.nativeaudio.Main depth=0' in text and 'depth=0' in text
    assert (logs / 'shell.log').read_text(errors='replace').count('dmabuf frame') > 0
    print('PASS APK ' + ('installed while the hub ran: ' if late else '') + ('AudioTrack JNI/PCM/clock/callbacks and attribution' if audio_mode else 'real attribution Parcel fields/identity/ownership') + ', frame commit and physical Back', flush=True)
finally:
    stop()
    if app is not None:
        try:
            os.killpg(app.pid, signal.SIGKILL)
        except ProcessLookupError:
            pass
        app.wait()
        app.stdout.close()
    if hub is not None: hub.wait(timeout=10)
    if capture: capture.close()
    os.close(notify)
    for name in ('apklayer-media.xml', 'apklayer-audio.xml', 'apklayer-audio-effects.xml'):
        (Path(ENV['APKLAYER_NATIVE']) / 'android-root/vendor/etc/vintf/manifest' / name).unlink(missing_ok=True)
    instance.kill('[a]pklayer.host.Main --hub')  # the instance hub (auto-started with the hub flags) also ends by itself when idle
    left = instance.mine('[a]pklayer.host.Main|[b]tload|[a]pklayer-shell')
    assert not left, left
