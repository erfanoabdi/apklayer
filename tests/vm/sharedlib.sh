#!/bin/bash
# Android's optional shared libraries (HostSharedLibraries): hello.sharedlib declares <uses-library org.apache.http.legacy> (and an unknown optional
# one), hello.sharedlib.old targets SDK 27 and gets it, and android.test.base, implicitly; both use Apache HTTP's real classes, not the sandbox's "Stub!".
# An APK that requires a library the build does not know is refused at install (INSTALL_FAILED_MISSING_SHARED_LIBRARY, exit 6).
# On the VM, in the group's instance (tests/run sharedlib): APKLAYER_INSTANCE, APKLY_STATE (its logs) and the test apps installed by the runner.
: "${APKLY_STATE:=${XDG_STATE_HOME:-$HOME/.local/state}/apklayer/${APKLAYER_INSTANCE:-default}}"
fail=0
check() {  # check <package> <testBase expected>
    out=$APKLY_STATE/sharedlib-$1.out
    bin/apklayer stop >/dev/null 2>&1
    APKLAYER_JOPTS="-Dapklayer.runms=30000" bin/apklayer run "$1" > "$out" 2>&1
    grep -a "\[sharedlib\]\|shared library" "$out" | cut -c1-400
    if grep -aq "\[sharedlib\] $1 PASS" "$out" && grep -aq "\[sharedlib\] $1 .*testBase=$2 libs=\[.*org.apache.http.legacy.jar" "$out"; then echo "PASS $1"
    else echo "FAIL $1"; grep -a -A12 "FAIL\|Exception" "$out" | head -30; fail=1; fi
}
check hello.sharedlib false
check hello.sharedlib.old true
bin/apklayer stop >/dev/null 2>&1
work=$(mktemp -d)
unzip -p .tests/apks/hello-sharedlib.apk assets/hello-sharedlib-missing.apk > "$work/missing.apk"
bin/apklayer install "$work/missing.apk" > "$work/install.out" 2>&1
code=$?
cat "$work/install.out" | tail -3
if [ $code = 6 ] && grep -q "INSTALL_FAILED_MISSING_SHARED_LIBRARY" "$work/install.out" && ! bin/apklayer list 2>/dev/null | grep -q hello.sharedlib.missing; then
    echo "PASS install refuses a required unknown library"
else echo "FAIL install of hello.sharedlib.missing exited $code"; fail=1; fi
rm -rf "$work"
bin/apklayer stop >/dev/null 2>&1
exit $fail
