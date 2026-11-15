#!/bin/bash
# setup-runtime-foreign.sh: tools/release/setup-runtime.sh run by another build must refuse a runtime that links into a still-existing half and leave its links alone.
set -u
t=$(mktemp -d /tmp/apklayer-foreign.XXXXXX); trap 'rm -rf "$t"' EXIT
mkdir -p "$t/ownerhalf" "$t/devhalf" "$t/rt"
touch "$t/ownerhalf/native-runtime.jar" "$t/devhalf/native-runtime.jar" "$t/rt/runtime.json"
ln -s "$t/ownerhalf/native-runtime.jar" "$t/rt/native-runtime.jar"
sh tools/release/setup-runtime.sh "$t/rt" "$t/devhalf" 2>"$t/err"; rc=$?
[ $rc -ne 0 ] && grep -q 'not relinking' "$t/err" || { echo "FAIL: foreign runtime not refused (rc=$rc)"; cat "$t/err"; exit 1; }
[ "$(readlink "$t/rt/native-runtime.jar")" = "$t/ownerhalf/native-runtime.jar" ] || { echo "FAIL: link changed"; exit 1; }
# the owner's half gone (dangling): not foreign any more, setup goes on (it stops later at the missing bundle files, but past the guard)
rm -rf "$t/ownerhalf"
sh tools/release/setup-runtime.sh "$t/rt" "$t/devhalf" 2>"$t/err"; grep -q 'not relinking' "$t/err" && { echo "FAIL: dangling owner refused"; exit 1; }
echo PASS
