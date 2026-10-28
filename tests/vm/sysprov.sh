#!/bin/bash
# The tree's own providers (SettingsProvider, ContactsProvider, UserDictionaryProvider) installed as apps and reached from another app through the hub.
# Needs scripts/install-system-apps.sh first. Usage: sysprov.sh [settings contacts dictionary]
set -euo pipefail
cd "$(dirname "$0")/../.."
. tests/vm/config.sh
tests/apps/build.sh hello-sysprov > /dev/null
sshpass -p "$PASS" scp -q build/hello-sysprov.apk "$VM:hello-sysprov.apk"
sshpass -p "$PASS" ssh "$VM" 'cd ~/apklayer; export XDG_RUNTIME_DIR=/run/user/1000; b/cli/apklayer stop >/dev/null 2>&1; b/cli/apklayer install ~/hello-sysprov.apk | grep -a installed'
status=0
for what in ${@:-settings contacts dictionary}; do
    sshpass -p "$PASS" ssh "$VM" "cd ~/apklayer; export XDG_RUNTIME_DIR=/run/user/1000 WAYLAND_DISPLAY=wayland-0 APKLAYER_JOPTS='-Dhello.sys=$what -Dapklayer.runms=40000'
        b/cli/apklayer run hello.sysprov > /tmp/sysprov-$what.out 2>&1 || true
        grep -a '\[sys\]' /tmp/sysprov-$what.out || { grep -a 'Exception\|Error' /tmp/sysprov-$what.out | head -4; }
        b/cli/apklayer stop >/dev/null 2>&1" || status=1
done
exit $status
