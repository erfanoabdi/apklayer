#!/bin/bash
# Android's optional shared libraries (HostSharedLibraries): hello.sharedlib declares <uses-library org.apache.http.legacy> (and an unknown optional
# one), hello.sharedlib.old targets SDK 27 and gets it, and android.test.base, implicitly; both use Apache HTTP's real classes, not the sandbox's "Stub!".
# An APK that requires a library the build does not know is refused at install (INSTALL_FAILED_MISSING_SHARED_LIBRARY, exit 6).
# Libraries installed apps provide (HostAppLibraries): hello.applib.user is refused before its providers are installed, then installed after
# hello.applib (a static library, version and signer named) and hello.applib.system (a system app's <library>), and runs their code.
# On the VM, in the group's instance (tests/run sharedlib): APKLAYER_INSTANCE, APKLY_STATE (its logs) and the test apps installed by the runner.
: "${APKLY_STATE:=${XDG_STATE_HOME:-$HOME/.local/state}/apklayer/${APKLAYER_INSTANCE:-default}}"
fail=0
check() {  # check <package> <testBase expected> <optional libraries' classes found>
    out=$APKLY_STATE/sharedlib-$1.out
    bin/apklayer stop >/dev/null 2>&1
    APKLAYER_JOPTS="-Dapklayer.runms=30000" bin/apklayer run "$1" > "$out" 2>&1
    grep -a "\[sharedlib\]\|shared library" "$out" | cut -c1-400
    if grep -aq "\[sharedlib\] $1 PASS" "$out" && grep -aq "\[sharedlib\] $1 optional=$3 .*testBase=$2 libs=\[.*org.apache.http.legacy.jar" "$out"; then echo "PASS $1"
    else echo "FAIL $1"; grep -a -A12 "FAIL\|Exception" "$out" | head -30; fail=1; fi
}
check hello.sharedlib false 6
grep -aq "\[sharedlib\] hello.sharedlib other PASS" "$APKLY_STATE/sharedlib-hello.sharedlib.out" && echo "PASS other app's sharedLibraryFiles/Infos" || { echo "FAIL other app's sharedLibraryFiles/Infos"; fail=1; }
check hello.sharedlib.old true 0
bin/apklayer stop >/dev/null 2>&1
work=$(mktemp -d)
unzip -p .tests/apks/hello-sharedlib.apk assets/hello-sharedlib-missing.apk > "$work/missing.apk"
bin/apklayer install "$work/missing.apk" > "$work/install.out" 2>&1
code=$?
cat "$work/install.out" | tail -3
if [ $code = 6 ] && grep -q "INSTALL_FAILED_MISSING_SHARED_LIBRARY" "$work/install.out" && ! bin/apklayer list 2>/dev/null | grep -q hello.sharedlib.missing; then
    echo "PASS install refuses a required unknown library"
else echo "FAIL install of hello.sharedlib.missing exited $code"; fail=1; fi
for app in hello-applib hello-applib-system hello-applib-user; do unzip -p .tests/apks/hello-sharedlib.apk assets/$app.apk > "$work/$app.apk"; done
for p in hello.applib.user hello.applib hello.applib.system; do bin/apklayer uninstall $p >/dev/null 2>&1; done
bin/apklayer install "$work/hello-applib-user.apk" > "$work/user-early.out" 2>&1; early=$?
bin/apklayer install "$work/hello-applib.apk" > "$work/static.out" 2>&1 && bin/apklayer install --system "$work/hello-applib-system.apk" > "$work/system.out" 2>&1 &&
    bin/apklayer install "$work/hello-applib-user.apk" > "$work/user.out" 2>&1; late=$?
out=$APKLY_STATE/sharedlib-hello.applib.user.out
[ $late = 0 ] && APKLAYER_JOPTS="-Dapklayer.runms=30000" bin/apklayer run hello.applib.user > "$out" 2>&1
grep -a "\[sharedlib\]\|shared library" "$out" | cut -c1-300
# sharedLibraryInfos: the static library (TYPE_STATIC 2) and the system app's (TYPE_DYNAMIC 1), not the optional native one no build has
if [ $early = 6 ] && [ $late = 0 ] && grep -aq "\[sharedlib\] hello.applib.user PASS static=static-library v3 system=system library infos=hello.applib:2 hello.applib.shared:1$" "$out"; then echo "PASS libraries of installed apps"
else echo "FAIL libraries of installed apps (before the providers: exit $early, after: $late)"; tail -n 2 "$work"/*.out; fail=1; fi
# the static library's provider stays while its user is installed (DELETE_FAILED_USED_SHARED_LIBRARY, exit 8), and goes after it
bin/apklayer uninstall hello.applib > "$work/provider.out" 2>&1; used=$?
bin/apklayer uninstall hello.applib.user >/dev/null 2>&1; bin/apklayer uninstall hello.applib > "$work/provider2.out" 2>&1; unused=$?
if [ $used = 8 ] && grep -q "DELETE_FAILED_USED_SHARED_LIBRARY" "$work/provider.out" && [ $unused = 0 ]; then echo "PASS uninstall keeps a used library provider"
else echo "FAIL uninstall of a used provider exited $used, unused $unused"; cat "$work/provider.out" "$work/provider2.out"; fail=1; fi
# a required <uses-native-library> no build lists (target 34): refused as a missing library
unzip -p .tests/apks/hello-sharedlib.apk assets/hello-sharedlib-native.apk > "$work/native.apk"
bin/apklayer install "$work/native.apk" > "$work/native.out" 2>&1; code=$?
if [ $code = 6 ] && grep -q "INSTALL_FAILED_MISSING_SHARED_LIBRARY.*libnot_on_this_device.so" "$work/native.out"; then echo "PASS install refuses a required unknown native library"
else echo "FAIL install of hello.sharedlib.native exited $code"; tail -3 "$work/native.out"; fail=1; fi
for p in hello.applib.user hello.applib hello.applib.system; do bin/apklayer uninstall $p >/dev/null 2>&1; done
rm -rf "$work"
bin/apklayer stop >/dev/null 2>&1
exit $fail
