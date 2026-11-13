#!/usr/bin/env python3
"""ART runtime queries and Java properties through a converted APK."""
import os
from pathlib import Path
import selectors
import signal
import subprocess
import sys
import time

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
import instance

env = dict(instance.ENV, APKLAYER_JOPTS='-Dhello.runtime.flag=true -Djava.net.preferIPv4Stack=true '
           '-Djavax.runtime.identity=yes -Duser.runtime.identity=yes')
run = None
try:
    run = subprocess.Popen(['bin/apklayer', 'run', 'hello.runtime'], env=env, stdout=subprocess.PIPE,
                           stderr=subprocess.STDOUT, start_new_session=True)
    with selectors.DefaultSelector() as events:
        events.register(run.stdout, selectors.EVENT_READ)
        pending = b''
        deadline = time.monotonic() + 60
        instance.busy()
        passed = False
        custom_flag = False
        while not passed:
            assert time.monotonic() < deadline, 'app startup exceeded 60 seconds'
            if not events.select(3):
                assert instance.busy(), 'app output stalled without CPU progress'
                print('Runtime startup is using CPU; no ART result yet', flush=True)
                continue
            instance.busy()
            chunk = os.read(run.stdout.fileno(), 65536)
            assert chunk, 'app ended before Runtime identity result'
            pending += chunk
            while b'\n' in pending:
                line, pending = pending.split(b'\n', 1)
                text = line.decode(errors='replace')
                print(text, flush=True)
                if '[hello-runtime] property hello.runtime.flag=true' in text:
                    custom_flag = True
                if '[hello-runtime] Runtime identity ' in text:
                    assert 'Runtime identity PASS' in text, text
                    passed = True
        assert custom_flag, 'app-supplied -D flag was lost'
        print('PASS APK runtime identity: properties, reflection, method handles, VMRuntime', flush=True)
finally:
    instance.stop()
    if run is not None:
        if run.poll() is None:
            os.killpg(run.pid, signal.SIGKILL)
        run.wait()
    instance.assert_clean()
