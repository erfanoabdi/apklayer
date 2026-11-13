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
# `apklayer exec` without a stack: the tool sees a device-shaped store (apklayer.prop, the tree's build.prop, derived ones), as a stack's tools do
unset APKLAYER_PROPERTIES
bin/apklayer stop >/dev/null 2>&1
bin/apklayer exec libapklayer-no-such-tool.so >/dev/null 2>&1 || true  # the tool need not run: its store is the check
store="$XDG_RUNTIME_DIR/apklayer/${APKLAYER_INSTANCE:-default}/properties"
model=$(APKLAYER_PROPERTIES="$store" "$work/aprop" get ro.product.model) fingerprint=$(APKLAYER_PROPERTIES="$store" "$work/aprop" get ro.build.fingerprint)
echo "exec without a stack: model '$model', fingerprint '$fingerprint'"
test "$model" = "Pixel 5" && [[ $fingerprint == google/redfin/redfin:* ]]
echo 'PASS cross-process read-only, mutable and independent instance properties'
