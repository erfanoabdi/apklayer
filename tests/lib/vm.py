"""Mac side of the tests: the test VM (tests/vm/local.conf through tests/vm/config.py), commands on it, copying the repo tree and APKs there,
and the environment of one apklayer instance per test group (own binder devices, own XDG_RUNTIME_DIR and with it its own window shell)."""
import os
import shlex
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO / 'tests/vm'))
import config  # noqa: E402

SSH = config.auth('ssh') + ['-o', 'ServerAliveInterval=15', config.VM]
ROOT = config.ROOT.replace('~', '$HOME', 1) if config.ROOT.startswith('~') else config.ROOT  # the repo copy on the VM (a shell word)
WORK = ROOT + '/.tests'  # the runner's files there: run/<instance> (runtime dirs), out/<group>, apks/


def ssh(command, **kw):
    return subprocess.run(SSH + [command], **kw)


def popen(command, **kw):
    return subprocess.Popen(SSH + [command], **kw)


def push():
    """The working tree (tracked and new files, as they are now) into ROOT on the VM."""
    files = subprocess.run(['git', 'ls-files', '-co', '--exclude-standard', '-z'], cwd=REPO, capture_output=True, check=True).stdout.split(b'\0')
    names = b'\0'.join(f for f in files if f and os.path.lexists(REPO / f.decode()))  # symlinks too (their targets are on the VM)
    tar = subprocess.Popen(['tar', '-c', '-f', '-', '--no-xattrs', '--null', '-T', '-'], cwd=REPO, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                           env=dict(os.environ, COPYFILE_DISABLE='1'))
    tar.stdin.write(names)  # the list fits the pipe: tar reads it while ssh takes its output
    tar.stdin.close()
    # tar keeps the local modification times; after a push from another checkout (a worktree, a bisect) the VM's files may be newer than this
    # tree's and ninja would skip real changes: then every file gets the extraction time once (tar -m), a full rebuild
    remote = ssh('mkdir -p %s && if [ "$(cat %s/.pushed-from 2>/dev/null)" = %s ]; then tar xf - -C %s; else tar xmf - -C %s && echo %s > %s/.pushed-from; fi'
                 % (ROOT, ROOT, shlex.quote(str(REPO)), ROOT, ROOT, shlex.quote(str(REPO)), ROOT), stdin=tar.stdout)
    assert tar.wait() == 0 and remote.returncode == 0, 'pushing the tree failed'


def copy(files, remote):
    """Local files into the VM directory `remote` (a shell word: $HOME works), by tar so that no error message carries the password."""
    files = [Path(f) for f in files]
    tar = subprocess.Popen(['tar', '-c', '-f', '-', '--no-xattrs', *[a for f in files for a in ('-C', str(f.parent), f.name)]], stdout=subprocess.PIPE, env=dict(os.environ, COPYFILE_DISABLE='1'))
    remote_ok = ssh('mkdir -p %s && tar xf - -C %s' % (remote, remote), stdin=tar.stdout).returncode == 0
    assert tar.wait() == 0 and remote_ok, 'copying %d files to the VM failed' % len(files)


def instance_env(instance, out=None):
    """The shell prefix that puts a VM command into `instance`: cwd ROOT, its binder devices and runtime dir; the desktop session's Wayland,
    D-Bus and audio sockets by absolute path. APKLY_VM_ENV (local.conf) adds or overrides (APKLAYER_NATIVE, XDG_DATA_HOME, ...)."""
    run = '%s/run/%s' % (WORK, instance)
    env = {'APKLAYER_INSTANCE': instance, 'XDG_RUNTIME_DIR': run, 'WAYLAND_DISPLAY': '/run/user/$(id -u)/wayland-0',
           'DBUS_SESSION_BUS_ADDRESS': 'unix:path=/run/user/$(id -u)/bus', 'PULSE_SERVER': 'unix:/run/user/$(id -u)/pulse/native',
           'PIPEWIRE_RUNTIME_DIR': '/run/user/$(id -u)', 'APKLAYER_TEST_INPUT': '1'}
    if out: env['APKLY_OUT'] = out
    exports = ' '.join('%s="%s"' % kv for kv in env.items())
    state = 'APKLY_STATE="${XDG_STATE_HOME:-$HOME/.local/state}/apklayer/%s"' % instance  # the instance's logs, for shell tests
    return 'cd %s && mkdir -p %s && chmod 700 %s && export %s %s && export %s; ' % (ROOT, run, run, exports, config.ENV, state)
