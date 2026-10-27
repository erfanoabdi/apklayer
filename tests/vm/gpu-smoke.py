#!/usr/bin/env python3
"""GPU smoke test: UiBench starts on the GPU route, buffers reach the shell, and a screenshot shows the app (differs from the bare desktop).
Window, dialog and resize behaviour: tests/vm/gpu-window.sh."""
import os
import subprocess
import sys
from PIL import Image, ImageChops

vm, password = sys.argv[1], sys.argv[2]
sys.path.insert(0, os.path.dirname(__file__))
from vmcapture import capture

ssh = ['sshpass', '-p', password, 'ssh', '-o', 'StrictHostKeyChecking=no', vm]
ENV = ('cd ~/apklayer-m1; export XDG_RUNTIME_DIR=/run/user/1000 WAYLAND_DISPLAY=wayland-0 APKLAYER_NATIVE=$HOME/arm64-native '
       'APKLAYER_RUNTIME_DEBUG=1 APKLAYER_JOPTS="-Dapklayer.runms=12000 $APKLAYER_EXTRA_JOPTS"; ')
CLEANUP = 'cd ~/apklayer-m1; bin/apklayer stop >/dev/null 2>&1; pkill -f "[b]in/apklayer hub"; pkill -9 -x tail; true'
tmp = os.environ.get('TMPDIR', '/tmp')
proc = None
try:
    subprocess.run(ssh + [CLEANUP], check=True)
    capture(tmp + '/apklayer-gpu-before.png', vm, password)
    proc = subprocess.Popen(ssh + [ENV + 'exec bin/apklayer run com.android.test.uibench'], stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    imported = 0
    shot = None
    for line in proc.stdout:
        if 'apklayer.egl: imported buffer' in line:
            imported += 1
            if imported == 3 and shot is None:  # the app is presenting GPU buffers: take the picture now
                shot = tmp + '/apklayer-gpu-during.png'
                capture(shot, vm, password)
        if 'Fatal signal' in line or 'SIGSEGV' in line or 'fatal error' in line:
            print(line.rstrip())
    code = proc.wait()
finally:
    subprocess.run(ssh + [CLEANUP])
assert code == 0, 'UiBench JVM exited with %d' % code
assert shot, 'no GPU buffer reached the compositor'
before, during = Image.open(tmp + '/apklayer-gpu-before.png').convert('RGB'), Image.open(shot).convert('RGB')
changed = sum(1 for p in ImageChops.difference(before, during).convert('L').getdata() if p > 40) / (before.width * before.height)
assert changed > 0.15, 'the screenshot barely differs from the bare desktop (%.0f%% changed)' % (changed * 100)
print('PASS GPU smoke: UiBench drew, %.0f%% of the screen changed (%s)' % (changed * 100, shot))
