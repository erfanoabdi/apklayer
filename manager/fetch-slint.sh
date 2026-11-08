#!/bin/sh
# fetch-slint: the prebuilt Slint C++ library, its compiler and the Material 3 component set, sha256-pinned (no Rust toolchain needed).
# Usage: manager/fetch-slint.sh [dest]   (default: ~/.cache/apklayer/slint). Then: meson setup manager/b manager -Dslint=<dest>
set -eu
DEST="${1:-$HOME/.cache/apklayer/slint}"
V=1.18.1 M=1.1.0
case "$(uname -m)" in
    aarch64) cpp=Slint-cpp-$V-Linux-arm64 cpp_sha=810cfed786c3f63aea9c1b73371a02ed591129d18e077094afa9e024e2d714fd
             comp=slint-compiler-Linux-aarch64 comp_sha=6d704012132ec8c8fadddefb86ba192cd83e09048edbcf61aee457aca300c78c ;;
    x86_64)  cpp=Slint-cpp-$V-Linux-x86_64 cpp_sha=f0b24ea601b80afe241677d1179bf5215dceae529f2b275b678a0af9a144b255
             comp=slint-compiler-Linux-x86_64 comp_sha=543091e6102a02db3403aa77c774a11d9f383b31cb03e25cddad63269a1fb779 ;;
    *) echo "fetch-slint: no pinned hashes for $(uname -m) yet: add them from https://github.com/slint-ui/slint/releases/tag/v$V" >&2; exit 1 ;;
esac
mkdir -p "$DEST"
cd "$DEST"
get() {  # url file sha256
    # python, not curl (not on every host); material.slint.dev refuses Python's default user agent
    # retried with backoff: GitHub's release downloads answer 504 now and then (two release runs failed on it)
    [ -f "$2" ] || { python3 -c '
import shutil, sys, time, urllib.error, urllib.request as u
for attempt in range(6):
    try:
        with u.urlopen(u.Request(sys.argv[1], headers={"User-Agent": "curl/8"}), timeout=120) as r, open(sys.argv[2], "wb") as out: shutil.copyfileobj(r, out)
        break
    except (urllib.error.URLError, OSError) as e:
        if getattr(e, "code", 500) < 500 and getattr(e, "code", None) != 429 or attempt == 5: raise
        print("fetch-slint: %s: %s, again" % (sys.argv[1], e), file=sys.stderr); time.sleep(5 * 2 ** attempt)
' "$1" "$2.part" && mv "$2.part" "$2"; }
    echo "$3  $2" | sha256sum -c --quiet || { rm -f "$2"; echo "fetch-slint: $2 does not match its pinned hash" >&2; exit 1; }
}
get "https://github.com/slint-ui/slint/releases/download/v$V/$cpp.tar.gz" "$cpp.tar.gz" "$cpp_sha"
get "https://github.com/slint-ui/slint/releases/download/v$V/$comp.tar.gz" "$comp.tar.gz" "$comp_sha"
get "https://material.slint.dev/zip/material-$M.zip" "material-$M.zip" 83c57b6f1b8799ddacd94b7e8b140f3f2112c36407b6df31883da0ba4e2dd425
rm -rf cpp bin material "material-$M" && mkdir -p cpp bin
tar xzf "$cpp.tar.gz" -C cpp --strip-components=1
tar xzf "$comp.tar.gz" -C bin
python3 -c 'import sys, zipfile; zipfile.ZipFile(sys.argv[1]).extractall(".")' "material-$M.zip" && mv "material-$M" material
echo "fetch-slint: Slint $V and Material $M in $DEST"
