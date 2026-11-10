"""VM side of every test (tests/vm/*.py): the apklayer instance tests/run gave this group, its environment and logs, cleanup that ends only
this instance's processes, screenshots, test input into this instance's windows and real key input. Importing it changes into the repo root."""
import ctypes
import os
import selectors
import socket
import struct
import signal
import subprocess
import sys
import time as _time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
os.chdir(ROOT)
sys.path.insert(0, str(ROOT / 'tools/debug'))
INSTANCE = os.environ.get('APKLAYER_INSTANCE', 'default')
SESSION = '/run/user/%d' % os.getuid()  # the desktop session: Wayland, D-Bus, audio and screenshots are reached there, whatever XDG_RUNTIME_DIR is
ENV = dict(os.environ)
ENV.setdefault('XDG_RUNTIME_DIR', SESSION)
ENV.setdefault('WAYLAND_DISPLAY', SESSION + '/wayland-0')
ENV.setdefault('APKLAYER_TEST_INPUT', '1')  # the shell takes test input (inject) from this instance's tests
# the native dir `meson install` made beside the tree (tests/run: <repo>/inst), as the CLI finds it (cli/layout.cpp)
ENV.setdefault('APKLAYER_NATIVE', str(ROOT / 'inst/lib/apklayer/native') if (ROOT / 'inst/lib/apklayer/native').is_dir() else str(Path.home() / 'arm64-native'))
NATIVE = Path(ENV['APKLAYER_NATIVE'])
STATE = Path(ENV.get('XDG_STATE_HOME', Path.home() / '.local/state')) / 'apklayer' / INSTANCE
LOGDIR = str(STATE)  # the instance's logs (shell.log, hub.log, tool.log, app-<package>.log, ...)
OUT = Path(os.environ.get('APKLY_OUT', '/tmp/apklayer-test-' + INSTANCE))  # this group's own files (screenshots, dumps)
OUT.mkdir(parents=True, exist_ok=True)
HOST_LIBS = ':'.join(str(NATIVE / ('libapklayer-hal-%s.so' % hal)) for hal in ('graphics', 'media'))


def kill(*patterns):
    """Ends this instance's processes whose command line matches (pgrep -f): never another instance's."""
    subprocess.run(['sh', 'tests/vm/kill-instance.sh', INSTANCE, *patterns], check=False)


def user_ns(pid):
    try: return os.readlink('/proc/%s/ns/user' % pid)
    except (OSError, TypeError): return None


def stack_pid():
    try: return (STATE / 'stack.pid').read_text().strip()
    except OSError: return None


def belongs(pid):
    """Whether process pid is this instance's: APKLAYER_INSTANCE in its environment ("default" without), or, for a sandboxed app process (its own uid,
    cli/sandbox.cpp: its environment is private), the user namespace its instance's stack made."""
    try: env = Path('/proc/%s/environ' % pid).read_bytes().split(b'\0')
    except OSError:
        ns = user_ns(pid)
        return ns is not None and ns != user_ns('self') and ns == user_ns(stack_pid())
    return next((e[18:].decode() for e in env if e.startswith(b'APKLAYER_INSTANCE=')), 'default') == INSTANCE


_cpu = [0]


def busy(seconds=0.3):
    """Whether this instance's processes used at least that much CPU time since the last call: a start that is silent on a loaded host (JVM
    start, hub boot) is busy, a hung one is idle (an idle stack's own ticks stay far below it)."""
    total = 0
    for p in Path('/proc').iterdir():
        if p.name.isdigit() and int(p.name) != os.getpid() and belongs(p.name):
            try: f = (p / 'stat').read_text().rsplit(')', 1)[1].split(); total += int(f[11]) + int(f[12])  # utime, stime
            except (OSError, IndexError, ValueError): pass
    was, _cpu[0] = _cpu[0], total
    return total - was >= seconds * os.sysconf('SC_CLK_TCK')


