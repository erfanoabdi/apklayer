#!/bin/bash
# PendingIntents across apps: hub-owned senders, fill-in, immutable, one-shot, cancel, creator identity.
set -euo pipefail
cd "$(dirname "$0")/../.."
. tests/vm/config.sh
for app in hello-pi-holder hello-pi-creator hello-act-target hello-svc-target; do tests/apps/build.sh "$app" > /dev/null; done
sshpass -p "$PASS" scp build/hello-pi-holder.apk build/hello-pi-creator.apk build/hello-act-target.apk build/hello-svc-target.apk "$VM:/tmp/"
COPYFILE_DISABLE=1 tar cf - tests/vm/pendingintents.py bin/apklayer scripts | sshpass -p "$PASS" ssh "$VM" 'tar xf - -C ~/apklayer'
sshpass -p "$PASS" ssh "$VM" 'set -e; cd ~/apklayer; export XDG_RUNTIME_DIR=/run/user/1000 APKLAYER_NATIVE="$HOME/arm64-native"; for a in pi-holder pi-creator act-target svc-target; do bin/apklayer install /tmp/hello-$a.apk 2>&1 | grep -a installed; done'
for phase in ${@:-broadcast targets cancel}; do
    sshpass -p "$PASS" ssh "$VM" "cd ~/apklayer; ACT_VERBOSE=\${ACT_VERBOSE:-} python3 -u tests/vm/pendingintents.py $phase"
done
