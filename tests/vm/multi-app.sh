#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/../.."
. tests/vm/config.sh
vm() { sshpass -p "$PASS" ssh -o StrictHostKeyChecking=no "$VM" "$@"; }
COPYFILE_DISABLE=1 tar cf - tests/vm/multi-app.py tests/vm/uinput-keys.c |
    vm 'tar xf - -C ~/apklayer'
python3 tests/vm/multi-app-driver.py "$VM" "$PASS"
