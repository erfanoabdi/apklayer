#!/usr/bin/env python3
"""Hold VM input until each screenshot and its pixel checks have finished."""
import os
import subprocess
import sys

sys.path.insert(0, os.path.dirname(__file__))
from vmcapture import capture

proc = subprocess.Popen(['sshpass', '-p', sys.argv[2], 'ssh', sys.argv[1],
    'cd ~/apklayer-m1; python3 -u tests/vm/gpu-window-run.py'], stdin=subprocess.PIPE,
    stdout=subprocess.PIPE, text=True)
count = 0
try:
    for line in proc.stdout:
        print(line, end='', flush=True)
        if line.startswith('CAPTURE '):
            count += 1
            image = f'/tmp/apklayer-gpu-{count}.png'
            capture(image, sys.argv[1], sys.argv[2])
            subprocess.run([sys.executable, 'tests/vm/gpu-check-image.py', image], check=True)
            proc.stdin.write('captured\n')
            proc.stdin.flush()
    sys.exit(proc.wait())
finally:
    proc.stdin.close()
    if proc.poll() is None:
        proc.wait(timeout=3)
