#!/bin/sh
# collect-android-root: the files of the tree's device build that Android processes read at fixed paths (PLAN P7.1 step 0.3), into the
# instance's Android root on the VM ($native/android-root, served by bridge/native/paths.c). Run on the Mac.
# ANDROID_PRODUCT (default generic_arm64) picks the product's outputs; STAGE_DEST=<dir> writes the root into a local directory instead of the VM
# (scripts/stage-x86_64.sh).
set -e
here="$(dirname "$0")"
ORB="${APKLAYER_ORB-orb -m ubuntu22-x86}"  # "env" when this runs inside the OrbStack machine
OUT=/home/erfanabdi/los24/out/target/product/${ANDROID_PRODUCT:-generic_arm64}
if [ -n "$STAGE_DEST" ]; then
    sink() { mkdir -p "$STAGE_DEST" && tar xf - -C "$STAGE_DEST"; }
else
    . "$here/../tests/vm/config.sh"
    sink() { sshpass -p "$PASS" ssh -o StrictHostKeyChecking=no "$VM" 'mkdir -p ~/arm64-native/android-root && tar xf - -C ~/arm64-native/android-root'; }
fi
$ORB tar cf - -C "$OUT" system/etc/cgroups.json system/etc/task_profiles.json system/etc/task_profiles system/usr/hyphen-data system/etc/permissions system/etc/compatconfig system/etc/sysconfig system/etc/default-permissions | sink
# Original audio policy tables are source XML assets, not generated libraries.
$ORB tar cf - -C /home/erfanabdi/los24/frameworks/av/services/audiopolicy/config \
    --transform='s,^,vendor/etc/,' audio_policy_volumes.xml default_volume_tables.xml | sink
# apklayer's own half of the device configuration (features the host layers provide).
COPYFILE_DISABLE=1 tar cf - -C "$here/../bridge/native/android-root" vendor/etc/permissions | sink
# Protection levels of the framework's permissions (normal ones are granted at install, signature ones to platform-signed system apps).
$ORB cat /home/erfanabdi/los24/frameworks/base/core/res/AndroidManifest.xml | python3 "$here/gen-permission-levels.py" > /tmp/apklayer-permission-levels.tsv
if [ -n "$STAGE_DEST" ]; then mkdir -p "$STAGE_DEST/system/etc" && cp /tmp/apklayer-permission-levels.tsv "$STAGE_DEST/system/etc/apklayer-permission-levels.tsv"
else sshpass -p "$PASS" ssh -o StrictHostKeyChecking=no "$VM" 'cat > ~/arm64-native/android-root/system/etc/apklayer-permission-levels.tsv' < /tmp/apklayer-permission-levels.tsv; fi
rm -f /tmp/apklayer-permission-levels.tsv
