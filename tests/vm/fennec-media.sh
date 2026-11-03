#!/bin/bash
# Fennec plays HTML5 video: a local page (tests/vm/www/video.html) plays generated VP9/Opus WebM (muted) and H.264/AAC MP4 (with sound)
# side by side, opened through a VIEW intent; it beacons each video's state (/r?src=..&t=..&w=..) to the local server once a second.
# Checks: each video's currentTime advances and it has a size and no error, two app screenshots differ (frames change), and PipeWire got
# an output stream from the instance. MEDIA_URL=<url> opens that page instead and checks frames and audio only; YOUTUBE=<video id>: that
# video in YouTube's embedded player (tests/vm/www/youtube.html).
# Usage (on the VM, in the caller's instance: APKLAYER_INSTANCE and the XDG_* dirs as for any run): tests/vm/fennec-media.sh
#   FENNEC_SECS (playing time before the checks, default 40). A fresh install works: a first start makes the profile, Fenix's own
#   preferences mark its first-run screen done.
cd "$(dirname "$0")/../.." || exit 1
pkg=org.mozilla.fennec_fdroid; port=${PORT:-18091}; secs=${FENNEC_SECS:-40}
work=$(mktemp -d "${XDG_STATE_HOME:-$HOME/.local/state}/fennec-media.XXXXXX"); trap 'rm -rf "$work"' EXIT
cp tests/vm/www/video.html tests/vm/www/youtube.html "$work/"
[ -n "$YOUTUBE" ] && MEDIA_URL="http://127.0.0.1:$port/youtube.html?v=$YOUTUBE"  # its embedded player in a local page
inst=${APKLAYER_INSTANCE:-default}; [ "$inst" = default ] && inst=0  # app data per instance (cli/layout.h)
prof=$(ls -d "${XDG_DATA_HOME:-$HOME/.local/share}/apklayer/$inst/app-data/$pkg/app/files/mozilla/"*.default* 2>/dev/null | head -1)
if [ -z "$prof" ]; then  # a fresh install: Gecko makes its own profile on the first start
    APKLAYER_JOPTS="-Dapklayer.runms=25000" bin/apklayer run "$pkg" > "$work/first-start.log" 2>&1 < /dev/null
    bin/apklayer stop > /dev/null 2>&1  # its hub goes down with its last app: the test's run must not attach to that ending hub
    prof=$(ls -d "${XDG_DATA_HOME:-$HOME/.local/share}/apklayer/$inst/app-data/$pkg/app/files/mozilla/"*.default* 2>/dev/null | head -1)
fi
[ -n "$prof" ] || { echo "FAIL Fennec made no profile on its first start (see $work/first-start.log)"; exit 1; }
# past Fenix's first-run screen (it would cover the page and keep its tab in the background): the preferences Fenix writes when Continue is pressed
setpref() {  # <shared_prefs file> <type> <name> <value>: one entry of an Android SharedPreferences file, created or replaced
    local line="<$2 name=\"$3\" value=\"$4\" />"
    [ -f "$1" ] || printf '<?xml version="1.0" encoding="utf-8" standalone="yes" ?>\n<map>\n</map>\n' > "$1"
    if grep -q "name=\"$3\"" "$1"; then sed -i "s|<[a-z]* name=\"$3\"[^>]*/>|$line|" "$1"; else sed -i "s|</map>|    $line\n</map>|" "$1"; fi
}
sp="$prof/../../../shared_prefs"; now=$(date +%s%3N)
setpref "$sp/fenix.onboarding.xml" int fenix.onboarding.last_version 1
for e in "boolean pref_key_terms_accepted true" "int pref_key_terms_accepted_version 5" "long pref_key_terms_accepted_date $now" \
    "long pref_key_onboarding_completed_timestamp $now" "boolean pref_key_is_first_run false"; do setpref "$sp/fenix_preferences.xml" $e; done
# Gecko's media logs go to the work dir (MOZ_LOG does not reach Gecko)
{ grep -v 'fennec-media.sh' "$prof/user.js" 2>/dev/null
  for p in "\"logging.config.LOG_FILE\", \"$work/gecko.log\"" '"logging.config.sync", true' \
      '"logging.PlatformDecoderModule", 5' '"logging.MediaFormatReader", 4' '"logging.AndroidDecoderModule", 5' '"logging.FFmpegVideo", 5' \
      '"logging.FFmpegAudio", 5'; do echo "user_pref($p); // fennec-media.sh"; done; } > "$work/user.js" && cp "$work/user.js" "$prof/user.js"
