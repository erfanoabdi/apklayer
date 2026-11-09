#!/usr/bin/env python3
"""A removable drive plugged in and out while an app runs (HubVolumes, HubMediaWatch, ShadowHostStorageManager.changed): tests/apps/hello-files'
hotplug scenario (Hotplug.java) against a vfat loop image that this script mounts where the desktop's udisks2 would (/media/<user>/<label>) and
removes again. DCIM/pre.png is put on the image before the app starts (the volume scan at mount must find it). Then what happens while the instance
is stopped (the hub's catch-up at its start): the drive mounted and a picture written on internal storage, seen at the next start; both gone again,
seen gone at the start after. Those two run with a watch budget of 3 (APKLAYER_MEDIA_WATCHES): `apklayer status` must show directories not watched.
Root for losetup and mount: SUDO_ASKPASS set -> `sudo -A`, else `sudo -n`. Steps are the app's `[hotplug] PASS|FAIL <step>` lines.
APKLAYER_SANDBOX=1: the same with hello.files and MediaProvider sandboxed (the drive through apklayerd's broker)."""
import os
import selectors
import subprocess
import sys
import time
import zlib
import struct
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
from instance import ENV, INSTANCE, OUT, stop  # noqa: E402

STEPS = ['mounted broadcast', 'volume callback', 'volume listed', 'mediastore volume', 'mount scan', 'drive file indexed', 'internal file indexed',
         'internal file dropped', 'MEDIA_EJECT broadcast', 'MEDIA_UNMOUNTED broadcast', 'MEDIA_REMOVED broadcast', 'volume gone', 'callback on removal', 'link removed',
         'catchup present volume', 'catchup present drive scanned', 'catchup present internal file', 'catchup status',
         'catchup gone volume', 'catchup gone mediastore volume', 'catchup gone internal file']
SUDO = ['sudo', '-A'] if os.environ.get('SUDO_ASKPASS') else ['sudo', '-n']
LABEL = 'APKHOT' + INSTANCE[-2:].upper()
image, point = OUT / 'hotplug.img', Path('/media') / os.environ.get('USER', 'user') / LABEL
data = Path(os.environ.get('XDG_DATA_HOME', Path.home() / '.local/share')) / 'apklayer' / ('0' if INSTANCE == 'default' else INSTANCE)
picture = data / 'storage/emulated/0/Pictures' / ('catchup-%d.png' % os.getpid())
device = None


def root(*args, check=True):
    done = subprocess.run(SUDO + list(args), capture_output=True, text=True)
    assert done.returncode == 0 or not check, '%s: %s' % (' '.join(args), done.stderr.strip())
    return done.stdout.strip()


def attach():
    global device
    device = root('losetup', '-f', '--show', str(image))


def detach():
    global device
    if device: root('losetup', '-d', device, check=False)
    device = None


def mount():
    root('mkdir', '-p', str(point))
    root('mount', '-t', 'vfat', '-o', 'uid=%d,gid=%d' % (os.getuid(), os.getgid()), device, str(point))


def unmount():
    if subprocess.run(['mountpoint', '-q', str(point)]).returncode == 0:
        done = subprocess.run(SUDO + ['umount', str(point)], capture_output=True, text=True)
        if done.returncode:
            # a MediaProvider that scanned the drive can keep an image mapped (Skia's file mapping, until a GC): as vold does (KillProcessesUsingPath),
            # this instance's processes using it end; another instance's (other trees on this desktop see the drive too) are left, the drive detached lazily
            ours = []
            for pid in root('fuser', '-m', str(point), check=False).split():
                env = root('cat', '/proc/%s/environ' % pid, check=False)
                print('umount: busy, held by %s %s' % (pid, root('cat', '/proc/%s/cmdline' % pid, check=False).replace('\0', ' ')[:100]), flush=True)
                if 'APKLAYER_INSTANCE=%s\n' % INSTANCE in env.replace('\0', '\n') + '\n': ours.append(int(pid))
            for pid in ours: root('kill', '-9', str(pid), check=False)
            end = time.monotonic() + 5
            while any(Path('/proc/%d' % pid).exists() for pid in ours) and time.monotonic() < end: time.sleep(0.1)
            if subprocess.run(SUDO + ['umount', str(point)], capture_output=True).returncode:
                root('umount', '-l', str(point), check=False)
    root('rmdir', str(point), check=False)


