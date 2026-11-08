#!/usr/bin/env python3
"""No sound server for this instance (PipeWire and PulseAudio point nowhere): the original AudioTrack still plays, into the audio HAL's
silent output (AOSP's DriverStubImpl), which takes PCM at real time; the HAL says once why it is silent."""
import os
from pathlib import Path
import subprocess
import sys
import time

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
import instance  # noqa: E402  (changes into the repo root)
env = dict(instance.ENV, PIPEWIRE_REMOTE='apklayer-no-server', PULSE_SERVER='unix:/nonexistent/apklayer-no-pulse',
    APKLAYER_HOST_LIBS=f'{instance.NATIVE}/libapklayer-hal-graphics.so:{instance.NATIVE}/libapklayer-audio-pipewire.so')
logs = Path(instance.LOGDIR)
tool = logs / 'tool.log'
tool.unlink(missing_ok=True)
stack = subprocess.Popen(['bin/apklayer', 'stack', 'libapklayer_audioserver.so', '--track'], env=env,
    stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, start_new_session=True)
try:
    instance.wait_file(tool, 'submitted=9600\n', 60)
    start = time.monotonic()
    instance.wait_file(tool, 'submitted=57600\n', 10)
    elapsed = time.monotonic() - start
    text = instance.wait_file(tool, 'PASS original AudioTrack', 10)
    # 48000 frames at 48 kHz: one second, less what the track's and the mixer's buffers took up front (a few thousand frames)
    assert 0.8 < elapsed < 1.6, f'AudioTrack writes not at real time: 48000 frames in {elapsed:.2f} s'
    print(f'audio: 48000 frames written in {elapsed:.2f} s', flush=True)
    print(next(line for line in text.splitlines() if line.startswith('PASS original AudioTrack')), flush=True)
    log = (logs / 'audio.log').read_text(errors='replace')
    line = next((line for line in log.splitlines() if '[audio] no sound server answers' in line), None)
    assert line, 'no silent-output log line in audio.log: ' + log[-2000:]
    assert log.count('[audio] no sound server answers') <= 2, 'the silent-output line is not logged once'  # once for playback, once for recording
    print(line, flush=True)
    assert stack.wait(timeout=10) == 0, 'the stack failed'
    print('PASS AudioTrack plays into a silent output at real time without a sound server', flush=True)
finally:
    if stack.poll() is None: os.killpg(stack.pid, 15); stack.wait(timeout=5)
    subprocess.run(['bin/apklayer', 'stop'], env=env, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
