#!/bin/bash
# Verify Android write-once semantics across separate host processes.
set -euo pipefail
cd "$(dirname "$0")/../.."
. tests/vm/config.sh
COPYFILE_DISABLE=1 tar cf - bridge/native/aprop.c bridge/native/properties.c |
    sshpass -p "$PASS" ssh "$VM" 'tar xf - -C ~/apklayer'
sshpass -p "$PASS" ssh "$VM" bash -s <<'REMOTE'
set -euo pipefail
cd ~/apklayer
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
cc -O2 -Wall -o "$work/aprop" bridge/native/aprop.c bridge/native/properties.c
export APKLAYER_PROPERTIES="$work/properties"
"$work/aprop" set ro.hyphen.data.dir /system/usr/hyphen-data
if "$work/aprop" set ro.hyphen.data.dir /tmp/other; then exit 1; fi
test "$("$work/aprop" get ro.hyphen.data.dir)" = /system/usr/hyphen-data
"$work/aprop" set test.mutable first
"$work/aprop" set test.mutable second
test "$("$work/aprop" get test.mutable)" = second
# A new instance has an independent area.
export APKLAYER_PROPERTIES="$work/other-instance"
"$work/aprop" set ro.hyphen.data.dir /other-instance
test "$("$work/aprop" get ro.hyphen.data.dir)" = /other-instance
echo 'PASS cross-process read-only, mutable and independent instance properties'
REMOTE
