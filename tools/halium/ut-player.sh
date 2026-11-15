#!/bin/bash
# ut-player.sh [ASSET]: hello.nativeplayer (original native MediaPlayer) 20 s on the phone, screenshot at PLAYER PASS, then the log's verdict
. ~/apklayer-dev-cam/env.sh
ASSET=${1:-red-tone.mp4}
LOG=$D/player.log
export APKLAYER_NATIVE_MEDIA_PARCELS=1 APKLAYER_DISPLAY_WIDTH=480 APKLAYER_DISPLAY_HEIGHT=400
export APKLAYER_JOPTS="-Dapklayer.nativemedia=true -Dapklayer.nativeaudio=true -Dapklayer.nativeparcels=true -Dapklayer.nativeplayer=true -Dhello.asset=$ASSET -Dapklayer.runms=20000"
$A stop >/dev/null 2>&1
: > "$LOG"
shot=0
while IFS= read -r line; do
  printf '%s\n' "$line" >> "$LOG"
  if [ $shot = 0 ] && printf '%s' "$line" | grep -q "PLAYER PASS"; then shot=1; $A screenshot hello.nativeplayer $D/player.png && echo "SHOT $D/player.png"; fi
done < <($A run hello.nativeplayer 2>&1)
$A stop >/dev/null 2>&1
grep -a "hello-native-player\]" "$LOG" | cut -c1-160
grep -a "AudioTrack: stop\|audio.codec\|c2\.\|codec" "$D/state/apklayer/cam/media.log" | tail -5 | cut -c1-200
