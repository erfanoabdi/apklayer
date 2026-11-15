#!/usr/bin/env python3
"""A SIGTERM to an app JVM (what the corpus runner and a unit stop send) ends it, whatever holds its end. exit-deadline.py [case]:
  stuck   (default) a shutdown hook that never returns: killed EXIT_DEADLINE_S (10 s, NativeLoader) after its end began, launcher exit 137;
  held    a thread holds java.lang.Shutdown, so the JVM's end never reaches a hook: killed 10 s after SIGTERM's arrival (native_loader.c), 137;
  spare   the instance's ready spare is SIGTERMed (its record: JVM and holder, as the runner ends every record) and the app launched at once:
          the launch must not take the ending spare (it failed with 143), it comes up and ends on SIGTERM;
  native  hello.arm64's library ignores and catches SIGTERM (signal, sigaction; through the native bridge on x86_64): it still ends, 143."""
import os
import signal
import subprocess
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
import instance  # noqa: E402
from instance import ENV, LOGDIR, stop  # noqa: E402

CASE = sys.argv[1] if len(sys.argv) > 1 else 'stuck'
PKG = 'hello.arm64' if CASE == 'native' else 'hello.acttarget'
STARTED = '[hello-arm64] SIGTERM ignored' if CASE == 'native' else 'started hello.acttarget.Main'
OPTS = {'stuck': '-Dhello.act=none -Dhello.stuckexit=true', 'held': '-Dhello.act=none -Dhello.heldshutdown=true',
        'spare': '-Dhello.act=none', 'native': '-Dhello.ignoreterm=true'}[CASE]
env = dict(ENV, APKLAYER_JOPTS='-Dapklayer.runms=60000 ' + OPTS, APKLAYER_RUNTIME_DEBUG='1')
runs = []


def launch():
    run = subprocess.Popen(['bin/apklayer', 'run', PKG], env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
    runs.append(run)
    lines = []
    for line in run.stdout:
        lines.append(line.decode(errors='replace'))
        if STARTED in lines[-1]: break
    else: raise AssertionError('%s ended before it started (exit %s):\n%s' % (PKG, run.wait(), ''.join(lines[-30:])))
    return run, lines, int(record(run).read_text().split()[0])


def record(run):
    """The launch's run.<pid> record: its own pid's; sandboxed, apklayerd runs the launcher (`--no-daemon run <package>`, no child of this one): its record is found by its command line."""
    own = Path(LOGDIR) / ('run.%d' % run.pid)
    if own.exists(): return own
    for f in Path(LOGDIR).glob('run.*'):
        try:
            if ('run\0' + PKG) in Path('/proc/%s/cmdline' % f.name[4:]).read_text() and len(f.read_text().split()) >= 2: return f
        except OSError: pass
    return own


def end(run, jvm, lines):
    """SIGTERM to the JVM; the launcher's exit status and the seconds it took, with the launch's output and app log."""
    began = time.monotonic()
    os.kill(jvm, signal.SIGTERM)
    out = run.communicate(timeout=30)[0].decode(errors='replace')
    took = time.monotonic() - began
    print('[exit-deadline] %s: launcher exit %d %.1f s after SIGTERM' % (CASE, run.returncode, took), flush=True)
    return run.returncode, took, ''.join(lines) + out + (Path(LOGDIR) / ('app-%s.log' % PKG)).read_text(errors='replace')


stop()
try:
    if CASE == 'spare':
        first, lines, jvm = launch()
        instance.wait_file(Path(LOGDIR) / 'app-spare.log', 'spare: preloaded', 90)  # the spare this launch started is ready
        spare = next(p for r in Path(LOGDIR).glob('run.*') for p in [[int(x) for x in r.read_text().split()]] if len(p) == 2 and p[1] == int(r.name[4:]))
        end(first, jvm, lines)
        for pid in spare: os.kill(pid, signal.SIGTERM)
        run, lines, jvm = launch()
        taken = any('app spare taken' in l for l in lines)
        print('[exit-deadline] spare: the launch right after the spare\'s SIGTERM %s' % ('took it' if taken else 'started cold'), flush=True)
        assert not taken and jvm != spare[0], 'the launch took the ending spare:\n' + ''.join(lines[-20:])
        status, took, log = end(run, jvm, lines)
        refused = [l for l in log.splitlines() if 'spare is not for this launch' in l]
        print('[exit-deadline] spare: %s' % (refused[0].strip() if refused else 'its holder was gone already'), flush=True)
        assert status == 143 and took < 5, 'the app did not end on SIGTERM (exit %d after %.1f s)\n%s' % (status, took, log[-1500:])
    else:
        run, lines, jvm = launch()
        status, took, log = end(run, jvm, lines)
        if CASE == 'native':
            assert '[hello-arm64] SIGTERM ignored and caught by the library: ok' in log, 'the library calls failed:\n' + log[-2000:]
            assert status == 143 and took < 5, 'the app did not end on SIGTERM (exit %d after %.1f s)\n%s' % (status, took, log[-1500:])
        else:
            assert ('[act-t] stuck exit hook' if CASE == 'stuck' else '[act-t] holding java.lang.Shutdown') in log, 'the stall was not set up:\n' + log[-2000:]
            assert 'exit deadline: killed' in log, 'no deadline kill in the app log:\n' + log[-2000:]
            assert status == 137 and 9 <= took <= 15, 'the stuck JVM did not end at its deadline (exit %d after %.1f s)\n%s' % (status, took, log[-1500:])
    print('[exit-deadline] %s PASS' % CASE, flush=True)
finally:
    for r in runs:
        if r.poll() is None: os.killpg(r.pid, signal.SIGKILL)
    stop()
