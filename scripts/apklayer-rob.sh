#!/usr/bin/env bash
# apklayer-rob: run a converted APK on the Robolectric method with our Wayland presenter.
# Usage: apklayer-rob <converted-classes.jar> <activity FQCN> [manifest-dir]
#   manifest-dir: dir containing the unpacked APK (binary AndroidManifest.xml + resources.arsc)
set -e
APPJAR="$1"; ACTIVITY="$2"; MANIFEST_DIR="${3:-$HOME/apklayer-apps/uibench}"
VM="${APKLY_VM:-parallels@10.211.55.4}"
VM_PASS="${APKLY_VM_PASS:-1234}"
D=apklayer-m1/tests/rob

sshpass -p "$VM_PASS" ssh -o StrictHostKeyChecking=no "$VM" "rm -rf $D && mkdir -p $D"
(cd "$(dirname "$0")/../tests" && rm -f rob/apklayer/rob/._* stubs/android/*/*. _* 2>/dev/null; find . -name '._*' -delete 2>/dev/null; COPYFILE_DISABLE=1 tar -cz rob stubs) | sshpass -p "$VM_PASS" ssh -o StrictHostKeyChecking=no "$VM" "tar -xz -C $D"

sshpass -p "$VM_PASS" ssh -o StrictHostKeyChecking=no "$VM" bash -s <<EOF
set -e
R=\$HOME/rob
rm -rf \$R/robout \$R/stubout && mkdir -p \$R/robout \$R/stubout
find \$HOME/apklayer-m1/tests/rob/stubs -name '._*' -delete
javac -nowarn -d \$R/stubout \$(find \$HOME/apklayer-m1/tests/rob/stubs -name "*.java")
javac -nowarn -d \$R/robout -cp "\$R/libs/*:\$R/android-all/*" \
  \$HOME/apklayer-m1/tests/rob/rob/apklayer/rob/*.java \
  \$HOME/apklayer-m1/tests/rob/rob/apklayer/wayland/*.java \
  \$HOME/apklayer-m1/tests/rob/rob/apklayer/tools/*.java
DN=/tmp/app-dni.jar
java -cp "\$R/libs/*:\$R/robout" apklayer.tools.DenyInstrument $APPJAR \$DN
cd $MANIFEST_DIR/../ || cd \$HOME
cd \$(dirname $MANIFEST_DIR)
XDG_RUNTIME_DIR=/run/user/1000 WAYLAND_DISPLAY=wayland-0 \
java -XX:UseSVE=0 \
  -Drobolectric.offline=true \
  -Drobolectric.dependency.dir=\$R/android-all \
  -Dandroid_resource_apk=\$HOME/apklayer-m1/tests/uibench/uibench.apk \
  -Dapklayer.wl=\$HOME/apklayer-m1/bridge/b/libapklayer-wl.so \
  -Dapklayer.hostcp=\$R/robout \
  -cp "\$R/libs/*:\$R/stubout:\$R/android-stub.jar:\$R/robout:/tmp/app-dni.jar:\$HOME/apklayer-apps" \
  org.junit.runner.JUnitCore apklayer.rob.RunUiBench \
  2>&1 | grep -vE '^OpenJDK' | tail -60
EOF
