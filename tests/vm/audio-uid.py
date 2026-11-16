#!/usr/bin/env python3
"""Per-uid call audio (patches/aosp/frameworks/av/0004, HubCallAudio): hello.nativeaaudio plays a VOICE_COMMUNICATION stream natively (AAudio, then OpenSL ES)
and asks for MODE_IN_COMMUNICATION; hello.nativeaaudio2 only asks for it, later. Neither tells the hub about a player. The newer requester owns the
mode for its 6 s grace; then the policy's per-uid activity says only the first one plays, so the owner goes back to it (the instance-wide answer, a
stream active somewhere, kept the silent one). Reads the hub log's "audio mode 3 of <package>" lines: first, second, first."""
import ctypes
import os
from pathlib import Path
import re
import selectors
import subprocess
import sys
import time

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
import instance  # noqa: E402  (changes into the repo root)
sys.path.insert(0, 'tests/vm')
from audio_capture import AudioCapture  # noqa: E402
ENV = dict(instance.ENV, APKLAYER_DISPLAY_WIDTH='480', APKLAYER_DISPLAY_HEIGHT='400', APKLAYER_RUNTIME_DEBUG='1')
ENV['APKLAYER_HOST_LIBS'] = ':'.join(ENV['APKLAYER_NATIVE'] + '/libapklayer-' + n + '.so' for n in ('hal-graphics', 'hal-media', 'audio-pipewire'))
logs = Path(instance.LOGDIR)


def stop():
    subprocess.run(['bin/apklayer', 'stop'], env=ENV, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


def owners():
    hub = logs / 'hub.log'
    return re.findall(r'audio mode 3 of (hello\.nativeaaudio2?) pid', hub.read_text(errors='replace')) if hub.exists() else []


def run_app(package, marker, env):
    """Starts the app; returns it once its output has the marker line (event driven: the select wakes on the app's output)."""
    app = subprocess.Popen(['bin/apklayer', 'run', package], env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
    end = time.monotonic() + 60
    text = b''
    with selectors.DefaultSelector() as events:
        events.register(app.stdout, selectors.EVENT_READ)
        while marker not in text:
            assert time.monotonic() < end, package + ' did not print ' + marker.decode() + ': ' + text.decode(errors='replace')[-2000:]
            for _ in events.select(3):
                data = os.read(app.stdout.fileno(), 65536)
                assert data, package + ' ended: ' + text.decode(errors='replace')[-2000:]
                text += data
                assert b'FAIL' not in text, text.decode(errors='replace')[-2000:]
    return app


libc = ctypes.CDLL(None)
notify = libc.inotify_init1(os.O_NONBLOCK | os.O_CLOEXEC)
logs.mkdir(parents=True, exist_ok=True)
assert notify >= 0 and libc.inotify_add_watch(notify, os.fsencode(logs), 0x102) >= 0  # IN_MODIFY | IN_CREATE
events = selectors.DefaultSelector()
events.register(notify, selectors.EVENT_READ)
for api in ('aaudio', 'opensl'):
    stop()
    capture = AudioCapture(ENV)
    env = dict(ENV, APKLAYER_AUDIO_TARGET=capture.name, APKLAYER_AUDIO_TARGET_IN=capture.name,
        APKLAYER_JOPTS='-Dapklayer.nativemedia=true -Dapklayer.nativeaudio=true -Dapklayer.nativeparcels=true -Dapklayer.runms=60000 -Dhello.api=' + api)
    apps = []
    try:
        apps.append(run_app('hello.nativeaaudio', b'PLAYING', env))
        assert owners() == ['hello.nativeaaudio'], owners()
        apps.append(run_app('hello.nativeaaudio2', b'CALLER mode 3', env))
        end = time.monotonic() + 25  # the caller's 6 s grace, then the hub's recheck
        want = ['hello.nativeaaudio', 'hello.nativeaaudio2', 'hello.nativeaaudio']
        while owners() != want:
            assert time.monotonic() < end and len(owners()) <= 3, 'call mode owners (%s): %s' % (api, owners())
            if events.select(1): os.read(notify, 65536)  # wakes on a write to the logs
        print('PASS %s: call mode owners %s' % (api, owners()), flush=True)
    finally:
        for app in apps:
            if app.poll() is None: os.killpg(app.pid, 15)
        stop()
        capture.close()
