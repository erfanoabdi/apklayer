"""VM side of every test (tests/vm/*.py): the apklayer instance tests/run gave this group, its environment and logs, cleanup that ends only
this instance's processes, desktop screenshots and real key input. Importing it changes into the repo root."""
import os
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
os.chdir(ROOT)
sys.path.insert(0, str(ROOT / 'tools/debug'))
INSTANCE = os.environ.get('APKLAYER_INSTANCE', 'default')
SESSION = '/run/user/%d' % os.getuid()  # the desktop session: Wayland, D-Bus, audio and screenshots are reached there, whatever XDG_RUNTIME_DIR is
ENV = dict(os.environ)
ENV.setdefault('XDG_RUNTIME_DIR', SESSION)
ENV.setdefault('WAYLAND_DISPLAY', SESSION + '/wayland-0')
ENV.setdefault('APKLAYER_NATIVE', str(Path.home() / 'arm64-native'))
NATIVE = Path(ENV['APKLAYER_NATIVE'])
STATE = Path(ENV.get('XDG_STATE_HOME', Path.home() / '.local/state')) / 'apklayer' / INSTANCE
LOGDIR = str(STATE)  # the instance's logs (shell.log, hub.log, tool.log, app-<package>.log, ...)
OUT = Path(os.environ.get('APKLY_OUT', '/tmp/apklayer-test-' + INSTANCE))  # this group's own files (screenshots, dumps)
OUT.mkdir(parents=True, exist_ok=True)
HOST_LIBS = ':'.join(str(NATIVE / ('libapklayer-hal-%s.so' % hal)) for hal in ('graphics', 'media'))


def kill(*patterns):
    """Ends this instance's processes whose command line matches (pgrep -f): never another instance's."""
    subprocess.run(['sh', 'tests/vm/kill-instance.sh', INSTANCE, *patterns], check=False)


def mine(pattern):
    """`pgrep -af pattern` restricted to this instance: the lines of its matching processes."""
    found = subprocess.run(['pgrep', '-af', pattern], capture_output=True, text=True).stdout.splitlines()
    result = []
    for line in found:
        try: env = Path('/proc/%s/environ' % line.split()[0]).read_bytes().split(b'\0')
        except OSError: continue
        if next((e[18:].decode() for e in env if e.startswith(b'APKLAYER_INSTANCE=')), 'default') == INSTANCE: result.append(line)
    return result


def assert_clean():
    """Nothing of this instance is left running (app JVMs, btload, the shell)."""
    left = mine('[a]pklayer.host.Main|[b]tload|[a]pklayer-shell')
    assert not left, 'left running:\n' + '\n'.join(left)


def stop():
    """The instance's stack, hub, apps and shell, then anything of this instance the stop missed."""
    subprocess.run(['bin/apklayer', 'stop'], env=ENV, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    kill('[a]pklayer.host.Main', '[c]omponent-host', '[c]li/apklayer run ')


def capture(name, app=None):
    """A screenshot as OUT/<name>.png; returns the path. With app (a package of this instance): its window as SurfaceFlinger composes it
    (`apklayer screenshot`), independent of the desktop and of other instances. Without: the whole desktop (other windows included)."""
    image = OUT / (name.replace(' ', '-') + '.png')
    image.unlink(missing_ok=True)
    if app:
        subprocess.run(['bin/apklayer', 'screenshot', app, str(image)], env=ENV, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        assert image.exists(), 'no screenshot of %s in instance %s' % (app, INSTANCE)
        print('CAPTURED ' + str(image), flush=True)
        return image
    env = dict(os.environ, XDG_RUNTIME_DIR=SESSION, DBUS_SESSION_BUS_ADDRESS='unix:path=%s/bus' % SESSION, WAYLAND_DISPLAY='wayland-0',
               XDG_SESSION_TYPE='wayland', XDG_CURRENT_DESKTOP='GNOME')
    subprocess.run(['flameshot', 'full', '-p', str(image)], env=env, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=True)
    print('CAPTURED ' + str(image), flush=True)
    return image


_keys = OUT / 'uinput-keys'


def keys(*steps, **kw):
    """Real input through the kernel (tests/vm/uinput-keys.c): it goes to the focused window, so groups that use it run alone (tests/groups.toml)."""
    if not _keys.exists():
        subprocess.run(['gcc', '-O1', '-o', str(_keys), 'tests/vm/uinput-keys.c'], check=True)
    kw.setdefault('check', True)
    return subprocess.run(['sudo', '-n', str(_keys), *steps], **kw)


if __name__ == '__main__':  # for shell tests: python3 tests/lib/instance.py stop|clean
    {'stop': stop, 'clean': assert_clean}[sys.argv[1]]()
