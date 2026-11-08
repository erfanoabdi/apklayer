#!/bin/bash
# A class dex2jvm cannot convert does not block the install (DexConverter.failedClassStub): hello.stub is installed with hello.stub.Victim forced
# to fail (DEX2JVM_TEST_FAIL_CLASS), the install says so, the app runs and its call into Victim throws VerifyError naming the class.
# Then installed again without the knob: the call returns. Both times its classes.dex loaded through InMemoryDexClassLoader (converted and verified
# in the app JVM: ShadowHostBaseDexClassLoader) gives a Victim of that loader whose call returns. On the VM, in the group's instance (tests/run dex2jvm).
: "${APKLY_STATE:=${XDG_STATE_HOME:-$HOME/.local/state}/apklayer/${APKLAYER_INSTANCE:-default}}"
fail=0
apk=.tests/apks/hello-stub.apk
check() {  # check <name> <pattern in the app's output>
    out=$APKLY_STATE/stub-$1.out
    bin/apklayer stop >/dev/null 2>&1
    APKLAYER_JOPTS="-Dapklayer.runms=20000" bin/apklayer run hello.stub > "$out" 2>&1
    grep -a "\[stub\]" "$out" | cut -c1-300
    if grep -aq "$2" "$out"; then echo "PASS $1"; else echo "FAIL $1"; grep -a -A8 "Exception\|Error" "$out" | head -20; fail=1; fi
}
bin/apklayer uninstall hello.stub >/dev/null 2>&1  # no cached conversion of the APK (the cache key does not see the knob)
DEX2JVM_TEST_FAIL_CLASS=hello/stub/Victim bin/apklayer install $apk > "$APKLY_STATE/stub-install.out" 2>&1
code=$?
grep -a "warning\|installed" "$APKLY_STATE/stub-install.out" | head -5
if [ $code = 0 ] && grep -aq "warning: 1 class.*hello.stub.Victim" "$APKLY_STATE/stub-install.out"; then echo "PASS install with a failed class (exit 0, warned)"
else echo "FAIL install with a failed class exited $code"; tail -5 "$APKLY_STATE/stub-install.out"; fail=1; fi
check stubbed "\[stub\] VerifyError .*class hello.stub.Victim could not be converted"
bin/apklayer uninstall hello.stub >/dev/null 2>&1  # the stubbed conversion is not left for a later install to reuse
bin/apklayer install $apk > /dev/null 2>&1 || { echo "FAIL plain install"; fail=1; }
check converted "\[stub\] call returned converted"
if grep -aq "\[stub\] in-memory returned converted$" "$APKLY_STATE/stub-converted.out"; then echo "PASS in-memory"; else echo "FAIL in-memory"; fail=1; fi
bin/apklayer stop >/dev/null 2>&1
exit $fail
