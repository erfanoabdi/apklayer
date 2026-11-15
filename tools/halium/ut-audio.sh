#!/bin/bash
# ut-audio.sh: hello.nativeaudio (AudioTrack PCM, record, communication) on the phone for 40 s, the app's verdict lines
. ~/apklayer-dev-cam/env.sh
LOG=$D/audio-app.log
export APKLAYER_DISPLAY_WIDTH=480 APKLAYER_DISPLAY_HEIGHT=400
export APKLAYER_JOPTS="-Dapklayer.nativeattribution=true -Dapklayer.nativemedia=true -Dapklayer.nativeaudio=true -Dapklayer.nativeparcels=true -Dhello.rate=1 -Dhello.record=1 -Dapklayer.runms=${RUNMS:-40000}"
$A stop >/dev/null 2>&1
$A permissions grant hello.nativeaudio android.permission.RECORD_AUDIO >/dev/null 2>&1
$A run hello.nativeaudio > "$LOG" 2>&1
$A stop >/dev/null 2>&1
grep -a "hello-native-audio\]" "$LOG" | cut -c1-170
grep -a "audio pulse" $D/state/apklayer/cam/audio*.log | sort | uniq -c | head
