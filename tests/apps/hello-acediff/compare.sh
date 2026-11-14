#!/usr/bin/env bash
# Differential check of the arm64 translator: builds acediff.c once with the NDK (static bionic program), runs it on a real arm64 host
# ($REAL, default utm@192.168.64.7, key auth), and diffs its lines against the lines the same library prints under Berberis
# (build/hello-acediff.apk run on x86_64: pass the log with the "[acediff]" lines as $1). Lines "info ..." are CPU specific and ignored.
# Usage: compare.sh <x86 log>      Prints the differing lines; exit 1 if any.
set -euo pipefail
cd "$(dirname "$0")/../../.."
REAL="${REAL:-utm@192.168.64.7}"
CC=$(ls "${ANDROID_HOME:-$HOME/Library/Android/sdk}"/ndk/*/toolchains/llvm/prebuilt/*/bin/aarch64-linux-android26-clang | tail -1)
W=$(mktemp -d); trap 'rm -rf "$W"' EXIT
"$CC" -O2 -w -DACEDIFF_MAIN -static tests/apps/hello-acediff/*.c -o "$W/acediff" -lm
D=$(ssh "$REAL" mktemp -d)
scp -q "$W/acediff" "$REAL:$D/acediff"; ssh "$REAL" "$D/acediff; rm -rf $D" | grep -v '^info' > "$W/real.txt"
sed -n 's/^.*\[acediff\] //p' "$1" | grep -v -e '^info' -e '^DONE' > "$W/x86.txt"
diff "$W/real.txt" "$W/x86.txt" && echo "acediff: identical ($(wc -l < "$W/real.txt") lines)"
