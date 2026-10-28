#!/usr/bin/env python3
import os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ''))
from vmcapture import capture
import subprocess
import sys
proc = subprocess.Popen(['sshpass', '-p', sys.argv[2], 'ssh', sys.argv[1],
    'cd ~/apklayer; python3 -u tests/vm/native-audio.py' + (' audio' if sys.argv[3:] == ['audio'] else '')], stdin=subprocess.PIPE,
    stdout=subprocess.PIPE, text=True)
try:
    for line in proc.stdout:
        print(line, end='', flush=True)
        if line.startswith('CAPTURE '):
            capture('/tmp/apklayer-native-audio.png')
            proc.stdin.write('captured\n')
            proc.stdin.flush()
    sys.exit(proc.wait())
finally:
    proc.stdin.close()
    if proc.poll() is None:
        proc.wait(timeout=3)
