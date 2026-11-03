#!/usr/bin/env python3
"""Start the headless hub, verify its singleton lock, then close every process."""
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
env = dict(instance.ENV, APKLAYER_NATIVE=str(instance.NATIVE))
proc = None
try:
    proc = subprocess.Popen(['bin/apklayer', 'hub'], env=env, stdout=subprocess.PIPE,
                            stderr=subprocess.STDOUT, start_new_session=True)
    pending = b''
    ready = False
    end = time.monotonic() + 30
    with selectors.DefaultSelector() as events, open('/tmp/apklayer-hub-smoke.out', 'wb') as log:
        events.register(proc.stdout, selectors.EVENT_READ)
        while not ready:
            assert time.monotonic() < end and events.select(3), 'hub startup stalled'
            chunk = os.read(proc.stdout.fileno(), 65536)
            assert chunk, 'hub exited before READY'
            log.write(chunk); log.flush()
            lines = (pending + chunk).split(b'\n'); pending = lines.pop()
            for raw in lines:
                line = raw.decode(errors='replace')
                if '[runtime]' in line or '[apklayer-hub]' in line: print(line, flush=True)
                ready |= '[apklayer-hub] READY pid=' in line
    second = subprocess.run(['bin/apklayer', 'hub'], env=env, capture_output=True, timeout=3)
    assert second.returncode == 0, second.stderr.decode()
    print('PASS headless instance hub startup and singleton lock', flush=True)
finally:
    if proc is not None:
        try: os.killpg(proc.pid, signal.SIGKILL)
        except ProcessLookupError: pass
        proc.wait(); proc.stdout.close()
    subprocess.run(['bin/apklayer', 'stop'], env=env, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    left = instance.mine('[a]pklayer.host.Main|[b]tload|[a]pklayer-shell')
    assert not left, left
