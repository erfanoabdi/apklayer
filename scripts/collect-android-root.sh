#!/bin/sh
# collect-android-root: the files of the tree's device build that Android processes read at fixed paths (PLAN P7.1 step 0.3), into the
# instance's Android root on the VM ($native/android-root, served by bridge/native/paths.c). Run on the Mac.
set -e
VM="${APKLAYER_VM:-utm@192.168.64.7}"; PASS="${APKLAYER_VM_PASS:-1234}"
OUT=/home/erfanabdi/los24/out/target/product/generic_arm64
orb -m ubuntu22-x86 tar cf - -C "$OUT" system/etc/cgroups.json system/etc/task_profiles.json system/etc/task_profiles system/usr/hyphen-data \
    | sshpass -p "$PASS" ssh -o StrictHostKeyChecking=no "$VM" 'mkdir -p ~/arm64-native/android-root && tar xf - -C ~/arm64-native/android-root && ls ~/arm64-native/android-root/system/etc | head'
# Original audio policy tables are source XML assets, not generated libraries.
orb -m ubuntu22-x86 tar cf - -C /home/erfanabdi/los24/frameworks/av/services/audiopolicy/config \
    --transform='s,^,vendor/etc/,' audio_policy_volumes.xml default_volume_tables.xml \
    | sshpass -p "$PASS" ssh -o StrictHostKeyChecking=no "$VM" 'tar xf - -C ~/arm64-native/android-root'
