#!/usr/bin/env bash
# vm-run: run an APK on the Robolectric method with our Wayland presenter (on the VM).
# Usage: vm-run.sh [app.apk]        (default: tests/uibench/uibench.apk)
#   Mirrors the deps cache (deps/deps.lock, filled by scripts/fetch-deps.sh on the Mac) and the
#   sources to the VM, rebuilds the shell, converts the APK's DEX with dex2jvm against the
#   android-all framework it runs on, and launches the host.
# APKLAYER_GFX=NATIVE: real HWUI/Skia via Robolectric's native runtime. Its prebuilts exist for
#   linux-x86_64 only, so on the arm64 VM the app JVM is x86_64 under Rosetta (C2 and G1 crash
#   there: C1 + Serial GC) and frames cross a shared framebuffer to the native apklayer-presentd.
#   This Rosetta path is a development stopgap until PLAN P7 (native arm64).
#   APKLAYER_GFX=LEGACY (default): arm64 JVM, in-process presenter, no text rendering.
# APKLAYER_JOPTS: extra JVM flags, e.g. -Dapklayer.script=10:tap:100,80+60:back
#   -Dapklayer.snapframes=50,90 -Dapklayer.frames=120 (headless-style verification).
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APK="${1:-$ROOT/tests/uibench/uibench.apk}"
NAME="$(basename "$APK" .apk)"
VM="${APKLY_VM:-parallels@10.211.55.4}"
VM_PASS="${APKLY_VM_PASS:-1234}"
DEPS="$HOME/.cache/apklayer/deps"
vm() { sshpass -p "$VM_PASS" ssh -o StrictHostKeyChecking=no "$VM" "$@"; }

"$ROOT/scripts/fetch-deps.sh" "$DEPS" | tail -1
vm "mkdir -p .cache/apklayer apklayer-apps"
rsync -a --delete -e "sshpass -p $VM_PASS ssh -o StrictHostKeyChecking=no" "$DEPS/" "$VM:.cache/apklayer/deps/"

# Sources (host, dex2jvm, shell) + the APK -> VM; the shell is rebuilt incrementally.
vm "rm -rf apklayer-m1/tests apklayer-m1/runtime apklayer-m1/bridge/src && mkdir -p apklayer-m1"
(cd "$ROOT" && COPYFILE_DISABLE=1 tar -cz runtime/host runtime/dex2jvm bridge/shell/meson.build bridge/shell/*.c) \
  | vm "tar -xz -C apklayer-m1 2>/dev/null; find apklayer-m1 -name '._*' -delete
        cd apklayer-m1/bridge/shell && { [ -d b ] || meson setup -Dbuildtype=release b >/dev/null; } && ninja -C b >/dev/null"
sshpass -p "$VM_PASS" scp -q -o StrictHostKeyChecking=no "$APK" "$VM:apklayer-apps/$NAME.apk"

vm bash -s <<EOF
set -e
R=\$HOME/.cache/apklayer/deps; S=\$HOME/apklayer-m1; B=\$HOME/apklayer-build; A=\$HOME/apklayer-apps/$NAME.apk
rm -rf \$B && mkdir -p \$B/host \$B/d2j \$B/cfg/com/android/tools
javac -nowarn -d \$B/host -cp "\$R/libs/*:\$R/android-all/*" \$(find \$S/runtime/host/java -name "*.java")
javac -nowarn -d \$B/d2j \$(find \$S/runtime/dex2jvm -name "*.java")

# DEX -> JVM classes; the class hierarchy comes from the framework we run on (android-all).
java -XX:UseSVE=0 -cp "\$B/d2j:\$R/android-all/*" com.macapk.dex.Main \$A /tmp/$NAME-classes.jar
java -XX:UseSVE=0 -cp "\$R/libs/*:\$B/host" apklayer.tools.DenyInstrument /tmp/$NAME-classes.jar /tmp/$NAME-dni.jar

# AGP build-system API: Robolectric reads it only from the classpath (not -D props); the real
# PackageParser then reads manifest + resources straight from the APK.
echo "android_resource_apk=\$A" > \$B/cfg/com/android/tools/test_config.properties

export XDG_RUNTIME_DIR=/run/user/1000 WAYLAND_DISPLAY=wayland-0
GFX=${APKLAYER_GFX:-LEGACY}
if [ "\$GFX" = NATIVE ]; then
  JAVA="\$HOME/jdk21-x86/bin/java -XX:TieredStopAtLevel=1 -XX:+UseSerialGC -Dapklayer.fb=/dev/shm/apklayer-fb"
  rm -f /dev/shm/apklayer-fb
  \$S/bridge/shell/b/apklayer-presentd /dev/shm/apklayer-fb &
else
  JAVA="java -XX:UseSVE=0"
fi
cd /tmp
\$JAVA @\$S/runtime/host/jvm.args $APKLAYER_JOPTS \
  -Drobolectric.offline=true \
  -Drobolectric.graphicsMode=\$GFX \
  -Drobolectric.dependency.dir=\$R/android-all \
  -Dapklayer.wl=\$S/bridge/shell/b/libapklayer-wl.so \
  -Dapklayer.hostcp=\$B/host \
  -cp "\$R/libs/*:\$R/android-stub.jar:\$B/host:\$B/cfg:/tmp/$NAME-dni.jar" \
  org.junit.runner.JUnitCore apklayer.rob.RunUiBench \
  2>&1 | grep --line-buffered -vE '^OpenJDK'
EOF
