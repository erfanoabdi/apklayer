#!/usr/bin/env python3
"""A launch whose window is closed before the app took it over ends (cli/run.cpp APKLAYER_CLOSE_ENDS, bridge/hals/graphics/display.c): `apklayer run`,
the window closed by test input the moment the shell configured it (the app still starting), then the run ends and no JVM of the package is left.
Before, the JVM ran on without a window and the next launch was handed to it (Play Store stuck at its splash, 2026-11-21). Usage: early-close.py [package]."""
import subprocess, sys, time
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
import instance  # noqa: E402

pkg = sys.argv[1] if len(sys.argv) > 1 else 'hello.acttarget'
shell_log = Path(instance.LOGDIR) / 'shell.log'
instance.stop()  # the instance's shell ends with it: the log then holds only this launch
if shell_log.exists(): shell_log.unlink()
run = subprocess.Popen(['bin/apklayer', 'run', pkg], env=instance.ENV, stdout=open(Path(instance.LOGDIR) / 'early-close.out', 'w'), stderr=subprocess.STDOUT)
ok = False
try:
    needle = ':%s:%s: configure content ' % (instance.INSTANCE, pkg)
    text = instance.wait_file(shell_log, needle, 60)
    title = next(l for l in text.splitlines() if needle in l).split('] ', 1)[1].split(': configure content ')[0]
    attached = ('takes the input of window "%s"' % title) in shell_log.read_text(errors='replace')
    instance.inject(title, 'close')
    closed = time.monotonic()
    try:
        code = run.wait(timeout=15)
        left = instance.mine(pkg, exact=True)
        ok = not attached and not left
        print('%s: closed %s before the app took it (taken: %s); the run ended %.1f s later (exit %d), JVMs left: %s'
              % ('PASS' if ok else 'FAIL', title, attached, time.monotonic() - closed, code, left or 'none'))
    except subprocess.TimeoutExpired:
        print('FAIL: the run still goes 15 s after its window was closed')
finally:
    instance.stop()
    if run.poll() is None: run.kill(); run.wait()
sys.exit(0 if ok else 1)
