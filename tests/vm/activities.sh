#!/bin/bash
# Activities of other apps through the instance hub: implicit/explicit start of a not-running app, results with full Parcel payloads, onNewIntent into a running app, exported rules.
set -euo pipefail
cd "$(dirname "$0")/../.."
VM="${APKLY_VM:-parallels@10.211.55.4}"
PASS="${APKLY_VM_PASS:-1234}"
for app in hello-act-target hello-act-caller; do tests/apps/build.sh "$app" > /dev/null; done
sshpass -p "$PASS" scp build/hello-act-target.apk build/hello-act-caller.apk "$VM:/tmp/"
COPYFILE_DISABLE=1 tar cf - tests/vm/activities.py bin/apklayer scripts | sshpass -p "$PASS" ssh "$VM" 'tar xf - -C ~/apklayer-m1'
sshpass -p "$PASS" ssh "$VM" 'set -e; cd ~/apklayer-m1; export XDG_RUNTIME_DIR=/run/user/1000 APKLAYER_NATIVE="$HOME/arm64-native"; for a in target caller; do bin/apklayer install /tmp/hello-act-$a.apk 2>&1 | grep -a installed; done'
for phase in ${@:-result explicit again private}; do
    sshpass -p "$PASS" ssh "$VM" "cd ~/apklayer-m1; ACT_VERBOSE=\${ACT_VERBOSE:-} python3 -u tests/vm/activities.py $phase"
done
