#!/usr/bin/env python3
"""splash.py [package...]: the shell's splash (bridge/shell/splash.c) for an app that has not drawn yet. Per package a window shell of this instance's
own runtime dir and a stand-in app (splash-client.py) that shows no frame: the shell must log the splash; screenshots of the splash and of the app's
first frame go to the group's output dir (judging the colours there needs a human check)."""
import os
import selectors
import subprocess
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
import instance  # noqa: E402  (changes into the repo root)

socket = Path(instance.ENV['XDG_RUNTIME_DIR']) / 'apklayer/shell.sock'
instance.stop()
for pkg in sys.argv[1:] or ['com.android.test.uibench']:
    go = instance.OUT / 'splash-go'
    go.unlink(missing_ok=True)
    socket.parent.mkdir(parents=True, exist_ok=True)
    socket.unlink(missing_ok=True)
    shell = subprocess.Popen(['b/bridge/shell/apklayer-shell'], env=instance.ENV, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    client = None
    try:
        seen = ''
        with selectors.DefaultSelector() as events:
            events.register(shell.stdout, selectors.EVENT_READ)
            end = time.monotonic() + 15
            while '[shell] splash ' + pkg not in seen:
                assert time.monotonic() < end and events.select(3), 'no splash for %s:\n%s' % (pkg, seen[-2000:])
                seen += os.read(shell.stdout.fileno(), 65536).decode(errors='replace')
                if client is None and socket.exists():
                    client = subprocess.Popen([sys.executable, 'tests/vm/splash-client.py', str(socket), pkg, str(go)])
        print(next(l for l in seen.splitlines() if '[shell] splash ' + pkg in l), flush=True)
        instance.capture(pkg + '-splash')
        go.touch()
        time.sleep(0.5)  # the stand-in's first frame replaces the splash
        instance.capture(pkg + '-app')
        print('PASS splash shown for ' + pkg, flush=True)
    finally:
        for proc in (client, shell):
            if proc is not None: proc.kill(); proc.wait()
        go.unlink(missing_ok=True)
