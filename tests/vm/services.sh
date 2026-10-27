#!/bin/bash
# Started services of other apps through the instance hub: start/stop of a service in a not-running app, stopSelf, sticky restart after a kill, exported rules.
set -euo pipefail
cd "$(dirname "$0")/../.."
VM="${APKLY_VM:-parallels@10.211.55.4}"
PASS="${APKLY_VM_PASS:-1234}"
for app in hello-svc-target hello-svc-caller; do tests/apps/build.sh "$app" > /dev/null; done
sshpass -p "$PASS" scp build/hello-svc-target.apk build/hello-svc-caller.apk "$VM:/tmp/"
COPYFILE_DISABLE=1 tar cf - tests/vm/services.py bin/apklayer scripts | sshpass -p "$PASS" ssh "$VM" 'tar xf - -C ~/apklayer-m1'
sshpass -p "$PASS" ssh "$VM" 'set -e; cd ~/apklayer-m1; export XDG_RUNTIME_DIR=/run/user/1000 APKLAYER_NATIVE="$HOME/arm64-native"; for a in target caller; do bin/apklayer install /tmp/hello-svc-$a.apk 2>&1 | grep -a installed; done'
for phase in ${@:-start selfstop sticky private}; do
    sshpass -p "$PASS" ssh "$VM" "cd ~/apklayer-m1; ACT_VERBOSE=\${ACT_VERBOSE:-} python3 -u tests/vm/services.py $phase"
done
