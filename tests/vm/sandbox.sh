#!/bin/bash
# sandbox.sh: the per-app sandbox (APKLAYER_SANDBOX=1, cli/sandbox.cpp). hello.sandbox runs as its own uid with Android's groups and sees neither another
# app's data, the user's home nor the system's part of /data (keystore2's database); a file picked through apklayerd's picker (APKLAYER_PICKER) is readable,
# the one beside it is not; the hub's kernel caller uid is the app's uid; a launch's intent file from the user's home reaches it; GeoClue answers it through
# apklayerd; with `apklayer home-access` on it reads the home, still not the other app's data. Needs tools/host-setup/sandbox.sh once. On the VM, in the
# group's instance (tests/run sandbox).
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
# a launch's intent file lies where its writer put it (here in the home): the sandbox hands it into the app's view
printf 'action=hello.SANDBOX\nextra.hello.extra=fromfile\ncomponent=hello.sandbox.Main\n' > "$home/intent.properties"
APKLAYER_INTENT_FILE=$home/intent.properties APKLAYER_JOPTS="-Dapklayer.runms=20000 $jopts" bin/apklayer run hello.sandbox > "$out" 2>&1
grep -a "\[sandbox\]\|apklayer: " "$out"
uid=$(grep -a '^uid=' "${XDG_DATA_HOME:-$HOME/.local/share}/apklayer/apps/hello.sandbox/info.properties" | cut -d= -f2)
reg=$(grep -a "registered hello.sandbox " "$APKLY_STATE/hub.log" | tail -1); echo "hub: ${reg#*registered }"
# GeoClue for a sandboxed process: through apklayerd, as the user (the buses know no app uid)
geo=$(bin/apklayer sandbox-exec hello.sandbox -- timeout 5 bin/apklayer portal helper geoclue.py 2>&1 | grep -a "^ready\|^unavailable\|refuses" | head -1); echo "geoclue: $geo"
consumed=1; [ -e "$home/intent.properties" ] && consumed=0
# the opt-in: the whole home, still not the other apps' data
bin/apklayer home-access hello.sandbox on
APKLAYER_JOPTS="-Dapklayer.runms=15000 $jopts" bin/apklayer run hello.sandbox > "$out.home" 2>&1
grep -a "\[sandbox\] \(home\|other\)" "$out.home"
bin/apklayer stop >/dev/null 2>&1
bin/apklayer uninstall hello.sandbox >/dev/null 2>&1; left=$(ls -d "$root/app-data/hello.sandbox" 2>/dev/null)
rm -rf "$home" "$other"
ok=1
# Android's groups (ProcessList): its own gid, cache and shared app gids, everybody (the kernel lists them in the order of their ids outside)
for want in "uid app=$uid os=$uid" "intent hello.SANDBOX extra=fromfile" "groups $uid $((uid + 10000)) $((uid + 40000)) 9997" "keystore denied" \
            "webview read webview" "other denied" "home denied" "own read mine" "picked read picked" "notpicked denied"; do
    grep -aq "\[sandbox\] $want" "$out" || { echo "FAIL: no '[sandbox] $want'"; ok=0; }
done
[ $consumed = 1 ] || { echo "FAIL: the intent file was not consumed"; ok=0; }
[ "$geo" = ready ] || [ ! -x /usr/libexec/geoclue ] || { echo "FAIL: no GeoClue in the sandbox ($geo)"; ok=0; }
grep -aq "\[sandbox\] home read home" "$out.home" && grep -aq "\[sandbox\] other denied" "$out.home" || { echo "FAIL: home-access on: not the home, or the other app's data too"; ok=0; }
echo "$reg" | grep -q " uid=$uid kernel-uid=$uid " || { echo "FAIL: the hub's caller uid is not $uid"; ok=0; }
[ -z "$left" ] || { echo "FAIL: uninstall left $left"; ok=0; }
[ $ok = 1 ] && echo "sandbox: PASS"
