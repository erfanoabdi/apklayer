#!/usr/bin/env bash
# VM corpus run (P3/P4/P5 exit numbers): each app in tests/corpus/vm-apps.txt is started on the VM for SECS seconds
# (default 30) and must (1) start its launcher activity, (2) not crash the host loop with an uncaught exception,
# (3) show a non-blank window (more than 2% of pixels differ from the most common colour in a snapshot).
# Prints one PASS/FAIL line per app and the pass count per tier; writes tests/corpus/results-vm.md.
# Usage: corpus.sh [tier-regex]   e.g. corpus.sh T2
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"; VM="${APKLY_VM:-parallels@10.211.55.4}"; PASS="${APKLY_VM_PASS:-1234}"
SECS="${SECS:-30}"; ONLY="${1:-.}"; OUT="$ROOT/tests/corpus/results-vm.md"
blank_pct() { python3 - "$1" <<'E2'
import sys, collections
from PIL import Image
px = list(Image.open(sys.argv[1]).convert("RGB").getdata())[::4]
print(round(100 * (1 - collections.Counter(px).most_common(1)[0][1] / len(px))))
E2
}
{ echo "# VM corpus results ($(date +%F))"; echo; echo "| tier | app | result | detail |"; echo "|---|---|---|---|"; } > "$OUT"
while read -r tier apk <&3; do
  [[ "$tier" =~ ^T[0-9]$ ]] || continue; [[ "$tier" =~ $ONLY ]] || continue
  apk="${apk/#\~/$HOME}"; [ -f "$apk" ] || apk="$ROOT/$apk"; name="$(basename "$apk" .apk)"
  [ -f "$apk" ] || { echo "SKIP  $name (no such file)"; continue; }
  for attempt in 1 2; do rm -f "/tmp/vmcorpus-$name.png" # a failed start is retried once: some apps start blank now and then (flake), those are marked
    LOG=$(APKLAYER_JOPTS="-Dapklayer.runms=$((SECS * 1000)) -Dapklayer.snapms=$(((SECS - 3) * 1000))" "$ROOT/scripts/vm-run.sh" "$apk" 2>&1)
    sshpass -p "$PASS" scp -q -o StrictHostKeyChecking=no "$VM:/tmp/apklayer-frame-$(((SECS - 3) * 1000))ms.png" "/tmp/vmcorpus-$name.png" 2>/dev/null
    sshpass -p "$PASS" ssh -o StrictHostKeyChecking=no "$VM" 'pkill -x apklayer-shell; pkill -f "[a]pklayer-webview"; pkill -f "[a]pklayer-media"; pkill -f "[a]pklayer-codec"; pkill -f "[p]w-cat"; pkill -f "[a]pklayer.host.Main"; true'
    why=""; pct=-1
    grep -aq "\[apklayer\] started" <<<"$LOG" || why="no activity started"
    [ -z "$why" ] && grep -aq "at apklayer.host.AppHost.loop" <<<"$LOG" && why="uncaught exception: $(grep -a -m1 -E '^(java|android|kotlin)[a-zA-Z.]*(Exception|Error)' <<<"$LOG" | cut -c1-110)"
    if [ -z "$why" ]; then
      if [ -f "/tmp/vmcorpus-$name.png" ]; then pct=$(blank_pct "/tmp/vmcorpus-$name.png" 2>/dev/null || echo 0); [ "${pct:-0}" -gt 2 ] || why="blank window (${pct:-0}% non-background pixels)"; else why="no snapshot"; fi
    fi
    [ -z "$why" ] && break
  done
  [ -n "${why:-}" ] || { [ "$attempt" = 2 ] && flaky=" (passed on the retry: flaky)" || flaky=""; }
  if [ -z "$why" ]; then echo "PASS  $tier $name (${pct}% drawn)$flaky"; echo "| $tier | $name | PASS | ${pct}% drawn$flaky |" >> "$OUT"
  else echo "FAIL  $tier $name: $why"; echo "| $tier | $name | FAIL | $why |" >> "$OUT"; fi
done 3< "$ROOT/tests/corpus/vm-apps.txt"
summary=$(awk -F'|' '/^\| T[0-9]/ {gsub(/ /,"",$2); gsub(/ /,"",$4); t[$2]++; if ($4=="PASS") p[$2]++} END {for (k in t) printf "%s: %d/%d\n", k, p[k], t[k]}' "$OUT" | sort)
echo "$summary"; { echo; echo "$summary" | sed 's/^/- /'; } >> "$OUT"
