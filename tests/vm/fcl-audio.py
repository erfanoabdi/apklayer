#!/usr/bin/env python3
"""fcl-audio.py <out dir> (the owner's x86 PC; not a group: it needs FCL with Minecraft 26.3 data in ~/apklayer-x86/proc): FCL in instance xf1 of ~/apklayer-x86/proc (its runtime and data, as prun.sh), scripted to Minecraft's title screen; once
OpenAL is up, every PipeWire stream of this instance (a sink input whose client process has APKLAYER_INSTANCE=xf1) is recorded through
parec --monitor-stream for RECORD_S seconds; per stream the RMS and peak in dBFS. Then that instance's stop."""
import json
import math
import os
import select
import subprocess
import sys
import time
from array import array

OUT = sys.argv[1]
RECORD_S = 60
ROOT = os.path.expanduser('~/apklayer-x86/proc')
uid = os.getuid()
env = dict(os.environ, APKLAYER_INSTANCE='xf1', APKLAYER_PREBUILTS=ROOT + '/prebuilts', XDG_DATA_HOME=ROOT + '/data', XDG_STATE_HOME=ROOT + '/state',
           XDG_CACHE_HOME=ROOT + '/cache', XDG_CONFIG_HOME=ROOT + '/config', XDG_RUNTIME_DIR=ROOT + '/repo/.tests/run/xf1',
           WAYLAND_DISPLAY='/run/user/%d/wayland-0' % uid, DBUS_SESSION_BUS_ADDRESS='unix:path=/run/user/%d/bus' % uid,
           PULSE_SERVER='unix:/run/user/%d/pulse/native' % uid, PIPEWIRE_RUNTIME_DIR='/run/user/%d' % uid,
           APKLAYER_SANDBOX='0', APKLAYER_PERMISSIONS='grant', APKLAYER_LOGCAT='1', APKLAYER_TEST_INPUT='1',
           APKLAYER_JOPTS='-Dapklayer.runms=200000 -Dapklayer.script=8000:tap:2142,1312+20000:tap:1383,808+32000:tap:1383,808')
os.makedirs(env['XDG_RUNTIME_DIR'], mode=0o700, exist_ok=True)
os.makedirs(OUT, exist_ok=True)
REPO = ROOT + '/repo'
pulse = dict(os.environ, PULSE_SERVER=env['PULSE_SERVER'])


def ours(pid):
    try: return b'APKLAYER_INSTANCE=xf1\0' in open('/proc/%s/environ' % pid, 'rb').read() + b'\0'
    except OSError: return False


def streams():
    """This instance's sink inputs: (index, client pid, corked)."""
    clients = {str(c['index']): c['properties'].get('application.process.id') for c in
               json.loads(subprocess.run(['pactl', '-f', 'json', 'list', 'clients'], env=pulse, capture_output=True, text=True).stdout)}
    found = []
    for s in json.loads(subprocess.run(['pactl', '-f', 'json', 'list', 'sink-inputs'], env=pulse, capture_output=True, text=True).stdout):
        pid = clients.get(str(s['client']))
        if pid and ours(pid): found.append((s['index'], pid, s.get('corked')))
    return found


def db(x):
    return 20 * math.log10(x) if x > 0 else -999.0


log = open(OUT + '/fcl.out', 'wb')
app = subprocess.Popen([REPO + '/bin/apklayer', 'run', 'com.tungsten.fcl'], cwd=REPO, env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
result = 1
try:
    deadline = time.monotonic() + 240
    openal = None
    pending = b''
    while openal is None or time.monotonic() < openal + 15:  # the title screen comes a few seconds after OpenAL
        assert time.monotonic() < deadline, 'no OpenAL in 240 s'
        r, _, _ = select.select([app.stdout], [], [], 1)
        if not r: continue
        data = os.read(app.stdout.fileno(), 65536)
        assert data, 'FCL ended'
        log.write(data)
        pending += data
        *lines, pending = pending.split(b'\n')
        for line in lines:
            if b'openal:' in line and openal is None:
                openal = time.monotonic()
                print('openal up:', line.decode(errors='replace')[:120], flush=True)
            if b'started ' in line or b'script: ' in line: print(line.decode(errors='replace')[:160], flush=True)
    found = streams()
    print('streams of xf1:', found, flush=True)
    assert found, 'no PipeWire stream of the instance'
    recs = {idx: subprocess.Popen(['parec', '--monitor-stream=%s' % idx, '--raw', '--format=s16le', '--channels=2', '--rate=48000'],
                                  env=pulse, stdout=subprocess.PIPE) for idx, _, _ in found}
    data = {idx: bytearray() for idx in recs}
    end = time.monotonic() + RECORD_S
    while time.monotonic() < end:
        fds = {p.stdout.fileno(): idx for idx, p in recs.items()}
        r, _, _ = select.select(list(fds) + [app.stdout.fileno()], [], [], 1)
        for fd in r:
            if fd == app.stdout.fileno():
                chunk = os.read(fd, 65536)
                log.write(chunk)
            else: data[fds[fd]] += os.read(fd, 65536)
    for p in recs.values(): p.terminate(); p.wait()
    subprocess.run([REPO + '/bin/apklayer', 'screenshot', 'com.tungsten.fcl', OUT + '/title.png'], cwd=REPO, env=env, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    loud = False
    for idx, raw in data.items():
        samples = array('h', bytes(raw[:len(raw) // 2 * 2]))
        n = len(samples)
        rms = math.sqrt(sum(s * s for s in samples) / n) / 32768 if n else 0
        peak = max((abs(s) for s in samples), default=0) / 32768
        secs = [math.sqrt(sum(s * s for s in samples[i:i + 96000]) / max(1, len(samples[i:i + 96000]))) / 32768 for i in range(0, n, 96000)]
        loud_secs = sum(1 for x in secs if db(x) > -50)
        print('stream %s: %.1f s recorded, RMS %.1f dBFS, peak %.1f dBFS, %d of %d seconds above -50 dBFS' % (
            idx, n / 96000, db(rms), db(peak), loud_secs, len(secs)), flush=True)
        open('%s/stream-%s.s16' % (OUT, idx), 'wb').write(raw)
        loud |= loud_secs > 0
    print('PASS: FCL sound reaches the sink, non-silent' if loud else 'FAIL: every stream of the instance silent', flush=True)
    result = 0 if loud else 1
finally:
    subprocess.run([REPO + '/bin/apklayer', 'stop'], cwd=REPO, env=env, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    try: app.wait(20)
    except subprocess.TimeoutExpired: app.kill(); app.wait()
    log.close()
sys.exit(result)
