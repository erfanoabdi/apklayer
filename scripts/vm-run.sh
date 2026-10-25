#!/usr/bin/env bash
# vm-run: run an APK on the Robolectric method with our Wayland presenter (on the VM).
# Usage: vm-run.sh [app.apk]        (default: tests/uibench/uibench.apk)
#   Mirrors the deps cache (deps/deps.lock, filled by scripts/fetch-deps.sh on the Mac) and the
#   sources to the VM, builds everything with Meson, converts the APK's DEX with dex2jvm against the
#   android-all framework it runs on, and launches the host.
# Graphics are always NATIVE (real HWUI/Skia via Robolectric's native runtime). Its prebuilts
#   exist for linux-x86_64 only, so on the arm64 VM the app JVM is x86_64 under Rosetta (C2 and
#   G1 crash there: C1 + Serial GC); the native arm64 apklayer-shell owns the Wayland side, so
#   no x86_64 native code is needed. This Rosetta path is a development stopgap until PLAN P7.
#   The shell is (re)started as a session daemon; its log is /tmp/apklayer-shell.log.
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

# Sources -> VM, then one Meson build (shell + dex2jvm.jar + apklayer-host.jar).
vm "rm -rf apklayer-m1 && mkdir -p apklayer-m1"
(cd "$ROOT" && COPYFILE_DISABLE=1 tar -cz meson.build meson_options.txt runtime bridge scripts/javac-jar.sh) \
  | vm "tar -xz -C apklayer-m1 2>/dev/null && cd apklayer-m1 && meson setup b >/dev/null && ninja -C b >/dev/null"
sshpass -p "$VM_PASS" scp -q -o StrictHostKeyChecking=no "$APK" "$VM:apklayer-apps/$NAME.apk"

vm bash -s <<EOF
set -e
R=\$HOME/.cache/apklayer/deps; S=\$HOME/apklayer-m1; A=\$HOME/apklayer-apps/$NAME.apk
HOST=\$S/b/runtime/host/apklayer-host.jar; D2J=\$S/b/runtime/dex2jvm/dex2jvm.jar

# DEX -> JVM classes; the class hierarchy comes from the framework we run on (android-all).
java -XX:UseSVE=0 -cp "\$D2J:\$R/android-all/*" com.macapk.dex.Main \$A /tmp/$NAME-classes.jar
java -XX:UseSVE=0 -cp "\$R/libs/*:\$HOST" apklayer.tools.DenyInstrument /tmp/$NAME-classes.jar /tmp/$NAME-dni.jar

export XDG_RUNTIME_DIR=/run/user/1000 WAYLAND_DISPLAY=wayland-0
JAVA="\$HOME/jdk21-x86/bin/java -XX:TieredStopAtLevel=1 -XX:+UseSerialGC"
pkill -x apklayer-shell || true; sleep 0.3
setsid nohup \$S/b/bridge/shell/apklayer-shell > /tmp/apklayer-shell.log 2>&1 < /dev/null &
for i in \$(seq 1 50); do [ -S \$XDG_RUNTIME_DIR/apklayer/shell.sock ] && break; sleep 0.1; done
cd /tmp
\$JAVA @\$S/runtime/host/jvm.args $APKLAYER_JOPTS \
  -Drobolectric.offline=true \
  -Drobolectric.dependency.dir=\$R/android-all \
  -cp "\$R/libs/*:\$R/android-stub.jar:\$HOST:/tmp/$NAME-dni.jar" \
  apklayer.host.Main \$A /tmp/$NAME-dni.jar \
  2>&1 | grep --line-buffered -vE '^OpenJDK'
sed 's/^/  /' /tmp/apklayer-shell.log | tail -6
EOF
