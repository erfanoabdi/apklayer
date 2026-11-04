#!/usr/bin/env python3
"""The host binder context (libbinder patch 0006, docs/HALIUM.md) with two instances: a second instance ("<instance>-dev", binder devices made
here) plays the device and registers a service on its own servicemanager; this instance reaches it through /dev/host_binder
($APKLAYER_HOST_BINDER = the device instance's binder) for a name listed in /data/apklayer/host_hals.conf, and not for one that is not listed."""
import os
import selectors
import subprocess
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
import instance  # noqa: E402  (changes into the repo root)

DEV = instance.INSTANCE + '-dev'
NAME = 'apklayer.test.IHostEcho/default'
subprocess.run(['sudo', '-n', 'sh', 'tools/host-setup/binder.sh', DEV], check=True, stdout=subprocess.DEVNULL)
# the instance's /data (cli/layout.cpp): the stack writes this list from the provider choice; the test writes it itself
conf = Path(os.environ.get('XDG_DATA_HOME', Path.home() / '.local/share')) / 'apklayer' / instance.INSTANCE / 'data/apklayer/host_hals.conf'
conf.parent.mkdir(parents=True, exist_ok=True)
conf.write_text('# test\napklayer.test.IHostEcho\n')
base = dict(instance.ENV)
dev_env = dict(base, APKLAYER_INSTANCE=DEV)
app_env = dict(base, APKLAYER_HOST_BINDER='/dev/binderfs/binder-' + DEV)
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


def tool(env, *args):
    r = subprocess.run(['bin/apklayer', 'exec', 'libapklayer_servicetool.so', *args], env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
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
    print('host-binder: OK')
finally:
    conf.unlink()
    for p in procs:
        try: os.killpg(p.pid, 15)
        except ProcessLookupError: pass
    for p in procs:
        try: p.wait(timeout=5)
        except subprocess.TimeoutExpired: os.killpg(p.pid, 9)
