#!/bin/bash
# Cross-app broadcasts over the instance hub: manifest receivers (package not running), dynamic receivers, permissions, exported rules, ordered, sticky.
set -euo pipefail
cd "$(dirname "$0")/../.."
VM="${APKLY_VM:-utm@192.168.64.7}"
PASS="${APKLY_VM_PASS:-1234}"
for app in hello-bc-receiver hello-bc-sender hello-bc-denied; do tests/apps/build.sh "$app" > /dev/null; done
sshpass -p "$PASS" scp build/hello-bc-receiver.apk build/hello-bc-sender.apk build/hello-bc-denied.apk "$VM:/tmp/"
COPYFILE_DISABLE=1 tar cf - tests/vm/broadcasts.py bin/apklayer scripts | sshpass -p "$PASS" ssh "$VM" 'tar xf - -C ~/apklayer-m1'
sshpass -p "$PASS" ssh "$VM" 'set -e; cd ~/apklayer-m1; export XDG_RUNTIME_DIR=/run/user/1000 APKLAYER_NATIVE="$HOME/arm64-native"; for a in receiver sender denied; do bin/apklayer install /tmp/hello-bc-$a.apk 2>&1 | grep -a installed; done'
for phase in ${@:-manifest basic ordered sticky async once death}; do
    sshpass -p "$PASS" ssh "$VM" "cd ~/apklayer-m1; BC_TRACE=${BC_TRACE:-} BC_VERBOSE=${BC_VERBOSE:-} python3 -u tests/vm/broadcasts.py $phase"
done
