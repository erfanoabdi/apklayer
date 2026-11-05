#!/bin/bash
# apklayer-manager's first run and update banner through its own UI (the screenshot mode's scripted input), against local fixtures (file://
# URLs: releases.json, runtime bundles whose system-apps/ holds the test APK, an F-Droid index) in a private data home, in the instance
# $APKLAYER_INSTANCE (never the default one). Each step ends on its own event (the work's closing refresh); the files then show the result.
# Usage: manager/tests/ui-updater.sh <manager build dir> <app.apk> <package> <screenshot dir> [<store.apk> <its package>] (also run by ui.sh;
# the F-Droid fixture offers store.apk, else app.apk again)
set -euo pipefail
b=$1 apk=$2 pkg=$3 shots=$4 store=${5:-$2} storepkg=${6:-}
[ -n "${APKLAYER_INSTANCE:-}" ] && [ "$APKLAYER_INSTANCE" != default ] || { echo "run in an instance of your own (APKLAYER_INSTANCE)" >&2; exit 2; }
cli=${APKLAYER_CLI:-"$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)/bin/apklayer"}  # APKLAYER_CLI: an installed package's (the manager's too)
m="$b/apklayer-manager"
apk=$(realpath "$apk")
mkdir -p "$shots"
fail() { echo "FAIL $*"; exit 1; }
"$cli" stop >/dev/null 2>&1 || true  # the instance's stack moves to the private data home below
f=$(mktemp -d "$HOME/.ui-updater.XXXXXX")
trap '"$cli" stop >/dev/null 2>&1 || true; rm -rf "$f"' EXIT
export XDG_DATA_HOME=$f/data XDG_CACHE_HOME=$f/cache APKLAYER_RELEASES_URL=file://$f/releases.json APKLAYER_FDROID_INDEX=file://$f/fdroid.json
rt=$XDG_DATA_HOME/apklayer/runtime info=$XDG_DATA_HOME/apklayer/apps/$pkg/info.properties
version=$("$cli" --version | awk -F'\t' '$1 == "version" { print $2 }') cpu=$(dpkg --print-architecture)
bundle() {  # runtime <version> -> rt-<version>.tar.zst
    local d=$f/src-$1/apklayer-runtime-$cpu-$1
    mkdir -p "$d/system-apps" && cp "$apk" "$d/system-apps/" && echo "{\"version\": \"$1\"}" > "$d/runtime.json"
    tar --zstd -cf "$f/rt-$1.tar.zst" -C "$f/src-$1" .
}
releases() {  # releases.json naming runtime <version> with <sha256> (this apklayer's own version: no deb update)
    printf '{"latest": "%s", "releases": [{"version": "%s", "date": "2026-11-04", "notes_url": "https://example.org/notes", "converter_abi": 1, "data_format": 1,
      "runtime": {"version": "%s", "assets": {"%s": {"url": "file://%s/rt-%s.tar.zst", "sha256": "%s"}}}, "debs": {}}]}\n' \
        "$version" "$version" "$1" "$cpu" "$f" "$1" "$2" > "$f/releases.json"
}
sha() { sha256sum "$1" | cut -c1-64; }
for v in r1 r2 r3; do bundle $v; done
cp "$store" "$f/client.apk"
printf '{"repo": {"address": "file://%s"}, "apps": [{"packageName": "org.fdroid.fdroid", "suggestedVersionCode": "1"}],
  "packages": {"org.fdroid.fdroid": [{"versionCode": 1, "apkName": "client.apk", "hash": "%s", "hashType": "sha256"}]}}\n' "$f" "$(sha "$f/client.apk")" > "$f/fdroid.json"
releases r1 "$(sha "$f/rt-r1.tar.zst")"

