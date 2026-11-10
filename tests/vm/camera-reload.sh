#!/bin/bash
# The camera provider restarts under an open camera (`providers set camera` reloads it): hello.camera's device reports an error or a
# disconnect, cameraserver keeps running (no fault in its log; it crashed in glibc's setxid handler, bionic_translation 0063) and the camera
# opens again with frames. In the group's instance (tests/run camera).
: "${APKLY_STATE:=${XDG_STATE_HOME:-$HOME/.local/state}/apklayer/${APKLAYER_INSTANCE:-default}}"
out=$APKLY_STATE/camera-reload.out
bin/apklayer stop >/dev/null 2>&1
: > $out; touch $out.start
trap 'bin/apklayer stop >/dev/null 2>&1; bin/apklayer providers unset camera >/dev/null' EXIT
bin/apklayer providers set camera fake cameras=back,front >/dev/null
bin/apklayer permissions grant hello.camera android.permission.CAMERA >/dev/null 2>&1
APKLAYER_JOPTS="-Dapklayer.runms=30000 -Dhello.reload=true" bin/apklayer run hello.camera 2>&1 | while IFS= read -r line; do
    echo "$line" >> $out
    case "$line" in *"[cam] reload"*) bin/apklayer providers set camera fake cameras=back,front fps=15 >/dev/null;; esac
done
grep -a "\[cam\]" $out
logs=$(find "$APKLY_STATE" -name 'cameraserver.log*' -newer $out.start)  # this run's (a restarted cameraserver rotates its log)
grep -a -i "runtime-trace\] signal\|fatal signal" $logs /dev/null && { echo "cameraserver crashed"; exit 1; }
grep -aq "\[cam\] PASS" $out || { tail -5 "$APKLY_STATE"/camera*.log 2>/dev/null; exit 1; }
