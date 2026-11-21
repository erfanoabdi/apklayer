#!/bin/sh
# import.sh [zip dir]: fills <cpu>/system/ of this branch from the LiteGapps zips in `sources` (downloaded and sha256-checked; a zip already in
# [zip dir] under its file name is used as it is, after the same check). Kept: the APKs of priv-app/ and app/ (their native libraries are inside the
# APKs, which Google signed: they stay whole) and the XMLs of etc/{permissions,sysconfig,default-permissions}, without the Pixel feature lists
# (pixel_experience_*) and the dialer support library's XML (its jar is not kept) -- what main's tools/release/gapps.sh uses.
# Files over 50 MB are split into 45 MB parts (GitHub's 100 MB file limit); SHA256SUMS lists every whole file (join-parts.sh checks it).
set -eu
here="$(cd "$(dirname "$0")" && pwd)"
zips="${1:-}"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
grep -v '^#' "$here/sources" | while read -r cpu url sha; do
    [ -n "$cpu" ] || continue
    name="$(basename "${url%/download}")"
    zip="$work/$name"
    if [ -n "$zips" ] && [ -f "$zips/$name" ]; then zip="$zips/$name"; else curl -fsSL --retry 3 -o "$zip" "$url"; fi
    echo "$sha  $zip" | sha256sum -c - >/dev/null || { echo "import.sh: $name: sha256 mismatch" >&2; exit 1; }
    rm -rf "$work/x" "$here/$cpu" && mkdir -p "$work/x" "$here/$cpu"
    unzip -p "$zip" files/files.tar.xz | tar -xJf - -C "$work/x"
    sys="$(find "$work/x" -mindepth 3 -maxdepth 3 -type d -name system | head -1)"
    [ -n "$sys" ] || { echo "import.sh: $name has no <cpu>/<sdk>/system tree" >&2; exit 1; }
    (cd "$sys/.." && find system -path '*app/*' -name '*.apk' ! -name '*-splitconfig-*'
     find system -path '*/etc/permissions/*.xml' -o -path '*/etc/sysconfig/*.xml' -o -path '*/etc/default-permissions/*.xml' \
        | grep -v -e '/pixel_experience_' -e '/com.google.android.dialer.support.xml$') | sort > "$work/list"
    (cd "$sys/.." && tar -cf - -T "$work/list") | tar -xf - -C "$here/$cpu"
    echo "$cpu: $name"
done
cd "$here"
find arm64 x86_64 -type f ! -name '*.part[0-9][0-9][0-9]' | sort | xargs sha256sum > SHA256SUMS
find arm64 x86_64 -type f -size +50M ! -name '*.part[0-9][0-9][0-9]' | while read -r f; do
    split -b 45M -d -a 3 "$f" "$f.part" && rm "$f"
done
