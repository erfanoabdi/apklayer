"""Mac side of the tests: the test VM (tests/vm/local.conf through tests/vm/config.py), commands on it, copying the repo tree and APKs there,
and the environment of one apklayer instance per test group (own binder, own XDG_RUNTIME_DIR and with it its own window shell)."""
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


def retried(run, attempts=5):
    """`run()` (a subprocess result) again while ssh itself fails (255: password hosts refuse about every other connection with "Permission
    denied" before any command runs). Only for calls that can be repeated: no stdin from a pipe."""
    for _ in range(attempts - 1):
        result = run()
        if result.returncode != 255: return result
    return run()


def ssh(command, **kw):
    if kw.get('stdin') not in (None, subprocess.DEVNULL): return subprocess.run(SSH + [command], **kw)  # a stream cannot be sent twice
    return retried(lambda: subprocess.run(SSH + [command], **kw))


def popen(command, **kw):
    return subprocess.Popen(SSH + [command], **kw)


def tar_to(tar_args, remote_command, names=None, attempts=5):
    """`tar -c tar_args` on this side piped into `remote_command` on the VM; the whole pipeline again when ssh refused the connection."""
    for attempt in range(attempts):
        tar = subprocess.Popen(['tar', '-c', '-f', '-', '--no-xattrs', *tar_args], stdout=subprocess.PIPE, env=dict(os.environ, COPYFILE_DISABLE='1'),
                               cwd=REPO if names is not None else None, stdin=subprocess.PIPE if names is not None else None)
        if names is not None: tar.stdin.write(names); tar.stdin.close()  # the file list fits the pipe: tar reads it while ssh takes its output
        remote = subprocess.run(SSH + [remote_command], stdin=tar.stdout)
        tar.stdout.close()
        if tar.wait() == 0 and remote.returncode == 0: return
        if remote.returncode != 255: break
    raise AssertionError('copying to the VM failed')


def _stat(name):
    st = os.lstat(REPO / name)
    return '%d %d %s' % (st.st_mtime_ns, st.st_size, name)


def push():
    """The working tree (tracked and new files, as they are now) into ROOT on the VM: only what differs from the manifest of the last push there
    (ROOT/.pushed: mtime, size, name). tar unlinks a file before it writes the new one (a rename in effect): a library a running JVM has mapped keeps
    its old inode (docs/AOSP-BUILDS.md, tests/vm/exit-clean.py)."""
    files = subprocess.run(['git', 'ls-files', '-co', '--exclude-standard', '-z'], cwd=REPO, capture_output=True, check=True).stdout.split(b'\0')
    names = sorted(f.decode() for f in files if f and os.path.lexists(REPO / f.decode()))  # symlinks too (their targets are on the VM)
    lines = {n: _stat(n) for n in names}
    # tar keeps the local modification times; after a push from another checkout (a worktree, a bisect) the VM's files may be newer than this
    # tree's and ninja would skip real changes: then every file gets the extraction time once (tar -m), a full rebuild
    state = ssh('mkdir -p %s && cat %s/.pushed-from %s/.pushed 2>/dev/null' % (ROOT, ROOT, ROOT), capture_output=True, text=True).stdout.split('\n')
    same = state[0] == str(REPO)
    have = set(state[1:]) if same else set()
    changed = [n for n in names if lines[n] not in have]
    if changed:
        tar_to(['--null', '-T', '-'], 'tar %s -C %s' % ('xf -' if same else 'xmf -', ROOT), names='\0'.join(changed).encode())
    # the manifest only after the files are there; an interrupted push repeats the files it did not record
    assert ssh('cat > %s/.pushed && echo %s > %s/.pushed-from' % (ROOT, shlex.quote(str(REPO)), ROOT), input='\n'.join(lines.values()) + '\n', text=True).returncode == 0, 'recording the push failed'
    # bionic_translation (untracked, tests/vm/README.md): a local checkout is copied once, so that the VM's meson never downloads it
    bt = Path(os.environ.get('APKLAYER_BIONIC_TRANSLATION') or REPO / 'subprojects/bionic_translation')
    d = ROOT + '/subprojects/bionic_translation'
    if bt.is_dir() and ssh('[ -d %s ]' % d).returncode != 0:
        tar_to(['-C', str(bt), '.'], 'mkdir -p %s && tar xmf - -C %s' % (d, d))


def copy(files, remote):
    """Local files into the VM directory `remote` (a shell word: $HOME works), by tar so that no error message carries the password."""
    files = [Path(f) for f in files]
    tar_to([a for f in files for a in ('-C', str(f.parent), f.name)], 'mkdir -p %s && tar xf - -C %s' % (remote, remote))


def instance_env(instance, out=None):
    """The shell prefix that puts a VM command into `instance`: cwd ROOT, its binder devices and runtime dir; the desktop session's Wayland,
    D-Bus and audio sockets by absolute path. APKLY_VM_ENV (local.conf) adds or overrides (APKLAYER_NATIVE, XDG_DATA_HOME, ...)."""
    run = '%s/run/%s' % (WORK, instance)
    env = {'APKLAYER_INSTANCE': instance, 'XDG_RUNTIME_DIR': run, 'WAYLAND_DISPLAY': '/run/user/$(id -u)/wayland-0',
           'DBUS_SESSION_BUS_ADDRESS': 'unix:path=/run/user/$(id -u)/bus', 'PULSE_SERVER': 'unix:/run/user/$(id -u)/pulse/native',
           'PIPEWIRE_RUNTIME_DIR': '/run/user/$(id -u)', 'APKLAYER_TEST_INPUT': '1',
           'APKLAYER_SANDBOX': '0',  # groups run unsandboxed whatever the VM's setup (the sandbox is on by default where set up); group sandbox turns it on
           'APKLAYER_PERMISSIONS': 'grant',  # runtime permissions held without the prompt (HostRuntimePermissions); group permissions turns it on
           'APKLAYER_NATIVE': ROOT + '/inst/lib/apklayer/native'}  # tests/run's meson install (the CLI finds it itself; shell tests read $APKLAYER_NATIVE)
    if out: env['APKLY_OUT'] = out
    exports = ' '.join('%s="%s"' % kv for kv in env.items())
    state = 'APKLY_STATE="${XDG_STATE_HOME:-$HOME/.local/state}/apklayer/%s"' % instance  # the instance's logs, for shell tests
    return 'cd %s && mkdir -p %s && chmod 700 %s && export %s %s && export %s; ' % (ROOT, run, run, exports, config.ENV, state)
