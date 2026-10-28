#!/bin/sh
# install-system-apps: the tree's own providers and system apps as ordinary apklayer apps on the test VM, so other apps reach them through the hub:
# SettingsProvider, ContactsProvider, CalendarProvider, DownloadProvider, UserDictionaryProvider, ExternalStorageProvider, TelephonyProvider, DocumentsUI.
# Run on the Mac (reads the LineageOS tree through OrbStack, installs on the VM named in tests/vm/local.conf).
set -e
. "$(dirname "$0")/../tests/vm/config.sh"
OUT=/home/erfanabdi/los24/out/target/product/generic_arm64/system/priv-app
for app in SettingsProvider ContactsProvider CalendarProvider DownloadProvider UserDictionaryProvider ExternalStorageProvider TelephonyProvider DocumentsUI; do
    orb -m ubuntu22-x86 cat "$OUT/$app/$app.apk" > "/tmp/sys-$app.apk"
    sshpass -p "$PASS" scp -q "/tmp/sys-$app.apk" "$VM:sys-$app.apk"
    rm -f "/tmp/sys-$app.apk"
    sshpass -p "$PASS" ssh "$VM" "cd ~/apklayer; export XDG_RUNTIME_DIR=/run/user/1000; b/cli/apklayer install ~/sys-$app.apk 2>&1 | grep -a 'installed\|rror\|xception' | head -3"
done
