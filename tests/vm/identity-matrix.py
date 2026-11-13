#!/usr/bin/env python3
"""Runtime identity probes (hello-identity) in one owned app instance: prints every "[identity] key=value" line; a measurement, not a gate
(it fails only when the app ends or stalls before its END line). tests/identity/collect.sh compares the result with Android (Waydroid)."""
import os
from pathlib import Path
import selectors
import signal
import subprocess
import sys
import time

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
import instance

run = None
try:
    run = subprocess.Popen(['bin/apklayer', 'run', 'hello.identity'], env=instance.ENV, stdout=subprocess.PIPE,
                           stderr=subprocess.STDOUT, start_new_session=True)
    with selectors.DefaultSelector() as events:
        events.register(run.stdout, selectors.EVENT_READ)
        pending = b''
        deadline = time.monotonic() + 90
        instance.busy()
        done = False
        count = 0
        while not done:
            assert time.monotonic() < deadline, 'identity probes exceeded 90 seconds'
            if not events.select(3):
                assert instance.busy(), 'app output stalled without CPU progress'
                continue
            instance.busy()
            chunk = os.read(run.stdout.fileno(), 1 << 20)
            assert chunk, 'app ended before the identity END line'
            pending += chunk
            while b'\n' in pending:
                line, pending = pending.split(b'\n', 1)
                text = line.decode(errors='replace')
                if '[identity] ' not in text:
                    continue
                print(text[text.index('[identity] '):], flush=True)
                count += 1
                if text.endswith('[identity] END'):
                    done = True
        print('PASS identity probes: %d lines' % count, flush=True)
finally:
    instance.stop()
    if run is not None:
        if run.poll() is None:
            os.killpg(run.pid, signal.SIGKILL)
        run.wait()
    instance.assert_clean()
