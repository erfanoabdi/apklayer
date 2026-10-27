#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/../.."
VM="${APKLY_VM:-utm@192.168.64.7}"
PASS="${APKLY_VM_PASS:-1234}"
COPYFILE_DISABLE=1 tar cf - meson.build bridge/hals/audio tests/hals/audio-test.c tests/vm/audio-host.py |
    sshpass -p "$PASS" ssh "$VM" 'set -e; tar xf - -C ~/apklayer-m1; cd ~/apklayer-m1; ninja -C b bridge/hals/audio/audio-test; python3 -u tests/vm/audio-host.py; python3 -u tests/vm/audio-host.py mono'
