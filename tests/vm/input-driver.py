#!/usr/bin/env python3
import os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ''))
from vmcapture import capture
import importlib.util
import json
from pathlib import Path
import subprocess
import sys

sys.dont_write_bytecode = True

path = Path(__file__).with_name('multi-app-driver.py')
spec = importlib.util.spec_from_file_location('geometry', path)
geometry = importlib.util.module_from_spec(spec)
spec.loader.exec_module(geometry)
proc = subprocess.Popen(['sshpass', '-p', sys.argv[2], 'ssh', '-o', 'StrictHostKeyChecking=no',
    sys.argv[1], 'cd ~/apklayer-m1; python3 -u tests/vm/input.py'],
    stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True)
capture = 0
try:
    for line in proc.stdout:
        print(line, end='', flush=True)
        if line.startswith(('LOCATE ', 'CAPTURE ')):
            capture += 1
            image = f'/tmp/apklayer-input-{capture}.png'
            capture(image)
            if line.startswith('LOCATE '):
                proc.stdin.write(json.dumps(geometry.headers(image, 1, content_y=int(line.split()[1]))) + '\n')
                proc.stdin.flush()
    sys.exit(proc.wait())
finally:
    proc.stdin.close()
    if proc.poll() is None:
        proc.wait(timeout=3)
