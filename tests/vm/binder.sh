#!/bin/bash
# Two app JVMs of one instance over the real kernel binder: a published binder, calls both ways, caller identities, death notification.
set -euo pipefail
cd "$(dirname "$0")/../.."
VM="${APKLY_VM:-parallels@10.211.55.4}"
PASS="${APKLY_VM_PASS:-1234}"
tests/apps/build.sh hello-binder-server
tests/apps/build.sh hello-binder-client
sshpass -p "$PASS" scp build/hello-binder-server.apk build/hello-binder-client.apk "$VM:/tmp/"
COPYFILE_DISABLE=1 tar cf - tests/vm/binder.py bin/apklayer scripts runtime bridge/native/runtime_shim.c |
    sshpass -p "$PASS" ssh "$VM" 'tar xf - -C ~/apklayer-m1'
sshpass -p "$PASS" ssh "$VM" 'set -e; cd ~/apklayer-m1; export XDG_RUNTIME_DIR=/run/user/1000 APKLAYER_NATIVE="$HOME/arm64-native"; trap '\''bin/apklayer stop >/dev/null 2>&1; pkill -9 -x tail || true'\'' EXIT; bin/apklayer install /tmp/hello-binder-server.apk > /tmp/apklayer-binder-install.log 2>&1; bin/apklayer install /tmp/hello-binder-client.apk >> /tmp/apklayer-binder-install.log 2>&1; grep -a installed /tmp/apklayer-binder-install.log'
sshpass -p "$PASS" ssh "$VM" 'cd ~/apklayer-m1; python3 tests/vm/binder.py'
