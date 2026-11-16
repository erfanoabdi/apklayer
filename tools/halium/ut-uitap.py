#!/usr/bin/env python3
"""ut-uitap.py X Y [X Y ...]: taps at screen pixels (720x1560) through a temporary uinput touchscreen (root); an argument X1,Y1>X2,Y2 swipes instead (one argument, not a pair)."""
import fcntl, os, struct, sys, time
W, H = 720, 1560
UI_SET_EVBIT, UI_SET_KEYBIT, UI_SET_ABSBIT, UI_SET_PROPBIT = 0x40045564, 0x40045565, 0x40045567, 0x4004556e
UI_DEV_CREATE, UI_DEV_DESTROY = 0x5501, 0x5502
EV_SYN, EV_KEY, EV_ABS = 0, 1, 3
BTN_TOUCH, ABS_X, ABS_Y, ABS_MT_SLOT, ABS_MT_POSITION_X, ABS_MT_POSITION_Y, ABS_MT_TRACKING_ID = 0x14a, 0, 1, 0x2f, 0x35, 0x36, 0x39
fd = os.open('/dev/uinput', os.O_WRONLY | os.O_NONBLOCK)
fcntl.ioctl(fd, UI_SET_EVBIT, EV_KEY)
fcntl.ioctl(fd, UI_SET_EVBIT, EV_ABS)
fcntl.ioctl(fd, UI_SET_KEYBIT, BTN_TOUCH)
fcntl.ioctl(fd, UI_SET_PROPBIT, 1)  # INPUT_PROP_DIRECT: a touchscreen
absmax, absmin = [0] * 64, [0] * 64
for code, mx in ((ABS_X, W - 1), (ABS_Y, H - 1), (ABS_MT_POSITION_X, W - 1), (ABS_MT_POSITION_Y, H - 1), (ABS_MT_SLOT, 9), (ABS_MT_TRACKING_ID, 65535)):
    fcntl.ioctl(fd, UI_SET_ABSBIT, code)
    absmax[code] = mx
os.write(fd, struct.pack('80sHHHHI', b'apklayer-test-touch', 3, 1, 2, 1, 0) + struct.pack('64i', *absmax) + struct.pack('64i', *absmin) + b'\0' * (2 * 64 * 4))
fcntl.ioctl(fd, UI_DEV_CREATE)
time.sleep(1.0)
def ev(t, c, v): os.write(fd, struct.pack('llHHi', 0, 0, t, c, v))
args = sys.argv[1:]
nums = [a for a in args if '>' not in a]
def tap(x, y, n):
    ev(EV_ABS, ABS_MT_SLOT, 0); ev(EV_ABS, ABS_MT_TRACKING_ID, n)
    ev(EV_ABS, ABS_MT_POSITION_X, x); ev(EV_ABS, ABS_MT_POSITION_Y, y)
    ev(EV_ABS, ABS_X, x); ev(EV_ABS, ABS_Y, y); ev(EV_KEY, BTN_TOUCH, 1); ev(EV_SYN, 0, 0)
def move(x, y):
    ev(EV_ABS, ABS_MT_POSITION_X, x); ev(EV_ABS, ABS_MT_POSITION_Y, y); ev(EV_ABS, ABS_X, x); ev(EV_ABS, ABS_Y, y); ev(EV_SYN, 0, 0)
def lift():
    ev(EV_ABS, ABS_MT_TRACKING_ID, -1); ev(EV_KEY, BTN_TOUCH, 0); ev(EV_SYN, 0, 0)
i, n = 0, 1
while i < len(args):
    if '>' in args[i]:
        (x1, y1), (x2, y2) = [[int(v) for v in p.split(',')] for p in args[i].split('>')]
        tap(x1, y1, n); time.sleep(0.05)
        for k in range(1, 13):
            move(x1 + (x2 - x1) * k // 12, y1 + (y2 - y1) * k // 12); time.sleep(0.02)
        lift(); time.sleep(0.6); i += 1
    else:
        tap(int(args[i]), int(args[i + 1]), n); time.sleep(0.1); lift(); time.sleep(0.6); i += 2
    n += 1
fcntl.ioctl(fd, UI_DEV_DESTROY)
os.close(fd)
