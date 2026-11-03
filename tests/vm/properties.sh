#!/bin/bash
# Android write-once property semantics across separate host processes; an instance has an independent area.
# On the VM, in the group's instance (tests/run properties: APKLAYER_INSTANCE, APKLY_STATE = its logs, APKLAYER_NATIVE).
set -euo pipefail
: "${APKLAYER_NATIVE:=$HOME/arm64-native}" "${APKLY_STATE:=${XDG_STATE_HOME:-$HOME/.local/state}/apklayer/${APKLAYER_INSTANCE:-default}}"
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
