#!/usr/bin/env python3
"""A virtual Xbox 360 style gamepad through /dev/uinput (run as root: sudo -n python3 tests/lib/uinput_pad.py <dir>), driven by lines on stdin:
  create <name> <vendor> <product>   the device, with rumble; its event node is made in <dir> (mode 0666, the shell's APKLAYER_INPUT_DIR), prints `created`
  key <evdev code> <0|1> | abs <evdev code> <value> | sync   events (sync: SYN_REPORT)
  destroy                            the device goes away (hot-unplug), its node too
  quit
Rumble the shell uploads and plays arrives here and is printed: `FF play <id> strong=<n> weak=<n> ms=<n>` / `FF stop <id>`.
Only the stdlib: fcntl ioctl numbers are computed from the struct sizes of 64-bit Linux."""
import fcntl
import os
import select
import struct
import sys

EV_SYN, EV_KEY, EV_ABS, EV_FF, EV_UINPUT = 0, 1, 3, 0x15, 0x101
FF_RUMBLE = 0x50
BUTTONS = [304, 305, 307, 308, 310, 311, 314, 315, 316, 317, 318]  # A B X Y L1 R1 select start mode thumbl thumbr
STICK = (-32768, 32767, 16)
# ABS_X, Y, Z (left trigger), RX, RY, RZ (right trigger), HAT0X, HAT0Y: the xpad driver's
ABS = {0: STICK, 1: STICK, 2: (0, 255, 0), 3: STICK, 4: STICK, 5: (0, 255, 0), 16: (-1, 1, 0), 17: (-1, 1, 0)}


def ioc(direction, nr, size, kind='U'):
    return direction << 30 | size << 16 | ord(kind) << 8 | nr


W, R = 1, 2
SET_EVBIT, SET_KEYBIT, SET_ABSBIT, SET_FFBIT = ioc(W, 100, 4), ioc(W, 101, 4), ioc(W, 103, 4), ioc(W, 107, 4)
DEV_SETUP, ABS_SETUP, DEV_CREATE, DEV_DESTROY = ioc(W, 3, 92), ioc(W, 4, 28), ioc(0, 1, 0), ioc(0, 2, 0)
BEGIN_UPLOAD, END_UPLOAD = ioc(W | R, 200, 104), ioc(W, 201, 104)
BEGIN_ERASE, END_ERASE = ioc(W | R, 202, 12), ioc(W, 203, 12)


class Pad:
    def __init__(self, directory, name, vendor, product):
        self.dir = directory
        self.fd = os.open('/dev/uinput', os.O_RDWR | os.O_NONBLOCK)
        fcntl.ioctl(self.fd, SET_EVBIT, EV_KEY)
        for b in BUTTONS: fcntl.ioctl(self.fd, SET_KEYBIT, b)
        fcntl.ioctl(self.fd, SET_EVBIT, EV_ABS)
        for code in ABS: fcntl.ioctl(self.fd, SET_ABSBIT, code)
        fcntl.ioctl(self.fd, SET_EVBIT, EV_FF)
        fcntl.ioctl(self.fd, SET_FFBIT, FF_RUMBLE)
        fcntl.ioctl(self.fd, DEV_SETUP, struct.pack('<HHHH80sI', 3, vendor, product, 0x110, name.encode(), 16))
        for code, (lo, hi, flat) in ABS.items():
            fcntl.ioctl(self.fd, ABS_SETUP, struct.pack('<HHiiiiii', code, 0, 0, lo, hi, 0, flat, 0))
        fcntl.ioctl(self.fd, DEV_CREATE)
        sysname = fcntl.ioctl(self.fd, ioc(R, 44, 64), b'\0' * 64).split(b'\0')[0].decode()
        sysdir = '/sys/devices/virtual/input/' + sysname
        event = next(e for e in os.listdir(sysdir) if e.startswith('event'))
        major, minor = map(int, open('%s/%s/dev' % (sysdir, event)).read().split(':'))
        self.node = os.path.join(directory, event)
        os.mknod(self.node, 0o666 | 0o020000, os.makedev(major, minor))  # no udev: the node is made here, readable by the test's user
        os.chmod(self.node, 0o666)
        self.effects, self.lengths = {}, {}

    def emit(self, kind, code, value):
        os.write(self.fd, struct.pack('<qqHHi', 0, 0, kind, code, value))

    def ff(self):
        """The driver's requests: an effect upload or erase (answered), and the shell's play and stop."""
        try:
            data = os.read(self.fd, 24 * 16)
        except BlockingIOError:
            return
        for i in range(0, len(data) - 23, 24):
            _, _, kind, code, value = struct.unpack('<qqHHi', data[i:i + 24])
            if kind == EV_UINPUT and code == 1:  # UI_FF_UPLOAD
                up = bytearray(struct.pack('<Ii', value, 0) + bytes(96))
                up = bytearray(fcntl.ioctl(self.fd, BEGIN_UPLOAD, bytes(up)))
                effect_id = struct.unpack_from('<h', up, 10)[0]
                self.effects[effect_id] = struct.unpack_from('<HH', up, 24)  # ff_effect (at 8): replay.length at +10 -> 18, rumble at +16 -> 24
                self.lengths[effect_id] = struct.unpack_from('<H', up, 18)[0]
                struct.pack_into('<i', up, 4, 0)
                fcntl.ioctl(self.fd, END_UPLOAD, bytes(up))
            elif kind == EV_UINPUT and code == 2:  # UI_FF_ERASE
                er = bytearray(struct.pack('<IiI', value, 0, 0))
                er = bytearray(fcntl.ioctl(self.fd, BEGIN_ERASE, bytes(er)))
                fcntl.ioctl(self.fd, END_ERASE, bytes(er))
            elif kind == EV_FF:
                if value:
                    strong, weak = self.effects.get(code, (0, 0))
                    print('FF play %d strong=%d weak=%d ms=%d' % (code, strong, weak, self.lengths.get(code, 0)), flush=True)
                else:
                    print('FF stop %d' % code, flush=True)

    def destroy(self):
        fcntl.ioctl(self.fd, DEV_DESTROY)
        os.close(self.fd)
        os.unlink(self.node)


def main():
    directory, pad, pending = sys.argv[1], None, b''
    while True:
        ready, _, _ = select.select([0] + ([pad.fd] if pad else []), [], [])
        if pad and pad.fd in ready: pad.ff()
        if 0 not in ready: continue
        data = os.read(0, 4096)  # not sys.stdin: its buffer would hold lines select cannot see
        if not data: break
        *lines, pending = (pending + data).split(b'\n')
        for line in lines:
            c = line.decode().split()
            if not c: continue
            if c[0] == 'create': pad = Pad(directory, c[1], int(c[2], 16), int(c[3], 16)); print('created', flush=True)
            elif c[0] == 'key': pad.emit(EV_KEY, int(c[1]), int(c[2]))
            elif c[0] == 'abs': pad.emit(EV_ABS, int(c[1]), int(c[2]))
            elif c[0] == 'sync': pad.emit(EV_SYN, 0, 0)
            elif c[0] == 'destroy': pad.destroy(); pad = None; print('destroyed', flush=True)
            elif c[0] == 'quit':
                if pad: pad.destroy()
                return
    if pad: pad.destroy()


if __name__ == '__main__':
    main()