def mine(pattern, exact=False):
    """`pgrep -af pattern` restricted to this instance: the lines of its matching processes. exact: match the process name (`pgrep -x`), for the
    JVMs, whose command line is their Android process name once the runtime has named them."""
    found = subprocess.run(['pgrep', '-a'] + (['-x'] if exact else ['-f']) + [pattern], capture_output=True, text=True).stdout.splitlines()
    return [line for line in found if belongs(line.split()[0])]


def assert_clean():
    """Nothing of this instance is left running (app JVMs, btload, the shell)."""
    left = mine('[b]tload|[a]pklayer-shell') + mine('java', exact=True)
    assert not left, 'left running:\n' + '\n'.join(left)


def stop():
    """The instance's stack, hub, apps and shell, then anything of this instance the stop missed. Returns the services that did not end
    cleanly at this stop (the stack daemon's "stopped: NAME (status N)" lines: SIGSEGV is 139)."""
    log = STATE / 'stack.log'
    seen = log.stat().st_size if log.exists() else 0
    subprocess.run(['bin/apklayer', 'stop'], env=ENV, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    kill('[c]omponent-host')
    for line in mine('java', exact=True):  # the app and service JVMs the stop missed (by name: their command lines are process names)
        try: os.kill(int(line.split()[0]), signal.SIGTERM)
        except (OSError, ValueError): pass
    if not log.exists(): return []
    with open(log, 'rb') as f:
        f.seek(seen)
        return [line for line in f.read().decode(errors='replace').splitlines() if line.startswith('stopped:')]


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
    # flameshot can wait forever on GNOME's screenshot portal: a bounded wait, so one stuck capture never stalls a run
    subprocess.run(['flameshot', 'full', '-p', str(image)], env=env, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=True, timeout=20)
    print('CAPTURED ' + str(image), flush=True)
    return image


_keys = OUT / 'uinput-keys'


def keys(*steps, **kw):
    """Real input through the kernel (tests/vm/uinput-keys.c): it goes to the focused window, so groups that use it run alone (tests/groups.toml)."""
    if not _keys.exists():
        subprocess.run(['gcc', '-O1', '-o', str(_keys), 'tests/vm/uinput-keys.c'], check=True)
    kw.setdefault('check', True)
    return subprocess.run(['sudo', '-n', str(_keys), *steps], **kw)


MODS = {'alt': 56, 'ctrl': 29, 'shift': 42, 'super': 125}  # evdev codes of combo:<mod>+<code>
TEST_INPUT, TEST_DONE = 11, 115  # bridge/shell/shell.h
FOCUS, KEY, POINTER, TOUCH, TEXT, RESIZE, SCALE, MAXIMIZE, CLOSE, IME = range(10)


def inject(window, *steps, secret=False):
    """Test input into one window of this instance's shell (bridge/shell/testinput.c): no desktop, no focus, so groups that use it run in
    parallel. The events take the shell's own path from the seat on (focus, xkb, pointer, touch, text-input, configure). window: the app's
    package. Steps (x, y: logical pixels in the window's content, y < 0: its title bar):
      focus | blur (the focus leaves it) | key:<evdev> | combo:<mod>+<evdev> | click:<x>,<y> | rclick:<x>,<y> | tap:<x>,<y> | press:<x>,<y> | move:<x>,<y> | release:<x>,<y> (a finger down, moved and up: a long press or a drag) | text:<utf8> | preedit:<utf8> | delete:<n> | resize:<w>,<h>
      | scale:<percent> (the compositor's preferred scale) | maximize:<w>,<h> (window geometry) | unmaximize[:<w>,<h>] | close
      | ime:<px> (the host's on-screen keyboard covers the bottom px app pixels, 0: hidden; as the phones' Maliit keyboard)"""
    print('INJECT %s %s' % (window, ' '.join('text:***' if secret and s.startswith('text:') else s for s in steps)), flush=True)  # secret: a password typed in
    messages = []
    for step in steps:
        kind, _, arg = step.partition(':')
        xy = [int(n) for n in arg.split(',')] if kind in ('click', 'rclick', 'tap', 'press', 'move', 'release', 'resize', 'maximize') or (kind == 'unmaximize' and arg) else [0, 0]
        if kind == 'focus': messages.append((FOCUS, 0, 0, 0, ''))
        elif kind == 'blur': messages.append((FOCUS, 1, 0, 0, ''))
        elif kind == 'key': messages += [(KEY, int(arg), 1, 0, ''), (KEY, int(arg), 0, 0, '')]
        elif kind == 'combo':
            mod, code = arg.split('+')
            messages += [(KEY, MODS[mod], 1, 0, ''), (KEY, int(code), 1, 0, ''), (KEY, int(code), 0, 0, ''), (KEY, MODS[mod], 0, 0, '')]
        elif kind in ('click', 'rclick'):
            button = 0x110 if kind == 'click' else 0x111
            messages += [(POINTER, *xy, button, ''), (POINTER, *xy, -button, '')]
        elif kind == 'tap': messages += [(TOUCH, 0, *xy, ''), (TOUCH, 1, *xy, '')]
        elif kind in ('press', 'release', 'move'): messages.append((TOUCH, ('press', 'release', 'move').index(kind), *xy, ''))
        elif kind in ('text', 'preedit'): messages.append((TEXT, int(kind == 'preedit'), 0, 0, arg))
        elif kind == 'delete': messages.append((TEXT, 2, 0, 0, arg))  # the IME deletes <n> characters before the cursor
        elif kind == 'resize': messages.append((RESIZE, *xy, 0, ''))
        elif kind == 'scale': messages.append((SCALE, int(arg), 0, 0, ''))
        elif kind == 'ime': messages.append((IME, int(arg), 0, 0, ''))
        elif kind == 'maximize': messages.append((MAXIMIZE, 1, *xy, ''))
        elif kind == 'unmaximize': messages.append((MAXIMIZE, 0, *xy, ''))  # with w,h: a floating configure of that size
        elif kind == 'close': messages.append((CLOSE, 0, 0, 0, ''))  # the compositor closes the window (its title bar's X, the spread)
        else: raise ValueError('unknown step ' + step)
    with socket.socket(socket.AF_UNIX) as s:
        s.settimeout(3)
        s.connect(str(Path(ENV['XDG_RUNTIME_DIR']) / 'apklayer/shell.sock'))
        for *v, text in messages:
            payload = struct.pack('<4i', *v) + window.encode() + b'\0' + text.encode()
            s.sendall(struct.pack('<2I', TEST_INPUT, len(payload)) + payload)
            while True:  # the answer (a test connection gets nothing else)
                kind, size = struct.unpack('<2I', s.recv(8, socket.MSG_WAITALL))
                body = s.recv(size, socket.MSG_WAITALL)
                if kind == TEST_DONE: break
            result = struct.unpack('<i', body)[0]
            assert result == 0, 'test input %s into %s: %s' % (v, window, {-1: 'no such window', -2: 'no text field enabled', -3: 'refused'}.get(result, result))


if __name__ == '__main__':  # for shell tests: python3 tests/lib/instance.py stop|clean
    {'stop': stop, 'clean': assert_clean}[sys.argv[1]]()


_libc = ctypes.CDLL(None, use_errno=True)


def wait_file(path, needle, seconds):
    """Block on inotify (no polling) until the file contains the needle; returns the text."""
    fd = _libc.inotify_init1(os.O_NONBLOCK | os.O_CLOEXEC)
    os.makedirs(path.parent, exist_ok=True)
    assert _libc.inotify_add_watch(fd, os.fsencode(path.parent), 0x102) >= 0  # IN_MODIFY | IN_CREATE in the directory
    end = _time.monotonic() + seconds
    with selectors.DefaultSelector() as events:
        events.register(fd, selectors.EVENT_READ)
        while _time.monotonic() < end:
            text = path.read_text(errors='replace') if path.exists() else ''
            if needle in text: os.close(fd); return text
            if events.select(min(2, max(0.1, end - _time.monotonic()))): os.read(fd, 65536)
    os.close(fd)
    raise AssertionError('timed out waiting for ' + needle + ' in ' + str(path) + '\n' + (path.read_text(errors='replace')[-2000:] if path.exists() else '(no file)'))
