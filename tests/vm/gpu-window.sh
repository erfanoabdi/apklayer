#!/bin/bash
# Window regression on the GPU route: UiBench dialog, real window resize while it is open, Back, clipboard. Slow; the quick check is tests/vm/gpu.sh.
set -euo pipefail
cd "$(dirname "$0")/../.."
VM="${APKLY_VM:-utm@192.168.64.7}"
PASS="${APKLY_VM_PASS:-1234}"
vm() { sshpass -p "$PASS" ssh -o StrictHostKeyChecking=no "$VM" "$@"; }
COPYFILE_DISABLE=1 tar cf - tests/vm/gpu-window-run.py tests/vm/uinput-keys.c |
    vm 'tar xf - -C ~/apklayer-m1'
python3 tests/vm/gpu-window-driver.py "$VM" "$PASS"
