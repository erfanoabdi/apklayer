#!/bin/bash
# Notifications through the hub: shown on the desktop, content/action/delete PendingIntents on the popup being clicked or dismissed.
set -euo pipefail
cd "$(dirname "$0")/../.."
. tests/vm/config.sh
for app in hello-nt-poster; do tests/apps/build.sh "$app" > /dev/null; done
sshpass -p "$PASS" scp build/hello-nt-poster.apk "$VM:/tmp/"
COPYFILE_DISABLE=1 tar cf - tests/vm/notifications.py bin/apklayer scripts | sshpass -p "$PASS" ssh "$VM" 'tar xf - -C ~/apklayer'
sshpass -p "$PASS" ssh "$VM" 'set -e; cd ~/apklayer; export XDG_RUNTIME_DIR=/run/user/1000 APKLAYER_NATIVE="$HOME/arm64-native"; for a in nt-poster; do bin/apklayer install /tmp/hello-$a.apk 2>&1 | grep -a installed; done'
for phase in ${@:-click}; do
    sshpass -p "$PASS" ssh "$VM" "cd ~/apklayer; ACT_VERBOSE=\${ACT_VERBOSE:-} python3 -u tests/vm/notifications.py $phase"
done
