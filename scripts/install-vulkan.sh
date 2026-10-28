#!/bin/bash
# Install the incremental Orb Vulkan artifacts on the arm64 test VM.
# The Android loader keeps its original SONAME; cfg.d selects the renamed copy.
set -euo pipefail
cd "$(dirname "$0")/.."
VM="${APKLY_VM:-utm@192.168.64.7}"; PASS="${APKLY_VM_PASS:-1234}"
stage="${1:-$HOME/OrbStack/ubuntu22-x86/home/erfanabdi/apklayer-vulkan-stage}"
for name in libvulkan.so vulkan.apklayer.so libapklayer_vktest.so; do
    target="$name"; [ "$name" != libvulkan.so ] || target=libapklayer_vulkan.so
    sshpass -p "$PASS" scp "$stage/$name" "$VM:android-arm64/lib64/$target"
    actual=$(sshpass -p "$PASS" ssh "$VM" "sha256sum android-arm64/lib64/$target" | cut -d' ' -f1)
    expected=$(shasum -a 256 "$stage/$name" | cut -d' ' -f1)
    [ "$actual" = "$expected" ]
    echo "Verified $target $actual"
done
COPYFILE_DISABLE=1 tar cf - bridge/native/apklayer.prop |
    sshpass -p "$PASS" ssh "$VM" 'tar xf - -C ~/apklayer-m1'
sshpass -p "$PASS" ssh "$VM" 'cfg="$HOME/arm64-native/share/bionic_translation/cfg.d/bionic_translation.cfg"; sed -i "/^libvulkan.so[[:space:]]/d" "$cfg"; echo "libvulkan.so libapklayer_vulkan.so" >> "$cfg"'
