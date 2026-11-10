#!/usr/bin/env python3
"""An app JVM whose end stalls (a shutdown hook that never returns, as a stuck hook or exit once kept a UiBench JVM 9 min past SIGTERM) is killed
EXIT_DEADLINE_S (10 s, NativeLoader) after its end began, as init and ActivityManager kill what SIGTERM did not end: SIGTERM to the JVM (what the
corpus runner and a unit stop send), then the launcher returns 137 within the deadline and the app log says why."""
import os
import signal
import subprocess
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
import instance  # noqa: E402
from instance import ENV, LOGDIR, stop  # noqa: E402

PKG = 'hello.acttarget'
stop()
env = dict(ENV, APKLAYER_JOPTS='-Dapklayer.runms=60000 -Dhello.act=none -Dhello.stuckexit=true')
run = subprocess.Popen(['bin/apklayer', 'run', PKG], env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
try:
    for line in run.stdout:
        if b'started hello.acttarget.Main' in line: break
    else: raise AssertionError('%s ended before its activity started (exit %s)' % (PKG, run.wait()))
    record = Path(LOGDIR) / ('run.%d' % run.pid)
    jvm = int(record.read_text().split()[0])
    began = time.monotonic()
    os.kill(jvm, signal.SIGTERM)
    out = run.communicate(timeout=30)[0].decode(errors='replace')
    took = time.monotonic() - began
    log = out + (Path(LOGDIR) / ('app-%s.log' % PKG)).read_text(errors='replace')
    print('[exit-deadline] launcher exit %d %.1f s after SIGTERM' % (run.returncode, took), flush=True)
    assert '[act-t] stuck exit hook' in log, 'the stuck hook did not run:\n' + log[-2000:]
    assert 'exit deadline: killed' in log, 'no deadline kill in the app log:\n' + log[-2000:]
    assert run.returncode == 137 and 9 <= took <= 15, 'the stuck JVM did not end at its deadline (exit %d after %.1f s)\n%s' % (run.returncode, took, out[-1500:])
    print('[exit-deadline] PASS', flush=True)
finally:
    if run.poll() is None: os.killpg(run.pid, signal.SIGKILL)
    stop()
