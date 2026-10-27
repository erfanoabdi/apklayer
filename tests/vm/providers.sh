#!/bin/bash
# Content providers of other apps through the instance hub: lazy provider start, CRUD/call/getType, identities, exported and permission rules, provider death.
set -euo pipefail
cd "$(dirname "$0")/../.."
VM="${APKLY_VM:-parallels@10.211.55.4}"
PASS="${APKLY_VM_PASS:-1234}"
for app in hello-cp-provider hello-cp-client hello-cp-denied; do tests/apps/build.sh "$app" > /dev/null; done
sshpass -p "$PASS" scp build/hello-cp-provider.apk build/hello-cp-client.apk build/hello-cp-denied.apk "$VM:/tmp/"
COPYFILE_DISABLE=1 tar cf - tests/vm/providers.py bin/apklayer scripts | sshpass -p "$PASS" ssh "$VM" 'tar xf - -C ~/apklayer-m1'
sshpass -p "$PASS" ssh "$VM" 'set -e; cd ~/apklayer-m1; export XDG_RUNTIME_DIR=/run/user/1000 APKLAYER_NATIVE="$HOME/arm64-native"; for a in provider client denied; do bin/apklayer install /tmp/hello-cp-$a.apk 2>&1 | grep -a installed; done'
for phase in ${@:-basic denied death}; do
    sshpass -p "$PASS" ssh "$VM" "cd ~/apklayer-m1; CP_VERBOSE=\${CP_VERBOSE:-} python3 -u tests/vm/providers.py $phase"
done
