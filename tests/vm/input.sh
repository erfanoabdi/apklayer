#!/bin/bash
# Requires hello.input installed from tests/apps/hello-input and the VM's existing IBus/wl-clipboard.
set -euo pipefail
cd "$(dirname "$0")/../.."
. tests/vm/config.sh
COPYFILE_DISABLE=1 tar cf - tests/vm/input.py tests/vm/ibus-engine.py tests/vm/uinput-keys.c |
    sshpass -p "$PASS" ssh "$VM" 'tar xf - -C ~/apklayer'
python3 tests/vm/input-driver.py "$VM" "$PASS"
