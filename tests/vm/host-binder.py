#!/usr/bin/env python3
"""The host binder contexts (libbinder frameworks/native 0006, libhwbinder/libhidl system 0001, docs/HALIUM.md) with two instances: a second
instance ("<instance>-dev", binder devices made here) plays the device. AIDL: it registers a service on its own servicemanager; this instance
reaches it through /dev/host_binder ($APKLAYER_HOST_BINDER = the device instance's binder) for a name listed in /data/apklayer/host_hals.conf,
and not for one that is not listed. HIDL: hidltool is the device's hwservicemanager serving atrace@1.0; this instance's IAtraceDevice::
getService reaches it through /dev/host_hwbinder ($APKLAYER_HOST_HWBINDER) and passes strings and vectors both ways."""
import os
import selectors
import shutil
import subprocess
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
import instance  # noqa: E402  (changes into the repo root)

DEV = instance.INSTANCE + '-dev'
NAME = 'apklayer.test.IHostEcho/default'
# the userspace binder (the default, docs/BINDER.md) needs no devices: DEV's daemon serves its contexts (no sudo when the kernel's are there already)
if os.environ.get('APKLAYER_BINDER') == 'kernel' and not all(os.path.exists('/dev/binderfs/%sbinder-%s' % (b, DEV)) for b in ('', 'hw', 'vnd')):
    subprocess.run(['sudo', '-n', 'sh', 'tools/host-setup/binder.sh', DEV], check=True, stdout=subprocess.DEVNULL)
# the instance's /data (cli/layout.cpp): the stack writes this list from the provider choice; the test writes it itself
conf = Path(os.environ.get('XDG_DATA_HOME', Path.home() / '.local/share')) / 'apklayer' / instance.INSTANCE / 'data/apklayer/host_hals.conf'
conf.parent.mkdir(parents=True, exist_ok=True)
conf.write_text('# test\napklayer.test.IHostEcho\nandroid.hardware.atrace\n')
base = dict(instance.ENV)
dev_env = dict(base, APKLAYER_INSTANCE=DEV)
app_env = dict(base, APKLAYER_HOST_BINDER='/dev/binderfs/binder-' + DEV, APKLAYER_HOST_HWBINDER='/dev/binderfs/hwbinder-' + DEV)
procs = []


def start(env, *args):
    p = subprocess.Popen(['bin/apklayer', 'exec', *args], env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
    procs.append(p)
    return p


def line_of(p, want, limit=10):
    """The first output line of p containing want, within limit seconds of silence (select: no polling)."""
    with selectors.DefaultSelector() as sel:
        sel.register(p.stdout, selectors.EVENT_READ)
        buf = b''
        end = time.monotonic() + limit
        while time.monotonic() < end:
            if not sel.select(max(0.1, end - time.monotonic())): break
            chunk = os.read(p.stdout.fileno(), 4096)
            if not chunk: break
            buf += chunk
            for line in buf.decode(errors='replace').splitlines():
                if want in line: return line
    raise AssertionError('no "%s" from %s; got: %s' % (want, p.args, buf.decode(errors='replace')[-2000:]))


def tool(env, *args, lib='libapklayer_servicetool.so'):
    r = subprocess.run(['bin/apklayer', 'exec', lib, *args], env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                       text=True, timeout=20)
    print('%s: exit %d: %s' % (' '.join(args), r.returncode, r.stdout.strip().splitlines()[-1:] if r.stdout.strip() else ''))
    return r


try:
    start(dev_env, 'libapklayer_servicemanager.so', '/dev/binder')
    start(base, 'libapklayer_servicemanager.so', '/dev/binder')
    line_of(start(dev_env, 'libapklayer_servicetool.so', 'add', NAME, '--stay'), 'add %s: 0' % NAME)
    local = tool(base, 'check', NAME)  # no host context: this instance's servicemanager alone has no such name
    assert local.returncode == 1 and 'not found' in local.stdout, local.stdout
    found = tool(app_env, 'check', NAME)
    assert found.returncode == 0 and NAME + ': found' in found.stdout, found.stdout
    dump = tool(app_env, 'dump', NAME)  # a transaction on the host proxy (BBinder's dump answers OK)
    assert dump.returncode == 0, dump.stdout
    other = tool(app_env, 'check', 'apklayer.test.INotListed/default')  # not listed: never asked on the host
    assert other.returncode == 1, other.stdout
    device = start(dev_env, 'libapklayer_hidltool.so', 'device')  # the device's hwservicemanager, on its hwbinder
    line_of(device, 'device: ready')
    hidl = tool(app_env, 'atrace', lib='libapklayer_hidltool.so')
    assert hidl.returncode == 0 and 'atrace: categories apklayer (served by the device instance) ; enableCategories SUCCESS' in hidl.stdout, hidl.stdout
    line_of(device, 'device: enableCategories apklayer')
    # libhidl 0004: the instance's own hwservicemanager beside the host forwarding (the stack starts it where host HALs are chosen, which needs a
    # Halium device; here the test stages what the stack stages: its VINTF entry in the instance's root, the marker, and runs the manager)
    root = Path(os.environ.get('XDG_RUNTIME_DIR', instance.SESSION)) / 'apklayer' / instance.INSTANCE / 'android-root/system/etc/vintf/manifest'
    root.mkdir(parents=True, exist_ok=True)
    for f in (instance.NATIVE / 'android-root/system/etc/vintf/manifest').iterdir():
        if not f.name.startswith('apklayer-'): (root / f.name).write_bytes(f.read_bytes())
    (root / 'apklayer-hwservicemanager.xml').write_bytes(Path('aosp/hals/hwservicemanager/manifest.xml').read_bytes())
    marker = conf.parent / 'hwservicemanager.local'
    marker.write_text('')
    start(base, 'libapklayer_hwservicemanager.so')
    for attempt in range(3):  # the manager takes the context a moment after it starts (no property service here to wait on)
        own = tool(app_env, 'local', lib='libapklayer_hidltool.so')
        if own.returncode == 0: break
    assert 'local: register 1, found 1, listed 1' in own.stdout, own.stdout
    hidl = tool(app_env, 'atrace', lib='libapklayer_hidltool.so')  # the host's names still go to the host with the manager running
    assert hidl.returncode == 0 and 'enableCategories SUCCESS' in hidl.stdout, hidl.stdout
    print('host-binder: OK')
finally:
    conf.unlink()
    (conf.parent / 'hwservicemanager.local').unlink(missing_ok=True)
    shutil.rmtree(Path(os.environ.get('XDG_RUNTIME_DIR', instance.SESSION)) / 'apklayer' / instance.INSTANCE / 'android-root', ignore_errors=True)
    for p in procs:
        try: os.killpg(p.pid, 15)
        except ProcessLookupError: pass
    for p in procs:
        try: p.wait(timeout=5)
        except subprocess.TimeoutExpired: os.killpg(p.pid, 9)
