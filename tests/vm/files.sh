#!/bin/bash
# Usage: files.sh [authority document]. The storage provider's roots and the home directory listing, as DocumentsUI queries them (the probe app is a privileged test package on the VM).
set -euo pipefail
cd "$(dirname "$0")/../.."
. tests/vm/config.sh
tests/apps/build.sh hello-files > /dev/null
sshpass -p "$PASS" scp -q build/hello-files.apk "$VM:hello-files.apk"
sshpass -p "$PASS" ssh "$VM" "URI='${3:+-Dhello.uri=$3}' READ='${READ:-}' A='${1:-com.android.externalstorage.documents}' D='${2:-linux-home:}'"'; cd ~/apklayer; export XDG_RUNTIME_DIR=/run/user/1000 WAYLAND_DISPLAY=wayland-0
f=~/arm64-native/android-root/system/etc/permissions/apklayer-test-hello-files.xml
[ -e $f ] || printf "<permissions><privapp-permissions package=\"hello.files\"><permission name=\"android.permission.MANAGE_DOCUMENTS\"/></privapp-permissions></permissions>\n" > $f
b/cli/apklayer stop >/dev/null 2>&1
b/cli/apklayer install ~/hello-files.apk | grep -a installed
APKLAYER_JOPTS="-Dapklayer.runms=60000 -Dhello.authority=$A -Dhello.doc=$D $URI ${READ:+-Dhello.read=$READ}" b/cli/apklayer run hello.files > /tmp/files.out 2>&1 || true
grep -a "\[files\]" /tmp/files.out | grep -av "\] entry" -A12 | head -40
grep -a -h "not started" ~/.local/state/apklayer/default/component-*.log | cut -c1-300
grep -a -h -B1 -A6 "Exception" ~/.local/state/apklayer/default/component-*.log | head -24
b/cli/apklayer stop >/dev/null 2>&1
grep -aq "\[files\] DONE" /tmp/files.out'
