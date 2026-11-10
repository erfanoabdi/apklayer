#!/usr/bin/env python3
"""fakeaccel.py: an evdev accelerometer (uinput, "apklayer-accel") for sensorfwd's evdev accelerometer adaptor. Each stdin line "x y z" (mG)
sets the gravity, sent at 20 Hz (+-1 jitter: evdev drops repeated values); EOF removes it."""
import fcntl, os, select, struct, sys
UI_SET_EVBIT, UI_SET_ABSBIT, UI_DEV_CREATE, UI_DEV_DESTROY = 0x40045564, 0x40045567, 0x5501, 0x5502
EV_SYN, EV_ABS = 0, 3
fd = os.open('/dev/uinput', os.O_WRONLY | os.O_NONBLOCK)
fcntl.ioctl(fd, UI_SET_EVBIT, EV_ABS)
absmax, absmin = [0] * 64, [0] * 64
for c in (0, 1, 2):
    fcntl.ioctl(fd, UI_SET_ABSBIT, c); absmax[c] = 4000; absmin[c] = -4000
os.write(fd, struct.pack('80sHHHHI', b'apklayer-accel', 3, 1, 2, 1, 0) + struct.pack('64i', *absmax) + struct.pack('64i', *absmin) + b'\0' * (2 * 64 * 4))
fcntl.ioctl(fd, UI_DEV_CREATE)
print("accel created", flush=True)
def ev(t, c, v): os.write(fd, struct.pack('llHHi', 0, 0, t, c, v))
g, n = (0, -1000, 0), 0
while True:
    if select.select([sys.stdin], [], [], 0.05)[0]:
        line = sys.stdin.readline()
        if not line: break
        try: g = tuple(int(v) for v in line.split()); print("accel", *g, flush=True)
        except ValueError: pass
    n ^= 1
    for c in range(3): ev(EV_ABS, c, g[c] + n)
    ev(EV_SYN, 0, 0)
fcntl.ioctl(fd, UI_DEV_DESTROY)
os.close(fd)
