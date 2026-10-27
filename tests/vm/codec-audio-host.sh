#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/../.."
VM="${APKLY_VM:-utm@192.168.64.7}"
PASS="${APKLY_VM_PASS:-1234}"
COPYFILE_DISABLE=1 tar cf - bridge/hals/media tests/hals/codec-audio-test.c |
    sshpass -p "$PASS" ssh "$VM" 'set -e; tar xf - -C ~/apklayer-m1; cd ~/apklayer-m1; ninja -C b bridge/hals/media/codec-audio-test; b/bridge/hals/media/codec-audio-test'
