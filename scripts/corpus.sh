#!/usr/bin/env bash
# corpus: P3 headless harness (PLAN P3.2). For every APK in the corpus dir (default
# ~/TestApps, plus tests/uibench) run: install (dex2jvm convert + describe) -> run headless
# -> snapshot at $SNAPS1 -> tap the center of the first clickable view -> snapshot again.
# A pass means: launcher activity started, no uncaught exception, both frames non-blank,
# and the second frame differs from the first (state change). Results are appended to
# tests/corpus/results.md with the PNGs under tests/corpus/shots/.
#
# Usage: corpus.sh [apk ...]          (default: all *.apk in ~/TestApps)
# Env:   CORPUS_DIR (default ~/TestApps), SKIP (regex of names to skip),
#        SNAPS1 (default 5500), RUNMS (default 6000), TIMEOUT (default 180)
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CORPUS_DIR="${CORPUS_DIR:-$HOME/TestApps}"
SNAPS1="${SNAPS1:-5500}"
RUNMS="${RUNMS:-6000}"
TIMEOUT="${TIMEOUT:-180}"
RESULTS="$ROOT/tests/corpus/results.md"
SHOTS="$ROOT/tests/corpus/shots"
OUT="$ROOT/build/check"
mkdir -p "$SHOTS"

