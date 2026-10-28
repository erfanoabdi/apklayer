#!/usr/bin/env python3
"""Holds OTR2's VM run at each capture until the screenshot has been taken and checked for game content (not black, not flat)."""
import os
import subprocess
import sys
from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
from vmcapture import capture

vm, password, image_dir = sys.argv[1], sys.argv[2], sys.argv[3]
# OTR2 asks for an OpenGL ES 3.1 context; a virgl host without compute reports ES 3.0 (`apklayer env` claims 3.2 on such hosts, see STATUS.md)
command = 'cd ~/apklayer; ' + ('MESA_GLES_VERSION_OVERRIDE=%s ' % os.environ['MESA_GLES_VERSION_OVERRIDE'] if os.environ.get('MESA_GLES_VERSION_OVERRIDE') else '') + 'python3 -u tests/vm/otr2-run.py'
proc = subprocess.Popen(['sshpass', '-p', password, 'ssh', vm, command], stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True)
count, best = 0, 0.0
try:
    for line in proc.stdout:
        print(line, end='', flush=True)
        if line.startswith('CAPTURE '):
            count += 1
            image = f'{image_dir}/apklayer-otr2-{count}.png'
            capture(image, vm, password)
            im = Image.open(image).convert('RGB').crop((120, 130, 830, 880))  # inside the app window, below its title bar
            pixels = list(im.getdata())
            lit = sum(1 for p in pixels if max(p) > 40) / len(pixels)
            colors = len({(p[0] >> 4, p[1] >> 4, p[2] >> 4) for p in pixels})
            print(f'IMAGE {image}: {lit:.0%} lit, {colors} colors', flush=True)
            best = max(best, lit if colors > 200 else 0.0)
            proc.stdin.write('captured\n')
            proc.stdin.flush()
finally:
    proc.stdin.close()
    code = proc.wait()
if best < 0.5:
    sys.exit('FAIL OTR2 drew no game content')
print('PASS OTR2 renders game frames (%.0f%% of the window lit)' % (best * 100))
sys.exit(0)
