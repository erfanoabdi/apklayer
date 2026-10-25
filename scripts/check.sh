#!/usr/bin/env bash
# check: headless verification run on this machine (PLAN D23): build, convert, run a scripted
# session, write snapshots. Needs a JDK 21 and the native runtime for this OS (mac or linux-x86_64).
# Usage: check.sh [app.apk] [script] [snapshot-ms] [run-ms]
#   defaults: uibench, General -> Dialog List -> back -> back, 4 snapshots, 8.5 s
# Output: build/check/<app>-<ms>ms.png and the log on stdout.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APK="${1:-$ROOT/tests/uibench/uibench.apk}"
APK="$(cd "$(dirname "$APK")" && pwd)/$(basename "$APK")"
SCRIPT="${2:-2000:tap:100,80+4000:tap:100,129+6000:back+7000:back}"
SNAPS="${3:-1500,3500,5800,8000}"
RUNMS="${4:-8500}"
NAME="$(basename "$APK" .apk)"
DEPS="$HOME/.cache/apklayer/deps"
OUT="$ROOT/build/check"
if [ -z "${JAVA_HOME:-}" ] && [ -x /usr/libexec/java_home ]; then JAVA_HOME="$(/usr/libexec/java_home -v 21)"; fi
J="${JAVA_HOME:+$JAVA_HOME/bin/}"

"$ROOT/scripts/fetch-deps.sh" "$DEPS" | tail -1
rm -rf "$OUT" && mkdir -p "$OUT/host" "$OUT/d2j" "$OUT/home"
"${J}javac" -nowarn -d "$OUT/host" -cp "$DEPS/libs/*:$DEPS/android-all/*" $(find "$ROOT/runtime/host/java" -name "*.java")
"${J}javac" -nowarn -d "$OUT/d2j" $(find "$ROOT/runtime/dex2jvm" -name "*.java")

# dex2jvm takes the class hierarchy from its classpath (android-all); keep it away from any
# installed Android SDK, which it would otherwise prefer.
ANDROID_HOME="$OUT/home" ANDROID_SDK_ROOT="$OUT/home" "${J}java" -Duser.home="$OUT/home" \
  -cp "$OUT/d2j:$DEPS/android-all/*" com.macapk.dex.Main "$APK" "$OUT/$NAME-classes.jar"
"${J}java" -cp "$DEPS/libs/*:$OUT/host" apklayer.tools.DenyInstrument "$OUT/$NAME-classes.jar" "$OUT/$NAME-dni.jar"

cd "$OUT"
"${J}java" @"$ROOT/runtime/host/jvm.args" -Djava.awt.headless=true \
  -Drobolectric.offline=true -Drobolectric.dependency.dir="$DEPS/android-all" \
  -Dapklayer.script="$SCRIPT" -Dapklayer.snapms="$SNAPS" -Dapklayer.runms="$RUNMS" \
  -Dapklayer.snapshot="$OUT/$NAME" ${APKLAYER_JOPTS:-} \
  -cp "$DEPS/libs/*:$DEPS/android-stub.jar:$OUT/host:$OUT/$NAME-dni.jar" \
  apklayer.host.Main "$APK" "$OUT/$NAME-dni.jar"
