#!/bin/bash
# Foreground native host + real Codec2/gralloc regression: host decoder, remote VP8/VP9 Codec2 work, hidden frames, ordinals/EOS, real gralloc YUV.
# On the VM, in the group's instance (tests/run codec2: APKLAYER_INSTANCE, APKLY_STATE = its logs, APKLAYER_NATIVE).
set -euo pipefail
: "${APKLAYER_NATIVE:=$HOME/arm64-native}" "${APKLY_STATE:=${XDG_STATE_HOME:-$HOME/.local/state}/apklayer/${APKLAYER_INSTANCE:-default}}"
export APKLAYER_HOST_LIBS="$APKLAYER_NATIVE/libapklayer-hal-graphics.so:$APKLAYER_NATIVE/libapklayer-hal-media.so"
trap 'bin/apklayer stop >/dev/null 2>&1; true; rm -f "$APKLAYER_NATIVE/android-root/vendor/etc/vintf/manifest/apklayer-media.xml"' EXIT
ninja -C b bridge/hals/media/codec-test bridge/hals/media/codec-reorder-test
cp --remove-destination b/bridge/hals/media/libapklayer-hal-media.so "$APKLAYER_NATIVE/"
b/bridge/hals/media/codec-test /tmp/apklayer-vp8.ivf /tmp/apklayer-vp9.ivf
b/bridge/hals/media/codec-reorder-test /tmp/apklayer-h264.au
python3 -u tests/vm/codec2-run.py - interface  # every codec's interface, those of fixed rates or sizes skipped (bounded: codec2-run ends a stalled stack)
grep -a 'codec2: \(PASS\|SKIP\|FAIL\)' $APKLY_STATE/tool.log
if grep -aq 'codec2: FAIL' $APKLY_STATE/tool.log; then exit 1; fi  # the stack's exit status is not the tool's
test ! -e "$XDG_RUNTIME_DIR/apklayer/$APKLAYER_INSTANCE/properties"  # a stopped stack's properties end with it (no servicemanager.ready=true left)
python3 -u tests/vm/codec2-run.py
grep -a 'codec2: PASS' $APKLY_STATE/tool.log
grep -aq 'codec2: registered' $APKLY_STATE/codec2.log
grep -aq 'PASS remote AIDL discovery/interface' $APKLY_STATE/tool.log
test "$(grep -ac 'codec2: PASS AIDL work' $APKLY_STATE/tool.log)" = 5
grep -aq 'PASS AIDL hidden work 0' $APKLY_STATE/tool.log
python3 -u tests/vm/codec2-run.py /tmp/apklayer-vp9.ivf
grep -a 'codec2: PASS' $APKLY_STATE/tool.log
test "$(grep -ac 'codec2: PASS AIDL work' $APKLY_STATE/tool.log)" = 5
grep -aq 'PASS AIDL hidden work 0' $APKLY_STATE/tool.log
python3 tests/vm/vp9-superframe.py /tmp/apklayer-vp9.ivf /tmp/apklayer-vp9-superframe.ivf
python3 -u tests/vm/codec2-run.py /tmp/apklayer-vp9-superframe.ivf visible
grep -a 'codec2:' $APKLY_STATE/tool.log
test "$(grep -ac 'codec2: PASS AIDL work' $APKLY_STATE/tool.log)" = 3
python3 tests/vm/vp9-superframe.py /tmp/apklayer-vp9.ivf /tmp/apklayer-vp9-multi.ivf multi
python3 -u tests/vm/codec2-run.py /tmp/apklayer-vp9-multi.ivf multi
grep -a 'codec2:' $APKLY_STATE/tool.log
test "$(grep -ac 'codec2: PASS AIDL work' $APKLY_STATE/tool.log)" = 4
python3 tests/vm/vp9-superframe.py /tmp/apklayer-vp9.ivf /tmp/apklayer-vp9-multi-last.ivf multi-last
python3 -u tests/vm/codec2-run.py /tmp/apklayer-vp9-multi-last.ivf multi-last
grep -a 'codec2:' $APKLY_STATE/tool.log
test "$(grep -ac 'codec2: PASS AIDL work' $APKLY_STATE/tool.log)" = 4
python3 -u tests/vm/codec2-run.py /tmp/apklayer-h264.au reorder
grep -a 'codec2:' $APKLY_STATE/tool.log
test "$(grep -ac 'PASS AIDL reordered picture' $APKLY_STATE/tool.log)" = 12
grep -aq 'PASS AIDL reordered EOS after 12 pictures' $APKLY_STATE/tool.log
bin/apklayer stop >/dev/null 2>&1
python3 tests/lib/instance.py clean
echo 'PASS native host decoder, remote VP8/VP9 Codec2 work/hidden frames, ordinals/EOS and real gralloc YUV'
