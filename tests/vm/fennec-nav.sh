#!/bin/bash
# Fennec: start, dismiss onboarding, load example.com, report child-process deaths, leave it open for a Mac-side screenshot.
cd "$(dirname "$0")/../.." || exit 1
pkg=org.mozilla.fennec_fdroid; out=/tmp/fx.out
(MOZ_LOG="${MOZ_LOG_SPEC:-}" MOZ_LOG_FILE=/tmp/moz-log.txt APKLAYER_JNI_EXC=1 APKLAYER_JOPTS="-Dapklayer.logcat=true" setsid bin/apklayer run $pkg; echo "exit=$?") > $out 2>&1 &
[ -n "$TRACE_SIGACT" ] && (until p=$(pgrep -f '[a]pklayer.package='$pkg | head -1); [ -n "$p" ]; do sleep 0.2; done; sudo -n strace -f -e trace=${STRACE_SET:-rt_sigaction} -p $(ls /proc/$p/task | paste -sd, -) -o /tmp/sigact.txt >/dev/null 2>&1) &
[ -n "$TRACE_SIGACT" ] && (until grep -q RESETHAND /tmp/sigact.txt 2>/dev/null; do sleep 0.1; done; cp /proc/$(grep -a -m1 RESETHAND /tmp/sigact.txt | cut -d' ' -f1)/maps /tmp/sigact-maps.txt) &

mkdir -p /tmp/www && echo '<html><body style="background:#fc6"><h1 style="font-size:80px">apklayer test page</h1></body></html>' > /tmp/www/index.html
(cd /tmp/www && sudo -n setsid python3 -m http.server 80 --bind 127.0.0.1 >/tmp/www.log 2>&1 &)
sudo -n /tmp/uinput-keys sleep:60000 abs:492,177 click sleep:2000 key:38 key:24 key:46 key:30 key:38 key:35 key:24 key:31 key:20 key:28 sleep:30000
grep -a -c "exiting" $out; grep -a "Cannot reuse\|jni-exc\] pending\|hs_err\|died" $out ~/.local/state/apklayer/default/hub.log | head -5 | cut -c1-200
sudo -n pkill -f '[h]ttp.server 80'
echo 'app left open: take the screenshot from the Mac, then bin/apklayer stop'
echo "alive at end: $(pgrep -f "[a]pklayer.package=$pkg" | wc -l); $(date +%T)"; tail -2 $out | cut -c1-120
