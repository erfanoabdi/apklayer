#!/bin/bash
# phone-test.sh <ut|sfos> <group>...: the quick checks there are for the owner's phones (docs/HALIUM.md "The phone route"), run from the Mac over ssh. ut is the
# Ubuntu Touch X23 (UBPORTS in tests/vm/local.conf, key auth), sfos the Sailfish Quintus (SAILFISH). Copies tools/halium's helpers, spare-race.py and the hello
# apps (the apks of $APKLAYER_APKS, default the repo's build/; tests/apps/build.sh builds them) into a scratch directory, runs phone-checks.sh there in an
# instance of its own (ptest), prints PASS/FAIL per group, removes what it made (scratch, instance) and, on ut, locks the phone again when it woke it.
# Groups: hello newpipe providers hidl camera sensors race (both), rotate (sfos). ut: the screen is woken and the PIN typed (ut-unlock.sh; apps are stopped while the
# phone is locked). sfos: unlock the phone first: `sed -n 's/^SAILFISH_LOCK=//p' tests/vm/local.conf | ssh root@<phone> 'sh sf-unlock.sh <dir>'` (sf-unlock.sh, touch.py in <dir>).
set -u
os=${1:-}; shift || true
[ -n "$os" ] && [ $# -gt 0 ] || { sed -n '2,8p' "$0" >&2; exit 2; }
repo=$(cd "$(dirname "$0")/../.." && pwd)
conf=$repo/tests/vm/local.conf
get() { sed -n "s/^$1=//p" "$conf" | head -1; }
case $os in
    ut)   host=$(get UBPORTS);  A=/opt/click.ubuntu.com/apklayer.erfanoabdi/current/usr/bin/apklayer; R=/home/${host%@*}/apklayer-phone-test ;;
    sfos) host=$(get SAILFISH); A=/usr/bin/apklayer; R=/userdata/apklayer/phone-test-${host%@*} ;;  # the Quintus' home is on a small partition
    *) echo "phone-test: ut or sfos" >&2; exit 2 ;;
esac
[ -n "$host" ] || { echo "phone-test: no phone in $conf" >&2; exit 2; }
apks=${APKLAYER_APKS:-$repo/build}
ssh() { command ssh -o BatchMode=yes -o ConnectTimeout=10 "$host" "$@"; }
h=$repo/tools/halium
ssh "mkdir -p $R/apks" || exit 2
scp -q $h/phone-checks.sh $h/ut-shot.sh $h/hidl-list.sh $h/ut-unlock.sh $h/ut-uikeys.py $h/ut-uitap.py $repo/tests/vm/spare-race.py "$host:$R/"
for app in hello-input hello-camera hello-hardware; do [ -e "$apks/$app.apk" ] && scp -q "$apks/$app.apk" "$host:$R/apks/"; done
woke=0
if [ $os = ut ]; then
    out=$(get UBPORTS_PASSWORD | ssh "bash $R/ut-unlock.sh on $R"); echo "ut-unlock: $out"
    [ "$out" = unlocked ] && woke=1
fi
ssh "A=$A R=$R bash $R/phone-checks.sh $os $*"; rc=$?
[ $woke = 1 ] && echo "ut-lock: $(ssh "bash $R/ut-unlock.sh off $R")"
ssh "APKLAYER_INSTANCE=ptest $A stop >/dev/null 2>&1; APKLAYER_INSTANCE=ptest $A instance remove ptest >/dev/null 2>&1; pkill -f '[-]-instance ptest ' >/dev/null 2>&1; rm -rf $R"  # a delayed app-spare outlives stop and blocks the next race run
echo "phone-test $os: $([ $rc = 0 ] && echo PASS || echo FAIL)"
exit $rc
