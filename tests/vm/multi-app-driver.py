#!/usr/bin/env python3
"""Locate the two real Wayland decorations in a capture, then let uinput focus them."""
import os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ''))
from vmcapture import capture
import json
import subprocess
import sys
from PIL import Image


def headers(filename, count=2, maximize=False, content_y=None):
    im = Image.open(filename).convert('RGB')
    px = im.load()
    found = []
    for y in range(32, im.height):
        start = None
        for x in range(im.width + 1):
            match = x < im.width and px[x, y] in ((43, 43, 43), (58, 58, 58))
            if match and start is None:
                start = x
            elif not match and start is not None:
                if x - start >= 240 and not any(abs(y - r[1]) < 32 and
                        min(x, r[2]) - max(start, r[0]) >= 240 for r in found):
                    found.append((start, y, x))
                start = None
    assert len(found) == count, ('unexpected app decorations', found)
    # Focus the lower window first, then the exposed upper title bar. This
    # avoids relying on Alt-Tab state or fixed desktop window coordinates.
    found.sort(key=lambda r: r[1], reverse=True)
    return [[round((right - 60 if maximize else (left + right) / 2) * 1000 / im.width),
             round((top + 16 if content_y is None else top + 32 + content_y) * 1000 / im.height)] for left, top, right in found]


if __name__ == '__main__':
    proc = subprocess.Popen(['sshpass', '-p', sys.argv[2], 'ssh', '-o', 'StrictHostKeyChecking=no',
                             sys.argv[1], 'cd ~/apklayer; python3 -u tests/vm/multi-app.py'],
                            stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True)
    capture = 0
    try:
        for line in proc.stdout:
            print(line, end='', flush=True)
            if line.startswith(('LOCATE ', 'CAPTURE ', 'RESIZE ')):
                capture += 1
                path = f'/tmp/apklayer-multi-app-{capture}.png'
                capture(path)
                if line.startswith(('LOCATE ', 'RESIZE ')):
                    points = headers(path, 1, True) if line.startswith('RESIZE ') else headers(path)
                    proc.stdin.write(json.dumps(points) + '\n')
                    proc.stdin.flush()
        sys.exit(proc.wait())
    finally:
        proc.stdin.close()
        if proc.poll() is None:
            proc.wait(timeout=3)
