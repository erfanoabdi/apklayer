"""An owned real PipeWire sink/monitor and independent PCM verification."""
import array
import math
import os
from pathlib import Path
import selectors
import signal
import subprocess

class AudioCapture:
    def __init__(self, env):
        self.env = env
        self.name = 'apklayer_audio_check_' + str(os.getpid())
        self.pcm = bytearray()
        self.module = subprocess.check_output(['pactl', 'load-module', 'module-null-sink',
            'sink_name=' + self.name, 'rate=48000', 'channels=2'], env=env, text=True).strip()
        self.recorder = None
        try:
            self.recorder = subprocess.Popen(['parec', '--device=' + self.name + '.monitor',
                '--format=s16le', '--rate=48000', '--channels=2', '--latency-msec=10', '--raw'],
                env=env, stdout=subprocess.PIPE, stderr=subprocess.PIPE, start_new_session=True)
        except BaseException:
            self.close(); raise
    def register(self, events):
        events.register(self.recorder.stdout, selectors.EVENT_READ)
        events.register(self.recorder.stderr, selectors.EVENT_READ)
    def owns(self, key):
        return key.fileobj in (self.recorder.stdout, self.recorder.stderr)
    def read(self, key):
        data = os.read(key.fd, 65536)
        if key.fileobj is self.recorder.stderr:
            assert not data, data.decode(errors='replace')
        else:
            assert data, 'audio monitor exited'
            self.pcm.extend(data)
    def verify(self, minimum=47500):
        samples = array.array('h', self.pcm[:len(self.pcm) // 4 * 4])
        left, right = samples[::2], samples[1::2]
        def power(channel, hz):
            coefficient = 2 * math.cos(2 * math.pi * hz / 48000)
            first = second = 0.0
            for value in channel:
                current = value + coefficient * first - second
                second, first = first, current
            return first * first + second * second - coefficient * first * second
        assert power(left, 440) > power(left, 880) * 50, 'left tone lost/swapped'
        assert power(right, 880) > power(right, 440) * 50, 'right tone lost/swapped'
        active = sum(abs(l) + abs(r) > 100 for l, r in zip(left, right))
        assert active > minimum, f'PCM missing: {active} non-silent frames'
        print(f'PASS native app monitor PCM 440/880 Hz stereo, {active} non-silent frames', flush=True)
    def close(self):
        proc = self.recorder
        if proc is not None:
            if proc.poll() is None: os.killpg(proc.pid, signal.SIGTERM)
            try: proc.wait(timeout=3)
            except subprocess.TimeoutExpired:
                os.killpg(proc.pid, signal.SIGKILL); proc.wait()
            proc.stdout.close(); proc.stderr.close()
        subprocess.run(['pactl', 'unload-module', self.module], env=self.env, check=True)
