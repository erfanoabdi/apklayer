#!/usr/bin/env python3
"""VM side: runs one app in this instance (tests/lib/instance.py's environment), follows a plan, then stops the instance.
Steps:
  shot[:<pkg>]          wait until the instance's logs settle (QUIET seconds without a new line, at most 5*QUIET), then `apklayer screenshot`
                        of the app (or of <pkg>, another app of the instance) into <prefix>-<n>.png
  yes | no              wait (at most 120 s) for the next question of the hub's installer (HubInstallDialog: "dialog shown:" in hub.log) and click
                        its yes or no button
  wait:<log>:<text>[:<s>] wait (at most s seconds, default 300) until <text> appears in the instance's log <log> (hub.log, app-<pkg>.log, ...);
                        a missing text ends the plan
  dump                  a thread dump of the app JVM (SIGQUIT) into its output
  gdb                   gdb attached to the app JVM: a fatal signal's native backtrace into <prefix>-gdb.log
  in:<window>:<steps>   test input into another window of the instance (a package or apklayer.installer), steps separated by '+'
  <input steps>         test input into the app's window (instance.inject: "tap:162,400 text:abc key:28")
Usage: drive.py <pkg> <out-prefix> <runms> <quiet> <step>...   Prints one line per step; exit 1 when a wait ran out."""
import os
import selectors
import subprocess
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
import instance  # noqa: E402  (changes into the repo root; ENV takes test input)

pkg, prefix, runms, quiet, plan = sys.argv[1], sys.argv[2], sys.argv[3], float(sys.argv[4]), sys.argv[5:]
state = Path(instance.LOGDIR)
out_log = prefix + '.log'
env = dict(instance.ENV, APKLAYER_JOPTS=('-Dapklayer.runms=' + runms + ' ' + os.environ.get('XJOPTS', '')).strip())
fd = instance._libc.inotify_init1(os.O_NONBLOCK | os.O_CLOEXEC)
for watched in (state, Path(out_log).resolve().parent):
    instance._libc.inotify_add_watch(fd, os.fsencode(str(watched)), 0x102)  # IN_MODIFY | IN_CREATE
events = selectors.DefaultSelector()
events.register(fd, selectors.EVENT_READ)


def size():
    total = 0
    for f in list(state.glob('*.log')) + [Path(out_log)]:
        try: total += f.stat().st_size
        except OSError: pass
    return total


def tick():
    if events.select(0.5): os.read(fd, 65536)


def settle(seconds, limit):
    start = last = time.monotonic(); n = size()
    while p.poll() is None and time.monotonic() - start < limit and time.monotonic() - last < seconds:
        tick()
        m = size()
        if m != n: n, last = m, time.monotonic()
    return round(time.monotonic() - start)


def text_of(name):
    try: return (state / name).read_text(errors='replace')
    except OSError: return ''


def wait_for(name, text, limit):
    start = time.monotonic()
    while time.monotonic() - start < limit:
        if text in text_of(name): return round(time.monotonic() - start)
        tick()
    return None


def app_pids():
    """The app's JVMs in this instance: their command line is the Android process name (pgrep -x sees only 15 characters of it)."""
    found = []
    for pid in filter(str.isdigit, os.listdir('/proc')):
        try:
            if open('/proc/%s/cmdline' % pid, 'rb').read().split(b'\0')[0] == pkg.encode() and instance.belongs(pid): found.append(int(pid))
        except OSError: pass
    return found


gdb = None
p = subprocess.Popen(['bin/apklayer', 'run', pkg], stdout=open(out_log, 'w'), stderr=subprocess.STDOUT, env=env)
shots, questions, status = 0, text_of('hub.log').count('dialog shown:'), 0
try:
    for step in plan:
        if p.poll() is not None: print('exited', p.returncode, flush=True); status = 1; break
        if step == 'shot' or step.startswith('shot:'):
            took = settle(quiet, 5 * quiet)
            out = '%s-%d.png' % (prefix, shots); shots += 1
            subprocess.run(['bin/apklayer', 'screenshot', step[5:] or pkg, out], env=env, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            print('shot', out, os.path.exists(out), 'after', took, 's', flush=True)
        elif step in ('yes', 'no'):
            questions += 1
            start = time.monotonic()
            while text_of('hub.log').count('dialog shown:') < questions and time.monotonic() - start < 120: tick()
            shown = [l for l in text_of('hub.log').split('\n') if 'dialog shown:' in l]
            if len(shown) < questions: print('no question came', flush=True); status = 1; break
            line = shown[questions - 1]
            xy = line.split(step + '=')[1].split()[0]
            print('question', line.split('dialog shown: ')[1], '->', step, flush=True)
            for attempt in range(20):  # the window takes input once the compositor configured it
                try: instance.inject('apklayer.installer', 'click:' + xy); break
                except AssertionError as missing:
                    if 'no such window' not in str(missing) or attempt == 19: raise
                    tick()
        elif step.startswith('wait:'):
            _, name, text, *limit = step.split(':', 3)
            took = wait_for(name, text, float(limit[0]) if limit else 300)
            print('wait', name, repr(text), 'after %s s' % took if took is not None else 'NOT SEEN', flush=True)
            if took is None: status = 1; break
        elif step == 'dump':  # a thread dump of the app JVM (SIGQUIT) into its output
            for pid in app_pids(): os.kill(pid, 3)
            settle(2, 4)
            print('dumped', flush=True)
        elif step == 'gdb':  # gdb attached to the app JVM (sudo: ptrace_scope): a fatal signal's native backtrace into <prefix>-gdb.log
            pid = str(app_pids()[0])
            quiet_signals = [a for s in ('SIGSEGV', 'SIGILL', 'SIGBUS', 'SIGFPE', 'SIGUSR1', 'SIGUSR2', 'SIGQUIT', 'SIGPIPE', 'SIG33', 'SIG34', 'SIG35', 'SIG36')
                             for a in ('-ex', 'handle %s nostop noprint pass' % s)]
            gdb = subprocess.Popen(['sudo', '-n', 'gdb', '-batch', '-p', pid, *quiet_signals, '-ex', 'continue', '-ex', 'thread', '-ex', 'bt 40', '-ex', 'info sharedlibrary'],
                                   stdout=open(prefix + '-gdb.log', 'w'), stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL)
            settle(3, 15)
            print('gdb attached to', pid, flush=True)
        elif step.startswith('in:'):
            _, window, steps = step.split(':', 2)
            try: instance.inject(window, *steps.split('+'))
            except (AssertionError, OSError) as failure: print('inject failed:', failure, flush=True)
            settle(1, 3)
        else:
            try: instance.inject(pkg, *step.split())
            except (AssertionError, OSError) as failure: print('inject failed:', failure, flush=True)
            settle(1, 3)
finally:
    subprocess.run(['bin/apklayer', 'stop'], env=env, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    p.wait()
    if gdb:
        try: gdb.wait(10)
        except subprocess.TimeoutExpired: subprocess.run(['sudo', '-n', 'kill', str(gdb.pid)]); gdb.wait()
sys.exit(status)
