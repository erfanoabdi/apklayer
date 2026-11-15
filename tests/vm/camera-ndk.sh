#!/bin/bash
# The camera NDK (Android's own libcamera2ndk over the stack's cameraserver) with the fake backend: the id list, LENS_FACING, open, a YUV frame
# (tests/apps/hello-camera-ndk). In the group's instance (tests/run camera-ndk).
: "${APKLY_STATE:=${XDG_STATE_HOME:-$HOME/.local/state}/apklayer/${APKLAYER_INSTANCE:-default}}"
out=$APKLY_STATE/camera-ndk.out
bin/apklayer stop >/dev/null 2>&1
trap 'bin/apklayer stop >/dev/null 2>&1; bin/apklayer providers unset camera >/dev/null' EXIT
bin/apklayer providers set camera fake cameras=back,front sizes=640x480,1280x720 >/dev/null
bin/apklayer permissions grant hello.cameranative android.permission.CAMERA >/dev/null 2>&1
APKLAYER_JOPTS="-Dapklayer.runms=25000" bin/apklayer run hello.cameranative > $out 2>&1 || true
grep -a "\[ndkcam\]" $out
grep -aq "\[ndkcam\] PASS" $out || { tail -5 "$APKLY_STATE"/camera*.log 2>/dev/null; exit 1; }
