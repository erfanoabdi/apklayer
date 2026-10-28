#!/usr/bin/env python3
import os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ''))
from vmcapture import capture
import subprocess
import sys
proc = subprocess.Popen(['sshpass', '-p', sys.argv[2], 'ssh', sys.argv[1],
    'cd ~/apklayer; python3 -u tests/vm/native-surface.py'], stdin=subprocess.PIPE,
    stdout=subprocess.PIPE, text=True)
try:
    for line in proc.stdout:
        print(line, end='', flush=True)
        if line.startswith('CAPTURE '):
            capture('/tmp/apklayer-native-surface.png')
            from PIL import Image
            im = Image.open('/tmp/apklayer-native-surface.png').convert('RGB')
            red = sum(r > 180 and g < 70 and b < 70 for r, g, b in im.get_flattened_data())
            assert red > 50000, f'no decoded red SurfaceView: {red} pixels'
            print(f'PASS decoded red SurfaceView: {red} pixels', flush=True)
            proc.stdin.write('captured\n')
            proc.stdin.flush()
    sys.exit(proc.wait())
finally:
    proc.stdin.close()
    if proc.poll() is None:
        proc.wait(timeout=3)
