#!/usr/bin/env bash
# apklayer-rob: run an APK on the Robolectric method with our Wayland presenter (on the VM).
# Usage: apklayer-rob [app.apk]        (default: tests/uibench/uibench.apk)
#   Syncs sources to the VM, rebuilds the bridge, converts the APK's DEX with dex2jvm against
#   the android-all framework it runs on, and launches the driver.
# APKLAYER_GFX=NATIVE: real HWUI/Skia via Robolectric's native runtime. Its prebuilts exist for
#   linux-x86_64 only, so on the arm64 VM the app JVM is x86_64 under Rosetta (C2 and G1 crash
#   there: C1 + Serial GC) and frames cross a shared framebuffer to the native apklayer-presentd.
#   APKLAYER_GFX=LEGACY (default): arm64 JVM, in-process presenter, no text rendering.
# APKLAYER_JOPTS: extra JVM flags, e.g. -Dapklayer.script=10:tap:100,80+60:back
#   -Dapklayer.snapframes=50,90 -Dapklayer.frames=120 (headless-style verification).
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APK="${1:-$ROOT/tests/uibench/uibench.apk}"
NAME="$(basename "$APK" .apk)"
VM="${APKLY_VM:-parallels@10.211.55.4}"
VM_PASS="${APKLY_VM_PASS:-1234}"
vm() { sshpass -p "$VM_PASS" ssh -o StrictHostKeyChecking=no "$VM" "$@"; }

# Sources (driver, stubs, dex2jvm, bridge) + the APK -> VM; bridge rebuilt incrementally.
vm "rm -rf apklayer-m1/tests apklayer-m1/runtime && mkdir -p apklayer-m1/bridge apklayer-apps"
(cd "$ROOT" && COPYFILE_DISABLE=1 tar -cz tests/rob tests/stubs runtime/dex2jvm bridge/meson.build bridge/src) \
  | vm "tar -xz -C apklayer-m1 2>/dev/null; find apklayer-m1 -name '._*' -delete; ninja -C apklayer-m1/bridge/b >/dev/null"
sshpass -p "$VM_PASS" scp -q -o StrictHostKeyChecking=no "$APK" "$VM:apklayer-apps/$NAME.apk"

vm bash -s <<EOF
set -e
R=\$HOME/rob; S=\$HOME/apklayer-m1; A=\$HOME/apklayer-apps/$NAME.apk
rm -rf \$R/robout \$R/stubout \$R/d2j && mkdir -p \$R/robout \$R/stubout \$R/d2j
javac -nowarn -d \$R/stubout \$(find \$S/tests/stubs -name "*.java")
javac -nowarn -d \$R/robout -cp "\$R/libs/*:\$R/android-all/*" \$(find \$S/tests/rob -name "*.java")
javac -nowarn -d \$R/d2j \$(find \$S/runtime/dex2jvm -name "*.java")

# DEX -> JVM classes; the class hierarchy comes from the framework we run on (android-all).
java -XX:UseSVE=0 -cp "\$R/d2j:\$R/android-all/*" com.macapk.dex.Main \$A /tmp/$NAME-classes.jar
java -XX:UseSVE=0 -cp "\$R/libs/*:\$R/robout" apklayer.tools.DenyInstrument /tmp/$NAME-classes.jar /tmp/$NAME-dni.jar

# AGP build-system API: Robolectric reads it only from the classpath (not -D props); the real
# PackageParser then reads manifest + resources straight from the APK.
mkdir -p \$R/cfg/com/android/tools
echo "android_resource_apk=\$A" > \$R/cfg/com/android/tools/test_config.properties

export XDG_RUNTIME_DIR=/run/user/1000 WAYLAND_DISPLAY=wayland-0
GFX=${APKLAYER_GFX:-LEGACY}
if [ "\$GFX" = NATIVE ]; then
  JAVA="\$HOME/jdk21-x86/bin/java -XX:TieredStopAtLevel=1 -XX:+UseSerialGC -Dapklayer.fb=/dev/shm/apklayer-fb"
  rm -f /dev/shm/apklayer-fb
  \$S/bridge/b/apklayer-presentd /dev/shm/apklayer-fb &
else
  JAVA="java -XX:UseSVE=0"
fi
cd /tmp
\$JAVA $APKLAYER_JOPTS \
  -Drobolectric.offline=true \
  -Drobolectric.graphicsMode=\$GFX \
  -Drobolectric.dependency.dir=\$R/android-all \
  -Dapklayer.wl=\$S/bridge/b/libapklayer-wl.so \
  -Dapklayer.hostcp=\$R/robout \
  -cp "\$R/libs/*:\$R/stubout:\$R/android-stub.jar:\$R/robout:\$R/cfg:/tmp/$NAME-dni.jar" \
  org.junit.runner.JUnitCore apklayer.rob.RunUiBench \
  2>&1 | grep --line-buffered -vE '^OpenJDK'
EOF
