#!/bin/bash
# NewPipe plays a YouTube video (ExoPlayer: MediaCodec through Codec2, AudioTrack through AudioFlinger), opened through a VIEW intent with its
# open action set to the video player. Checks: two screenshots of its window differ (frames change) and PipeWire plays an output stream from
# the instance. Usage (on the VM, in the caller's instance: APKLAYER_INSTANCE and the XDG_* dirs as for any run): tests/vm/newpipe-media.sh [video id]
#   NEWPIPE_SECS (time from launch to the first screenshot, default 30). Needs the network (YouTube).
cd "$(dirname "$0")/../.." || exit 1
pkg=org.schabi.newpipe; video=${1:-aqz-KE-bpKQ}; secs=${NEWPIPE_SECS:-30}
work=$(mktemp -d "${XDG_STATE_HOME:-$HOME/.local/state}/newpipe-media.XXXXXX"); trap 'rm -rf "$work"' EXIT
inst=${APKLAYER_INSTANCE:-default}; [ "$inst" = default ] && inst=0  # app data per instance (cli/layout.h)
prefs="${XDG_DATA_HOME:-$HOME/.local/share}/apklayer/$inst/app-data/$pkg/app/shared_prefs/${pkg}_preferences.xml"
# a link opens the video player, not NewPipe's chooser (the user's "Always" choice)
key='<string name="preferred_open_action_key">video_player</string>'
if [ ! -f "$prefs" ]; then mkdir -p "$(dirname "$prefs")" && printf '<?xml version="1.0" encoding="utf-8" standalone="yes" ?>\n<map>\n    %s\n</map>\n' "$key" > "$prefs"
elif grep -q 'name="preferred_open_action_key"' "$prefs"; then sed -i "s|<string name=\"preferred_open_action_key\">[^<]*</string>|$key|" "$prefs"
else sed -i "s|</map>|    $key\n</map>|" "$prefs"; fi
printf 'action=android.intent.action.VIEW\ndata=https://www.youtube.com/watch?v=%s\ncomponent=org.schabi.newpipe.RouterActivity\n' "$video" > "$work/intent.properties"
APKLAYER_INTENT_FILE="$work/intent.properties" APKLAYER_JOPTS="-Dapklayer.logcat=true -Dapklayer.runms=$(( (secs + 10) * 1000 ))" bin/apklayer run $pkg > "$work/run.out" 2>&1 &
app=$!
sleep "$secs"
bin/apklayer screenshot $pkg "$work/a.png" > "$work/shot.out" 2>&1
pw-dump 2>/dev/null > "$work/pw.json"
sleep 2
bin/apklayer screenshot $pkg "$work/b.png" >> "$work/shot.out" 2>&1
wait $app
fail=0
check() { if [ "$2" = 1 ]; then echo "PASS $1"; else echo "FAIL $1"; fail=1; fi; }
diff=$(python3 tests/vm/media-check.py frames "$work/a.png" "$work/b.png")
check "frames change between screenshots ($diff px differ)" "$([ "$diff" -gt 2000 ] && echo 1)"
streams=$(python3 tests/vm/media-check.py pipewire "$work/pw.json" "${APKLAYER_INSTANCE:-default}")
check "PipeWire plays an output stream from the instance ($streams running)" "$([ "$streams" -gt 0 ] && echo 1)"
grep -a -m3 'ExoPlaybackException\|AudioTrack init failed\|CodecException' "$work/run.out" | sed 's/^/  /'
d="${XDG_STATE_HOME:-$HOME/.local/state}/newpipe-media-last"; rm -rf "$d"; cp -r "$work" "$d"; echo "  screenshots and logs: $d"
exit $fail
