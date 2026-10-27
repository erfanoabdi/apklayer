#!/bin/bash
# Foreground GPU route regression with real input, event-driven waits and unconditional cleanup.
set -euo pipefail
cd "$(dirname "$0")/../.."
VM="${APKLY_VM:-parallels@10.211.55.4}"
PASS="${APKLY_VM_PASS:-1234}"
vm() { sshpass -p "$PASS" ssh -o StrictHostKeyChecking=no "$VM" "$@"; }
COPYFILE_DISABLE=1 tar cf - tests/vm/gpu-run.py tests/vm/uinput-keys.c |
    vm 'tar xf - -C ~/apklayer-m1'
python3 tests/vm/gpu-driver.py "$VM" "$PASS"
