#!/usr/bin/env python3
"""Hold VM input until each screenshot and its pixel checks have finished."""
import os
import subprocess
import sys

proc = subprocess.Popen(['sshpass', '-p', sys.argv[2], 'ssh', sys.argv[1],
    'cd ~/apklayer-m1; python3 -u tests/vm/gpu-run.py'], stdin=subprocess.PIPE,
    stdout=subprocess.PIPE, text=True)
count = 0
try:
    for line in proc.stdout:
        print(line, end='', flush=True)
        if line.startswith('CAPTURE '):
            count += 1
            image = f'/tmp/apklayer-gpu-{count}.png'
            subprocess.run(['prlctl', 'capture', os.environ.get('APKLAYER_VM_NAME',
                'Ubuntu 24.04 (with Rosetta)'), '--file', image], check=True)
            subprocess.run([sys.executable, 'tests/vm/gpu-check-image.py', image], check=True)
            proc.stdin.write('captured\n')
            proc.stdin.flush()
    sys.exit(proc.wait())
finally:
    proc.stdin.close()
    if proc.poll() is None:
        proc.wait(timeout=3)
