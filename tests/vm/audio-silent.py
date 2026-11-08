#!/usr/bin/env python3
"""No sound server for this instance (PipeWire and PulseAudio point nowhere): the original AudioTrack still plays, into the audio HAL's
silent output (AOSP's DriverStubImpl), which takes PCM at real time; the HAL says once why it is silent."""
import ctypes
import os
from pathlib import Path
import selectors
import subprocess
import sys
import time

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
import instance  # noqa: E402  (changes into the repo root)
env = dict(instance.ENV, PIPEWIRE_REMOTE='apklayer-no-server', PULSE_SERVER='unix:/nonexistent/apklayer-no-pulse')
logs = Path(instance.LOGDIR)
logs.mkdir(parents=True, exist_ok=True)
tool = logs / 'tool.log'
tool.unlink(missing_ok=True)
libc = ctypes.CDLL(None)
notify = libc.inotify_init1(os.O_NONBLOCK | os.O_CLOEXEC)
assert notify >= 0 and libc.inotify_add_watch(notify, os.fsencode(logs), 0x102) >= 0  # IN_MODIFY | IN_CREATE
stack = subprocess.Popen(['bin/apklayer', 'stack', 'libapklayer_audioserver.so', '--track'], env=env,
    stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, start_new_session=True)
created = None  # when the track's first log line (stderr, unbuffered) appears; the probe's stdout reaches the file only at its exit
try:
    end = time.monotonic() + 60
    with selectors.DefaultSelector() as events:
        events.register(notify, selectors.EVENT_READ)
        while stack.poll() is None:
            assert time.monotonic() < end, 'the AudioTrack probe did not finish in 60 s'
            if events.select(3): os.read(notify, 65536)
            if created is None and tool.exists() and 'AudioTrack' in tool.read_text(errors='replace'): created = time.monotonic()
    elapsed = time.monotonic() - created if created else 0
    text = tool.read_text(errors='replace')
    assert stack.returncode == 0 and 'PASS original AudioTrack' in text, 'the AudioTrack probe failed: ' + text[-3000:]
    print(next(line for line in text.splitlines() if line.startswith('PASS original AudioTrack')), flush=True)
    # the probe returns once 48000 frames were presented: a second at 48 kHz (an output that discards at once takes a fraction of it);
    # the upper end is loose: it includes the stack's teardown, slower beside other groups
    assert 0.95 < elapsed < 15, f'AudioTrack not paced at real time: {elapsed:.2f} s from the track to the end of the stack'
    print(f'audio: {elapsed:.2f} s from the track to the end of the stack (48000 frames presented)', flush=True)
    log = (logs / 'audio.log').read_text(errors='replace')
    lines = [line for line in log.splitlines() if '[audio] no sound server answers' in line]
    assert any('playback is silent' in line for line in lines), 'no silent-output log line in audio.log: ' + log[-2000:]
    assert len(lines) <= 2, 'the silent-output line is logged more than once per direction'
    print('\n'.join(lines), flush=True)
    print('PASS AudioTrack plays into a silent output at real time without a sound server', flush=True)
finally:
    os.close(notify)
    if stack.poll() is None: os.killpg(stack.pid, 15); stack.wait(timeout=5)
    subprocess.run(['bin/apklayer', 'stop'], env=env, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
