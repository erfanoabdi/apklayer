#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/../.."
VM="${APKLY_VM:-parallels@10.211.55.4}"; PASS="${APKLY_VM_PASS:-1234}"
sshpass -p "$PASS" scp tests/vm/texture-view.py "$VM:apklayer-m1/tests/vm/texture-view.py"
python3 - "$VM" "$PASS" <<'PY'
import subprocess, sys
from PIL import Image, ImageChops
images = []
p = subprocess.Popen(['sshpass', '-p', sys.argv[2], 'ssh', sys.argv[1], 'cd ~/apklayer-m1; python3 -u tests/vm/texture-view.py'],
    stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True)
try:
    for line in p.stdout:
        print(line, end='', flush=True)
        if line.startswith('CAPTURE '):
            path = '/tmp/apklayer-texture-' + line.split()[-1] + '.png'
            subprocess.run(['prlctl', 'capture', 'Ubuntu 24.04 (with Rosetta)', '--file', path], check=True)
            im = Image.open(path).convert('RGB')
            orange = sum(r > 80 and r > g * 1.2 and g > b * 1.2 for r,g,b in im.getdata())
            assert orange > 60000, ('missing canyon texture', path, orange)
            images.append(im)
            print('PASS canyon texture pixels:', orange, flush=True)
            p.stdin.write('captured\n'); p.stdin.flush()
    assert p.wait() == 0
    assert len(images) == 2
    changed = sum(max(p) > 30 for p in ImageChops.difference(*images).getdata())
    assert changed > 20000, ('TextureView animation did not change', changed)
    print('PASS animated TextureView pixels:', changed)
finally:
    p.stdin.close(); p.stdout.close()
    if p.poll() is None: p.wait(timeout=3)
PY
