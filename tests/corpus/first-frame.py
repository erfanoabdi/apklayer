#!/usr/bin/env python3
"""Runs on the test VM: blocks (inotify, no polling) until the shell has received a GPU frame after `offset` bytes of shell.log. Usage: first-frame.py <shell.log> <offset> <patience-seconds> [frames] [quiet-seconds];
passes once `frames` (default 1) GPU frames arrived; with quiet-seconds it then waits until no frame arrived for that long (a splash screen handing over to
the next activity, a list filling), at most 5 times quiet-seconds (an animation never goes quiet)."""
import ctypes
import os
import selectors
import sys
import time

path, offset, patience = sys.argv[1], int(sys.argv[2]), float(sys.argv[3])
wanted = int(sys.argv[4]) if len(sys.argv) > 4 else 1
quiet = float(sys.argv[5]) if len(sys.argv) > 5 else 0
libc = ctypes.CDLL(None, use_errno=True)
fd = libc.inotify_init1(os.O_NONBLOCK | os.O_CLOEXEC)
libc.inotify_add_watch(fd, os.fsencode(os.path.dirname(path)), 0x102)  # IN_MODIFY | IN_CREATE


def frames():
    if not os.path.exists(path): return 0
    with open(path, 'rb') as f:
        f.seek(offset)
        return f.read().count(b'dmabuf frame')


def wait(until):
    """Blocks until the directory changes or `until` passes; False once it has passed."""
    left = until - time.monotonic()
    if left <= 0: return False
    if events.select(left): os.read(fd, 65536)
    return True


with selectors.DefaultSelector() as events:
    events.register(fd, selectors.EVENT_READ)
    end = time.monotonic() + patience
    while frames() < wanted:
        if not wait(end): print('NOFRAME'); sys.exit(1)
    if quiet:
        cap = time.monotonic() + 5 * quiet
        seen, still = frames(), time.monotonic() + quiet
        while wait(min(still, cap)):
            now = frames()
            if now != seen: seen, still = now, time.monotonic() + quiet
    print('FRAME')
