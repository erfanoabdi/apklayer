#!/bin/bash
# The file chooser portal behind ACTION_OPEN_DOCUMENT: the app opens the desktop's dialog, a real Esc key cancels it, the app gets RESULT_CANCELED.
set -euo pipefail
cd "$(dirname "$0")/../.."
. tests/vm/config.sh
tests/apps/build.sh hello-portal > /dev/null
sshpass -p "$PASS" scp -q build/hello-portal.apk tests/vm/uinput-keys.c "$VM:"
sshpass -p "$PASS" ssh "$VM" 'cd ~/apklayer; export XDG_RUNTIME_DIR=/run/user/1000 WAYLAND_DISPLAY=wayland-0
gcc -O1 -o /tmp/uinput-keys ~/uinput-keys.c
b/cli/apklayer stop >/dev/null 2>&1
b/cli/apklayer install ~/hello-portal.apk | grep -a installed
# nothing tells the test when the dialog has appeared and can take a key (GNOME does not give an app that event): once the request is made, Esc goes out once a
# second until the app has its answer
APKLAYER_JOPTS="-Dapklayer.runms=40000" b/cli/apklayer run hello.portal 2>&1 | while IFS= read -r line; do
    case "$line" in
        *"[apklayer] portal request"*) echo "$line"; ( for i in $(seq 1 25); do sleep 1; sudo -n /tmp/uinput-keys key:1; done ) & sender=$! ;;
        *"[portal]"*|*"file chooser portal unavailable"*) echo "$line"; kill ${sender:-0} 2>/dev/null; break ;;
    esac
done > /tmp/portal.out
cat /tmp/portal.out
b/cli/apklayer stop >/dev/null 2>&1
grep -aq "CANCELED" /tmp/portal.out'
