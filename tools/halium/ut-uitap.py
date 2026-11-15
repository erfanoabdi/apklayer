#!/usr/bin/env python3
"""ut-uitap.py (X Y | X1,Y1,X2,Y2 | wait:MS)...: taps and swipes at screen pixels (720x1560) through a temporary uinput touchscreen (root)."""
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
def touch(x, y, tid, first):
    if first: ev(EV_ABS, ABS_MT_SLOT, 0); ev(EV_ABS, ABS_MT_TRACKING_ID, tid)
    ev(EV_ABS, ABS_MT_POSITION_X, x); ev(EV_ABS, ABS_MT_POSITION_Y, y)
    ev(EV_ABS, ABS_X, x); ev(EV_ABS, ABS_Y, y); ev(EV_KEY, BTN_TOUCH, 1); ev(EV_SYN, 0, 0)
def up():
    ev(EV_ABS, ABS_MT_TRACKING_ID, -1); ev(EV_KEY, BTN_TOUCH, 0); ev(EV_SYN, 0, 0)
args, n = sys.argv[1:], 0
while args:
    n += 1
    if args[0].startswith('wait:'): time.sleep(int(args.pop(0)[5:]) / 1000); continue
    if ',' in args[0]:  # a swipe X1,Y1,X2,Y2: 10 steps in 0.4 s
        x1, y1, x2, y2 = [int(v) for v in args.pop(0).split(',')]
        touch(x1, y1, n, True)
        for k in range(1, 11):
            time.sleep(0.04); touch(x1 + (x2 - x1) * k // 10, y1 + (y2 - y1) * k // 10, n, False)
        up()
    else:
        touch(int(args.pop(0)), int(args.pop(0)), n, True); time.sleep(0.1); up()
    time.sleep(0.6)
fcntl.ioctl(fd, UI_DEV_DESTROY)
os.close(fd)
