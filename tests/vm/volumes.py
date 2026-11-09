#!/usr/bin/env python3
"""A removable drive plugged in and out while an app runs (HubVolumes, HubMediaWatch, ShadowHostStorageManager.changed): tests/apps/hello-files'
hotplug scenario (Hotplug.java) against a vfat loop image that this script mounts where the desktop's udisks2 would (/media/<user>/<label>) and
removes again. DCIM/pre.png is put on the image before the app starts (the volume scan at mount must find it). Root for losetup and mount:
SUDO_ASKPASS set -> `sudo -A`, else `sudo -n`. Steps are the app's `[hotplug] PASS|FAIL <step>` lines."""
import os
import selectors
import subprocess
import sys
import time
import zlib
import struct
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
from instance import ENV, OUT, stop  # noqa: E402

STEPS = ['mounted broadcast', 'volume callback', 'volume listed', 'mediastore volume', 'mount scan', 'drive file indexed', 'internal file indexed',
         'internal file dropped', 'MEDIA_EJECT broadcast', 'MEDIA_UNMOUNTED broadcast', 'MEDIA_REMOVED broadcast', 'volume gone', 'callback on removal', 'link removed']
SUDO = ['sudo', '-A'] if os.environ.get('SUDO_ASKPASS') else ['sudo', '-n']
LABEL = 'APKHOT' + os.environ.get('APKLAYER_INSTANCE', 'x')[-2:].upper()
image, point = OUT / 'hotplug.img', Path('/media') / os.environ.get('USER', 'user') / LABEL
device = None


def root(*args, check=True):
    done = subprocess.run(SUDO + list(args), capture_output=True, text=True)
    assert done.returncode == 0 or not check, '%s: %s' % (' '.join(args), done.stderr.strip())
    return done.stdout.strip()


def mount():
    root('mkdir', '-p', str(point))
    root('mount', '-t', 'vfat', '-o', 'uid=%d,gid=%d' % (os.getuid(), os.getgid()), device, str(point))


def unmount():
    root('umount', str(point), check=False)
    root('rmdir', str(point), check=False)


def png():
    raw = b''.join(b'\0' + b'\x33\x66\x99' * 4 for _ in range(4))
    chunk = lambda kind, data: struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data))
    return b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', 4, 4, 8, 2, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(raw)) + chunk(b'IEND', b'')


stop()
image.unlink(missing_ok=True)
subprocess.run(['truncate', '-s', '16M', str(image)], check=True)
subprocess.run(['/usr/sbin/mkfs.vfat', '-n', LABEL, str(image)], check=True, capture_output=True)
lines, phase = [], 'start'
proc = None
try:
    device = root('losetup', '-f', '--show', str(image))
    mount()
    (point / 'DCIM').mkdir()
    (point / 'DCIM/pre.png').write_bytes(png())
    unmount()
    env = dict(ENV, APKLAYER_JOPTS='-Dapklayer.runms=150000 -Dhello.hotplug=1')
    subprocess.run(['bin/apklayer', 'permissions', 'grant', 'hello.files', 'android.permission.READ_MEDIA_IMAGES'], env=env, capture_output=True)
    proc = subprocess.Popen(['bin/apklayer', 'run', 'hello.files'], env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
    end, pending = time.monotonic() + 150, b''
    with selectors.DefaultSelector() as events:
        events.register(proc.stdout, selectors.EVENT_READ)
        while time.monotonic() < end and phase != 'done':
            if not events.select(0.5): continue
            chunk = os.read(proc.stdout.fileno(), 65536)
            if not chunk: break
            parts = (pending + chunk).split(b'\n'); pending = parts.pop()
            for raw in parts:
                line = raw.decode(errors='replace'); lines.append(line)
                if '[hotplug]' in line or 'FATAL' in line: print(line, flush=True)
                if phase == 'start' and '[hotplug] ready' in line: mount(); phase = 'mounted'
                elif phase == 'mounted' and '[hotplug] unmount' in line:
                    unmount(); root('losetup', '-d', device, check=False); device = None; phase = 'removed'
                elif '[hotplug] DONE' in line: phase = 'done'
finally:
    stop()
    if proc: proc.wait()
    unmount()
    if device: root('losetup', '-d', device, check=False)
    image.unlink(missing_ok=True)
passed = {l.split('[hotplug] PASS ')[1].strip() for l in lines if '[hotplug] PASS ' in l}
for step in STEPS: print('%s %s' % ('PASS' if step in passed else 'FAIL', step))
sys.exit(0 if all(s in passed for s in STEPS) else 1)
