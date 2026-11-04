#!/bin/bash
# camera2 over the stack's cameraserver and camera provider with the fake backend (colour bars): the camera list, a TextureView preview, a
# YUV ImageReader frame with the bars' colours, a JPEG still (tests/apps/hello-camera). In the group's instance (tests/run camera).
: "${APKLY_STATE:=${XDG_STATE_HOME:-$HOME/.local/state}/apklayer/${APKLAYER_INSTANCE:-default}}"
out=$APKLY_STATE/camera.out
export APKLAYER_CAMERA="fake cameras=back,front"
bin/apklayer stop >/dev/null 2>&1
bin/apklayer permissions grant hello.camera android.permission.CAMERA >/dev/null 2>&1
APKLAYER_JOPTS="-Dapklayer.runms=25000" bin/apklayer run hello.camera > $out 2>&1 || true
grep -a "\[cam\]" $out
bin/apklayer stop >/dev/null 2>&1
grep -aq "\[cam\] PASS" $out || { tail -5 "$APKLY_STATE"/camera*.log 2>/dev/null; exit 1; }
