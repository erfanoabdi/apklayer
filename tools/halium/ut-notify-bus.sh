#!/bin/bash
# ut-notify-bus.sh [dir with notify-bus.py]: runs ON the X23. A Notify through bridge/portal/notify-bus.py (one bus connection, as the hub does on Lomiri)
# must still be open 3 s later; the same Notify by `gdbus call` (its connection ends at once) is closed by Lomiri within 30 ms: the control. Prints PASS/FAIL.
d=${1:-$(dirname "$0")/../../bridge/portal}; N=org.freedesktop.Notifications; mon=$(mktemp)
dbus-monitor --session "interface='$N',member='NotificationClosed'" > "$mon" 2>&1 & m=$!
exec 3< <(python3 - "$d" <<'PY'
import subprocess, sys, time
p = subprocess.Popen(['python3', sys.argv[1] + '/notify-bus.py'], stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True)
p.stdin.write("notify ('ut-notify-bus', 0, '', 'kept', 'one bus connection', [], {}, int32 -1)\n"); p.stdin.flush()
print(p.stdout.readline().strip(), flush=True); time.sleep(3); p.stdin.write("close 0\n"); p.stdin.flush(); p.stdout.readline(); p.stdin.close()
PY
)
read -r kept <&3; id=${kept#uint32 }
gdbus call --session --dest $N --object-path /org/freedesktop/Notifications --method $N.Notify ut-notify-bus-ctl 0 '' control body '[]' '{}' 20000 > /dev/null
cat <&3 > /dev/null; kill $m 2>/dev/null; [ -n "$KEEP" ] && cat "$mon"
closed=$(grep -a -c 'uint32 '"$id"'$' "$mon")  # a close of the kept one before the 3 s were over (the helper's own close at the end comes after: it names id 0)
if [ -n "$id" ] && [ "$closed" = 0 ]; then echo "PASS ut-notify-bus: notification $id still open after 3 s"; else echo "FAIL ut-notify-bus: notification '$kept' closed ($closed)"; fi; rm -f "$mon"
