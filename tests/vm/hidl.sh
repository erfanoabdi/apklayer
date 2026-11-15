#!/bin/bash
# HIDL for old system apps (docs/HIDL.md): hello.hidl calls the instance's hwservicemanager (libapklayer_hwservicemanager) through the framework's
# HwBinder/HwParcel JNI (android.hidl.manager@1.0 IServiceManager.getService/getTransport/list), after install accepted its <uses-library
# android.hidl.*-java>; then a native client in the same stack (`hidltool manager`) asks the same.
# On the VM, in the group's instance (tests/run hidl): APKLAYER_INSTANCE, APKLY_STATE (its logs) and the test app installed by the runner.
: "${APKLY_STATE:=${XDG_STATE_HOME:-$HOME/.local/state}/apklayer/${APKLAYER_INSTANCE:-default}}"
fail=0
bin/apklayer stop >/dev/null 2>&1
out=$APKLY_STATE/hidl-hello.hidl.out
APKLAYER_JOPTS="-Dapklayer.runms=30000" bin/apklayer run hello.hidl > "$out" 2>&1
grep -a "\[hidl\]" "$out" | cut -c1-300
if grep -aq "\[hidl\] hello.hidl PASS" "$out"; then echo "PASS hello.hidl: IServiceManager through HwBinder"
else echo "FAIL hello.hidl"; grep -a -A12 "FAIL\|Exception\|hwservicemanager" "$out" | head -30; fail=1; fi
out=$APKLY_STATE/hidl-native.out
bin/apklayer exec libapklayer_hidltool.so manager > "$out" 2>&1
if grep -aq "manager: transport hwbinder, listed 1" "$out"; then echo "PASS native client: hwservicemanager lists itself"
else echo "FAIL native client"; tail -5 "$out"; fail=1; fi
bin/apklayer stop >/dev/null 2>&1
exit $fail
