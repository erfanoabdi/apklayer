#!/bin/bash
# sysapps-update.sh: `apklayer setup` after a runtime update installs only the system APKs that changed (cli/setup.cpp, the record
# system-apps.sha256) and keeps the apps' data. Runtime A holds hello-payload v1 and hello-heap, runtime B the same hello-heap and hello-payload v2
# (one changed file): after B the CLI must install exactly one APK, hello.payload gets version 2 with its data file still there, hello-heap is not
# touched (its info.properties keeps its time). The runtime directories are planted (`current` switched as the unpacking does): the step only reads
# <runtime>/current/system-apps. Own data home and instance; the tree's installed CLI, a built-in runtime (inst/).
set -u
cd "$(dirname "$0")/../.."
APKS="${APKLY_APKS:-.tests/apks}"
CLI="$PWD/inst/lib/apklayer/cli/apklayer"
T="$(mktemp -d "$HOME/sysapps-update.XXXXXX")"
ID=sysupd
fails=0
cleanup() { env "${ENV[@]}" "$CLI" --instance $ID stop >/dev/null 2>&1; rm -rf "$T"; }
trap cleanup EXIT
mkdir -p "$T/root" "$T/state" "$T/data/apklayer/runtime/A/system-apps" "$T/data/apklayer/runtime/B/system-apps"
for e in * .[!.]*; do [ "$e" = versions ] || [ "$e" = tests ] || ln -s "$PWD/$e" "$T/root/$e" 2>/dev/null; done
ln -s "$PWD/tests" "$T/root/tests"
echo "runtime=0.0.1" > "$T/root/versions"  # an installed package names its runtime (cli/layout.cpp: not a source tree)
ENV=(XDG_DATA_HOME="$T/data" XDG_STATE_HOME="$T/state" APKLAYER_ROOT="$T/root" APKLAYER_BUILD="$PWD/inst/lib/apklayer"
     APKLAYER_NATIVE="$PWD/inst/lib/apklayer/native" APKLAYER_INSTANCE=$ID)
run() { env "${ENV[@]}" "$CLI" --instance $ID "$@" 2>&1 < /dev/null; }
check() { if [ "$2" = "$3" ]; then echo "ok   $1"; else echo "FAIL $1: expected '$2', got '$3'"; fails=$((fails + 1)); fi; }
A="$T/data/apklayer/runtime/A/system-apps" B="$T/data/apklayer/runtime/B/system-apps"
cp "$APKS/hello-payload.apk" "$A/payload.apk"; cp "$APKS/hello-heap.apk" "$A/heap.apk"
cp "$APKS/hello-payload2.apk" "$B/payload.apk"; cp "$APKS/hello-heap.apk" "$B/heap.apk"
ln -s A "$T/data/apklayer/runtime/current"

out="$(run setup)"
check "runtime A: both installed" "2 installed" "$(sed -n $'s/^step\tsystem-apps\tdone\t//p' <<<"$out")"
check "record lists both" "2" "$(wc -l < "$T/data/apklayer/system-apps.sha256")"
check "payload v1" "1" "$(run info hello.payload | sed -n $'s/^versionCode\t//p')"
data="$(run info hello.payload | sed -n $'s/^dataDir\t//p')"
mkdir -p "$data" && echo kept > "$data/user-file"
heap_time="$(stat -c %Y "$(run info hello.heap | sed -n $'s/^dir\t//p')/info.properties")"

out="$(run setup)"
check "unchanged runtime: nothing installed" "Installed" "$(sed -n $'s/^step\tsystem-apps\tdone\t//p' <<<"$out")"

# the runtime update: B holds one changed APK. `setup --check` says so, setup installs that one
ln -sfn B "$T/data/apklayer/runtime/current"
out="$(run setup --check)"
check "check reports the change" "todo" "$(sed -n $'s/^step\tsystem-apps\t\\([a-z]*\\)\t.*/\\1/p' <<<"$out")"
out="$(run setup)"
check "runtime B: exactly one installed" "1 installed" "$(sed -n $'s/^step\tsystem-apps\tdone\t//p' <<<"$out")"
grep -c "Installing" <<<"$out" | { read -r n; check "one Installing line" 1 "$n"; }
grep -q "Installing payload.apk" <<<"$out" && echo "ok   it is the changed one" || { echo "FAIL not payload.apk"; fails=$((fails + 1)); }
check "payload v2" "2" "$(run info hello.payload | sed -n $'s/^versionCode\t//p')"
check "its data kept" "kept" "$(cat "$data/user-file" 2>/dev/null)"
check "unchanged app untouched" "$heap_time" "$(stat -c %Y "$(run info hello.heap | sed -n $'s/^dir\t//p')/info.properties")"
out="$(run setup --check)"
check "check: current again" "done" "$(sed -n $'s/^step\tsystem-apps\t\\([a-z]*\\)\t.*/\\1/p' <<<"$out")"
if [ "$fails" = 0 ]; then echo "PASS sysapps-update"; else echo "FAIL sysapps-update: $fails"; exit 1; fi
