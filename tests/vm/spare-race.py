#!/usr/bin/env python3
"""Rapid `apklayer run` / `stop <pkg>` cycles (the X23 round of 2026-11-15 left `run` processes blocked in takeAppSpare's socket read and several
app-spare holders alive after `stop <pkg>`): a launch that took the instance's spare is known to `stop <package>` and `info` at once (its JVM carries no
package name until it renamed itself: held certain with a SIGSTOPped spare), every `run` ends once its app is stopped, no holder outlives its launcher
(apart from the instance's one idle spare), and a full `apklayer stop` then leaves no process of the instance. Python, not bash: the Sailfish phone has
only busybox. `spare-race.py [PKG [CYCLES]]` (hello.input, 12) in the repo, or with APKLAYER=<the CLI of an installed package> anywhere (phones)."""
import os
import signal
import subprocess
import sys
import time
from pathlib import Path

if not os.environ.get('APKLAYER'): os.chdir(Path(__file__).resolve().parents[2])
A = os.environ.get('APKLAYER') or 'bin/apklayer'
pkg = sys.argv[1] if len(sys.argv) > 1 else 'hello.input'
cycles = int(sys.argv[2]) if len(sys.argv) > 2 else 12
inst = os.environ.get('APKLAYER_INSTANCE', 'default')
state = Path(os.environ.get('XDG_STATE_HOME') or Path.home() / '.local/state') / 'apklayer' / inst
os.environ.update(APKLAYER_DISPLAY_WIDTH='480', APKLAYER_DISPLAY_HEIGHT='400')
SHORT, LONG = '-Dapklayer.runms=6000', '-Dapklayer.runms=300000'  # the first launch's, and the cycles' (an app a stop missed lives on)


def apk(*args, capture=False):
    return subprocess.run([A, *args], stdout=subprocess.PIPE if capture else subprocess.DEVNULL, stderr=subprocess.DEVNULL, text=True).stdout


def fail(message):
    print('FAIL', message, flush=True)
    apk('stop')
    sys.exit(1)


own = set()  # this process and its callers (the harness carries the instance in its environment too)
p = os.getpid()
while p > 1 and p not in own:
    own.add(p)
    p = int(Path('/proc/%d/stat' % p).read_text().rsplit(')', 1)[1].split()[1])


def procs(sub=''):
    """The instance's processes (it is in their environment) whose command line has `sub`: [(pid, command line)]."""
    found = []
    for name in os.listdir('/proc'):
        if not name.isdigit() or int(name) in own: continue
        try:
            if b'\0APKLAYER_INSTANCE=%s\0' % inst.encode() not in b'\0' + Path('/proc/%s/environ' % name).read_bytes() + b'\0': continue
            cmd = Path('/proc/%s/cmdline' % name).read_bytes().replace(b'\0', b' ').decode(errors='replace')
        except OSError: continue
        if sub in cmd: found.append((int(name), cmd[:150]))
    return found


def until(condition, seconds):
    end = time.monotonic() + seconds
    while not condition():
        if time.monotonic() > end: return False
        time.sleep(0.3)
    return True


def spare_log():
    try: return (state / 'app-spare.log').read_text(errors='replace')
    except OSError: return ''


apk('stop')
if procs(): fail('processes before the test: %s' % procs())
os.environ['APKLAYER_JOPTS'] = SHORT
apk('run', pkg)  # the cold start (stack, hub; a phone takes a minute) is not what is tested: one short launch, to its end
os.environ['APKLAYER_JOPTS'] = LONG

# the window made certain: the idle spare's JVM, stopped (SIGSTOP), takes a launch but cannot rename itself: unnamed until it is killed
if not until(lambda: 'preloaded' in spare_log(), 60): fail('no spare got ready')
jvms = [int(l.rsplit('pid=', 1)[1].split()[0]) for l in spare_log().splitlines() if 'app spare pid=' in l and 'taken' not in l]
jvm = next((j for j in reversed(jvms) if Path('/proc/%d/cmdline' % j).exists()), 0)  # the idle spare: its holder's newest line, the JVM alive
if not jvm: fail('no idle spare JVM in its holder\'s log')
os.kill(jvm, signal.SIGSTOP)
run = subprocess.Popen([A, 'run', pkg], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
if not until(lambda: (state / ('run.%d' % run.pid)).exists(), 30): fail('the launch that took the stopped spare left no record')
if b'--spare' not in Path('/proc/%d/cmdline' % jvm).read_bytes(): fail('the taken spare JVM is not the unnamed one: the test does not hold the window')
if '\trunning\t1' not in '\t' + apk('info', pkg, capture=True).replace('\n', '\t'): fail('info does not know the launch that took the spare')
apk('stop', pkg)
if not until(lambda: run.poll() is not None, 40): fail('stop %s did not end the launch that took the spare' % pkg)

runs = []
for i in range(cycles):
    until(lambda: 'preloaded' in spare_log(), 30)  # the next spare, so that this launch takes it
    run = subprocess.Popen([A, 'run', pkg], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    runs.append(run)
    until(lambda: (state / ('run.%d' % run.pid)).exists() or run.poll() is not None, 20)  # recorded: it has its JVM (spare or cold)
    if i % 3: apk('stop', pkg)  # every third launch is left running
apk('stop', pkg)
if not until(lambda: all(r.poll() is not None for r in runs), 40):
    fail('%d of %d run processes alive after 40 s: %s' % (sum(r.poll() is None for r in runs), len(runs), procs(' app-spare ')))
# a holder serves one launcher (a hub-started component host runs for good) or is the instance's one idle spare; one started by the last launch
# may still be on its way to find the lock taken and end
holders = launchers = 0
def settled():
    global holders, launchers
    holders, launchers = len(procs(' app-spare ')), len([c for _, c in procs('apklayer') if ' run ' in c or ' component-host ' in c])
    return holders <= launchers + 1
if not until(settled, 20): fail('%d app-spare holders for %d launchers after the runs ended: %s' % (holders, launchers, procs(' app-spare ')))
apk('stop')
left, records = procs(), sorted(f for f in os.listdir(state) if f.startswith('run.'))
if left: fail('left after stop: %s' % left)
if records: fail('run records left after stop: %s' % records)
print('PASS spare-race: a launch on a stopped spare is stopped by name; %d run/stop cycles, every run ended, %d spare holder(s) for %d launchers, nothing left after stop'
      % (cycles, holders, launchers), flush=True)
