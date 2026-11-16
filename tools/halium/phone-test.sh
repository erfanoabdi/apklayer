#!/bin/bash
# phone-test.sh <ut|sfos> <group>...: the quick checks there are for the owner's phones (docs/HALIUM.md "The phone route"), run from the Mac over ssh. ut is the
# Ubuntu Touch X23 (UBPORTS in tests/vm/local.conf, key auth), sfos the Sailfish Quintus (SAILFISH). Copies tools/halium's helpers, spare-race.py and the hello
# apps (the apks of $APKLAYER_APKS, default the repo's build/; tests/apps/build.sh builds them) into a scratch directory, runs phone-checks.sh there in an
# instance of its own (ptest), prints PASS/FAIL per group, removes what it made (scratch, instance) and, on ut, locks the phone again when it woke it.
# Groups: hello newpipe providers hidl hidl-host camera sensors fullscreen race notify (both), navbar (ut), rotate (sfos). Both phones are woken and unlocked first with
# unlock.sh (the X23's PIN, the Quintus' SAILFISH_LOCK, both in local.conf; apps are stopped while the phone is locked).
set -u
tag=${PHONE_TEST_TAG:-}; inst=ptest$tag  # PHONE_TEST_TAG=x: another scratch directory and instance (ptestx), so two runs on one phone do not meet
os=${1:-}; shift || true
[ -n "$os" ] && [ $# -gt 0 ] || { sed -n '2,8p' "$0" >&2; exit 2; }
repo=$(cd "$(dirname "$0")/../.." && pwd)
conf=$repo/tests/vm/local.conf
get() { sed -n "s/^$1=//p" "$conf" | head -1; }
case $os in
    ut)   host=$(get UBPORTS);  A=/opt/click.ubuntu.com/apklayer.erfanoabdi/current/usr/bin/apklayer; R=/home/${host%@*}/apklayer-phone-test$tag ;;
    sfos) host=$(get SAILFISH); A=/usr/bin/apklayer; R=/userdata/apklayer/phone-test-${host%@*}$tag ;;  # the Quintus' home is on a small partition
    *) echo "phone-test: ut or sfos" >&2; exit 2 ;;
esac
[ -n "$host" ] || { echo "phone-test: no phone in $conf" >&2; exit 2; }
apks=${APKLAYER_APKS:-$repo/build}
ssh() { command ssh -o BatchMode=yes -o ConnectTimeout=10 "$host" "$@"; }
h=$repo/tools/halium
ssh "mkdir -p $R/apks" || exit 2
scp -q $h/phone-checks.sh $h/ut-shot.sh $h/hidl-list.sh $h/ut-uitap.py $h/touch.py $repo/tests/vm/spare-race.py "$host:$R/"
for app in hello-input hello-camera hello-hardware hello-notify hello-hidl newpipe; do [ -e "$apks/$app.apk" ] && scp -q "$apks/$app.apk" "$host:$R/apks/"; done
woke=0
if [ $os = ut ]; then
    case " $* " in *" notify "*) $h/unlock.sh ut off >/dev/null ;; esac  # popups need the unlocked screen: a woken phone can still show its lock clock, so lock it first, then unlock
    out=$($h/unlock.sh ut); echo "unlock: $out"
    [ "$out" = unlocked ] && woke=1
    [ "$out" != unlocked ] && { echo "phone-test ut: FAIL (not unlocked)"; ssh "rm -rf $R"; exit 1; }  # apps are stopped behind the lock screen
fi
# sfos: the Quintus is unlocked with its lock code (SAILFISH_LOCK) and kept lit; locked again at the end. PHONE-SHOT <name> lines of a check: Lipstick's own picture
# (saveScreenshot is for the privileged group: root) into $APKLAYER_SHOTS (default build/phone-shots)
shots=${APKLAYER_SHOTS:-$repo/build/phone-shots}; sfroot=root@${host#*@}
if [ $os = sfos ]; then
    scp -q $h/sf-lshot.sh "$host:$R/"
    command ssh -o BatchMode=yes $sfroot "mcetool --set-inhibit-mode=stay-on-with-charger"
    out=$($h/unlock.sh sfos); echo "unlock: $out"
fi
mkdir -p "$shots"; rc=0; fails=$(mktemp)
ssh "A=$A R=$R APKLAYER_INSTANCE=$inst ${APKLAYER_NATIVE:+APKLAYER_NATIVE=$APKLAYER_NATIVE} bash $R/phone-checks.sh $os $*; echo EXIT \$?" | while IFS= read -r line; do
    case $line in
    "PHONE-SHOT "*) if [ $os = sfos ]; then n=${line#PHONE-SHOT }; command ssh -o BatchMode=yes $sfroot "sh $R/sf-lshot.sh $R/$n.png" && scp -q "$host:$R/$n.png" "$shots/$n-sfos.png" && echo "picture $shots/$n-sfos.png"; fi ;;
    "EXIT "*) [ "${line#EXIT }" = 0 ] || echo 1 > "$fails" ;;
    *) echo "$line" ;;
    esac
done
[ -s "$fails" ] && rc=1; rm -f "$fails"
if [ $os = sfos ]; then command ssh -o BatchMode=yes $sfroot "mcetool --set-inhibit-mode=disabled; dbus-send --system --type=method_call --dest=com.nokia.mce /com/nokia/mce/request com.nokia.mce.request.req_tklock_mode_change string:locked"; fi
[ $woke = 1 ] && echo "ut-lock: $($h/unlock.sh ut off)"
ssh "APKLAYER_INSTANCE=$inst $A stop >/dev/null 2>&1; APKLAYER_INSTANCE=$inst $A instance remove $inst >/dev/null 2>&1; pkill -f "[-]-instance $inst " >/dev/null 2>&1; [ -n '${KEEP:-}' ] || rm -rf $R"  # KEEP=1 keeps the scratch directory (the groups' logs);  a delayed app-spare outlives stop and blocks the next race run
echo "phone-test $os: $([ $rc = 0 ] && echo PASS || echo FAIL)"
exit $rc