def png():
    raw = b''.join(b'\0' + b'\x33\x66\x99' * 4 for _ in range(4))
    chunk = lambda kind, data: struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data))
    return b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', 4, 4, 8, 2, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(raw)) + chunk(b'IEND', b'')


def app(jopts, react, extra_env=None):
    """hello.files with these options until DONE (or 150 s), then the instance stopped; react(line) answers its lines. Returns its lines."""
    env = dict(ENV, APKLAYER_JOPTS='-Dapklayer.runms=150000 ' + jopts, **(extra_env or {}))
    proc = subprocess.Popen(['bin/apklayer', 'run', 'hello.files'], env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
    lines, end, pending = [], time.monotonic() + 150, b''
    try:
        with selectors.DefaultSelector() as events:
            events.register(proc.stdout, selectors.EVENT_READ)
            while time.monotonic() < end and not any('[hotplug] DONE' in l for l in lines):
                if not events.select(0.5): continue
                chunk = os.read(proc.stdout.fileno(), 65536)
                if not chunk: break
                parts = (pending + chunk).split(b'\n'); pending = parts.pop()
                for raw in parts:
                    line = raw.decode(errors='replace'); lines.append(line)
                    if '[hotplug]' in line or 'FATAL' in line: print(line, flush=True)
                    react(line, lines)
    finally:
        stop()
        proc.wait()
    return lines


def hotplug(line, lines):
    if '[hotplug] ready' in line: mount()
    elif '[hotplug] unmount' in line: unmount(); detach()


def status(line, lines):
    if '[hotplug] status' not in line: return
    shown = subprocess.run(['bin/apklayer', 'status'], env=ENV, capture_output=True, text=True).stdout
    watch = [l for l in shown.splitlines() if l.startswith('media watch\t')]
    print('status: %s' % (watch[0] if watch else 'no media watch line'), flush=True)
    unwatched = bool(watch) and int(watch[0].split(', ')[1].split()[0]) > 0
    lines.append('[hotplug] %s catchup status' % ('PASS' if unwatched else 'FAIL'))


stop()
image.unlink(missing_ok=True)
subprocess.run(['truncate', '-s', '16M', str(image)], check=True)
subprocess.run(['/usr/sbin/mkfs.vfat', '-n', LABEL, str(image)], check=True, capture_output=True)
lines = []
try:
    attach()
    mount()
    (point / 'DCIM').mkdir()
    (point / 'DCIM/pre.png').write_bytes(png())
    unmount()
    subprocess.run(['bin/apklayer', 'permissions', 'grant', 'hello.files', 'android.permission.READ_MEDIA_IMAGES'], env=ENV, capture_output=True)
    lines += app('-Dhello.hotplug=1', hotplug)
    # while stopped: the drive plugged in, a picture written on internal storage (the hot plug run may have stopped before its removal)
    unmount()
    detach()
    attach()
    mount()
    picture.parent.mkdir(parents=True, exist_ok=True)
    picture.write_bytes(png())
    budget = {'APKLAYER_MEDIA_WATCHES': '3'}
    jopts = '-Dhello.hotplug=catchup -Dhello.catchup.file=%s ' % picture.name
    lines += app(jopts + '-Dhello.catchup.drive=1', status, budget)
    # while stopped: both gone again
    unmount()
    detach()
    picture.unlink()
    lines += app(jopts + '-Dhello.catchup.drive=0', lambda line, lines: None, budget)
finally:
    stop()
    unmount()
    detach()
    image.unlink(missing_ok=True)
    picture.unlink(missing_ok=True)
passed = {l.split('[hotplug] PASS ')[1].strip() for l in lines if '[hotplug] PASS ' in l}
for step in STEPS: print('%s %s' % ('PASS' if step in passed else 'FAIL', step))
sys.exit(0 if all(s in passed for s in STEPS) else 1)
