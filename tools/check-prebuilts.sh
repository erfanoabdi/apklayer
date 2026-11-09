#!/bin/sh
# check-prebuilts.sh <prebuilts dir>: the prebuilts' Android libraries against deps/android-tree-files*.lock (the hashes main was built and tested
# with). A stale copy on a test host would otherwise be installed silently and the tests would run old native code (2026-11-09: ten libraries).
# Exit 1 with the stale files listed; files the lock names that the prebuilts lack are not checked (the arm64 lock comes from the VM's tree).
set -eu
p=$1 repo=$(cd "$(dirname "$0")/.." && pwd)
case $(uname -m) in
    x86_64) lock=$repo/deps/android-tree-files-x86_64.lock ;;
    *) lock=$repo/deps/android-tree-files.lock ;;  # its paths are the VM's (android-arm64/lib64), the prebuilts' arm64/android-libs/lib64
esac
list=$(mktemp); trap 'rm -f "$list"' EXIT
awk '!/^#/ && NF >= 3 { f = $3; sub(/^android-arm64\/lib64\//, "arm64/android-libs/lib64/", f); if (f ~ /^(x86_64|arm64)\/android-libs\/lib64\//) print $1, f }' "$lock" |
    while read -r sum f; do [ -f "$p/$f" ] && printf '%s  %s\n' "$sum" "$f"; done > "$list" || true
[ -s "$list" ] || { echo "check-prebuilts: no locked library found under $p" >&2; exit 1; }
stale=$(cd "$p" && sha256sum -c --quiet "$list" 2>/dev/null | sed -n 's/: FAILED$//p') || true
[ -z "$stale" ] && exit 0
echo "check-prebuilts: $p is behind $(basename "$lock") (copy these from the prebuilts branch):" >&2
echo "$stale" | sed 's/^/  /' >&2
exit 1
