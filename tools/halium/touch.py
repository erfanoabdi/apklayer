#!/usr/bin/env python3
"""touch.py [-d /dev/input/eventN] ACTION...: touches written into the phone's real touchscreen node (Sailfish: goodix_ts; a uinput touchscreen is not
picked up by Lipstick's Qt 5.6 evdevtouch). Run as root. Coordinates are screen pixels (the node's range is scaled to the screen: -s WxH, default 1080x2400).
Actions: tap:X,Y  swipe:X1,Y1,X2,Y2[,MS]  hold:X,Y,MS  two:X1,Y1,X2,Y2 (two fingers down together, up)  wait:MS"""
import fcntl, os, struct, sys, time, glob

EV_SYN, EV_KEY, EV_ABS = 0, 1, 3
SLOT, TRACK, X, Y, BTN_TOUCH = 0x2f, 0x39, 0x35, 0x36, 0x14a


def find():
    for d in glob.glob('/sys/class/input/event*'):
        if 'touch' in open(d + '/device/name').read().lower() or 'goodix' in open(d + '/device/name').read():
            return '/dev/input/' + os.path.basename(d)
    sys.exit('touch.py: no touchscreen node')


args = sys.argv[1:]
dev, size = None, (1080, 2400)
while args and args[0] in ('-d', '-s'):
    if args[0] == '-d': dev = args[1]
    else: size = tuple(int(v) for v in args[1].split('x'))
    args = args[2:]
dev = dev or find()
fd = os.open(dev, os.O_WRONLY)


def absmax(code):  # EVIOCGABS: struct input_absinfo (value, min, max, fuzz, flat, res)
    buf = fcntl.ioctl(os.open(dev, os.O_RDONLY), 0x80184540 + code, b'\0' * 24)
    return struct.unpack('6i', buf)[2]


mx, my = absmax(X), absmax(Y)


def ev(t, c, v): os.write(fd, struct.pack('llHHi', 0, 0, t, c, v))
def syn(): ev(EV_SYN, 0, 0)
def pos(slot, x, y):
    ev(EV_ABS, SLOT, slot); ev(EV_ABS, X, int(x * mx / size[0])); ev(EV_ABS, Y, int(y * my / size[1]))
def down(slot, x, y, tid):
    ev(EV_ABS, SLOT, slot); ev(EV_ABS, TRACK, tid)
    if tid == slot + 1: ev(EV_KEY, BTN_TOUCH, 1)
    pos(slot, x, y); syn()
def up(slot, last=True):
    ev(EV_ABS, SLOT, slot); ev(EV_ABS, TRACK, -1)
    if last: ev(EV_KEY, BTN_TOUCH, 0)
    syn()
def nap(ms): time.sleep(ms / 1000.0)


for a in args:
    name, _, v = a.partition(':')
    n = [float(p) for p in v.split(',')] if v else []
    if name == 'tap': down(0, n[0], n[1], 1); nap(120); up(0)
    elif name == 'hold': down(0, n[0], n[1], 1); nap(n[2]); up(0)
    elif name == 'swipe':
        ms = n[4] if len(n) > 4 else 300; steps = max(2, int(ms / 16))
        down(0, n[0], n[1], 1)
        for i in range(1, steps + 1):
            pos(0, n[0] + (n[2] - n[0]) * i / steps, n[1] + (n[3] - n[1]) * i / steps); syn(); nap(ms / steps)
        up(0)
    elif name == 'two':
        down(0, n[0], n[1], 1); down(1, n[2], n[3], 2); nap(80); up(1, False); up(0)
    elif name == 'wait': nap(n[0])
    else: sys.exit('touch.py: unknown action ' + a)
    nap(300)
