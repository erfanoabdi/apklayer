#!/bin/bash
# sandbox.sh: the per-app sandbox (APKLAYER_SANDBOX=1, cli/sandbox.cpp). hello.sandbox runs as its own uid and sees neither another app's data nor the
# user's home; a file picked through apklayerd's picker (APKLAYER_PICKER) is readable, the one beside it is not; the hub's kernel caller uid is the
# app's uid; with `apklayer home-access` on it reads the home, still not the other app's data. Needs tools/host-setup/sandbox.sh once. On the VM, in the group's instance (tests/run sandbox).
: "${APKLY_STATE:=${XDG_STATE_HOME:-$HOME/.local/state}/apklayer/${APKLAYER_INSTANCE:-default}}"
export APKLAYER_SANDBOX=1
inst=${APKLAYER_INSTANCE:-default}; root=${XDG_DATA_HOME:-$HOME/.local/share}/apklayer/$([ "$inst" = default ] && echo 0 || echo "$inst")
out=$APKLY_STATE/sandbox.out home=$HOME/.apklayer-sandbox-test-$inst
other=$root/app-data/hello.other
mkdir -p "$other" "$home" && echo secret > "$other/secret.txt" && echo home > "$home/home.txt" && echo picked > "$home/picked.txt" && echo beside > "$home/notpicked.txt"
export APKLAYER_PICKER="echo $home/picked.txt"  # apklayerd's picker (it runs as the user); the app's own environment does not reach it
bin/apklayer install .tests/apks/hello-sandbox.apk 2>&1 | grep -aq installed || { echo "install hello-sandbox failed"; exit 1; }
bin/apklayer stop >/dev/null 2>&1; bin/apklayer home-access hello.sandbox off >/dev/null
jopts="-Dhello.other=$other -Dhello.home=$home/home.txt -Dhello.notpicked=$home/notpicked.txt"
APKLAYER_JOPTS="-Dapklayer.runms=20000 $jopts" bin/apklayer run hello.sandbox > "$out" 2>&1
grep -a "\[sandbox\]\|apklayer: " "$out"
uid=$(grep -a '^uid=' "${XDG_DATA_HOME:-$HOME/.local/share}/apklayer/apps/hello.sandbox/info.properties" | cut -d= -f2)
reg=$(grep -a "registered hello.sandbox " "$APKLY_STATE/hub.log" | tail -1); echo "hub: ${reg#*registered }"
# the opt-in: the whole home, still not the other apps' data
bin/apklayer home-access hello.sandbox on
APKLAYER_JOPTS="-Dapklayer.runms=15000 $jopts" bin/apklayer run hello.sandbox > "$out.home" 2>&1
grep -a "\[sandbox\] \(home\|other\)" "$out.home"
bin/apklayer stop >/dev/null 2>&1
bin/apklayer uninstall hello.sandbox >/dev/null 2>&1; left=$(ls -d "$root/app-data/hello.sandbox" 2>/dev/null)
rm -rf "$home" "$other"
ok=1
for want in "uid app=$uid os=$uid" "other denied" "home denied" "own read mine" "picked read picked" "notpicked denied"; do
    grep -aq "\[sandbox\] $want" "$out" || { echo "FAIL: no '[sandbox] $want'"; ok=0; }
done
grep -aq "\[sandbox\] home read home" "$out.home" && grep -aq "\[sandbox\] other denied" "$out.home" || { echo "FAIL: home-access on: not the home, or the other app's data too"; ok=0; }
echo "$reg" | grep -q " uid=$uid kernel-uid=$uid " || { echo "FAIL: the hub's caller uid is not $uid"; ok=0; }
[ -z "$left" ] || { echo "FAIL: uninstall left $left"; ok=0; }
[ $ok = 1 ] && echo "sandbox: PASS"
