#!/usr/bin/env python3
"""Real MediaCodec APK, committed GPU frame, capture handshake and Back (the shell's test input)."""
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
ENV = dict(instance.ENV, APKLAYER_DEBUG_INPUT='1', APKLAYER_RUNTIME_DEBUG='1',
    APKLAYER_DISPLAY_WIDTH='480', APKLAYER_DISPLAY_HEIGHT='400',
    APKLAYER_JOPTS='-Dapklayer.nativemedia=true -Dapklayer.runms=90000')
extra = Path.home() / '.apklayer-extra-jopts'  # regression runs with a feature flag (e.g. -Dapklayer.realbinder=true): the file holds the extra options
if extra.exists(): ENV['APKLAYER_JOPTS'] += ' ' + extra.read_text().strip()
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
committed = captured = focused = False
try:
    app = subprocess.Popen(['bin/apklayer', 'run', 'hello.nativecodec'], env=ENV,
        stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
    with selectors.DefaultSelector() as events, Path('/tmp/apklayer-native-media.out').open('wb') as log:
        events.register(app.stdout, selectors.EVENT_READ)
        events.register(notify, selectors.EVENT_READ)
        pending = b''
        end = time.monotonic() + 100
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
            # Capture after the real HWUI commit callback and a visible active window: the shell's test input activates it (the desktop's
            # focus belongs to whichever instance mapped a window last)
            if committed and not focused:
                instance.inject('hello.nativecodec', 'focus')
                focused = True
            if committed and not captured and any('window state active=true' in s for s in seen):
                instance.capture('native MediaCodec result', 'hello.nativecodec')
                instance.inject('hello.nativecodec', 'key:1')  # Esc = Back
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
    assert '[hello-native-codec] AAC PASS' in text
    for asset in ('tone.mp3', 'tone-vorbis.ogg', 'tone-opus.ogg', 'tone.flac', 'tone.wav'):
        assert '[hello-native-codec] AUDIO PASS ' + asset in text, asset
    for asset in ('red-hevc.mp4', 'red-mpeg4.mp4', 'red-av1.webm', 'red-h263.3gp', 'red-mpeg2.ts'):
        assert '[hello-native-codec] VIDEO PASS ' + asset in text, asset
    for kind in ('audio/mp4a-latm', 'audio/opus', 'audio/flac', 'video/avc'):
        assert '[hello-native-codec] ENCODER PASS ' + kind in text, kind
    for asset in ('tone-mulaw.wav', 'tone-alaw.wav', 'tone-amrwb.awb', 'zeros-amrnb.amr'):
        assert '[hello-native-codec] AUDIO PASS ' + asset in text, asset
    print('PASS APK MediaCodec JNI, real YUV/PTS/EOS, frame commit and Back', flush=True)
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
    instance.kill('[a]pklayer.host.Main --hub')  # the instance hub (auto-started with the hub flags) also ends by itself when idle
    left = instance.mine('[a]pklayer.host.Main|[b]tload|[a]pklayer-shell')
    assert not left, left
