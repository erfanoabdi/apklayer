#!/usr/bin/env python3
"""Original native MediaPlayer, Parcel callbacks, Surface pixels and physical Back."""
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
ENV = dict(os.environ, XDG_RUNTIME_DIR='/run/user/1000', APKLAYER_NATIVE=str(Path.home() / 'arm64-native'),
    APKLAYER_CODEC2='1', APKLAYER_MEDIA_SERVICES='1', APKLAYER_AUDIO='1', APKLAYER_AUDIO_SERVICES='1', APKLAYER_NATIVE_MEDIA_PARCELS='1', APKLAYER_DEBUG_INPUT='1', APKLAYER_RUNTIME_DEBUG='1',
    APKLAYER_DISPLAY_WIDTH='480', APKLAYER_DISPLAY_HEIGHT='400',
    APKLAYER_JOPTS='-Dapklayer.nativemedia=true -Dapklayer.nativeaudio=true -Dapklayer.nativeparcels=true -Dapklayer.nativeplayer=true -Dhello.asset=' + os.environ.get('PLAYER_ASSET', 'red-h264.mp4') + ' -Dapklayer.runms=20000')
extra = Path.home() / '.apklayer-extra-jopts'  # regression runs with a feature flag (e.g. -Dapklayer.realbinder=true): the file holds the extra options
if extra.exists(): ENV['APKLAYER_JOPTS'] += ' ' + extra.read_text().strip()
ENV['APKLAYER_HOST_LIBS'] = ENV['APKLAYER_NATIVE'] + '/libapklayer-hal-graphics.so:' + ENV['APKLAYER_NATIVE'] + '/libapklayer-hal-media.so:' + ENV['APKLAYER_NATIVE'] + '/libapklayer-hal-audio.so'

def stop():
    subprocess.run(['bin/apklayer', 'stop'], env=ENV, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    subprocess.run(['pkill', '-9', '-x', 'tail'], check=False)

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
    subprocess.run(['gcc', '-O1', '-o', '/tmp/uinput-keys', 'tests/vm/uinput-keys.c'], check=True)
    app = subprocess.Popen(['bin/apklayer', 'run', 'hello.nativeplayer'], env=ENV,
        stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
    with selectors.DefaultSelector() as events, Path('/tmp/apklayer-native-player.out').open('wb') as log:
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
                    if any(tag in line for tag in ('[hello-native-player]', '[apklayer]', 'media JNI')):
                        print(line, flush=True)
                    assert '[hello-native-player] FAIL' not in line, line
                    if '[hello-native-player] PLAYER PASS' in line:
                        committed = True
            # Capture after the real HWUI commit callback and a visible active window.
            if committed and not captured and any('window state active=true' in s for s in seen):
                with open('/tmp/apklayer-native-player-layers.txt', 'wb') as dump:
                    subprocess.run(['b/cli/apklayer', 'exec', 'libapklayer_servicetool.so', 'dump', 'SurfaceFlinger'], env=ENV, stdout=dump, stderr=subprocess.STDOUT, check=True)
                print('CAPTURE native SurfaceView result', flush=True)
                assert sys.stdin.readline().strip() == 'captured', 'capture not acknowledged'
                subprocess.run(['sudo', '-n', '/tmp/uinput-keys', 'key:1'],
                    stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=True)
                captured = True
    text = '\n'.join(seen)
    assert '[runtime] Android media JNI registered' in text
    asset = os.environ.get('PLAYER_ASSET', 'red-h264.mp4')
    expected_tracks = 2 if asset == 'red-tone.mp4' else 1
    import re
    assert re.search(r'\[hello-native-player\] prepared tracks=%d duration=4\d\d' % expected_tracks, text)
    if expected_tracks == 2:  # audio really reached the audio stack: the player's AudioTrack delivered the decoded PCM
        import re as _re
        delivered = [int(n) for n in _re.findall(r'AudioTrack: stop\(\d+\): called with (\d+) frames delivered', Path(LOGDIR + '/media.log').read_text(errors='replace'))]
        assert delivered and max(delivered) >= 15000, 'audio frames delivered ' + str(delivered)
        assert 'audio.codec=c2.apklayer.aac.decoder' in text
        print('PASS native MediaPlayer audio: AAC decoded and %d frames delivered to AudioFlinger' % max(delivered), flush=True)
    assert '[hello-native-player] completion=2' in text
    assert '[hello-native-player] seek complete' in text
    assert '[hello-native-player] time=' in text
    assert '[hello-native-player] released' in text
    assert '[hello-native-player] created valid=true' in text
    assert '[hello-native-player] PLAYER PASS' in text
    assert 'SurfaceView[hello.nativeplayer' in Path('/tmp/apklayer-native-player-layers.txt').read_text()
    assert committed and captured
    assert 'finished hello.nativeplayer.Main depth=0' in text and 'depth=0' in text
    assert (logs / 'shell.log').read_text(errors='replace').count('dmabuf frame') > 0
    print('PASS native MediaPlayer video and physical Back', flush=True)
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
    for manifest in ('apklayer-media.xml', 'apklayer-audio.xml', 'apklayer-audio-effects.xml'):
        (Path(ENV['APKLAYER_NATIVE']) / 'android-root/vendor/etc/vintf/manifest' / manifest).unlink(missing_ok=True)
    subprocess.run(['pkill', '-9', '-f', '[a]pklayer.host.Main --hub'], check=False)  # the instance hub (auto-started with the hub flags) also ends by itself when idle
    left = subprocess.run(['pgrep', '-af', '[a]pklayer.host.Main|[b]tload|[a]pklayer-shell'], capture_output=True, text=True)
    assert left.returncode != 0, left.stdout
