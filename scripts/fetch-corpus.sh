#!/usr/bin/env bash
# fetch-corpus: P3.1. Downloads the F-Droid APKs listed in tests/corpus/packages.txt once into
# ~/.cache/apklayer/corpus and pins versions + sha256 in tests/corpus/corpus.lock
# ("<tier> <package> <versionCode> <sha256>"). Pinned entries are verified, never re-resolved.
# Usage: fetch-corpus.sh [tier...]   (default: T1)
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DIR="${CORPUS_CACHE:-$HOME/.cache/apklayer/corpus}"; LOCK="$ROOT/tests/corpus/corpus.lock"
mkdir -p "$DIR"; touch "$LOCK"
TIERS=("${@:-T1}")
while read -r tier pkg _; do
  [[ "$tier" =~ ^T[123]$ ]] || continue
  [[ " ${TIERS[*]} " == *" $tier "* ]] || continue
  pinned=$(awk -v p="$pkg" '$2==p {print $3, $4}' "$LOCK")
  if [ -n "$pinned" ]; then vc=${pinned% *}; want=${pinned#* }; else
    vc=$(curl -s -m 20 "https://f-droid.org/api/v1/packages/$pkg" | python3 -c "import sys,json; print(json.load(sys.stdin).get('suggestedVersionCode',''))" 2>/dev/null); want=""
  fi
  [ -n "$vc" ] || { echo "skip  $pkg (not on F-Droid)"; continue; }
  f="$DIR/${pkg}_$vc.apk"
  [ -s "$f" ] || curl -sfL -m 300 -o "$f" "https://f-droid.org/repo/${pkg}_$vc.apk" || { echo "skip  $pkg (download failed)"; rm -f "$f"; continue; }
  sum=$(shasum -a 256 "$f" | cut -d' ' -f1)
  if [ -n "$want" ] && [ "$sum" != "$want" ]; then echo "FAIL  $pkg sha256 mismatch"; continue; fi
  [ -n "$want" ] || echo "$tier $pkg $vc $sum" >> "$LOCK"
  echo "ok    $pkg $vc"
done < "$ROOT/tests/corpus/packages.txt"