# a data home with no apps and no setup: the first-run screen by itself
"$m" --screenshot "$shots/ui-6-first-run.png" --size 400x840 --expect "Welcome to apklayer"
"$m" --screenshot "$shots/ui-6b-first-run.png" --size 1100x760 --expect "Welcome to apklayer"
echo "ok   a fresh data home opens on the first-run screen"
"$m" --screenshot "$shots/ui-7-first-run-done.png" --size 400x840 --click F-Droid --click "Set up" --wait-refresh 1
[ "$(readlink "$rt/current")" = r1 ] && [ -e "$rt/r1/runtime.json" ] || fail "the first run did not unpack runtime r1"
"$cli" list --tsv | awk -F'\t' -v p=$pkg '$1 == p && $7 == 1 { found = 1 } END { exit !found }' || fail "the runtime's system app $pkg is not installed as a system app"
[ -z "$storepkg" ] || "$cli" list --tsv | grep -q "^$storepkg	" || fail "the F-Droid fixture's $storepkg is not installed"
echo "ok   first run: runtime downloaded and unpacked, the system apps installed (--system), the F-Droid client from its index"
"$m" --screenshot "$shots/ui-7b-first-run-skip.png" --size 400x840 --first-run --click Skip --wait-refresh 1
[ -e "$XDG_DATA_HOME/apklayer/setup-done" ] || fail "finishing the first run left no mark"
echo "ok   the first run's end is remembered"

# the add-ons on the Apps page (the filters off: the section on screen): F-Droid removed and installed again by the first run's path
if [ "$storepkg" = org.fdroid.fdroid ]; then
    addons() { "$m" --screenshot "$shots/$1.png" --size 400x840 --click Installed --click System "${@:2}" --wait-refresh 1; }
    has() { "$cli" list --tsv | grep -q "^org.fdroid.fdroid	"; }
    addons ui-11-addon-remove --click "Remove F-Droid" --click Remove
    ! has || fail "Remove F-Droid left org.fdroid.fdroid installed"
    addons ui-11b-addon-install --click "Install F-Droid"
    has || fail "Install F-Droid did not install org.fdroid.fdroid"
    "$m" --screenshot "$shots/ui-11c-addons.png" --size 400x840 --click Installed --click System --expect "Update F-Droid" --expect "Install microG"
    echo "ok   add-ons: F-Droid removed and installed again from the Apps page"
fi

# a newer runtime: the banner, its update, and a bundle with a wrong SHA256 refused
releases r2 "$(sha "$f/rt-r2.tar.zst")"
"$m" --screenshot "$shots/ui-8-update.png" --size 1100x760 --expect "Android runtime r2 is available"
"$m" --screenshot "$shots/ui-8b-update.png" --size 400x840 --click Update --wait-refresh 1
[ "$(readlink "$rt/current")" = r2 ] && [ -d "$rt/r1" ] || fail "the update did not switch to runtime r2 keeping r1"
echo "ok   update banner: runtime r2 downloaded, current -> r2, r1 kept"
releases r3 0000000000000000000000000000000000000000000000000000000000000000
"$m" --screenshot "$shots/ui-9-bad-hash.png" --size 1100x760 --click Update --wait-refresh 1
[ "$(readlink "$rt/current")" = r2 ] && [ ! -e "$rt/r3" ] || fail "a bundle with a wrong SHA256 was used"
echo "ok   a runtime bundle with a wrong SHA256 is refused, current stays r2"

# an app converted by an older converter: the banner converts it again
releases r2 "$(sha "$f/rt-r2.tar.zst")"
sed -i 's/^converterAbi=.*/converterAbi=0/' "$info" && echo older-converter > "$(dirname "$info")/key"
"$m" --screenshot "$shots/ui-10-stale.png" --size 400x840 --expect "1 app needs converting again"
"$m" --screenshot "$shots/ui-10b-stale-done.png" --size 400x840 --click "Convert again" --wait-refresh 1
grep -qx "converterAbi=$("$cli" --version | awk -F'\t' '$1 == "converterAbi" { print $2 }')" "$info" || fail "the stale app was not converted again"
echo "ok   stale apps banner: converted again"
echo "PASS manager updater UI"
