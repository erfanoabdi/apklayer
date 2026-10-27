#!/usr/bin/env python3
"""Own a real PipeWire test sink, monitor PCM, native producer and cleanup."""
import array
import math
import os
from pathlib import Path
import selectors
import signal
import sys
import subprocess
import time

os.chdir(Path.home() / 'apklayer-m1')
env = dict(os.environ, XDG_RUNTIME_DIR='/run/user/1000')
aidl = sys.argv[1:] == ['aidl']
mono = sys.argv[1:] == ['mono']
name = 'apklayer_audio_test_' + str(os.getpid())
module = subprocess.check_output(['pactl', 'load-module', 'module-null-sink',
    'sink_name=' + name, 'rate=48000', 'channels=2'], env=env, text=True).strip()
recorder = producer = None
pcm = bytearray()
try:
    recorder = subprocess.Popen(['parec', '--device=' + name + '.monitor', '--format=s16le',
        '--rate=48000', '--channels=2', '--latency-msec=10', '--raw'], env=env, stdout=subprocess.PIPE,
        stderr=subprocess.PIPE, start_new_session=True)
    with selectors.DefaultSelector() as events:
        events.register(recorder.stdout, selectors.EVENT_READ)
        events.register(recorder.stderr, selectors.EVENT_READ)
        deadline = time.monotonic() + (25 if aidl else 8)
        pending = b''
        done = False
        while not done:
            ready = events.select(3)
            assert ready and time.monotonic() < deadline, 'PipeWire audio test stalled'
            for key, _ in ready:
                data = os.read(key.fd, 65536)
                if key.fileobj is recorder.stderr:
                    assert not data, data.decode(errors='replace')
                elif key.fileobj is recorder.stdout:
                    assert data, 'monitor exited'
                    pcm.extend(data)
                    if producer is None:
                        print('Monitor receives real PipeWire PCM', flush=True)
                        producer_command = ['b/bridge/hals/audio/audio-test', name] + (['mono'] if mono else [])
                        if aidl:
                            env.update(APKLAYER_AUDIO_TARGET=name, APKLAYER_AUDIO='1')
                            producer_command = ['scripts/arm64-stack.sh', 'libapklayer_audio.so', '--probe']
                        producer = subprocess.Popen(producer_command, env=env,
                            stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
                        events.register(producer.stdout, selectors.EVENT_READ)
                else:
                    if not data:
                        status = producer.wait()
                        if status and aidl:
                            for logname in ('audio.log', 'tool.log'):
                                print((Path('/tmp/apklayer-stack') / logname).read_text(errors='replace')[-6000:], flush=True)
                        assert status == 0, 'native audio probe failed'
                        done = True
                        break
                    pending += data
                    lines = pending.split(b'\n'); pending = lines.pop()
                    for line in lines: print(line.decode(errors='replace'), flush=True)
    print(f'Captured {len(pcm)//4} monitor frames', flush=True)
    samples = array.array('h', pcm[:len(pcm) // 4 * 4])
    left, right = samples[::2], samples[1::2]
    def power(samples, hz):
        coefficient = 2 * math.cos(2 * math.pi * hz / 48000)
        first = second = 0.0
        for value in samples:
            current = value + coefficient * first - second
            second, first = first, current
        return first * first + second * second - coefficient * first * second
    assert power(left, 440) > power(left, 880) * 50, 'left channel tone lost/swapped'
    assert power(right, 440 if mono else 880) > power(right, 880 if mono else 440) * 50, 'right channel tone lost/swapped'
    active = sum(abs(l) + abs(r) > 100 for l, r in zip(left, right))
    if aidl:
        log = Path('/tmp/apklayer-stack/tool.log').read_text(errors='replace')
        print('\n'.join(line for line in log.splitlines() if line.startswith(('audio:', 'PASS audio'))), flush=True)
        assert 'PASS audio remote AIDL/FMQ' in log, 'AIDL PCM probe did not finish'
    assert active > (47500 if aidl else 52000), f'PCM missing: {active} non-silent frames'
    print(f'PASS monitor captured {"F32 mono 440" if mono else "S16 stereo 440/880"} Hz PCM, {active} non-silent frames', flush=True)
finally:
    for proc in (producer, recorder):
        if proc is None: continue
        if proc.poll() is None: os.killpg(proc.pid, signal.SIGTERM)
        try: proc.wait(timeout=3)
        except subprocess.TimeoutExpired:
            os.killpg(proc.pid, signal.SIGKILL); proc.wait()
        for stream in (proc.stdout, proc.stderr):
            if stream is not None: stream.close()
    if aidl:
        subprocess.run(['bin/apklayer', 'stop'], env=env, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        for manifest in ('apklayer-audio.xml',):
            (Path.home() / 'arm64-native/android-root/vendor/etc/vintf/manifest' / manifest).unlink(missing_ok=True)
    subprocess.run(['pactl', 'unload-module', module], env=env, check=True)
