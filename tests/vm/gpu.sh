#!/bin/bash
# Quick GPU route check: UiBench starts, GPU buffers reach the shell, a screenshot shows the app. (Window/dialog/resize: tests/vm/gpu-window.sh)
set -euo pipefail
cd "$(dirname "$0")/../.."
. tests/vm/config.sh
python3 tests/vm/gpu-smoke.py "$VM" "$PASS"
