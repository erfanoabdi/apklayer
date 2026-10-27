#!/usr/bin/env python3
"""Foreground GPU regression on the VM; log events drive real compositor input."""
import os
import ctypes
import time
from pathlib import Path
import re
import selectors
import signal
import subprocess
import sys

ROOT = Path.home() / 'apklayer-m1'
os.chdir(ROOT)
PKG = 'com.android.test.uibench'
ENV = dict(os.environ, XDG_RUNTIME_DIR='/run/user/1000',
           APKLAYER_NATIVE=str(Path.home() / 'arm64-native'), APKLAYER_RUNTIME_DEBUG='1')
LOG = Path('/tmp/gpu-test.out')
steps = ['1000:hover:0,0', '3000:hover:1,1', '5000:hover:0,0', '6000:hover:2,2',
         '8000:hover:0,0', '9000:hover:3,3', '12000:hover:0,0', '14000:hover:4,4',
         '16000:clipcopy:gpu-resize-alive', '18000:hover:0,0']
ENV['APKLAYER_JOPTS'] = ('-Dapklayer.runms=19000 -Dapklayer.tracewindows=true -Dapklayer.debugkeys=true -Dapklayer.runtimetrace=true '
                          '-Dapklayer.script=' + '+'.join(steps))


def run(args, **kwargs):
    return subprocess.run(args, check=True, env=ENV, **kwargs)


def stop():
    subprocess.run(['bin/apklayer', 'stop'], env=ENV, stdout=subprocess.DEVNULL,
                   stderr=subprocess.DEVNULL)
    subprocess.run(['pkill', '-9', '-x', 'tail'], check=False)


