#!/usr/bin/env python3
"""dev-uikeys.py KEY[+KEY...] ...: press key chords through a temporary uinput keyboard (root). Names: VOLUMEUP VOLUMEDOWN POWER ..."""
import fcntl, os, struct, sys, time
codes = {'VOLUMEDOWN': 114, 'VOLUMEUP': 115, 'POWER': 116, 'ENTER': 28, 'ESC': 1}
UI_SET_EVBIT, UI_SET_KEYBIT, UI_DEV_CREATE, UI_DEV_DESTROY = 0x40045564, 0x40045565, 0x5501, 0x5502
EV_SYN, EV_KEY = 0, 1
fd = os.open('/dev/uinput', os.O_WRONLY | os.O_NONBLOCK)
fcntl.ioctl(fd, UI_SET_EVBIT, EV_KEY)
for c in codes.values(): fcntl.ioctl(fd, UI_SET_KEYBIT, c)
# struct uinput_user_dev: name[80], input_id (4 x u16), ff_effects_max u32, absmax/absmin/absfuzz/absflat [64] s32
os.write(fd, struct.pack('80sHHHHI', b'apklayer-test-keys', 3, 1, 1, 1, 0) + b'\0' * (4 * 64 * 4))
fcntl.ioctl(fd, UI_DEV_CREATE)
time.sleep(1.0)  # the compositor opens the new device
def ev(t, c, v): os.write(fd, struct.pack('llHHi', 0, 0, t, c, v))
for chord in sys.argv[1:]:
    keys = [codes[k] for k in chord.split('+')]
    for k in keys: ev(EV_KEY, k, 1)
    ev(EV_SYN, 0, 0)
    time.sleep(0.3)
    for k in reversed(keys): ev(EV_KEY, k, 0)
    ev(EV_SYN, 0, 0)
    time.sleep(0.5)
fcntl.ioctl(fd, UI_DEV_DESTROY)
os.close(fd)
