#!/bin/bash
# Android Vulkan swapchain end to end (VK_ANDROID_native_buffer over the host driver): hello-vulkan's Present activity clears and presents
# frames on a SurfaceView; the screenshot must show them. Needs a host Vulkan device that imports dma-bufs and shares sync fds.
set -euo pipefail
cd "$(dirname "$0")/../.."
VM="${APKLY_VM:-utm@192.168.64.7}"; PASS="${APKLY_VM_PASS:-1234}"
tests/apps/build.sh hello-vulkan
sshpass -p "$PASS" scp -q build/hello-vulkan.apk "$VM:/tmp/hello-vulkan.apk"
COPYFILE_DISABLE=1 tar cf - tests/vm/vulkan-present.py bridge/native/apklayer.prop 2>/dev/null | sshpass -p "$PASS" ssh "$VM" 'tar xf - -C ~/apklayer-m1 2>/dev/null'
sshpass -p "$PASS" ssh "$VM" 'cd ~/apklayer-m1; export XDG_RUNTIME_DIR=/run/user/1000 APKLAYER_NATIVE="$HOME/arm64-native"; bin/apklayer install /tmp/hello-vulkan.apk 2>&1 | grep -a installed; bin/apklayer stop >/dev/null 2>&1; pkill -f "[b]in/apklayer hub"; true'
python3 - "$VM" "$PASS" <<'PY'
import os, subprocess, sys
sys.path.insert(0, 'tests/vm')
from vmcapture import capture
from PIL import Image
vm, password = sys.argv[1], sys.argv[2]
p = subprocess.Popen(['sshpass', '-p', password, 'ssh', vm, 'cd ~/apklayer-m1; python3 -u tests/vm/vulkan-present.py'], stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True)
magenta = 0.0
try:
    for line in p.stdout:
        print(line, end='', flush=True)
        if line.startswith('CAPTURE '):
            image = os.environ.get('TMPDIR', '/tmp') + '/apklayer-vulkan-present.png'
            capture(image, vm, password)
            pixels = list(Image.open(image).convert('RGB').getdata())
            magenta = sum(1 for r, g, b in pixels if r > 200 and g < 60 and 100 < b < 160) / len(pixels)
            print('IMAGE %s: %.0f%% magenta' % (image, magenta * 100), flush=True)
            p.stdin.write('captured\n'); p.stdin.flush()
finally:
    p.stdin.close(); p.stdout.close()
    code = p.wait()
assert code == 0, 'VM side failed (%d)' % code
assert magenta > 0.25, 'the presented frames are not on screen (%.0f%% magenta)' % (magenta * 100)
print('PASS Vulkan swapchain frames visible (%.0f%% of the screen)' % (magenta * 100))
PY
