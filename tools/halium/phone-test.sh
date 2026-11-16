#!/bin/bash
# phone-test.sh <ut|sfos> <group>...: the quick checks there are for the owner's phones (docs/HALIUM.md "The phone route"), run from the Mac over ssh. ut is the
# Ubuntu Touch X23 (UBPORTS in tests/vm/local.conf, key auth), sfos the Sailfish Quintus (SAILFISH). Copies tools/halium's helpers, spare-race.py and the hello
# apps (the apks of $APKLAYER_APKS, default the repo's build/; tests/apps/build.sh builds them) into a scratch directory, runs phone-checks.sh there in an
# instance of its own (ptest), prints PASS/FAIL per group, removes what it made (scratch, instance) and, on ut, locks the phone again when it woke it.
# Groups: hello newpipe providers hidl hidl-host camera sensors fullscreen race (both), navbar (ut), rotate (sfos). both: the screen is woken and the lock code typed (unlock.sh; apps are stopped while
# the phone is locked; sfos needs SAILFISH_LOCK in local.conf).
set -u
os=${1:-}; shift || true
[ -n "$os" ] && [ $# -gt 0 ] || { sed -n '2,8p' "$0" >&2; exit 2; }
repo=$(cd "$(dirname "$0")/../.." && pwd)
conf=$repo/tests/vm/local.conf
get() { sed -n "s/^$1=//p" "$conf" | head -1; }
case $os in
    ut)   host=$(get UBPORTS);  A=/opt/click.ubuntu.com/apklayer.erfanoabdi/current/usr/bin/apklayer; R=/home/${host%@*}/apklayer-phone-test${PTEST_ID:+-$PTEST_ID} ;;
    sfos) host=$(get SAILFISH); A=/usr/bin/apklayer; R=/userdata/apklayer/phone-test-${host%@*}${PTEST_ID:+-$PTEST_ID} ;;  # the Quintus' home is on a small partition
    *) echo "phone-test: ut or sfos" >&2; exit 2 ;;
esac
[ -n "$host" ] || { echo "phone-test: no phone in $conf" >&2; exit 2; }
apks=${APKLAYER_APKS:-$repo/build}
inst=ptest${PTEST_ID:+-$PTEST_ID}  # PTEST_ID: a second run at the same time on the same phone (another agent) has its own scratch directory and instance
ssh() { command ssh -o BatchMode=yes -o ConnectTimeout=10 "$host" "$@"; }
h=$repo/tools/halium
ssh "mkdir -p $R/apks" || exit 2
scp -q $h/phone-checks.sh $h/ut-shot.sh $h/hidl-list.sh $h/ut-uitap.py $repo/tests/vm/spare-race.py "$host:$R/"
for app in hello-input hello-camera hello-hardware hello-hidl newpipe; do [ -e "$apks/$app.apk" ] && scp -q "$apks/$app.apk" "$host:$R/apks/"; done
woke=0
out=$($h/unlock.sh $os); echo "unlock: $out"
[ "$out" = unlocked ] && woke=1
[ $os = ut ] && [ "$out" != unlocked ] && { echo "phone-test ut: FAIL (not unlocked)"; ssh "rm -rf $R"; exit 1; }  # apps are stopped behind the lock screen
ssh "A=$A R=$R PTEST_INSTANCE=$inst bash $R/phone-checks.sh $os $*"; rc=$?
[ $woke = 1 ] && [ $os = ut ] && echo "ut-lock: $($h/unlock.sh ut off)"
ssh "APKLAYER_INSTANCE=$inst $A stop >/dev/null 2>&1; APKLAYER_INSTANCE=$inst $A instance remove $inst >/dev/null 2>&1; pkill -f '[-]-instance $inst ' >/dev/null 2>&1; [ -n '${KEEP:-}' ] || rm -rf $R"  # KEEP=1 leaves the scratch directory (the groups' logs) for a look, remove it by hand; a delayed app-spare outlives stop and blocks the next race run
echo "phone-test $os: $([ $rc = 0 ] && echo PASS || echo FAIL)"
exit $rc
