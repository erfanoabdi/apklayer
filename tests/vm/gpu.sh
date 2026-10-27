#!/bin/bash
# Foreground GPU route regression with real input, event-driven waits and unconditional cleanup.
set -euo pipefail
cd "$(dirname "$0")/../.."
VM="${APKLY_VM:-parallels@10.211.55.4}"
PASS="${APKLY_VM_PASS:-1234}"
vm() { sshpass -p "$PASS" ssh -o StrictHostKeyChecking=no "$VM" "$@"; }
COPYFILE_DISABLE=1 tar cf - tests/vm/gpu-run.py tests/vm/uinput-keys.c |
    vm 'tar xf - -C ~/apklayer-m1'
capture=0
vm 'cd ~/apklayer-m1; python3 -u tests/vm/gpu-run.py' | while IFS= read -r line; do
    printf '%s\n' "$line"
    if [[ "$line" == CAPTURE* ]] && command -v prlctl >/dev/null; then
        capture=$((capture + 1))
        prlctl capture "${APKLAYER_VM_NAME:-Ubuntu 24.04 (with Rosetta)}" --file "/tmp/apklayer-gpu-$capture.png"
        python3 tests/vm/gpu-check-image.py "/tmp/apklayer-gpu-$capture.png"
    fi
done
