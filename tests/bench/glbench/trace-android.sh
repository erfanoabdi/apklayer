#!/bin/sh
# run-android.py with bpftrace scripts attached (GLBENCH_TRACE, default "swap-trace.bt sf-trace.bt"; or sf-offcpu.bt) (sudo bpftrace) and the frames the shell showed (shell.log, APKLAYER_RUNTIME_DEBUG): one mode
# and one scene at a time, so the averages belong to it. Usage (instance environment as for run-android.py): trace-android.sh [mode] [scene] [seconds]
here=$(cd "$(dirname "$0")" && pwd)
libs=$APKLAYER_NATIVE/android-libs
log=${XDG_STATE_HOME:-$HOME/.local/state}/apklayer/${APKLAYER_INSTANCE:-default}/shell.log
script=$(mktemp --suffix=.bt)
(cd "$here" && cat ${GLBENCH_TRACE:-swap-trace.bt sf-trace.bt}) |
    sed "s|@LIBS@|$libs|g; s|@NATIVE@|$APKLAYER_NATIVE|g; s|@HOST@|/usr/lib/$(gcc -dumpmachine)|g" > "$script"
out=$(mktemp)
sudo -n bpftrace "$script" > "$out" 2>&1 &
tracer=$!
before=$(wc -l < "$log" 2>/dev/null || echo 0)
APKLAYER_RUNTIME_DEBUG=1 python3 -u "$here/run-android.py" "${1:-window}" "${2:-clear}" "${3:-3}"
sudo -n pkill -INT -f "^bpftrace $script"   # bpftrace prints its maps on SIGINT (matched by its unique script path)
wait "$tracer"
grep -v '^Attaching' "$out"
rm -f "$script" "$out"
# the shell's dmabuf frames of this run: count and rate over the run's span
tail -n +"$((before + 1))" "$log" | grep 'dmabuf frame' | python3 -c '
import sys, statistics
ts = [float(l.split("t=")[1].split()[0]) for l in sys.stdin]
d = [b - a for a, b in zip(ts, ts[1:])]
d = d[len(d) // 5:]  # skip the first frames (startup)
s = sorted(d)
if d: print("shell: %d dmabuf frames; steady part: %.1f fps, interval median %.1f p95 %.1f max %.1f ms, %d intervals > 25 ms" % (len(ts), len(d) / sum(d), 1000 * statistics.median(d), 1000 * s[int(len(s) * 0.95)], 1000 * s[-1], sum(x > 0.025 for x in d)))'