def input_steps(*steps):
    print('INPUT ' + ' '.join(steps), flush=True)
    run(['sudo', '-S', '/tmp/uinput-keys', *steps], input=b'1234\n',
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


def dump(name):
    with open('/tmp/gpu-' + name + '.txt', 'wb') as out:
        run(['bash', '-c', '. scripts/native-env.sh; exec "$N/bin/btload" "$L" '
             'libapklayer_servicetool.so --main dump SurfaceFlinger --list'],
            stdout=out, stderr=subprocess.STDOUT)
    return Path('/tmp/gpu-' + name + '.txt').read_text(errors='replace')


stop()
proc = None
seen = []
resized = False
try:
    run(['gcc', '-O1', '-o', '/tmp/uinput-keys', 'tests/vm/uinput-keys.c'])
    proc = subprocess.Popen(['bin/apklayer', 'run', PKG], env=ENV, stdout=subprocess.PIPE,
                            stderr=subprocess.STDOUT, start_new_session=True)
    selector = selectors.DefaultSelector()
    selector.register(proc.stdout, selectors.EVENT_READ)
    # Startup logs belong to the service processes, not the launcher's pipe.
    # Observe their writes too, without polling a readiness file.
    libc = ctypes.CDLL(None, use_errno=True)
    notify = libc.inotify_init1(os.O_NONBLOCK | os.O_CLOEXEC)
    assert notify >= 0
    logs = Path('/tmp/apklayer-stack')
    logs.mkdir(exist_ok=True)
    assert libc.inotify_add_watch(notify, bytes(logs), 0x102) >= 0
    selector.register(notify, selectors.EVENT_READ)
    last_report = time.monotonic()
    deadline = time.monotonic() + 45
    pending = b''
    with LOG.open('wb') as log:
        while True:
            # No readiness polling: wait for pipe data, and diagnose a silent app promptly.
            events = selector.select(3)
            if time.monotonic() > deadline:
                raise RuntimeError('GPU regression exceeded its 45-second budget')
            if not events:
                raise RuntimeError('app produced no output for 3 seconds; see /tmp/gpu-test.out')
            if any(key.fd == notify for key, _ in events):
                activity = os.read(notify, 65536)
                if time.monotonic() - last_report > 1:
                    print('STACK log activity (' + str(len(activity)) + ' event bytes)', flush=True)
                    last_report = time.monotonic()
            if not any(key.fileobj is proc.stdout for key, _ in events):
                continue
            data = os.read(proc.stdout.fileno(), 65536)
            if not data:
                break
            log.write(data)
            pending += data
            lines = pending.split(b'\n')
            pending = lines.pop()
            for raw in lines:
                line = raw.decode(errors='replace')
                if '[apklayer]' not in line:
                    continue
                seen.append(line)
                print(line, flush=True)
                if 'script: hover:1,1' in line:
                    input_steps('abs:260,157', 'click')
                elif 'script: hover:2,2' in line:
                    input_steps('abs:260,213', 'click')
                elif 'script: hover:3,3' in line:
                    before = dump('before-resize')
                    if 'DialogListActivity' not in '\n'.join(seen):
                        raise RuntimeError('real clicks did not open Dialog List')
                    if 'VRI-' + PKG not in before:
                        raise RuntimeError('no app layer in SurfaceFlinger')
                    print('CAPTURE dialog before resize', flush=True)
                    input_steps('abs:260,300', 'click', 'combo:alt+66', 'abs:650,650', 'key:28')
                    resized = True
                elif 'script: hover:4,4' in line:
                    after = dump('after-resize')
                    if 'VRI-' + PKG not in after:
                        raise RuntimeError('app layer disappeared after resize')
                    print('CAPTURE dialog after resize', flush=True)
                    input_steps('key:1')  # real Esc/Back must dismiss the dialog
                elif 'script: hover:0,0' in line and 'clipboard to the system (16 chars)' in '\n'.join(seen):
                    final_layers = dump('after-back')
                    print('LAYERS after resize=' + str(after.count('VRI-')) + ' after Back=' + str(final_layers.count('VRI-')), flush=True)
    if proc.wait() != 0:
        raise RuntimeError('app process failed')
    text = '\n'.join(seen)
    assert resized, 'resize step never reached'
    sizes = re.findall(r'configure (\d+x\d+)', text)
    assert len(set(sizes)) > 1, 'real compositor resize never reached the app'
    final_size = sizes[-1]
    assert final_size != sizes[0], 'resize returned to the original dimensions'
    assert 'clipboard to the system (16 chars)' in text, 'main loop stalled after resize'
    assert final_layers.count('VRI-') < after.count('VRI-'), 'real Back did not remove the dialog layer'
    shell = Path('/tmp/apklayer-stack/shell.log').read_text(errors='replace')
    assert shell.count('dmabuf frame') > 5, 'no GPU frame delivery'
    assert final_size in shell, 'no GPU frame at the final resized dimensions'
    assert Path('/tmp/apklayer-stack/vdisplay-' + PKG + '.log').exists(), 'sink was not launched'
    print('PASS GPU dialog, real resize, Back and main-loop responsiveness', flush=True)
except BaseException:
    services = subprocess.run(['pgrep', '-f', '[b]tload.*libapklayer_(surfaceflinger|composer)'], capture_output=True, text=True)
    for pid in services.stdout.split():
        Path('/tmp/gpu-maps-' + pid + '.txt').write_text(Path('/proc/' + pid + '/maps').read_text())
        with open('/tmp/gpu-native-' + pid + '.txt', 'wb') as out:
            subprocess.run(['sudo', '-S', 'gdb', '-batch', '-p', pid, '-ex', 'set pagination off', '-ex', 'thread apply all bt 8', '-ex', 'info sharedlibrary'], input=b'1234\n', stdout=out, stderr=subprocess.STDOUT)
    if proc is not None and proc.poll() is None:
        pids = subprocess.run(['pgrep', '-f', '[a]pklayer.host.Main'], capture_output=True, text=True)
        for pid in pids.stdout.split():
            with open('/tmp/gpu-threads-' + pid + '.txt', 'wb') as out:
                subprocess.run(['jcmd', pid, 'Thread.print'], stdout=out, stderr=subprocess.STDOUT)
    raise
finally:
    stop()
    if proc is not None:
        if proc.poll() is None:
            os.killpg(proc.pid, signal.SIGKILL)
        proc.wait()
    left = subprocess.run(['pgrep', '-af', '[a]pklayer.host.Main|[b]tload|[a]pklayer-shell'],
                          capture_output=True, text=True)
    if left.returncode == 0:
        print('FAIL leftover processes:\n' + left.stdout, file=sys.stderr)
        sys.exit(1)
