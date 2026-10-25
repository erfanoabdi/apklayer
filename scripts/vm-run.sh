#!/usr/bin/env bash
# vm-run: run an APK on the Robolectric method with our Wayland presenter (on the VM).
# Usage: vm-run.sh [app.apk]        (default: tests/uibench/uibench.apk)
#   Mirrors the deps cache (deps/deps.lock, filled by scripts/fetch-deps.sh on the Mac) and the
#   sources to the VM, rebuilds the shell, converts the APK's DEX with dex2jvm against the
#   android-all framework it runs on, and launches the host.
# Graphics are always NATIVE (real HWUI/Skia via Robolectric's native runtime). Its prebuilts
#   exist for linux-x86_64 only, so on the arm64 VM the app JVM is x86_64 under Rosetta (C2 and
#   G1 crash there: C1 + Serial GC) and frames cross a shared framebuffer to the native
#   apklayer-presentd. This Rosetta path is a development stopgap until PLAN P7 (native arm64).
# APKLAYER_JOPTS: extra JVM flags for verification, e.g.
#   -Dapklayer.script=2000:tap:100,80+4000:back -Dapklayer.snapms=1500,3500 -Dapklayer.runms=6000
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
rm -rf \$B && mkdir -p \$B/host \$B/d2j
javac -nowarn -d \$B/host -cp "\$R/libs/*:\$R/android-all/*" \$(find \$S/runtime/host/java -name "*.java")
javac -nowarn -d \$B/d2j \$(find \$S/runtime/dex2jvm -name "*.java")

# DEX -> JVM classes; the class hierarchy comes from the framework we run on (android-all).
java -XX:UseSVE=0 -cp "\$B/d2j:\$R/android-all/*" com.macapk.dex.Main \$A /tmp/$NAME-classes.jar
java -XX:UseSVE=0 -cp "\$R/libs/*:\$B/host" apklayer.tools.DenyInstrument /tmp/$NAME-classes.jar /tmp/$NAME-dni.jar

export XDG_RUNTIME_DIR=/run/user/1000 WAYLAND_DISPLAY=wayland-0
JAVA="\$HOME/jdk21-x86/bin/java -XX:TieredStopAtLevel=1 -XX:+UseSerialGC -Dapklayer.fb=/dev/shm/apklayer-fb"
rm -f /dev/shm/apklayer-fb
\$S/bridge/shell/b/apklayer-presentd /dev/shm/apklayer-fb &
cd /tmp
\$JAVA @\$S/runtime/host/jvm.args $APKLAYER_JOPTS \
  -Drobolectric.offline=true \
  -Drobolectric.dependency.dir=\$R/android-all \
  -Dapklayer.wl=\$S/bridge/shell/b/libapklayer-wl.so \
  -Dapklayer.hostcp=\$B/host \
  -cp "\$R/libs/*:\$R/android-stub.jar:\$B/host:/tmp/$NAME-dni.jar" \
  apklayer.host.Main \$A /tmp/$NAME-dni.jar \
  2>&1 | grep --line-buffered -vE '^OpenJDK'
EOF
