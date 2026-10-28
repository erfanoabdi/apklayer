#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/../.."
VM="${APKLY_VM:-utm@192.168.64.7}"; PASS="${APKLY_VM_PASS:-1234}"
tests/apps/build.sh hello-vulkan
sshpass -p "$PASS" scp build/hello-vulkan.apk "$VM:/tmp/hello-vulkan.apk"
COPYFILE_DISABLE=1 tar cf - tests/vm/vulkan.py tests/vm/uinput-keys.c bridge/native/apklayer.prop |
    sshpass -p "$PASS" ssh "$VM" 'tar xf - -C ~/apklayer-m1'
sshpass -p "$PASS" ssh "$VM" 'cd ~/apklayer-m1; export XDG_RUNTIME_DIR=/run/user/1000 APKLAYER_NATIVE="$HOME/arm64-native"; trap '\''bin/apklayer stop >/dev/null 2>&1; pkill -9 -x tail || true'\'' EXIT; bin/apklayer install /tmp/hello-vulkan.apk > /tmp/apklayer-vulkan-install.log 2>&1; grep -a installed /tmp/apklayer-vulkan-install.log'
python3 - "$VM" "$PASS" <<'PY'
import os, subprocess, sys
sys.path.insert(0, 'tests/vm')
from vmcapture import capture
p = subprocess.Popen(['sshpass', '-p', sys.argv[2], 'ssh', sys.argv[1], 'cd ~/apklayer-m1; python3 -u tests/vm/vulkan.py'],
    stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True)
try:
    for line in p.stdout:
        print(line, end='', flush=True)
        if line.startswith('CAPTURE '):
            capture('/tmp/apklayer-vulkan.png', sys.argv[1], sys.argv[2])
            p.stdin.write('captured\n'); p.stdin.flush()
    assert p.wait() == 0
finally:
    p.stdin.close(); p.stdout.close()
    if p.poll() is None: p.wait(timeout=3)
PY
