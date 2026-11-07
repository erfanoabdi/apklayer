#!/bin/bash
# The native runtime's shared ICU copy (<state>/tmp/robolectric-nativeruntime-assets/icu-<key>) is keyed by the framework jar's content:
# an app start after the jar was rewritten with the same content (a new modification time: every meson install of a tree) reuses it.
# Run on the VM: `tests/vm/asset-cache.sh <package>`; honours APKLAYER_INSTANCE, APKLAYER_NATIVE, XDG_* like the CLI.
cd "$(dirname "$0")/../.."
pkg="$1"
state="${XDG_STATE_HOME:-$HOME/.local/state}/apklayer/${APKLAYER_INSTANCE:-default}"
cache="$state/tmp/robolectric-nativeruntime-assets"
jars=$(readlink -f "$APKLAYER_NATIVE"/aosp/android-all*/android-all-16-*.jar)  # the plain and the instrumented framework
export APKLAYER_JOPTS="-Dapklayer.runms=3000 $APKLAYER_EXTRA_JOPTS"
bin/apklayer stop
bin/apklayer run "$pkg" > /dev/null 2>&1
before=$(ls -d "$cache"/icu-*/ 2>/dev/null | wc -l)
for j in $jars; do touch -r "$j" "$j.mtime"; touch "$j"; done  # same content, a new modification time
bin/apklayer stop  # a fresh stack and spare: the next start loads the native runtime again
bin/apklayer run "$pkg" > /dev/null 2>&1
for j in $jars; do touch -r "$j.mtime" "$j"; rm -f "$j.mtime"; done
bin/apklayer stop
after=$(ls -d "$cache"/icu-*/ 2>/dev/null | wc -l)
echo "ICU copies: $before before, $after after the jar's new modification time"
[ "$before" -ge 1 ] && [ "$after" = "$before" ] || { echo "FAIL asset-cache: a new ICU copy for the same jar content"; exit 1; }
echo "PASS asset-cache: one ICU copy per framework content"
