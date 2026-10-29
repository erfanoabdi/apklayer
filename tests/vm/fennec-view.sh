#!/bin/bash
# Fennec opens a page handed to it as a VIEW intent (no typing): serves tests/vm/www/ on 127.0.0.1:80 and leaves the app open for a Mac-side screenshot.
# Usage (on the VM): tests/vm/fennec-view.sh [url]    then screenshot, then bin/apklayer stop; sudo pkill -f '[h]ttp.server 80'
cd "$(dirname "$0")/../.." || exit 1
pkg=org.mozilla.fennec_fdroid; out=/tmp/fx.out; url=${1:-http://localhost/}
mkdir -p /tmp/www; echo '<html><body style="background:#fc6"><h1 style="font-size:80px">apklayer test page</h1></body></html>' > /tmp/www/index.html
(cd /tmp/www && sudo -n setsid python3 -m http.server 80 --bind 127.0.0.1 > /tmp/www.log 2>&1 &)
printf 'action=android.intent.action.VIEW\ndata=%s\ncomponent=org.mozilla.fenix.IntentReceiverActivity\n' "$url" > /tmp/fx-intent.properties
[ -n "$TRACE_EGL" ] && (sudo -n bpftrace tests/vm/egl-trace.bt > /tmp/egl-trace.txt 2>&1 &)
(APKLAYER_GECKO_PREFS="$GECKO_PREFS" APKLAYER_EGL_PROBE="${EGL_PROBE:-}" MOZ_LOG="$MOZ_LOG_SPEC" MOZ_LOG_FILE=${MOZ_LOG_TO:-} APKLAYER_INTENT_FILE=/tmp/fx-intent.properties APKLAYER_JNI_EXC=1 APKLAYER_JOPTS="-Dapklayer.logcat=true -Dapklayer.geckodiag=true $JOPTS_EXTRA" setsid bin/apklayer run $pkg; echo "exit=$?") > $out 2>&1 &
sudo -n /tmp/uinput-keys sleep:${VIEW_WAIT:-70000}
echo "alive: $(pgrep -f "[a]pklayer.package=$pkg" | wc -l); web log:"; tail -3 /tmp/www.log | cut -c1-120; grep -a "^exit=" $out
