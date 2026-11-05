#!/bin/bash
# ut-cam.sh [APP] [RUNMS]: camera = device, the app (default hello.camera) on the phone, then the log's camera lines and cameraserver's tail
. ~/apklayer-dev-cam/env.sh
APP=${1:-hello.camera}
LOG=$D/cam-$APP.log
$A stop >/dev/null 2>&1
$A providers set camera device 2>&1 | grep -v pw.conf
$A permissions grant $APP android.permission.CAMERA >/dev/null 2>&1
APKLAYER_JOPTS="-Dapklayer.runms=${2:-40000}" $A run $APP > "$LOG" 2>&1
$A stop >/dev/null 2>&1
grep -a "\[cam\]" "$LOG" | cut -c1-220
grep -a -v "sync_file\|MALI\|linkToDeath\|libbinder" $D/state/apklayer/cam/cameraserver.log | tail -${N:-25} | cut -c1-240
