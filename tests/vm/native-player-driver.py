#!/usr/bin/env python3
import os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ''))
from vmcapture import capture
import os
import subprocess
import sys
proc = subprocess.Popen(['sshpass', '-p', sys.argv[2], 'ssh', sys.argv[1],
    'cd ~/apklayer-m1; PLAYER_ASSET=%s python3 -u tests/vm/native-player.py' % os.environ.get('PLAYER_ASSET', 'red-h264.mp4') + ''], stdin=subprocess.PIPE,
    stdout=subprocess.PIPE, text=True)
try:
    for line in proc.stdout:
        print(line, end='', flush=True)
        if line.startswith('CAPTURE '):
            capture('/tmp/apklayer-native-player.png')
            from PIL import Image
            im = Image.open('/tmp/apklayer-native-player.png').convert('RGB')
            asset = os.environ.get('PLAYER_ASSET', 'red-h264.mp4')
            if asset.startswith(('white', 'black')):
                # range check: limited-range video (Y 16..235) must reach the full 0 / 255, not 16 / 235 (washed out)
                from collections import Counter
                top = Counter(im.get_flattened_data()).most_common(3)
                print('most common colors', top, flush=True)
                level = max((c for c in top if c[0][0] == c[0][1] == c[0][2] and c[1] > 50000), key=lambda c: c[1], default=None)
                assert level, f'no uniform video area: {top}'
                value = level[0][0]
                assert (value >= 250) if asset.startswith('white') else (value <= 4), f'{asset}: video shows gray {value}'
                print(f'PASS {asset} shows gray {value}', flush=True)
            else:
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
