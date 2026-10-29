#!/bin/sh
# Puts the files that were split for GitHub (name.partNNN, 45 MB each) back together: sh join-parts.sh [dir]  (default: this directory), then checks SHA256SUMS-split.
set -eu
cd "${1:-$(dirname "$0")}"
for first in $(find . -name '*.part000' | sort); do
    base="${first%.part000}"
    cat "$base".part??? > "$base"
    rm -f "$base".part???
    echo "joined $base"
done
shasum -a 256 -c SHA256SUMS-split
