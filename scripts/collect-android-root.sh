#!/bin/sh
# collect-android-root: the files of the tree's device build that Android processes read at fixed paths (PLAN P7.1 step 0.3), into the
# instance's Android root on the VM ($native/android-root, served by bridge/native/paths.c). Run on the Mac.
set -e
. "$(dirname "$0")/../tests/vm/config.sh"
OUT=/home/erfanabdi/los24/out/target/product/generic_arm64
orb -m ubuntu22-x86 tar cf - -C "$OUT" system/etc/cgroups.json system/etc/task_profiles.json system/etc/task_profiles system/usr/hyphen-data system/etc/permissions system/etc/compatconfig system/etc/sysconfig system/etc/default-permissions \
    | sshpass -p "$PASS" ssh -o StrictHostKeyChecking=no "$VM" 'mkdir -p ~/arm64-native/android-root && tar xf - -C ~/arm64-native/android-root && ls ~/arm64-native/android-root/system/etc | head'
# Original audio policy tables are source XML assets, not generated libraries.
orb -m ubuntu22-x86 tar cf - -C /home/erfanabdi/los24/frameworks/av/services/audiopolicy/config \
    --transform='s,^,vendor/etc/,' audio_policy_volumes.xml default_volume_tables.xml \
    | sshpass -p "$PASS" ssh -o StrictHostKeyChecking=no "$VM" 'tar xf - -C ~/arm64-native/android-root'
# apklayer's own half of the device configuration (features the host layers provide).
COPYFILE_DISABLE=1 tar cf - -C "$(dirname "$0")/../bridge/native/android-root" vendor/etc/permissions \
    | sshpass -p "$PASS" ssh -o StrictHostKeyChecking=no "$VM" 'tar xf - -C ~/arm64-native/android-root'
# Protection levels of the framework's permissions (normal ones are granted at install, signature ones to platform-signed system apps).
orb -m ubuntu22-x86 cat /home/erfanabdi/los24/frameworks/base/core/res/AndroidManifest.xml | python3 "$(dirname "$0")/gen-permission-levels.py" \
    | sshpass -p "$PASS" ssh -o StrictHostKeyChecking=no "$VM" 'cat > ~/arm64-native/android-root/system/etc/apklayer-permission-levels.tsv'
