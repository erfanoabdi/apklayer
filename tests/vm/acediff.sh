#!/bin/sh
# acediff: hello-acediff under the translator (x86_64 hosts) prints the same "[acediff]" lines as real arm64 (tests/apps/hello-acediff/real-arm64.txt; regenerate with compare.sh's build on real hardware).
# Prints the differing lines; fails if there are any (or no DONE line). Not for arm64 hosts: the app is then native and the file the same.
set -eu
out=$(mktemp)
trap 'rm -f "$out"; bin/apklayer stop >/dev/null 2>&1 || true' EXIT
APKLAYER_JOPTS=-Dapklayer.runms=60000 bin/apklayer run hello.acediff > "$out" 2>&1 || true
grep -q '\[acediff\] DONE' "$out"
sed -n 's/^.*\[acediff\] //p' "$out" | grep -v -e '^info' -e '^DONE' > "$out.x86"
# the differences still known, not fixed (known-diffs.txt: SHA512/SM4 are not in the emulated HWCAP; default NaN sign and FPCR.DN, fused FRECPS/FRSQRTS,
# NaN operands of fmax/fmin/fmls, kill(getpid()) timing, prctl, ID and PMU registers): a new difference fails the group
f=tests/apps/hello-acediff/known-diffs.txt
grep -v -f "$f" tests/apps/hello-acediff/real-arm64.txt > "$out.real"
grep -v -f "$f" "$out.x86" > "$out.x86f"
rc=0; diff "$out.real" "$out.x86f" || rc=1
rm -f "$out.x86" "$out.real" "$out.x86f"
[ "$rc" = 0 ] && echo "acediff: identical ($(grep -c -v -f "$f" tests/apps/hello-acediff/real-arm64.txt) lines, the known differences left out)"
exit $rc
