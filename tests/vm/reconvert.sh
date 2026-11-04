#!/bin/bash
# Converter ABI and data format (cli/version.h): an install records converterAbi; an app made stale (an older number and conversion key, as an
# install by an older converter leaves it) is listed stale and `reconvert --all-stale` converts it again from its kept base.apk, keeping its data
# and uid; an app that fails to convert again keeps its old install and does not stop the others; a data home of a newer format is refused.
# In a private data home holding only UiBench (the instance's own apps are neither read nor changed). On the VM, in the repo copy, in the
# instance's environment (tests/run reconvert).
set -euo pipefail
cli=bin/apklayer pkg=com.android.test.uibench
w=$(mktemp -d "$HOME/.reconvert.XXXXXX")
trap '$cli stop >/dev/null 2>&1 || true; rm -rf "$w"' EXIT
$cli stop >/dev/null 2>&1 || true  # a stack started with the instance's own data home would describe from its apps
export XDG_DATA_HOME=$w/home
apps=$XDG_DATA_HOME/apklayer/apps
fail() { echo "FAIL $*"; exit 1; }
ok() { echo "ok   $*"; }
stale() { $cli list --tsv | awk -F'\t' -v p=${1:-$pkg} '$1 == p { print $9 }'; }
info() { $cli info $pkg | awk -F'\t' -v k=$1 '$1 == k { print $2 }'; }

abi=$($cli --version | awk -F'\t' '$1 == "converterAbi" { print $2 }')
[ -n "$abi" ] || fail "apklayer --version names no converterAbi"
$cli install tests/uibench/uibench.apk >/dev/null
props=$apps/$pkg/info.properties
grep -qx "converterAbi=$abi" $props || fail "the install did not record converterAbi=$abi"
[ "$(stale)" = 0 ] || fail "a fresh install is listed stale"
ok "install records converterAbi=$abi, not stale"

data=$(info dataDir) uid=$(info uid)
mkdir -p "$data/files" && echo kept > "$data/files/reconvert-marker"
sed -i 's/^converterAbi=.*/converterAbi=0/' $props
echo older-converter > $apps/$pkg/key  # an older converter's cache key: no cached classes match, it converts again
# a second stale app whose kept APK no longer converts: it must keep its old install and not stop UiBench's
broken=aaa.broken
cp -a $apps/$pkg $apps/$broken && : > $apps/$broken/base.apk
sed -i "s/^package=.*/package=$broken/; s/^uid=.*/uid=19999/" $apps/$broken/info.properties
[ "$(stale)" = 1 ] && [ "$(stale $broken)" = 1 ] || fail "an app with an older converterAbi is not listed stale"
ok "an older converterAbi is listed stale"

if out=$($cli reconvert --all-stale 2>&1); then fail "reconvert --all-stale succeeded although $broken cannot convert: $out"; fi
grep -q "not converted again.* $broken" <<<"$out" || fail "reconvert did not name the failed app: $out"
grep -q "converting" <<<"$out" || fail "reconvert reused the old classes: $out"
grep -q "installed $pkg" <<<"$out" || fail "reconvert stopped at $broken instead of installing $pkg: $out"
grep -qx "converterAbi=$abi" $props && [ "$(stale)" = 0 ] || fail "after reconvert the app is still stale"
[ "$(cat "$data/files/reconvert-marker")" = kept ] && [ "$(info uid)" = "$uid" ] || fail "reconvert lost the app's data or uid"
ok "reconvert --all-stale converted it again from base.apk; data and uid kept"
[ -s $apps/$broken/classes.jar ] && grep -qx "package=$broken" $apps/$broken/info.properties && [ "$(stale $broken)" = 1 ] ||
    fail "the app that failed to convert again lost its old install"
ok "the app that failed keeps its old install, the failure did not stop the others"
rm -rf $apps/$broken
grep -q "no stale apps" <<<"$($cli reconvert --all-stale)" || fail "a second reconvert --all-stale found stale apps"
ok "nothing stale afterwards"

mkdir -p $w/new/apklayer && echo 99 > $w/new/apklayer/data-format
if msg=$(XDG_DATA_HOME=$w/new $cli list 2>&1); then fail "a data home of format 99 was used"; fi
grep -q "newer apklayer" <<<"$msg" || fail "no reason for refusing format 99: $msg"
XDG_DATA_HOME=$w/fresh $cli list >/dev/null && [ "$(cat $w/fresh/apklayer/data-format)" = "$($cli --version | awk -F'\t' '$1 == "dataFormat" { print $2 }')" ] ||
    fail "a fresh data home did not get the current format"
ok "data format: a newer one is refused, a fresh data home records the current one"
echo "PASS reconvert"
