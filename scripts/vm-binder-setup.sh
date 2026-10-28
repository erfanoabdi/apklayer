#!/bin/sh
# vm-binder-setup: kernel binderfs for the services stack on the Ubuntu test VM.
# Run as root: echo PASSWORD | sudo -S sh scripts/vm-binder-setup.sh. Loads binder_linux, mounts binderfs, creates the binder, hwbinder and vndbinder
# nodes through the binder-control ioctl (a loaded module ignores devices=), and makes them usable by every user. Idempotent; run it after a reboot.
set -e
modprobe binder_linux 2>/dev/null || true
mkdir -p /dev/binderfs
mountpoint -q /dev/binderfs || mount -t binder binder /dev/binderfs
python3 - <<'PY'
import fcntl, os, struct
control = os.open('/dev/binderfs/binder-control', os.O_RDWR)
for name in ('binder', 'hwbinder', 'vndbinder'):
    if os.path.exists('/dev/binderfs/' + name):
        continue
    # BINDER_CTL_ADD = _IOWR('b', 1, struct binderfs_device { char name[256]; __u32 major, minor; })
    fcntl.ioctl(control, 0xC1086201, struct.pack('256sII', name.encode(), 0, 0))
PY
chmod 666 /dev/binderfs/binder /dev/binderfs/hwbinder /dev/binderfs/vndbinder
for n in binder hwbinder vndbinder; do ln -sf /dev/binderfs/$n /dev/$n; done
ls -la /dev/binder
