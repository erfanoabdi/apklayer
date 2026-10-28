#!/bin/bash
# Foreground native host + real Codec2/gralloc regression, with unconditional cleanup.
set -euo pipefail
cd "$(dirname "$0")/../.."
. tests/vm/config.sh
COPYFILE_DISABLE=1 tar cf - tests/vm/codec2-run.py bridge/hals/media tests/hals/codec-test.c tests/hals/codec-reorder-test.c tests/vm/vp9-superframe.py bridge/native/apklayer.prop aosp/hals/media/codec2/manifest.xml |
    sshpass -p "$PASS" ssh "$VM" 'tar xf - -C ~/apklayer'
sshpass -p "$PASS" ssh "$VM" bash -s <<'REMOTE'
set -euo pipefail
cd ~/apklayer
export XDG_RUNTIME_DIR=/run/user/1000 APKLAYER_NATIVE="$HOME/arm64-native" APKLAYER_CODEC2=1
export APKLAYER_HOST_LIBS="$APKLAYER_NATIVE/libapklayer-hal-graphics.so:$APKLAYER_NATIVE/libapklayer-hal-media.so"
trap 'bin/apklayer stop >/dev/null 2>&1; pkill -9 -x tail || true; rm -f "$APKLAYER_NATIVE/android-root/vendor/etc/vintf/manifest/apklayer-media.xml"' EXIT
ninja -C b bridge/hals/media/codec-test bridge/hals/media/codec-reorder-test
cp b/bridge/hals/media/libapklayer-hal-media.so "$APKLAYER_NATIVE/"
b/bridge/hals/media/codec-test /tmp/apklayer-vp8.ivf /tmp/apklayer-vp9.ivf
b/bridge/hals/media/codec-reorder-test /tmp/apklayer-h264.au
b/cli/apklayer exec libapklayer_codec2.so --probe > /tmp/apklayer-codec2-interface.log 2>&1
grep -a 'codec2: PASS' /tmp/apklayer-codec2-interface.log
python3 -u tests/vm/codec2-run.py
grep -a 'codec2: PASS' $HOME/.local/state/apklayer/default/tool.log
grep -aq 'codec2: registered' $HOME/.local/state/apklayer/default/codec2.log
grep -aq 'PASS remote AIDL discovery/interface' $HOME/.local/state/apklayer/default/tool.log
test "$(grep -ac 'codec2: PASS AIDL work' $HOME/.local/state/apklayer/default/tool.log)" = 5
grep -aq 'PASS AIDL hidden work 0' $HOME/.local/state/apklayer/default/tool.log
python3 -u tests/vm/codec2-run.py /tmp/apklayer-vp9.ivf
grep -a 'codec2: PASS' $HOME/.local/state/apklayer/default/tool.log
test "$(grep -ac 'codec2: PASS AIDL work' $HOME/.local/state/apklayer/default/tool.log)" = 5
grep -aq 'PASS AIDL hidden work 0' $HOME/.local/state/apklayer/default/tool.log
python3 tests/vm/vp9-superframe.py /tmp/apklayer-vp9.ivf /tmp/apklayer-vp9-superframe.ivf
python3 -u tests/vm/codec2-run.py /tmp/apklayer-vp9-superframe.ivf visible
grep -a 'codec2:' $HOME/.local/state/apklayer/default/tool.log
test "$(grep -ac 'codec2: PASS AIDL work' $HOME/.local/state/apklayer/default/tool.log)" = 3
python3 tests/vm/vp9-superframe.py /tmp/apklayer-vp9.ivf /tmp/apklayer-vp9-multi.ivf multi
python3 -u tests/vm/codec2-run.py /tmp/apklayer-vp9-multi.ivf multi
grep -a 'codec2:' $HOME/.local/state/apklayer/default/tool.log
test "$(grep -ac 'codec2: PASS AIDL work' $HOME/.local/state/apklayer/default/tool.log)" = 4
python3 tests/vm/vp9-superframe.py /tmp/apklayer-vp9.ivf /tmp/apklayer-vp9-multi-last.ivf multi-last
python3 -u tests/vm/codec2-run.py /tmp/apklayer-vp9-multi-last.ivf multi-last
grep -a 'codec2:' $HOME/.local/state/apklayer/default/tool.log
test "$(grep -ac 'codec2: PASS AIDL work' $HOME/.local/state/apklayer/default/tool.log)" = 4
python3 -u tests/vm/codec2-run.py /tmp/apklayer-h264.au reorder
grep -a 'codec2:' $HOME/.local/state/apklayer/default/tool.log
test "$(grep -ac 'PASS AIDL reordered picture' $HOME/.local/state/apklayer/default/tool.log)" = 12
grep -aq 'PASS AIDL reordered EOS after 12 pictures' $HOME/.local/state/apklayer/default/tool.log
bin/apklayer stop >/dev/null 2>&1
if ps -eo args | grep -E '[a]pklayer.host.Main|[b]tload|[a]pklayer-shell' | grep -v grep; then exit 1; fi
echo 'PASS native host decoder, remote VP8/VP9 Codec2 work/hidden frames, ordinals/EOS and real gralloc YUV'
REMOTE
