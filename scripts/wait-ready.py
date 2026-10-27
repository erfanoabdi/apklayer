#!/usr/bin/env python3
"""Wait for a service's ready marker or its death, without polling."""
import ctypes
import os
from pathlib import Path
import selectors
import sys
import time

path = Path(sys.argv[1])
pid = int(sys.argv[2])
libc = ctypes.CDLL(None, use_errno=True)
fd = libc.inotify_init1(os.O_CLOEXEC | os.O_NONBLOCK)
if fd < 0 or libc.inotify_add_watch(fd, bytes(path.parent), 0x108) < 0:
    raise OSError(ctypes.get_errno(), 'watch service readiness')
try:
    process = os.pidfd_open(pid)
except ProcessLookupError:
    sys.exit('service exited before becoming ready')
try:
    with selectors.DefaultSelector() as events:
        events.register(fd, selectors.EVENT_READ)
        events.register(process, selectors.EVENT_READ)
        end = time.monotonic() + 3
        while not path.is_file():
            ready = events.select(max(0, end - time.monotonic()))
            if not ready:
                sys.exit('service produced no ready marker within 3 seconds; inspect its logs')
            if any(key.fd == process for key, _ in ready):
                sys.exit('service exited before becoming ready')
            os.read(fd, 65536)
finally:
    os.close(fd)
    os.close(process)
