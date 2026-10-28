#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/../.."
. tests/vm/config.sh
MODE="${1:-bound}"
[[ "$MODE" = bound || "$MODE" = auto ]] || exit 2
tests/apps/build.sh hello-hub-server
tests/apps/build.sh hello-hub-client
sshpass -p "$PASS" scp build/hello-hub-server.apk build/hello-hub-client.apk "$VM:/tmp/"
COPYFILE_DISABLE=1 tar cf - tests/vm/binder.py bin/apklayer |
    sshpass -p "$PASS" ssh "$VM" 'tar xf - -C ~/apklayer'
sshpass -p "$PASS" ssh "$VM" 'set -e; cd ~/apklayer; export XDG_RUNTIME_DIR=/run/user/1000 APKLAYER_NATIVE="$HOME/arm64-native"; trap '\''bin/apklayer stop >/dev/null 2>&1; pkill -9 -x tail || true'\'' EXIT; bin/apklayer install /tmp/hello-hub-server.apk > /tmp/apklayer-hub-install.log 2>&1; bin/apklayer install /tmp/hello-hub-client.apk >> /tmp/apklayer-hub-install.log 2>&1; grep -a installed /tmp/apklayer-hub-install.log'
sshpass -p "$PASS" ssh "$VM" "cd ~/apklayer; python3 -u tests/vm/binder.py $MODE"
