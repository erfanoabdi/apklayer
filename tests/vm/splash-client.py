#!/usr/bin/env python3
"""VM side: a stand-in app for the splash test. Connects to the shell, creates a window titled like a real app's
(apklayer:<pid>:<inst>:<package>) and shows nothing until the file /tmp/splash-go exists, then sends one real frame.
Usage: splash-client.py SOCKET PACKAGE   (it stays until killed)"""
import os, socket, struct, sys, select

sock_path, pkg = sys.argv[1], sys.argv[2]
W, H = 540, 960
s = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
s.connect(sock_path)
def send(t, payload):
    s.sendall(struct.pack('<II', t, len(payload)) + payload)
send(1, struct.pack('<i', os.getpid()) + pkg.encode())
path = '/tmp/splash-shm-%d' % os.getpid()
with open(path, 'wb') as f:
    px = bytearray(b'\xf4\xf1\xee\xff' * (W * H * 2))  # near-white ARGB, two buffers
    px[:W * 90 * 4] = b'\x80\x40\x20\xff' * (W * 90)    # a dark app bar
    f.write(px)
send(2, struct.pack('<iii', 1, W, H) + (path + '\0apklayer:%d:splash:%s' % (os.getpid(), pkg)).encode())
sent = False
while True:
    if not sent and os.path.exists('/tmp/splash-go'):
        send(3, struct.pack('<iiiiii', 1, 0, 0, 0, W, H))
        sent = True
    r, _, _ = select.select([s], [], [], 0.2)
    if r and not s.recv(65536):
        break
