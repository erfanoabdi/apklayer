#!/usr/bin/env python3
"""A launch outlives the freezing of the unit that started it (docs/HALIUM.md "Out of Lomiri's lifecycle"): Lomiri SIGSTOPs a whole
`lomiri-app-launch--...` unit when its app loses the focus, and what apklayer-manager started ran in it. Here a stand-in for the manager (a shell in
a transient scope of its own) starts `apklayer run hello.acttarget`; the moment the launch has begun, that scope is frozen (cgroup.freeze, as
`systemctl --user freeze`). The app must still come up with its window, the stack, hub, shell, spare and app JVM must all run in apklayer's own
units, another app must start meanwhile (from the instance's spare, which a frozen unit used to hold), and a SIGTERM to the launcher (a unit's stop
reaches only it now) must end its app."""
import os
import selectors
import signal
import subprocess
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
import instance  # noqa: E402
from instance import ENV, INSTANCE, LOGDIR, SESSION, STATE, stop  # noqa: E402

TARGET, OTHER = 'hello.acttarget', 'hello.actcaller'
env = dict(ENV, APKLAYER_RUNTIME_DEBUG='1', APKLAYER_DISPLAY_WIDTH='480', APKLAYER_DISPLAY_HEIGHT='400', APKLAYER_JOPTS='-Dapklayer.runms=90000 -Dhello.act=none')
manager = 'apklayer-test-%s-manager-%d.scope' % (INSTANCE, os.getpid())
session = dict(env, XDG_RUNTIME_DIR=SESSION)  # the user's systemd manager, whatever the instance's runtime dir is


def systemctl(*args):
    return subprocess.run(['systemctl', '--user', *args], env=session, capture_output=True, text=True)


class Launch:
    def __init__(self, argv, **kw):
        self.proc = subprocess.Popen(argv, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True, **kw)
        self.lines, self.pending, self.events = [], b'', selectors.DefaultSelector()
        self.events.register(self.proc.stdout, selectors.EVENT_READ)

    def expect(self, text, seconds=90):
        """The first line so far that contains text."""
        seen, end = 0, time.monotonic() + seconds
        while time.monotonic() < end:
            for line in self.lines[seen:]:
                if text in line: return line
            seen = len(self.lines)
            if not self.events.select(1): continue
            data = os.read(self.proc.stdout.fileno(), 65536)
            if not data: raise AssertionError('the launch ended waiting for %r\n' % text + '\n'.join(self.lines[-20:]))
            parts = (self.pending + data).split(b'\n')
            self.pending = parts.pop()
            self.lines += [p.decode(errors='replace') for p in parts]
        raise AssertionError('no %r within %d s\n' % (text, seconds) + '\n'.join(self.lines[-25:]))


def unit_of(pid):
    try: return Path('/proc/%s/cgroup' % pid).read_text().strip().rsplit('/', 1)[-1]
    except OSError: return None


def first_pid(path):
    try: return int((STATE / path).read_text().split()[0])
    except (OSError, IndexError, ValueError): return None


def children(pid):
    try: return [int(c) for c in Path('/proc/%d/task/%d/children' % (pid, pid)).read_text().split()]
    except OSError: return []


if not Path(SESSION, 'systemd/private').exists():
    print('SKIP units: no systemd user manager', flush=True)
    sys.exit(0)
stop()
launches = []
try:
    # the manager stand-in: its shell stays in its scope (as apklayer-manager in its lomiri-app-launch unit), `apklayer run` is its child
    outer = Launch(['systemd-run', '--user', '--scope', '--quiet', '--collect', '--unit=' + manager, '-E', 'XDG_RUNTIME_DIR=' + env['XDG_RUNTIME_DIR'],
                    '--', 'sh', '-c', 'bin/apklayer run %s; echo "manager: run ended $?"' % TARGET], env=session)
    launches.append(outer)
    outer.expect(' run %s' % TARGET, 20)  # the launch began (its trace after the move into its own scope, when it makes one)
    frozen = systemctl('freeze', manager)
    assert frozen.returncode == 0, 'cannot freeze %s: %s' % (manager, frozen.stderr)
    print('[units] %s frozen' % manager, flush=True)

    pid = int(outer.expect('[act-t] main pid=').split('pid=')[1].split()[0])
    print('[units] %s came up while its starter was frozen: pid %d' % (TARGET, pid), flush=True)
    launcher = next(c for c in children(outer.proc.pid) if 'apklayer' in Path('/proc/%d/cmdline' % c).read_text())
    units = {'launcher': unit_of(launcher), 'app JVM': unit_of(pid), 'stack': unit_of(first_pid('stack.pid')), 'hub': unit_of(first_pid('hub.pid'))}
    spare_log = instance.wait_file(Path(LOGDIR) / 'app-spare.log', 'app spare pid=', 30)  # the next spare, started by this launch
    units['spare'] = unit_of(spare_log.split('app spare pid=')[1].split()[0])
    shell = instance.mine('[a]pklayer-shell')
    if shell: units['shell'] = unit_of(shell[0].split()[0])
    print('[units] ' + ', '.join('%s: %s' % kv for kv in units.items()), flush=True)
    for what, unit in units.items():
        assert unit and unit != manager and unit.startswith('apklayer-'), '%s runs in %s, not in a unit of its own' % (what, unit)

    other = Launch(['bin/apklayer', 'run', OTHER], env=env)
    launches.append(other)
    other.expect('[act] unknown scenario')
    spare = [l for l in other.lines if 'app spare taken' in l]
    print('[units] %s started meanwhile%s' % (OTHER, ' from the spare' if spare else ' (cold)'), flush=True)
    assert spare, 'no spare served the second launch:\n' + '\n'.join(other.lines[-15:])

    os.kill(launcher, signal.SIGTERM)  # a stop of the starter's unit: only its main process, the launcher, gets it
    end = time.monotonic() + 20
    while time.monotonic() < end and Path('/proc/%d' % pid).exists(): time.sleep(0.2)
    assert not Path('/proc/%d' % pid).exists(), 'SIGTERM to the launcher left its app running'
    print('[units] SIGTERM to the launcher ended %s' % TARGET, flush=True)

    # always-on mode: the hub alone (its unit's command) prepares the spare; the first launch takes it (the spare's helper runs under the hub's
    # path layer, which once spelled the storage directory as /storage: a key no launch matched)
    stop()
    (Path(LOGDIR) / 'app-spare.log').unlink(missing_ok=True)  # the next spare's word only
    hub = Launch(['bin/apklayer', 'hub'], env=env)
    launches.append(hub)
    instance.wait_file(Path(LOGDIR) / 'app-spare.log', 'app spare pid=', 90)
    first = Launch(['bin/apklayer', 'run', TARGET], env=env)
    launches.append(first)
    first.expect('[act-t] main pid=')
    assert any('app spare taken' in l for l in first.lines), 'the hub\'s spare did not serve the launch:\n' + '\n'.join(first.lines[-15:])
    print('[units] the hub\'s spare served the first launch', flush=True)
    print('PASS units', flush=True)
finally:
    systemctl('thaw', manager)
    systemctl('stop', manager)
    for launch in launches:
        if launch.proc.poll() is None:
            try: os.killpg(launch.proc.pid, signal.SIGTERM)
            except OSError: pass
    stop()
