#!/bin/bash
# GPU route check on the VM (device-library flavor, PLAN P7.1): the app is started through the launcher alone, its window must be a
# real SurfaceFlinger layer, frames must reach the shell as dmabufs, and a real click (uinput) must be handled by the app.
# Usage: tests/vm/gpu.sh   (run on the Mac; needs the VM, tests/vm/uinput-keys.c, an installed com.android.test.uibench)
set -uo pipefail
VM="${APKLY_VM:-parallels@10.211.55.4}"; PASS="${APKLY_VM_PASS:-1234}"
vm() { sshpass -p "$PASS" ssh -o StrictHostKeyChecking=no "$VM" "$@"; }
PKG=com.android.test.uibench
vm "cd ~/apklayer-m1; XDG_RUNTIME_DIR=/run/user/1000 bin/apklayer stop" >/dev/null 2>&1
vm "cd ~/apklayer-m1; export XDG_RUNTIME_DIR=/run/user/1000 APKLAYER_NATIVE=\$HOME/arm64-native APKLAYER_RUNTIME_DEBUG=1; (bin/apklayer run $PKG >/tmp/gpu-test.out 2>&1 &)"
vm "gcc -O1 -o /tmp/uinput-keys ~/apklayer-m1/tests/vm/uinput-keys.c"
ready=0
for _ in $(seq 1 120); do vm "grep -aq 'started com.android.test.uibench' /tmp/gpu-test.out" && { ready=1; break; }; sleep 1; done
check() { if eval "$2"; then echo "PASS  $1"; else echo "FAIL  $1"; FAILED=1; fi; }
FAILED=0
check "the app starts on the native stack" "[ $ready = 1 ]"
for _ in $(seq 1 40); do   # the first frame takes a few seconds after the activity starts
    vm "cd ~/apklayer-m1; . scripts/native-env.sh; \$N/bin/btload \$L libapklayer_servicetool.so --main dump SurfaceFlinger > /tmp/gpu-sf.txt 2>&1"
    vm "grep -aq 'VRI-$PKG' /tmp/gpu-sf.txt" && break
done
check "its window is a SurfaceFlinger layer" "vm \"grep -aq 'VRI-$PKG' /tmp/gpu-sf.txt\""
check "frames reach the shell as dmabufs" "vm \"[ \\\$(grep -ac 'dmabuf frame' /tmp/apklayer-stack/shell.log) -gt 5 ]\""
vm "echo $PASS | sudo -S /tmp/uinput-keys abs:200,163 sleep:300 click sleep:2500 2>/dev/null"
vm "cd ~/apklayer-m1; . scripts/native-env.sh; \$N/bin/btload \$L libapklayer_servicetool.so --main dump SurfaceFlinger > /tmp/gpu-sf2.txt 2>&1"
check "a real click is handled by the app (a second window layer appears)" "vm \"[ \\$(grep -ac 'Layer \\[' /tmp/gpu-sf2.txt) -gt \\$(grep -ac 'Layer \\[' /tmp/gpu-sf.txt) ]\""
vm "cd ~/apklayer-m1; XDG_RUNTIME_DIR=/run/user/1000 bin/apklayer stop" >/dev/null 2>&1
[ "$FAILED" = 0 ] && echo "all passed" || { echo "FAILED"; exit 1; }
