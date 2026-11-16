#!/bin/sh
# `apklayer app <package> translation-mode`: the per-app translator mode (berberis.mode.<package>, binary_translation 0008) in the instance's overrides
set -eu
trap 'bin/apklayer prop unset berberis.mode.hello.arm64 >/dev/null 2>&1 || true' EXIT
a="bin/apklayer app hello.arm64 translation-mode"
$a | grep -qx 'translation-mode	default'
$a lite | grep -qx 'translation-mode	lite'
bin/apklayer prop list | grep -q '^berberis.mode.hello.arm64	lite-translate-or-interpret	instance'
$a | grep -qx 'translation-mode	lite'
$a interpret | grep -qx 'translation-mode	interpret'
$a default | grep -qx 'translation-mode	default'
! bin/apklayer prop list | grep -q '^berberis.mode.hello.arm64'
$a lite | grep -q 'next start'
if [ "$(uname -m)" = aarch64 ]; then $a | grep -q 'not translated'; fi
! $a fast >/dev/null 2>&1
! bin/apklayer app no.such.package translation-mode >/dev/null 2>&1
! bin/apklayer app com.example.aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa translation-mode >/dev/null 2>&1
echo 'PASS app translation-mode'
