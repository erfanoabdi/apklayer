#!/bin/bash
# Opening a file as DocumentsUI does: a privileged sender starts ACTION_VIEW with a document uri and FLAG_GRANT_READ_URI_PERMISSION; the viewer, which holds no permission, reads it.
set -euo pipefail
cd "$(dirname "$0")/.."/..
. tests/vm/config.sh
tests/apps/build.sh hello-reader > /dev/null
tests/apps/build.sh hello-files > /dev/null
sshpass -p "$PASS" scp -q build/hello-reader.apk build/hello-files.apk "$VM:"
sshpass -p "$PASS" ssh "$VM" 'cd ~/apklayer; export XDG_RUNTIME_DIR=/run/user/1000 WAYLAND_DISPLAY=wayland-0
f=~/arm64-native/android-root/system/etc/permissions/apklayer-test-hello-files.xml
[ -e $f ] || printf "<permissions><privapp-permissions package=\"hello.files\"><permission name=\"android.permission.MANAGE_DOCUMENTS\"/></privapp-permissions></permissions>\n" > $f
b/cli/apklayer stop >/dev/null 2>&1
b/cli/apklayer install ~/hello-reader.apk | grep -a installed; b/cli/apklayer install ~/hello-files.apk | grep -a installed
APKLAYER_JOPTS="-Dapklayer.runms=40000 -Dhello.view=linux-home:apklayer/CLAUDE.md" b/cli/apklayer run hello.files > /tmp/view.out 2>&1 || true
grep -a "\[files\] DONE\|may not grant" /tmp/view.out
grep -a -h "\[reader\]" /tmp/view.out ~/.local/state/apklayer/default/app-hello.reader.log
b/cli/apklayer stop >/dev/null 2>&1
grep -a -h -q "\[reader\] read:" /tmp/view.out ~/.local/state/apklayer/default/app-hello.reader.log'
