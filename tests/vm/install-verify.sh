#!/bin/bash
# The install's own output check (apklayer.tools.VerifyInstall): an installed UiBench jar passes, a copy with one class dropped and a copy with a
# truncated class file fail. No Android stack is started (APKLAYER_NATIVE, APKLAYER_ROOT: the VM paths to test). Prints PASS/FAIL per case, exits 1 on a wrong verdict.
set -uo pipefail
cd "$(dirname "$0")/../.."
. tests/vm/config.sh
sshpass -p "$PASS" ssh "$VM" env APKLAYER_NATIVE="${APKLAYER_NATIVE:-}" APKLAYER_ROOT="${APKLAYER_ROOT:-}" bash -s <<'REMOTE'
N=${APKLAYER_NATIVE:-$HOME/arm64-native} R=${APKLAYER_ROOT:-$HOME/apklayer}
app=$HOME/.local/share/apklayer/apps/com.android.test.uibench
w=$(mktemp -d "$HOME/.install-verify.XXXXXX"); trap 'rm -rf "$w"' EXIT
verify() { java -Xbootclasspath/a:$N/aosp/Robolectric_all.jar -cp $N/aosp/host-classes:$R/b/runtime/dex2jvm/dex2jvm.jar apklayer.tools.VerifyInstall $app/base.apk "$1"; }
classes=$(unzip -Z1 $app/classes.jar | grep '\.class$')
cp $app/classes.jar $w/dropped.jar && zip -q -d $w/dropped.jar "$(sed -n 100p <<<"$classes")"
victim=$(sed -n 200p <<<"$classes")
cp $app/classes.jar $w/truncated.jar && mkdir $w/x && (cd $w/x && unzip -q $app/classes.jar "$victim" && truncate -s 300 "$victim" && zip -q ../truncated.jar "$victim")
status=0
for c in "pass $app/classes.jar" "fail $w/dropped.jar" "fail $w/truncated.jar"; do
    set -- $c
    out=$(verify "$2"); code=$?
    if { [ $1 = pass ] && [ $code = 0 ]; } || { [ $1 = fail ] && [ $code = 1 ]; }; then echo "PASS $(basename $2): exit $code"; else echo "FAIL $(basename $2): exit $code"; status=1; fi
    sed 's/^/    /' <<<"$out" | head -3
done
exit $status
REMOTE
