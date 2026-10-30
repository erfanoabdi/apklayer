#!/usr/bin/env bash
# fetch-deps: populate a deps directory from deps/deps.lock (PLAN D18).
# Usage: fetch-deps.sh [dest]          (default: ~/.cache/apklayer/deps)
# Resolution order per entry, every result sha256-verified:
#   1. already in dest
#   2. local: dirs in $APKLAYER_DEPS_LOCAL (searched by file name), ~/.m2, the Android SDK
#   3. Maven Central ($APKLAYER_MAVEN), only for maven:/aar-classes: sources
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LOCK="$ROOT/deps/deps.lock"
DEST="${1:-$HOME/.cache/apklayer/deps}"
MAVEN="${APKLAYER_MAVEN:-https://repo1.maven.org/maven2}"
M2="$HOME/.m2/repository"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
[ -n "$SDK" ] || for d in "$HOME/Library/Android/sdk" "$HOME/Android/Sdk"; do [ -d "$d" ] && SDK="$d" && break; done

sha() { if command -v sha256sum >/dev/null; then sha256sum "$1"; else shasum -a 256 "$1"; fi | cut -c1-64; }

# Echo the path of a local file named $1 (from $APKLAYER_DEPS_LOCAL dirs), or nothing.
local_by_name() {
    for d in ${APKLAYER_DEPS_LOCAL:-}; do
        [ -d "$d" ] && find "$d" -name "$1" -type f 2>/dev/null | head -1 | grep . && return
    done
    return 0
}

# Copy maven artifact g:a:v:ext to $2 from ~/.m2 or Maven Central.
get_maven() {
    IFS=: read -r g a v ext <<<"$1"
    local rel="${g//.//}/$a/$v/$a-$v.$ext"
    if [ -f "$M2/$rel" ]; then cp "$M2/$rel" "$2"; echo "    from ~/.m2"; return; fi
    echo "    downloading $MAVEN/$rel"
    curl -fsSL -o "$2" "$MAVEN/$rel" && return
    echo "    not on Maven Central: Google's Maven"  # androidx.test.* and other Google artifacts
    curl -fsSL -o "$2" "https://maven.google.com/$rel"
}

fetched=0 local=0 ok=0
while read -r hash dest src; do
    case "$hash" in ''|'#'*) continue ;; esac
    out="$DEST/$dest"
    if [ -f "$out" ] && [ "$(sha "$out")" = "$hash" ]; then ok=$((ok + 1)); continue; fi
    mkdir -p "$(dirname "$out")"
    echo "  $dest"
    tmp="$out.part"
    found="$(local_by_name "$(basename "$dest")")"
    if [ -n "$found" ] && [ "$(sha "$found")" = "$hash" ]; then
        cp "$found" "$tmp"; echo "    from $found"; local=$((local + 1))
    else
        case "$src" in
            maven:*) get_maven "${src#maven:}" "$tmp"; fetched=$((fetched + 1)) ;;
            aar-classes:*)
                get_maven "${src#aar-classes:}:aar" "$tmp.aar"
                unzip -p "$tmp.aar" classes.jar > "$tmp"; rm -f "$tmp.aar"; fetched=$((fetched + 1)) ;;
            sdk:*)
                [ -n "$SDK" ] && [ -f "$SDK/${src#sdk:}" ] || { echo "    missing: $SDK/${src#sdk:} (set ANDROID_HOME or APKLAYER_DEPS_LOCAL)"; exit 1; }
                cp "$SDK/${src#sdk:}" "$tmp"; echo "    from the Android SDK"; local=$((local + 1)) ;;
            *) echo "    unknown source: $src"; exit 1 ;;
        esac
    fi
    got="$(sha "$tmp")"
    if [ "$got" != "$hash" ]; then echo "    sha256 mismatch: got $got want $hash"; rm -f "$tmp"; exit 1; fi
    mv "$tmp" "$out"
done < "$LOCK"
echo "deps: $DEST — $ok already present, $local copied locally, $fetched resolved via maven"
