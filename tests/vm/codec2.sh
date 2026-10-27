#!/bin/bash
# Foreground native host + real Codec2/gralloc regression, with unconditional cleanup.
set -euo pipefail
cd "$(dirname "$0")/../.."
VM="${APKLY_VM:-parallels@10.211.55.4}"
PASS="${APKLY_VM_PASS:-1234}"
COPYFILE_DISABLE=1 tar cf - bridge/hals/media tests/hals/codec-test.c scripts/native-env.sh scripts/arm64-stack.sh |
    sshpass -p "$PASS" ssh "$VM" 'tar xf - -C ~/apklayer-m1'
sshpass -p "$PASS" ssh "$VM" bash -s <<'REMOTE'
set -euo pipefail
cd ~/apklayer-m1
export XDG_RUNTIME_DIR=/run/user/1000 APKLAYER_NATIVE="$HOME/arm64-native"
export APKLAYER_HOST_LIBS="$APKLAYER_NATIVE/libapklayer-hal-graphics.so:$APKLAYER_NATIVE/libapklayer-hal-media.so"
trap 'bin/apklayer stop >/dev/null 2>&1; pkill -9 -x tail || true' EXIT
ninja -C b bridge/hals/media/codec-test
cp b/bridge/hals/media/libapklayer-hal-media.so "$APKLAYER_NATIVE/"
b/bridge/hals/media/codec-test /tmp/apklayer-vp8.ivf
(
    . scripts/native-env.sh
    "$N/bin/btload" "$L" libapklayer_codec2.so --main --probe > /tmp/apklayer-codec2-interface.log 2>&1
)
grep -a 'codec2: PASS' /tmp/apklayer-codec2-interface.log
scripts/arm64-stack.sh libapklayer_codec2.so --decode /tmp/apklayer-vp8.ivf
grep -a 'codec2: PASS' /tmp/apklayer-stack/tool.log
test "$(grep -ac 'codec2: PASS work' /tmp/apklayer-stack/tool.log)" = 3
bin/apklayer stop >/dev/null 2>&1
if ps -eo args | grep -E '[a]pklayer.host.Main|[b]tload|[a]pklayer-shell' | grep -v grep; then exit 1; fi
echo 'PASS native host decoder, Codec2 interface, work ordinals/EOS and real gralloc YUV'
REMOTE
