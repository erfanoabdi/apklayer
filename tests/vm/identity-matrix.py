#!/usr/bin/env python3
"""Runtime identity probes (hello-identity) in one owned app instance: prints every "[identity] key=value" line; a measurement; it fails when the app ends or stalls before its END line
or the index-out-of-bounds messages are wrong. tests/identity/collect.sh compares the result with Android (Waydroid)."""
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
        probes = {}
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
                key, _, value = text[text.index('[identity] ') + 11:].partition('=')
                probes[key] = value
                if text.endswith('[identity] END'):
                    done = True
        # ArrayList.get: libcore's text; array and String index messages are not rewritten into it (they are Android's "length=3; index=5")
        assert probes.get('art.msg.listIoobe') == 'Index: 5, Size: 0', probes.get('art.msg.listIoobe')
        for key in ('art.msg.aioobe', 'art.msg.sioobe'):
            assert not probes.get(key, '').startswith('Index: '), (key, probes.get(key))
        print('PASS identity probes: %d lines' % count, flush=True)
finally:
    instance.stop()
    if run is not None:
        if run.poll() is None:
            os.killpg(run.pid, signal.SIGKILL)
        run.wait()
    instance.assert_clean()
