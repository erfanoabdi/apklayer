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
# an x86_64 home needs these folders too (the x86 PC's lead home had neither: no system apps, no gfxstream); a note only
[ "$(uname -m)" != x86_64 ] || for d in x86_64/system-apps common/gfxstream; do [ -d "$p/$d" ] || echo "check-prebuilts: note: $p has no $d (copy it from the prebuilts branch)" >&2; done
# the lock's libraries live in <cpu>/android-libs/lib64: a prebuilts copy without that folder (only common/ copied) is not a stale one
case $(uname -m) in x86_64) cpu=x86_64 ;; *) cpu=arm64 ;; esac
[ -d "$p/$cpu/android-libs/lib64" ] || { echo "check-prebuilts: $p has no $cpu/android-libs/lib64: copy the prebuilts branch's $cpu/ folder there too (not only common/)" >&2; exit 1; }
list=$(mktemp); trap 'rm -f "$list"' EXIT
awk '!/^#/ && NF >= 3 { f = $3; sub(/^android-arm64\/lib64\//, "arm64/android-libs/lib64/", f); if (f ~ /^(x86_64|arm64)\/android-libs\/lib64\//) print $1, f }' "$lock" |
    while read -r sum f; do [ -f "$p/$f" ] && printf '%s  %s\n' "$sum" "$f"; done > "$list" || true
[ -s "$list" ] || { echo "check-prebuilts: no locked library found under $p" >&2; exit 1; }
# a staged library the lock does not name is never compared (libvulkan, libapklayer_vulkan, vulkan.apklayer were once missing): a note
awk '{ print $2 }' "$list" | LC_ALL=C sort > "$list.l"; (cd "$p" && ls -d "$cpu"/android-libs/lib64/*.so | LC_ALL=C sort) | LC_ALL=C comm -13 "$list.l" - | sed 's/^/check-prebuilts: note: not in the lock, unchecked: /' >&2; rm -f "$list.l"
stale=$(cd "$p" && sha256sum -c --quiet "$list" 2>/dev/null | sed -n 's/: FAILED$//p') || true
[ -z "$stale" ] && exit 0
echo "check-prebuilts: $p is behind $(basename "$lock") (copy these from the prebuilts branch):" >&2
echo "$stale" | sed 's/^/  /' >&2
exit 1
