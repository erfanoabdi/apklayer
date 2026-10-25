#!/usr/bin/env bash
# apklayer-rob: run a converted APK on the Robolectric method with our Wayland presenter.
# Usage: apklayer-rob <converted-classes.jar> <activity FQCN> [manifest-dir]
#   manifest-dir: dir containing the unpacked APK (binary AndroidManifest.xml + resources.arsc)
# APKLAYER_GFX=NATIVE: real HWUI/Skia via Robolectric's native runtime. Its prebuilts exist for
#   linux-x86_64 only, so on the arm64 VM the app JVM is x86_64 (Rosetta; C2 crashes there, so
#   C1 only; G1 also crashes there, so Serial GC) and frames go through a shared framebuffer to the native apklayer-presentd.
#   APKLAYER_GFX=LEGACY (default): arm64 JVM, in-process presenter, no text rendering.
# APKLAYER_JOPTS: extra JVM flags, e.g. -Dapklayer.script=10:tap:100,80+60:back
#   -Dapklayer.snapframes=50,90 -Dapklayer.frames=120 (headless-style verification).
set -e
APPJAR="$1"; ACTIVITY="$2"; MANIFEST_DIR="${3:-$HOME/apklayer-apps/uibench}"
VM="${APKLY_VM:-parallels@10.211.55.4}"
VM_PASS="${APKLY_VM_PASS:-1234}"
D=apklayer-m1/tests/rob

sshpass -p "$VM_PASS" ssh -o StrictHostKeyChecking=no "$VM" "rm -rf $D && mkdir -p $D"
(cd "$(dirname "$0")/../tests" && rm -f rob/apklayer/rob/._* stubs/android/*/*. _* 2>/dev/null; find . -name '._*' -delete 2>/dev/null; COPYFILE_DISABLE=1 tar -cz rob stubs) | sshpass -p "$VM_PASS" ssh -o StrictHostKeyChecking=no "$VM" "tar -xz -C $D"

# Bridge (presenter) sources -> VM, rebuilt incrementally.
(cd "$(dirname "$0")/../bridge" && COPYFILE_DISABLE=1 tar -cz meson.build src) | sshpass -p "$VM_PASS" ssh -o StrictHostKeyChecking=no "$VM" "tar -xz -C apklayer-m1/bridge 2>/dev/null; ninja -C apklayer-m1/bridge/b >/dev/null"

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
# AGP build-system API: Robolectric reads these only from the classpath (not -D props).
mkdir -p \$R/cfg/com/android/tools
cat > \$R/cfg/com/android/tools/test_config.properties <<PROPS
android_merged_manifest=$MANIFEST_DIR/manifest.txt
android_resource_apk=$MANIFEST_DIR/base.apk
android_custom_package=
PROPS
DN=/tmp/app-dni.jar
java -cp "\$R/libs/*:\$R/robout" apklayer.tools.DenyInstrument $APPJAR \$DN
cd $MANIFEST_DIR/../ || cd \$HOME
cd \$(dirname $MANIFEST_DIR)
export XDG_RUNTIME_DIR=/run/user/1000 WAYLAND_DISPLAY=wayland-0
GFX=${APKLAYER_GFX:-LEGACY}
if [ "\$GFX" = NATIVE ]; then
  JAVA="\$HOME/jdk21-x86/bin/java -XX:TieredStopAtLevel=1 -XX:+UseSerialGC -Dapklayer.fb=/dev/shm/apklayer-fb"
  rm -f /dev/shm/apklayer-fb
  \$HOME/apklayer-m1/bridge/b/apklayer-presentd /dev/shm/apklayer-fb &
else
  JAVA="java -XX:UseSVE=0"
fi
\$JAVA $APKLAYER_JOPTS \
  -Drobolectric.offline=true \
  -Drobolectric.graphicsMode=\$GFX \
  -Drobolectric.dependency.dir=\$R/android-all \
  -Dapklayer.wl=\$HOME/apklayer-m1/bridge/b/libapklayer-wl.so \
  -Dapklayer.hostcp=\$R/robout \
  -cp "\$R/libs/*:\$R/stubout:\$R/android-stub.jar:\$R/robout:\$R/cfg:/tmp/app-dni.jar:\$HOME/apklayer-apps" \
  org.junit.runner.JUnitCore apklayer.rob.RunUiBench \
  2>&1 | grep --line-buffered -vE '^OpenJDK'
EOF
