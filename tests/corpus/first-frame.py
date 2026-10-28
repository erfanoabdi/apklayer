#!/usr/bin/env python3
"""Runs on the test VM: blocks (inotify, no polling) until the shell has received a GPU frame after `offset` bytes of shell.log. Usage: first-frame.py <shell.log> <offset> <patience-seconds>"""
import ctypes
import os
import selectors
import sys
import time

path, offset, patience = sys.argv[1], int(sys.argv[2]), float(sys.argv[3])
libc = ctypes.CDLL(None, use_errno=True)
fd = libc.inotify_init1(os.O_NONBLOCK | os.O_CLOEXEC)
libc.inotify_add_watch(fd, os.fsencode(os.path.dirname(path)), 0x102)  # IN_MODIFY | IN_CREATE
end = time.monotonic() + patience
with selectors.DefaultSelector() as events:
    events.register(fd, selectors.EVENT_READ)
    while True:
        if os.path.exists(path):
            with open(path, 'rb') as f:
                f.seek(offset)
                if b'dmabuf frame' in f.read():
                    print('FRAME'); sys.exit(0)
        left = end - time.monotonic()
        if left <= 0: print('NOFRAME'); sys.exit(1)
        if events.select(left): os.read(fd, 65536)
