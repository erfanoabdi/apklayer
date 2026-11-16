#!/usr/bin/env bash
# Heavy tier differential: neondiff.c's per-case hashes on real arm64 ($REAL, default utm@192.168.64.7, key auth) against the "[nd]" lines of a hello.neondiff run under Berberis
# (pass the log: run the app once per mode with `apklayer prop set debug.neondiff "n=3000"` plus berberis.mode=interpret-only | lite-translate-or-interpret, or
# berberis.gearup_policy=legacy + berberis.gearup_min_insns=0 (every hot region heavy); bin/apklayer run build/hello-neondiff.apk). Prints the cases whose hash differs; exit 1 if any.
# Heavy bug = differs between the forced-heavy and the lite log. Lines differing from real hardware in all modes are the translators' known gaps (FP exception flags, NaN rules...).
set -euo pipefail
cd "$(dirname "$0")/../../.."
REAL="${REAL:-utm@192.168.64.7}"
CC=$(ls "${ANDROID_HOME:-$HOME/Library/Android/sdk}"/ndk/*/toolchains/llvm/prebuilt/*/bin/aarch64-linux-android26-clang | tail -1)
W=$(mktemp -d); trap 'rm -rf "$W"' EXIT
python3 tests/apps/hello-neondiff/gen.py > tests/apps/hello-neondiff/cases.inc
"$CC" -O2 -w -DNEONDIFF_MAIN -static tests/apps/hello-neondiff/neondiff.c tests/apps/hello-neondiff/kernels.c -I tests/apps/hello-neondiff -o "$W/nd" -lm
D=$(ssh "$REAL" mktemp -d); scp -q "$W/nd" "$REAL:$D/nd"; ssh "$REAL" "cd $D && ./nd n=${N:-3000} 2>&1 >/dev/null; rm -rf $D" | grep '^\[nd\]' | grep -v DONE | sort > "$W/real.txt"
sed -n 's/^.*\(\[nd\] .*\)$/\1/p' "$1" | grep -v DONE | sort > "$W/x86.txt"
diff "$W/real.txt" "$W/x86.txt" && echo "neondiff: identical ($(wc -l < "$W/real.txt") cases)"
