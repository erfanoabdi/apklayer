#!/bin/sh
# pack.sh <out dir>: <out>/apklayer-gapps-<cpu>.zip per CPU (its system/ tree, the layout main's tools/release/gapps.sh reads) and <out>/SHA256SUMS.
# The zips are reproducible (sorted entries, no extra fields, fixed times and modes, stored: APKs do not compress), so their sha256 can be pinned
# in main's data/downloads.json before the release exists.
set -eu
[ $# -eq 1 ] || { sed -n '2,4p' "$0" >&2; exit 2; }
here="$(cd "$(dirname "$0")" && pwd)"
mkdir -p "$1" && out="$(cd "$1" && pwd)"
tree="$(mktemp -d)"
trap 'rm -rf "$tree"' EXIT
cp -r "$here/arm64" "$here/x86_64" "$here/SHA256SUMS" "$tree/"
sh "$here/join-parts.sh" "$tree" >/dev/null
rm -f "$out"/apklayer-gapps-*.zip "$out/SHA256SUMS"
for cpu in arm64 x86_64; do
    (cd "$tree/$cpu"
     find system -type d -exec chmod 755 {} + && find system -type f -exec chmod 644 {} +
     find system -exec touch -h -d '2000-01-01 00:00:00 UTC' {} +
     find system -type f | LC_ALL=C sort | TZ=UTC zip -q -X -D -0 "$out/apklayer-gapps-$cpu.zip" -@)
done
(cd "$out" && sha256sum apklayer-gapps-*.zip > SHA256SUMS && cat SHA256SUMS)
