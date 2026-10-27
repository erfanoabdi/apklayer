#!/usr/bin/env python3
import subprocess
import sys
proc = subprocess.Popen(['sshpass', '-p', sys.argv[2], 'ssh', sys.argv[1],
    'cd ~/apklayer-m1; python3 -u tests/vm/native-audio.py' + (' audio' if sys.argv[3:] == ['audio'] else '')], stdin=subprocess.PIPE,
    stdout=subprocess.PIPE, text=True)
try:
    for line in proc.stdout:
        print(line, end='', flush=True)
        if line.startswith('CAPTURE '):
            subprocess.run(['prlctl', 'capture', 'Ubuntu 24.04 (with Rosetta)',
                '--file', '/tmp/apklayer-native-audio.png'], check=True)
            proc.stdin.write('captured\n')
            proc.stdin.flush()
    sys.exit(proc.wait())
finally:
    proc.stdin.close()
    if proc.poll() is None:
        proc.wait(timeout=3)