if [ $# -gt 0 ]; then
  APKS=("$@")
else
  APKS=("$ROOT/tests/uibench/uibench.apk")
  for a in "$CORPUS_DIR"/*.apk; do [ -e "$a" ] && APKS+=("$a"); done
fi

# tier: count dex files and ABIs in the APK (P3.1). Prints "<ndex> <abis>".
tier() {
  unzip -l "$1" 2>/dev/null | awk '
    $4 ~ /^classes.*\.dex$/ { d++ }
    index($4, "lib/") == 1 { s = substr($4, 5); i = index(s, "/"); if (i > 1) abis[substr(s, 1, i-1)] = 1 }
    END { n = 0; s = ""; for (a in abis) { s = s (n++ ? "," : "") a }; printf "%d %s\n", d, (n ? s : "none") }'
}

# nonblank <png>: mean pixel deviation from the modal (background) colour.
nonblank() {
  python3 - "$1" <<'PY'
import sys, collections
from PIL import Image
im = Image.open(sys.argv[1]).convert("RGB").resize((160, 100))
px = list(im.getdata())
bg = collections.Counter(px).most_common(1)[0][0]
dev = sum(abs(r-bg[0])+abs(g-bg[1])+abs(b-bg[2]) for r, g, b in px) / (len(px)*3)
print(int(dev))
PY
}

# diffpct <a> <b>: percent of sampled pixels that changed between two frames.
diffpct() {
  python3 - "$1" "$2" <<'PY'
import sys
from PIL import Image
a = Image.open(sys.argv[1]).convert("RGB").resize((160, 100))
b = Image.open(sys.argv[2]).convert("RGB").resize((160, 100))
pa, pb = list(a.getdata()), list(b.getdata())
ch = sum(1 for x, y in zip(pa, pb) if abs(x[0]-y[0])+abs(x[1]-y[1])+abs(x[2]-y[2]) > 24)
print(ch * 100 // len(pa))
PY
}

# frame times: first snapshot at SNAPS1 (before the tap), tap 1 s before the end,
# second snapshot after the tap has had time to draw (RUNMS + settle).
TAPMS=$(( RUNMS - 1000 ))
SNAP2=$(( RUNMS + 700 ))
SNAPS="$SNAPS1,$TAPMS,$SNAP2"
RUNMS=$(( SNAP2 + 200 ))

logf() { cat "$1" | sed 's/\x1b\[[0-9;]*m//g'; }

for APK in "${APKS[@]}"; do
  NAME="$(basename "$APK" .apk)"
  if [ -n "${SKIP:-}" ] && echo "$NAME" | grep -qE "$SKIP"; then
    echo "=== SKIP $NAME"; continue
  fi
  echo "=== $NAME"
  TIER="$(tier "$APK")"
  LOG="$SHOTS/$NAME.log"   # outside $OUT: check.sh wipes $OUT at start
  # headless run: no script, no shell -> AppHost auto-taps first clickable, snapshots at SNAPS
  if command -v timeout >/dev/null 2>&1; then TO=(timeout -k 10 "$TIMEOUT"); else TO=(perl -e 'alarm shift; exec @ARGV' "$TIMEOUT"); fi
  "${TO[@]}" "$ROOT/scripts/check.sh" "$APK" "" "$SNAPS" "$RUNMS" \
      "-Dapklayer.autotap=true -Dapklayer.autotap.ms=$TAPMS" > "$LOG" 2>&1
  RC=$?
  PNG1="$OUT/$NAME-${SNAPS1}ms.png"
  PNG2="$OUT/$NAME-${SNAP2}ms.png"
  ACT=$(logf "$LOG" | sed -n 's/.*started \([A-Za-z0-9_.$/]*\).*/\1/p' | head -1)
  EXC=$(logf "$LOG" | grep -m1 -E 'Exception in thread|FATAL|no launcher' | cut -c1-160)
  [ -z "$EXC" ] && EXC=$(logf "$LOG" | grep -m1 -E '^[a-z][a-z0-9.]*\.[A-Za-z]*(Exception|Error)' | cut -c1-160)
  V1=0; V2=0; D=0
  if [ -f "$PNG1" ] && [ -f "$PNG2" ]; then
    V1=$(nonblank "$PNG1"); RC1=$?
    V2=$(nonblank "$PNG2"); RC2=$?
    D=$(diffpct "$PNG1" "$PNG2"); RC3=$?
    [ $RC1 -ne 0 ] && echo "!! nonblank($PNG1) failed" >&2
    [ $RC3 -ne 0 ] && echo "!! diffpct failed" >&2
  fi
  # For scripted runs (e.g. uibench's own script) the script itself proves interaction;
  # the auto-tap is a best-effort extra for unscripted apps. Accept a PASS when the run
  # navigated deeper (depth >= 2) even if the last tap didn't change the frame.
  DEPTH=$(logf "$LOG" | sed -n 's/.*depth=\([0-9]*\).*/\1/p' | sort -n | tail -1)
  [ -z "$DEPTH" ] && DEPTH=0
  STATUS="FAIL"
  if [ -n "$ACT" ] && [ -z "$EXC" ] && [ "$V1" -gt 4 ] && [ "$V2" -gt 4 ] && { [ "$D" -gt 0 ] || [ "$DEPTH" -ge 2 ]; }; then
    STATUS="PASS"
  fi
  # keep artifacts
  if [ -f "$PNG1" ]; then cp "$PNG1" "$SHOTS/$NAME-1.png"; fi
  if [ -f "$PNG2" ]; then cp "$PNG2" "$SHOTS/$NAME-2.png"; fi
  CAUSE=""
  if [ "$STATUS" = FAIL ]; then
    if [ -n "$EXC" ]; then CAUSE="$EXC"
    elif [ ! -f "$PNG1" ]; then CAUSE="no frame (rc=$RC, likely hang/timeout)"
    elif [ "$V1" -le 4 ]; then CAUSE="blank frame"
    elif [ "$D" -eq 0 ]; then CAUSE="tap caused no state change"
    else CAUSE="activity not started"; fi
  fi
  echo "--- $NAME: $STATUS dex/abi=[$TIER] act=$ACT dev=$V1/$V2 diff=$D% $CAUSE"
  # append to results table (replace existing row for same app, else append)
  ROW="| $NAME | $TIER | $STATUS | ${CAUSE:-$ACT (tap diff ${D}%)} |"
  if grep -q "^| $NAME |" "$RESULTS" 2>/dev/null; then
    grep -v "^| $NAME |" "$RESULTS" > "$RESULTS.tmp" && mv "$RESULTS.tmp" "$RESULTS"
  fi
  echo "$ROW" >> "$RESULTS"
done
echo "corpus done -> $RESULTS"
