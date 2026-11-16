#!/usr/bin/env python3
"""A sound server started after the stack, and only a PulseAudio one. The stack starts with the PipeWire backend and no server at all (the audio HAL's
output is the silent stub), an app plays a native stream again every few seconds; then a PipeWire daemon with its own runtime directory starts, but only
its pulse socket is named to the stack (PULSE_SERVER; the stack's PIPEWIRE_REMOTE still points nowhere). The next stream the HAL starts must leave the stub,
fail the PipeWire backend and use the PulseAudio one (aosp/hals/audio/Stream.cpp: the per-stream backend table): the tone arrives at the new server's sink."""
import array
import ctypes
import os
from pathlib import Path
import selectors
import shutil
import subprocess
import sys
import tempfile
import time

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
import instance  # noqa: E402  (changes into the repo root)
logs = Path(instance.LOGDIR)
work = Path(tempfile.mkdtemp(prefix='apklayer-audioswap-'))
SINK = 'apklayer_swap_sink'
ENV = dict(instance.ENV, APKLAYER_DISPLAY_WIDTH='480', APKLAYER_DISPLAY_HEIGHT='400', PIPEWIRE_REMOTE='apklayer-no-server',
    PULSE_SERVER='unix:' + str(work / 'native'), APKLAYER_AUDIO_TARGET=SINK, APKLAYER_AUDIO_TARGET_IN=SINK + '.monitor',
    APKLAYER_JOPTS='-Dapklayer.nativemedia=true -Dapklayer.nativeaudio=true -Dapklayer.nativeparcels=true -Dapklayer.runms=120000 -Dhello.loop=1')
ENV['APKLAYER_HOST_LIBS'] = ':'.join(ENV['APKLAYER_NATIVE'] + '/libapklayer-' + n + '.so' for n in ('hal-graphics', 'hal-media', 'audio-pipewire'))
server = dict(os.environ, PIPEWIRE_RUNTIME_DIR=str(work), XDG_RUNTIME_DIR=str(work), PULSE_RUNTIME_PATH=str(work),
    DBUS_SESSION_BUS_ADDRESS='unix:path=/nonexistent/bus')  # the second daemon's own world
procs = []
libc = ctypes.CDLL(None)


def watch(path):
    fd = libc.inotify_init1(os.O_NONBLOCK | os.O_CLOEXEC)
    assert fd >= 0 and libc.inotify_add_watch(fd, os.fsencode(path), 0x102) >= 0  # IN_MODIFY | IN_CREATE
    return fd


def until(fd, condition, what, seconds=60, extra=()):
    """Waits (a select that wakes on the watched directory or an extra fd) until condition() holds."""
    end = time.monotonic() + seconds
    with selectors.DefaultSelector() as events:
        events.register(fd, selectors.EVENT_READ)
        for other in extra: events.register(other, selectors.EVENT_READ)
        while not condition():
            assert time.monotonic() < end, 'timed out: ' + (what() if callable(what) else what)
            for key, _ in events.select(2):
                if key.fd == fd: os.read(fd, 65536)
                else: yield key


def run(*a):
    for _ in until(*a): pass


def text(name):
    path = logs / name
    return path.read_text(errors='replace') if path.exists() else ''


def stop():
    subprocess.run(['bin/apklayer', 'stop'], env=ENV, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


stop()
logs.mkdir(parents=True, exist_ok=True)
for old in logs.glob('audio.log*'): old.unlink()  # a stale "silent" line from the last run must not end the wait
log_fd, work_fd = watch(logs), watch(work)
app = None
try:
    app = subprocess.Popen(['bin/apklayer', 'run', 'hello.nativeaaudio'], env=ENV, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, start_new_session=True)
    run(log_fd, lambda: 'playback is silent' in text('audio.log'), lambda: 'the silent output (no server yet): ' + text('audio.log')[-1500:], 90)
    assert 'a sound server answers now' not in text('audio.log')
    print('stack up, playing into the silent output', flush=True)
    for name in ('pipewire', 'wireplumber', 'pipewire-pulse'):  # WirePlumber links the streams; no D-Bus, so it leaves the desktop's devices alone
        procs.append(subprocess.Popen([name], env=server, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, start_new_session=True))
    run(work_fd, lambda: (work / 'native').exists(), 'the pulse socket of the new server')
    ctl = ['pactl', '--server', 'unix:' + str(work / 'native')]
    created = lambda: subprocess.run(ctl + ['load-module', 'module-null-sink', 'sink_name=' + SINK, 'rate=48000', 'channels=2'],
        env=server, capture_output=True, text=True)
    sink = created()
    assert sink.returncode == 0, 'the new server has no null sink: ' + sink.stderr
    recorder = subprocess.Popen(['parec', '--server', 'unix:' + str(work / 'native'), '--device=' + SINK + '.monitor', '--format=s16le', '--rate=48000',
        '--channels=2', '--latency-msec=10', '--raw'], env=server, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, start_new_session=True)
    procs.append(recorder)
    print('a PulseAudio-only server is up; waiting for the next stream', flush=True)
    peak = 0
    def heard():
        global peak
        return peak > 1000
    for key in until(log_fd, heard, lambda: 'the tone on the new server (HAL log: %s)' % text('audio.log')[-1500:], 60, [recorder.stdout]):
        samples = array.array('h', os.read(recorder.stdout.fileno(), 65536)[:65536 // 2 * 2])
        peak = max([peak, *map(abs, samples)])
    log = text('audio.log')
    assert 'a sound server answers now' in log, log[-1500:]
    print('PASS the next stream left the silent output for the PulseAudio-only server (peak %d)' % peak, flush=True)
finally:
    for p in [app, *reversed(procs)]:
        if p is not None and p.poll() is None:
            os.killpg(p.pid, 15)
            try: p.wait(timeout=5)
            except subprocess.TimeoutExpired: os.killpg(p.pid, 9)
    stop()
    shutil.rmtree(work, ignore_errors=True)
