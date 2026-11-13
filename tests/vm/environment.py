#!/usr/bin/env python3
"""Java, Android Os and active-ABI libc environment coherence."""
import os
from pathlib import Path
import selectors
import signal
import subprocess
import sys
import time

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
import instance

env = dict(instance.ENV)
run = None
try:
    run = subprocess.Popen(['bin/apklayer', 'run', 'hello.environment'], env=env, stdout=subprocess.PIPE,
                           stderr=subprocess.STDOUT, start_new_session=True)
    with selectors.DefaultSelector() as events:
        events.register(run.stdout, selectors.EVENT_READ)
        pending = b''
        deadline = time.monotonic() + 60
        instance.busy()
        passed = False
        while not passed:
            assert time.monotonic() < deadline, 'app startup exceeded 60 seconds'
            if not events.select(3):
                assert instance.busy(), 'app output stalled without CPU progress'
                print('Runtime startup is using CPU; no environment result yet', flush=True)
                continue
            instance.busy()
            chunk = os.read(run.stdout.fileno(), 65536)
            assert chunk, 'app ended before environment result'
            pending += chunk
            while b'\n' in pending:
                line, pending = pending.split(b'\n', 1)
                text = line.decode(errors='replace')
                print(text, flush=True)
                if text.startswith('[environment] PASS') or text.startswith('[environment] FAIL'):
                    assert text == '[environment] PASS', text
                    passed = True
        print('PASS APK environment: Java, Android Os and native libc', flush=True)
finally:
    instance.stop()
    if run is not None:
        if run.poll() is None:
            os.killpg(run.pid, signal.SIGKILL)
        run.wait()
    instance.assert_clean()