# afterwards the profile logs no more: these lines go from user.js and the values Gecko saved from them from prefs.js
cleanup() { sed -i "/fennec-media.sh/d" "$prof/user.js"; sed -i "/\"logging\.\(config\.\|PlatformDecoderModule\|MediaFormatReader\|AndroidDecoderModule\|FFmpeg\)/d" "$prof/prefs.js"; rm -rf "$work"; }
trap cleanup EXIT
v="videotestsrc num-buffers=150 pattern=smpte horizontal-speed=4 ! video/x-raw,format=I420,width=320,height=240,framerate=30/1"
a="audiotestsrc num-buffers=250 freq=440 ! audioconvert ! audioresample ! audio/x-raw,rate=48000,channels=2"
gst-launch-1.0 -q $v ! vp9enc deadline=1 cpu-used=8 ! webmmux name=m ! filesink location="$work/t.webm" $a ! opusenc ! m. &&
gst-launch-1.0 -q $v ! x264enc speed-preset=ultrafast ! video/x-h264,profile=main ! h264parse ! mp4mux faststart=true name=m \
    ! filesink location="$work/t.mp4" $a ! voaacenc ! aacparse ! m. || { echo "FAIL test media (gst-launch)"; exit 1; }
(cd "$work" && exec python3 -m http.server "$port" --bind 127.0.0.1 > "$work/www.log" 2>&1) & web=$!
trap 'kill $web 2>/dev/null; cleanup' EXIT  # the server goes even when the test is interrupted
sleep 1; kill -0 $web 2>/dev/null || { echo "FAIL the page server cannot listen on port $port: $(tail -1 "$work/www.log")"; exit 1; }
url=${MEDIA_URL:-http://127.0.0.1:$port/video.html?src=t.webm,t.mp4&muted=1,0&run=$$}
printf 'action=android.intent.action.VIEW\ndata=%s\ncomponent=org.mozilla.fenix.IntentReceiverActivity\n' "$url" > "$work/intent.properties"
APKLAYER_TEST_INPUT=1 APKLAYER_INTENT_FILE="$work/intent.properties" APKLAYER_JOPTS="-Dapklayer.logcat=true -Dapklayer.runms=$(( (secs * 3 / 2 + 12) * 1000 ))" bin/apklayer run $pkg > "$work/run.out" 2>&1 &
app=$!
sleep $((secs / 2))  # then a tap in the page: sound plays only after a user gesture (Fenix blocks audible autoplay)
for i in $(seq $((secs / 2))); do { [ -n "$MEDIA_URL" ] || grep -q "GET /r?" "$work/www.log"; } && break; sleep 1; done  # a cold first start shows the page later
python3 -c 'import sys; sys.path.insert(0, "tests/lib"); import instance; instance.inject(sys.argv[1], "tap:160,400")' $pkg > "$work/tap.out" 2>&1
sleep $((secs - secs / 2))
bin/apklayer screenshot $pkg "$work/a.png" > "$work/shot.out" 2>&1
pw-dump 2>/dev/null > "$work/pw.json"
sleep 2
bin/apklayer screenshot $pkg "$work/b.png" >> "$work/shot.out" 2>&1
wait $app; kill $web 2>/dev/null; wait $web 2>/dev/null
logs="${XDG_STATE_HOME:-$HOME/.local/state}/apklayer/${APKLAYER_INSTANCE:-default}"
fail=0
check() { if [ "$2" = 1 ]; then echo "PASS $1"; else echo "FAIL $1"; fail=1; fi; }
if [ -z "$MEDIA_URL" ]; then
    for src in t.webm t.mp4; do
        last=$(grep -o "GET /r?src=$src&[^ ]*" "$work/www.log" | tail -1)
        times=$(grep -o "GET /r?src=$src&t=[0-9.]*" "$work/www.log" | sed 's/.*t=//' | sort -u | wc -l)  # distinct currentTimes (it loops)
        echo "  $src last report: ${last#GET /r?}"
        check "$src plays ($times distinct currentTime values, size and no error)" \
            "$(grep -q '&w=320&h=240&' <<< "$last" && grep -q '&err=&' <<< "$last" && grep -q '&paused=false&' <<< "$last" && [ "$times" -ge 5 ] && echo 1)"
    done
fi
diff=$(python3 tests/vm/media-check.py frames "$work/a.png" "$work/b.png")
check "frames change between screenshots ($diff px differ)" "$([ "$diff" -gt 2000 ] && echo 1)"
streams=$(python3 tests/vm/media-check.py pipewire "$work/pw.json" "${APKLAYER_INSTANCE:-default}")
check "PipeWire plays an output stream from the instance ($streams running)" "$([ "$streams" -gt 0 ] && echo 1)"
d="${XDG_STATE_HOME:-$HOME/.local/state}/fennec-media-last"; rm -rf "$d"; cp -r "$work" "$d"; echo "  screenshots, logs and reports: $d"
exit $fail
