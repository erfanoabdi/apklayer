#!/bin/bash
# unlock.sh <ut|sfos> [off]: from the Mac, wakes the owner's phone and types its lock code (ut: UBPORTS_PASSWORD, the PIN; sfos: SAILFISH_LOCK, both in
# tests/vm/local.conf, never committed) on the lock screen by emulated presses (ut-unlock.sh / sf-unlock.sh on the phone). Prints "unlocked" and exits 0 only
# when the screen is on and unlocked; `off` (ut) presses the power key to lock again. The code goes over ssh's stdin, never as an argument.
set -u
os=${1:-}; mode=${2:-on}
repo=$(cd "$(dirname "$0")/../.." && pwd); h=$repo/tools/halium
conf=${LOCAL_CONF:-$repo/tests/vm/local.conf}
get() { sed -n "s/^$1=//p" "$conf" | head -1; }
case $os in
    ut)   host=$(get UBPORTS);   R=/home/${host%@*}/apklayer-unlock;       files="ut-unlock.sh ut-uikeys.py ut-uitap.py" ;;
    sfos) host=$(get SAILFISH);  R=/userdata/apklayer/unlock-${host%@*};   files="sf-unlock.sh touch.py" ;;
    *) echo "usage: unlock.sh <ut|sfos> [off]" >&2; exit 2 ;;
esac
[ -n "$host" ] || { echo "unlock: no $os phone in $conf" >&2; exit 2; }
S() { ssh -o BatchMode=yes -o ConnectTimeout=10 "$host" "$@"; }
S "mkdir -p $R" && (cd "$h" && scp -q $files "$host:$R/") || exit 2
if [ $os = ut ]; then
    out=$(get UBPORTS_PASSWORD | S "bash $R/ut-unlock.sh $mode $R" | tail -1)
else
    [ "$mode" = on ] || { echo "unlock: off is ut only" >&2; S "rm -rf $R"; exit 2; }
    code=$(get SAILFISH_LOCK); [ -n "$code" ] || { echo "unlock: no SAILFISH_LOCK in $conf" >&2; S "rm -rf $R"; exit 2; }
    # root for uinput and mce, as phone-test.sh does (key auth); the code goes over stdin
    echo "$code" | command ssh -o BatchMode=yes -o ConnectTimeout=10 "root@${host#*@}" "sh $R/sf-unlock.sh $R" >/dev/null 2>&1
    out=$(S 'mcetool 2>/dev/null' | sed -n 's/^Display state: *on.*/on/p;s/^Tklock mode: *\(.*\)/\1/p' | tr '\n' ' ')
    case $out in *on*unlocked*) out=unlocked;; esac
fi
S "rm -rf $R"
echo "$out"
case $out in unlocked|"screen off") exit 0;; *) exit 1;; esac
