#!/bin/bash
# Fennec: start, dismiss onboarding, load example.com, report child-process deaths, leave it open for a Mac-side screenshot.
cd "$(dirname "$0")/../.." || exit 1
pkg=org.mozilla.fennec_fdroid; out=/tmp/fx.out
(APKLAYER_JNI_EXC=1 APKLAYER_JOPTS="-Dapklayer.logcat=true" setsid bin/apklayer run $pkg; echo "exit=$?") > $out 2>&1 &
[ -n "$TRACE_SIGACT" ] && (until p=$(pgrep -f '[a]pklayer.package='$pkg | head -1); [ -n "$p" ]; do sleep 0.2; done; sudo -n strace -f -e trace=rt_sigaction -p $(ls /proc/$p/task | paste -sd, -) -o /tmp/sigact.txt >/dev/null 2>&1) &
[ -n "$TRACE_SIGACT" ] && (until grep -q RESETHAND /tmp/sigact.txt 2>/dev/null; do sleep 0.1; done; cp /proc/$(grep -a -m1 RESETHAND /tmp/sigact.txt | cut -d' ' -f1)/maps /tmp/sigact-maps.txt) &
[ -n "$TRACE_KILLS" ] && sudo -n bpftrace -e 'tracepoint:signal:signal_generate /args->sig==11 || args->sig==9/ { printf("sig %d to %s pid %d code %d (sent by %s pid %d)\n", args->sig, args->comm, args->pid, args->code, comm, pid); }' > /tmp/kills9.txt 2>&1 &
mkdir -p /tmp/www && echo '<html><body style="background:#fc6"><h1 style="font-size:80px">apklayer test page</h1></body></html>' > /tmp/www/index.html
(cd /tmp/www && setsid python3 -m http.server 8000 --bind 127.0.0.1 >/tmp/www.log 2>&1 &)
sudo -n /tmp/uinput-keys sleep:60000 abs:492,177 click sleep:2000 key:38 key:24 key:46 key:30 key:38 key:35 key:24 key:31 key:20 combo:shift+39 key:9 key:11 key:11 key:11 key:28 sleep:30000
grep -a -c "exiting" $out; grep -a "Cannot reuse\|jni-exc\] pending\|hs_err\|died" $out ~/.local/state/apklayer/default/hub.log | head -5 | cut -c1-200
pkill -f '[h]ttp.server 8000'
echo 'app left open: take the screenshot from the Mac, then bin/apklayer stop'
echo "alive at end: $(pgrep -f "[a]pklayer.package=$pkg" | wc -l); $(date +%T)"; tail -2 $out | cut -c1-120
