#!/usr/bin/env python3
"""Own the complete media stack; actual log events expose progress and stalls."""
import os as _os
LOGDIR = _os.path.expanduser('~/.local/state/apklayer/default')  # the default instance's logs (apklayer tool)
import ctypes
import os
from pathlib import Path
import selectors
import signal
import subprocess
import sys
import time

logs = Path(os.environ.get('APKLAYER_LOGS', LOGDIR))
logs.mkdir(parents=True, exist_ok=True)
libc = ctypes.CDLL(None, use_errno=True)
fd = libc.inotify_init1(os.O_NONBLOCK | os.O_CLOEXEC)
if fd < 0 or libc.inotify_add_watch(fd, os.fsencode(logs), 0x102) < 0:
    raise OSError(ctypes.get_errno(), 'inotify')
positions = {}

def progress():
    for name in ('codec2.log', 'tool.log'):
        path = logs / name
        try:
            stat = path.stat()
            inode, offset, partial = positions.get(name, (stat.st_ino, 0, ''))
            if inode != stat.st_ino or offset > stat.st_size:
                offset, partial = 0, ''
            with path.open() as stream:
                stream.seek(offset)
                text = partial + stream.read()
                offset = stream.tell()
            lines = text.split('\n')
            positions[name] = stat.st_ino, offset, lines.pop()
            for line in lines:
                if line.startswith('codec2:'):
                    print(line, flush=True)
        except FileNotFoundError:
            positions.pop(name, None)

fixture = sys.argv[1] if len(sys.argv) > 1 else '/tmp/apklayer-vp8.ivf'
mode = sys.argv[2:]
probe = '--reorder-probe' if mode == ['reorder'] else '--remote-probe'
if probe == '--reorder-probe': mode = []
proc = subprocess.Popen(['b/cli/apklayer', 'stack', 'libapklayer_codec2.so', probe,
    fixture, *mode], stdout=subprocess.PIPE,
    stderr=subprocess.STDOUT, start_new_session=True)
try:
    with selectors.DefaultSelector() as events:
        events.register(fd, selectors.EVENT_READ)
        events.register(proc.stdout, selectors.EVENT_READ)
        deadline = time.monotonic() + 20
        while True:
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                raise RuntimeError('media stack exceeded 20 seconds')
            ready = events.select(min(3, remaining))
            if not ready:
                raise RuntimeError('media stack stalled without log activity for 3 seconds')
            done = False
            for key, _ in ready:
                if key.fileobj == fd:
                    os.read(fd, 65536)
                    progress()
                else:
                    chunk = os.read(proc.stdout.fileno(), 65536)
                    if chunk:
                        print(chunk.decode(errors='replace'), end='', flush=True)
                    else:
                        done = True
            if done:
                progress()
                raise SystemExit(proc.wait())
finally:
    os.close(fd)
    # Always terminate the owned group, including children if its leader exited.
    try:
        os.killpg(proc.pid, signal.SIGTERM)
    except ProcessLookupError:
        pass
    try:
        proc.wait(timeout=3)
    except subprocess.TimeoutExpired:
        os.killpg(proc.pid, signal.SIGKILL)
        proc.wait()
    proc.stdout.close()
