#!/bin/bash
# The install's own output check (apklayer.tools.VerifyInstall): an installed UiBench jar passes (HotSpot's verifier too, as cli/apps.cpp runs it), a
# copy with one class dropped and a copy with a truncated class file fail; the verifier alone (--classes) refuses a type error and a class whose check
# loads a missing class, not one that only has a field of a missing type (tests/vm/VerifyCases.java). No Android stack is started. On the VM, in the repo copy (tests/run install-verify). Prints PASS/FAIL per case, exits 1 on a wrong verdict.
set -uo pipefail
N=${APKLAYER_NATIVE:-$HOME/arm64-native} R=$PWD
app=${XDG_DATA_HOME:-$HOME/.local/share}/apklayer/apps/com.android.test.uibench
w=$(mktemp -d "$HOME/.install-verify.XXXXXX"); trap 'rm -rf "$w"' EXIT
fw=$(printf '%s:' $N/aosp/android-all/*.jar)$N/aosp/host-classes.jar:$N/aosp/Robolectric_all.jar
bytecode() { java -Xlog:class+init=info:file=$w/verify.log -Dapklayer.verify.classpath=$fw -Dapklayer.verify.log=$w/verify.log "$@"; }
verify() { bytecode -Xbootclasspath/a:$N/aosp/Robolectric_all.jar -cp $N/aosp/host-classes.jar:$R/b/runtime/dex2jvm/dex2jvm.jar apklayer.tools.VerifyInstall $app/base.apk "$1"; }
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
java -cp $N/aosp/Robolectric_all.jar tests/vm/VerifyCases.java $w/cases.jar
out=$(bytecode -cp $N/aosp/host-classes.jar:$N/aosp/Robolectric_all.jar:$R/b/runtime/dex2jvm/dex2jvm.jar apklayer.tools.VerifyInstall --classes $w/cases.jar); code=$?
got=$(cut -f1,2 $w/cases.jar.refused 2>/dev/null | tr '\t\n' '  ')
if [ $code = 3 ] && [ "$got" = "v/BadType java/lang/VerifyError v/BadMissing java/lang/NoClassDefFoundError " ]; then echo "PASS bytecode cases: refused $got"; else echo "FAIL bytecode cases: exit $code, refused '$got'"; status=1; fi
exit $status
