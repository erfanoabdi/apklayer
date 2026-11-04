#!/bin/bash
# Converter ABI and data format (cli/version.h): an install records converterAbi; an app made stale (an older number and conversion key, as an
# install by an older converter leaves it) is listed stale and `reconvert --all-stale` converts it again from its kept base.apk, keeping its data
# and uid; a data home of a newer format is refused. On the VM, in the repo copy, in the instance's environment (tests/run reconvert).
set -euo pipefail
cli=bin/apklayer pkg=com.android.test.uibench
apps=${XDG_DATA_HOME:-$HOME/.local/share}/apklayer/apps
fail() { echo "FAIL $*"; exit 1; }
ok() { echo "ok   $*"; }
stale() { $cli list --tsv | awk -F'\t' -v p=$pkg '$1 == p { print $9 }'; }
info() { $cli info $pkg | awk -F'\t' -v k=$1 '$1 == k { print $2 }'; }

abi=$($cli --version | awk -F'\t' '$1 == "converterAbi" { print $2 }')
[ -n "$abi" ] || fail "apklayer --version names no converterAbi"
had=$([ -d $apps/$pkg ] && echo yes)  # the UiBench groups need it installed: left as it was found
$cli install tests/uibench/uibench.apk >/dev/null
props=$apps/$pkg/info.properties
grep -qx "converterAbi=$abi" $props || fail "the install did not record converterAbi=$abi"
[ "$(stale)" = 0 ] || fail "a fresh install is listed stale"
ok "install records converterAbi=$abi, not stale"

data=$(info dataDir) uid=$(info uid)
mkdir -p "$data/files" && echo kept > "$data/files/reconvert-marker"
sed -i 's/^converterAbi=.*/converterAbi=0/' $props
echo older-converter > $apps/$pkg/key  # an older converter's cache key: no cached classes match, it converts again
[ "$(stale)" = 1 ] || fail "an app with an older converterAbi is not listed stale"
ok "an older converterAbi is listed stale"

out=$($cli reconvert --all-stale)
grep -q "converting" <<<"$out" || fail "reconvert reused the old classes: $out"
grep -q "installed $pkg" <<<"$out" || fail "reconvert did not install $pkg: $out"
grep -qx "converterAbi=$abi" $props && [ "$(stale)" = 0 ] || fail "after reconvert the app is still stale"
[ "$(cat "$data/files/reconvert-marker")" = kept ] && [ "$(info uid)" = "$uid" ] || fail "reconvert lost the app's data or uid"
ok "reconvert --all-stale converted it again from base.apk; data and uid kept"
grep -q "no stale apps" <<<"$($cli reconvert --all-stale)" || fail "a second reconvert --all-stale found stale apps"
ok "nothing stale afterwards"

w=$(mktemp -d "$HOME/.reconvert.XXXXXX"); trap 'rm -rf "$w"' EXIT
mkdir -p $w/apklayer && echo 99 > $w/apklayer/data-format
if msg=$(XDG_DATA_HOME=$w $cli list 2>&1); then fail "a data home of format 99 was used"; fi
grep -q "newer apklayer" <<<"$msg" || fail "no reason for refusing format 99: $msg"
XDG_DATA_HOME=$w/fresh $cli list >/dev/null && [ "$(cat $w/fresh/apklayer/data-format)" = "$($cli --version | awk -F'\t' '$1 == "dataFormat" { print $2 }')" ] ||
    fail "a fresh data home did not get the current format"
ok "data format: a newer one is refused, a fresh data home records the current one"

[ -n "$had" ] || $cli uninstall $pkg >/dev/null
$cli stop >/dev/null
echo "PASS reconvert"
