#!/bin/bash
# Quick GPU route check: UiBench starts, GPU buffers reach the shell, a screenshot shows the app. (Window/dialog/resize: tests/vm/gpu-window.sh)
set -euo pipefail
cd "$(dirname "$0")/../.."
VM="${APKLY_VM:-utm@192.168.64.7}"
PASS="${APKLY_VM_PASS:-1234}"
python3 tests/vm/gpu-smoke.py "$VM" "$PASS"
